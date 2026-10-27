# Interfaces contract

Устные договорённости по текущим интерфейсам. Что не выражено в коде — записано здесь.

## ISP: узкие роли + композиты

Жирные интерфейсы расколоты на узкие по клиентам. Композитные интерфейсы `extends` всем узким — реализациям удобно, клиенты принимают только узкие.

| Композит | Узкие роли |
|---|---|
| `Wal` | `WalWriter { append, fsync }` + `WalReader { replay, replayFrom }` |
| `Clog` | `ClogWriter { mark }` + `ClogReader { status }` |
| `SharedBuffer` | `BufferAppender { append }` + `BufferDrain { drain(ClogReader) }` + `BufferAbort { discard }` + `MemoryBounded` |
| `SegmentSink` | `SegmentWriter { appendSegment }` + `Tombstoner { tombstone }` |
| `ShardRouter` | `ShardLocator { locate }` + `Topology { leaderOf, replicasOf, localShards }` |
| `Replicator` | `ReplicationSender { replicate, catchUp }` + `ReplicationReceiver { apply, appliedLsn }` |
| `HybridStorage` | `MutableTableStore` + `Catalog` + `Statistics` |
| `RowStore` | `MutableTableStore` + `Catalog` + `Statistics` + `MemoryBounded` + `RowDrain` |
| `ColumnStore` | `TableStore` + `Catalog` + `Statistics` + `SegmentSink` |

**Клиенты принимают только то, что зовут.** Пример:
```java
new SpillWorker(RowDrain src, SegmentWriter sink, WalWriter wal) { ... }
//                            ^^^ не SegmentSink  ^^^ не Wal
new TransactionManager(WalWriter wal, ClogWriter clog, BufferAbort buffer) { ... }
```

## Specifications: builder для расширяемости

`ScanSpecification` / `JoinSpecification` имеют nested `Builder`. Это main-way конструирования:
```java
ScanSpecification.builder(table)
    .filter(r -> ...)
    .projection(List.of("c1", "c2"))
    .snapshot(snap)
    .build();
```
Публичный record-конструктор остаётся (для внутренних нужд, валидации, pattern matching), но добавление нового поля ломает **только** внутренние вызовы, не внешних клиентов (они используют builder).

## WAL / durability

- **WAL-first.** Writer сначала `wal.append(DataChange(m)) + wal.fsync()`, затем `buffer.append(m)`. До первого WAL fsync клиенту коммит не возвращается.
- **No-Steal / No-Force / REDO-only.** Pending-мутации никогда не попадают в rowstore (buffer хранит до commit). При recovery проигрываем только `COMMITTED`. UNDO не нужен.
- **`TxnBoundary` обязательна.** Любая явная txn завершается записью `TxnBoundary(xid, COMMITTED|ABORTED)` + fsync до возврата клиенту.
- **`SpillMark` после spill.** Spill rowstore→column пишет `SpillMark(table, segment)` в WAL; recovery до этой отметки по этой таблице скипается.
- **DDL не журналируется.** `createTable`/`dropTable` идут через персистентный Catalog отдельно (не через WAL).
- **LSN.** `Wal.append` возвращает `long lsn` — монотонно растущий id записи. Используется для replication catch-up (`ReplicationSender.catchUp(target, fromLsn)` + `ReplicationReceiver.appliedLsn()`) и `Wal.replayFrom(lsn)`. `WalEntry = (lsn, record)`.

## Dependencies и wiring

- **Статические зависимости — через конструктор реализации.** `Planner(Catalog, Statistics)`, `QueryEngine(Planner, HybridStorage, TransactionManager)`, `SpillWorker(RowDrain, SegmentSink, Wal, Catalog)`, `TransactionManager(Wal, Clog)`.
- **Через параметр метода — только per-call данные:** `sql`, `TableId`, `RowKey`, `Snapshot`, `TxnContext`.

## DML / Read path

- **DML через `HybridStorage` напрямую.** `INSERT`/`UPDATE`/`DELETE` в SQL реализуются прямыми вызовами `storage.insert/update/delete`. Отдельные DML-операторы — опция соагента, не обязательны.
- **Load back — внутренность `HybridStorage`.** UPDATE/DELETE/GET на уехавший в column store ключ: `HybridStorage` сам зовёт `columnStore.get` → `columnStore.tombstone` → `rowstore.insert`. Снаружи это выглядит как обычная операция.
- **OLAP eventual consistency.** Сторонний OLAP-клиент читает `ColumnStore` напрямую и не видит свежие записи, ещё не спиллнутые из rowstore.

## Snapshot / xid / Clog conventions

- **`Snapshot == null` → latest committed.** `TableStore.get(t, k)` без snapshot и `ScanSpecification.snapshot = null` — читают последнее закоммиченное.
- **`Mutation.xid == null` → auto-commit.** Такие мутации считаются `COMMITTED` без обращения к `Clog`.
- **`SharedBuffer.drain(Clog)`** возвращает: `COMMITTED` / `null`-xid — выгружает и убирает из buffer; `ABORTED` — убирает без выдачи; `PENDING` — оставляет в buffer.

## Query / Scan

- **`projection` обязательна к соблюдению на выходе.** Push-down к колоночному чтению — опциональная оптимизация реализации, но наружу курсор отдаёт колонки в порядке `projection`.
- **Агрегатный `scan` выход.** При `aggregates ≠ []` курсор отдаёт `Row` с `values = [groupBy_cols..., aggregates...]`. `Row.key` — синтетический (например, индекс группы). Клиент знает порядок через `ScanSpecification`.
- **JOIN — equi-join по одной колонке** (`joinColumn`). Colocation — свойство **размещения** (один `shardKey` на оба стола), не контракта `JoinSpecification`.

## Column store

- **Самодостаточен для стороннего OLAP.** Свой `Catalog`, `scan`/`get`/`join` учитывают tombstone-ы — сторонний клиент идёт в `ColumnStore` напрямую без `HybridStorage`.
- **`tombstone` зовёт только `HybridStorage`** (при load back). Внешние OLAP-клиенты не триггерят tombstone-ы.
- **`estimateRowCount` — approximate** (сумма row_count сегментов без вычитания tombstone'ов — допустимо).
- **`columnStats` — approximate и может быть stale** (обновляются на spill / compaction / background re-sample). Реализация кэширует, `min`/`max` могут быть `null` (пустая таблица), `distinctCount` допускается HyperLogLog-подобная оценка.

## Replication

- **Sync + all-slaves.** `ReplicationSender.replicate` блокирует до ack от всех slaves bucket'а. Не quorum, не async.
- **`xid` сохраняется.** `LogicalChange.xid` на slave применяется с тем же значением, что на leader — упрощает отладку.
- **LSN в `LogicalChange`.** Каждая реплицируемая пачка несёт `lsn` leader'а. Slave хранит `appliedLsn` — последнюю применённую. При переподключении slave сообщает `appliedLsn`, leader зовёт `catchUp(slave, appliedLsn)` — досылает всё с `lsn > appliedLsn`.

## Cluster / Shard / Shared-nothing

- **`shardKeyIndex` обязателен в `Schema`.** Для single-node ставить `shardKeyIndex = pkIndex`.
- **`ShardKey` — обёртка над `Object`.** Vызывающий извлекает значение из `row.values()[schema.shardKeyIndex()]`.
- **`HybridStorage` всегда локальный.** Не делает RPC, не знает про кластер — обслуживает только bucket'ы этого узла. Любая попытка UPDATE/INSERT/GET чужого bucket'а — ошибка либо бессмысленный ответ.
- **`Gateway` — единственная точка входа кластерных операций.** `execute(sql)` смотрит `router.locate → leaderOf`: если `== self` — зовёт локальный `QueryEngine` → `HybridStorage`; иначе — RPC на leader. На любом узле есть свой Gateway, клиент стучится в ближайший.
- **Multi-master.** Один узел — leader для одних `ShardId` и slave для других. `router.localShards()` — всё, что держит узел (leader и replica). `router.leaderOf(shard)` — где принимается write.
- **Replication строго внутри bucket'а.** `ReplicationSender.replicate` на leader'е шлёт только своим slaves (не всему кластеру). Разные bucket'ы друг про друга не знают по данным.
- **Topology — статический конфиг.** `ShardRouter` принимает карту `bucket → (leader, replicas)` в конструкторе. Dynamic discovery / rebalancing — не в MVP.
- **Cross-shard SQL (напр. `SELECT COUNT(*) FROM users`) в MVP не поддерживается.** Запрос обслуживается только для одного bucket'а (определённого по `WHERE shardKey = ...`). Scatter-gather — следующая итерация.

## Buffer / Spill flow

```
insert/update/delete:
  wal.append(DataChange(m)) + wal.fsync()
  buffer.append(m)

commit:
  wal.append(TxnBoundary(xid, COMMITTED)) + wal.fsync()
  clog.mark(xid, COMMITTED)

abort:
  clog.mark(xid, ABORTED)
  buffer.discard(xid)

spill (bytesUsed ≥ bytesLimit):
  // buffer → rowstore
  try (c = buffer.drain(clog)) while c.next(): rowstore.apply(m)
  // rowstore → column (если переполнен)
  seg = colStore.appendSegment(t, rowStore.drain(t))
  wal.append(SpillMark(t, seg)) + wal.fsync()
```
