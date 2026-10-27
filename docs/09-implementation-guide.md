# Implementation guide for agents

Инструкция для LLM-агентов, которые будут писать реализации по уже готовым интерфейсам.
Контракт по семантике — в `08-interfaces-contract.md`. Этот документ — **что** и **в каком порядке** строить.

## Как работает агент

1. Берёт один слой из build order ниже (строго снизу вверх).
2. Пишет реализацию интерфейса(ов) этого слоя **в подпакете `impl/`** соответствующего модуля.
3. Запускает self-check (минимальный программный тест в `src/test/java`).
4. Переходит к следующему слою — к этому моменту все нижележащие уже готовы и используются как-есть.
5. Не трогает публичные интерфейсы (всё, что НЕ в `impl/`) — только добавляет реализации.

## Структура: интерфейсы vs реализации

**Convention: `impl/` — поддиректория для реализаций в каждом модуле.**

```
storage/
├── TableStore.java              ← интерфейс (на уровне пакета)
├── Catalog.java
├── Statistics.java
├── HybridStorage.java
├── ...
├── impl/
│   ├── HybridStorageImpl.java   ← реализация (в impl/)
│   └── package-info.java
├── wal/
│   ├── Wal.java                 ← интерфейсы
│   ├── WalWriter.java
│   ├── WalReader.java
│   ├── WalRecord.java
│   └── impl/
│       └── FileWal.java         ← реализация
├── rowstore/
│   ├── RowStore.java
│   └── impl/
│       └── InMemoryRowStore.java
└── (и так далее)
```

**Правила:**
- Файл на уровне пакета (`storage/`, `storage/wal/`, …) = **интерфейс** или **record/DTO**.
- Файл в `impl/` = **реализация** (`class`, `final class`).
- Composite-интерфейсы (`Wal extends WalWriter, WalReader`) живут **с интерфейсами**, не в `impl/`.
- Internal utility классы (helper'ы одной реализации) — тоже в `impl/`.
- `common/` — только DTO, подпакета `impl/` нет.

Если интерфейс оказался недостаточен — остановись и выпиши пропущенное одним списком, не правь интерфейс самостоятельно.

## Каталог интерфейсов

### `common/` — DTO (уже есть, ничего реализовывать не нужно)
`Row` строка, `RowKey` ключ строки, `TableId` id таблицы, `Column` / `ColumnType` / `Schema` описание таблицы (включая `shardKeyIndex`), `Mutation` + `Op` одна операция записи, `SegmentId` id колоночного сегмента, `ShardKey` ключ шардирования, `TxnId` id транзакции, `SortKey` + `Direction` сортировка, `Aggregate` + `AggregateFunction` агрегат, `ColumnStats` статистика колонки.

### `storage/` — ядро хранения
- **`Catalog`** DDL и lookup схемы по `TableId`.
- **`Statistics`** приблизительные стaтсы (rowCount, columnStats) для планировщика.
- **`MemoryBounded`** учёт памяти (`bytesUsed`, `bytesLimit`), для spill-watermark.
- **`TableStore`** read API: get, scan, join по спецификациям.
- **`MutableTableStore`** extends `TableStore` + DML.
- **`RowDrain`** отдать все строки таблицы и очистить (источник spill).
- **`SegmentWriter`** записать колоночный сегмент на диск.
- **`Tombstoner`** пометить ключ "уехал обратно в rowstore".
- **`SegmentSink`** композит `SegmentWriter + Tombstoner`.
- **`HybridStorage`** facade OLTP: `MutableTableStore + Catalog + Statistics`.
- **`ScanSpecification` / `JoinSpecification`** параметры read-запросов (с builder'ом).
- **`RowCursor` / `JoinedRowCursor` / `MutationCursor`** ленивые курсоры с `close()`.

### `storage/rowstore/`
- **`RowStore`** in-mem хранилище для OLTP. Композит из `MutableTableStore + Catalog + Statistics + MemoryBounded + RowDrain`.

### `storage/colstore/`
- **`ColumnStore`** on-disk колоночное хранилище для OLAP. Композит из `TableStore + Catalog + Statistics + SegmentSink`.

### `storage/buffer/`
- **`BufferAppender`** writer кладёт мутацию.
- **`BufferDrain`** `drain(ClogReader)` отдаёт committed, оставляет pending, выкидывает aborted.
- **`BufferAbort`** `discard(xid)` явно удалить мутации txn.
- **`SharedBuffer`** композит + `MemoryBounded`.

### `storage/wal/`
- **`WalWriter`** `append(WalRecord) → lsn` + `fsync`.
- **`WalReader`** `replay()` / `replayFrom(lsn)`.
- **`Wal`** композит.
- **`WalRecord`** sealed: `DataChange(mutation)`, `TxnBoundary(xid, status)`, `SpillMark(table, segment)`.
- **`WalEntry(lsn, record)`**, **`WalEntryCursor`**.

### `storage/spill/`
- **`SpillPolicy`** решение «пора ли спиллить» на основе `MemoryBounded`.
- **`SpillWorker`** оркестратор: `spill(TableId)` / `spillAll()`.

### `txn/`
- **`ClogWriter`** `mark(xid, status)`.
- **`ClogReader`** `status(xid)`.
- **`Clog`** композит.
- **`Snapshot`** `isVisible(xid)` для MVCC-видимости.
- **`TransactionManager`** begin / commit / abort / snapshot.
- **`TxnContext(id, isolation)`**, **`TxnId`**, **`TxnStatus`**, **`IsolationLevel`**.

### `query/`
- **`PhysicalPlan`** marker. Реализация пусть делает sealed hierarchy внутри.
- **`Planner`** `plan(sql): PhysicalPlan`.
- **`QueryEngine`** `execute(sql)` / `run(plan): RowCursor`.
- **`query/operator/Operator`** extends `RowCursor` + `open()` — Volcano iterator.

### `cluster/`
- **`ShardLocator`** `locate(ShardKey): ShardId`.
- **`Topology`** `leaderOf` / `replicasOf` / `localShards`.
- **`ShardRouter`** композит.
- **`Gateway`** `execute(sql)`: решает local vs RPC.
- **`ShardId`**, **`NodeRef`**.

### `replication/`
- **`ReplicationSender`** `replicate(change)` sync + `catchUp(target, fromLsn)`.
- **`ReplicationReceiver`** `apply(change)` + `appliedLsn()`.
- **`Replicator`** композит.
- **`LogicalChange(lsn, xid, mutations)`**.

### `rest/`
- **`RestServer`** HTTP entry: start/close. Тонкий адаптер поверх `Gateway`.

## Build order (снизу вверх)

Таблица: что ↔ куда положить ↔ принять в конструктор ↔ как проверить.

### Шаг 1 — WAL
- **Класс:** `storage/wal/FileWal implements Wal`.
- **Конструктор:** `Path logFile` (сам создаёт/дооткрывает).
- **Что делает:** бинарный append-only файл; `append` возвращает монотонный `lsn`, `fsync` принудительно сбрасывает, `replay`/`replayFrom` читают назад.
- **Self-check:** append 3 `DataChange` + `fsync` → переоткрыть → `replay()` вернул 3 записи, lsn монотонны.

### Шаг 2 — Clog
- **Класс:** `txn/InMemoryClog implements Clog`.
- **Конструктор:** нет.
- **Что делает:** `ConcurrentHashMap<TxnId, TxnStatus>`. Для MVP не персистится.
- **Self-check:** `mark(x1, COMMITTED)` → `status(x1) == COMMITTED`; `status(unknown)` → `null` или `PENDING` (договориться — рекомендую `null`).

### Шаг 3 — ColumnStore
- **Класс:** `storage/colstore/DiskColumnStore implements ColumnStore`.
- **Конструктор:** `Path dataDir`.
- **Что делает:**
  - Файл на `(table, segment)` или один файл на таблицу с индексом.
  - `appendSegment(table, RowCursor)` → читает все rows, конвертит в column-major, пишет на диск, возвращает `SegmentId`.
  - `tombstone(table, key)` — append в in-mem/on-disk bitmap/множество.
  - `scan`/`get`/`join` — читают сегменты, применяют tombstone-overlay, применяют projection/filter/sort.
  - `columnStats` считает по сегментам, кэширует.
- **Self-check:** createTable → appendSegment 100 строк → `get(key5)` возвращает; `tombstone(key5)` → `get` пусто, `scan` даёт 99.

### Шаг 4 — RowStore
- **Класс:** `storage/rowstore/InMemoryRowStore implements RowStore`.
- **Конструктор:** `long bytesLimit`.
- **Что делает:** `Map<TableId, Map<RowKey, Row>>` + атомарный счётчик байт (оценка через фиксированные размеры по `ColumnType`). `drain(t)` отдаёт cursor + очищает таблицу.
- **Self-check:** insert N → `bytesUsed` растёт; `drain(t)` → N строк, таблица пустая, схема сохранилась.

### Шаг 5 — SharedBuffer
- **Класс:** `storage/buffer/InMemorySharedBuffer implements SharedBuffer`.
- **Конструктор:** `long bytesLimit`.
- **Что делает:** FIFO-очередь мутаций с атомарным счётчиком байт. `drain(ClogReader)` возвращает только committed (`xid == null` или `status == COMMITTED`), `ABORTED` выкидывает, `PENDING` оставляет. `discard(xid)` — убрать все с этим xid.
- **Self-check:** append(m1 xid=null, m2 xid=X1), clog(X1=PENDING) → drain вернул только m1; discard(X1) → drain пусто.

### Шаг 6 — SpillPolicy
- **Класс:** `storage/spill/WatermarkSpillPolicy implements SpillPolicy`.
- **Конструктор:** `double threshold` (например 0.8).
- **Что делает:** `shouldSpill(source) = source.bytesUsed() >= source.bytesLimit() * threshold`.
- **Self-check:** FakeMemoryBounded с заданными bytesUsed/bytesLimit — пограничные случаи.

### Шаг 7 — SpillWorker
- **Класс:** `storage/spill/SpillWorkerImpl implements SpillWorker`.
- **Конструктор:** `RowDrain source, SegmentWriter sink, WalWriter wal, Catalog catalog`.
- **Что делает:**
  ```
  spill(t):
      try (c = source.drain(t)) {
          SegmentId seg = sink.appendSegment(t, c);
          wal.append(new SpillMark(t, seg));
          wal.fsync();
      }
  spillAll():
      для каждой t из catalog.tables(): spill(t)
  ```
- **Self-check:** rowstore с 100 строк → spill → колоночный сегмент имеет 100 строк, rowstore пуст, WAL содержит `SpillMark`.

### Шаг 8 — TransactionManager
- **Класс:** `txn/MvccTxnManager implements TransactionManager`.
- **Конструктор:** `WalWriter wal, ClogWriter clog, BufferAbort buffer`.
- **Что делает:**
  - `begin(level)` → новый `TxnId` (atomic counter) + `TxnContext`. **Не** пишет в WAL (REDO-only: boundary только при commit/abort).
  - `commit(ctx)` → `wal.append(TxnBoundary(xid, COMMITTED)) + wal.fsync()` → `clog.mark(xid, COMMITTED)`.
  - `abort(ctx)` → `wal.append(TxnBoundary(xid, ABORTED)) + wal.fsync()` → `clog.mark(xid, ABORTED)` → `buffer.discard(xid)`.
  - `snapshot(ctx)` → возвращает immutable Snapshot, который фиксирует множество committed xids на момент вызова.
- **Self-check:** begin → commit → clog.status = COMMITTED, WAL имеет TxnBoundary; begin → abort → clog = ABORTED.

### Шаг 9 — HybridStorage
- **Класс:** `storage/HybridStorageImpl implements HybridStorage`.
- **Конструктор:** `RowStore rs, ColumnStore cs, SharedBuffer buf, WalWriter wal, TransactionManager tm, Clog clog, SpillPolicy policy, SpillWorker worker`.
- **Что делает:**
  - **insert/update/delete** → implicit txn: `tm.begin()`, собрать `Mutation(..., xid)`, `wal.append(DataChange)` + fsync, `buffer.append`, `tm.commit`. Проверить `policy.shouldSpill(buf)` → `drainBufferToRowstore()`. Проверить `policy.shouldSpill(rs)` → `worker.spillAll()`.
  - **update на уехавший ключ:** если `rs.get(k).isEmpty()` и `cs.get(t, k, null).isPresent()` → `cs.tombstone(t, k)` + продолжить как обычный update (попадёт в rowstore через drain).
  - **get(t, k)** → сначала rs, потом cs. Для MVCC — передать snapshot.
  - **scan/join** → по rs + cs (union, но для MVP: только rs; OLAP идёт прямо в cs).
  - **drainBufferToRowstore:** внутренняя функция, зовётся из insert и из планового spill.
- **Self-check:** 1000 insert'ов с маленьким bytesLimit → хотя бы один spill → все 1000 читаются; UPDATE уехавшего ключа работает; crash+recovery по WAL восстанавливает всё committed.

### Шаг 10 — Query (отдаётся отдельному агенту-"планировщику")
- **Классы:** `query/SqlParser, VolcanoPlanner implements Planner, LocalQueryEngine implements QueryEngine`, операторы в `query/operator/`: `ScanOperator`, `JoinOperator`, `AggregateOperator`, `SortOperator`, `InsertOperator`/`UpdateOperator`/`DeleteOperator` (опционально).
- **Конструктор QueryEngine:** `Planner planner, HybridStorage storage, TransactionManager tm`.
- **Конструктор Planner:** `Catalog catalog, Statistics stats`.
- **Что делает:** SQL → AST → bound query → PhysicalPlan (tree of Operator) → `run(plan)` исполняет через open/next/close.
- **Self-check:** `CREATE TABLE t ...`, вставить 10 строк, `SELECT * FROM t WHERE x > 5` → ожидаемые строки; `SELECT COUNT(*)` → 10.

### Шаг 11 — Cluster (отдаётся отдельному агенту-"сетевику")
- **Классы:** `cluster/StaticShardRouter implements ShardRouter`, `cluster/LocalGateway implements Gateway`.
- **Конструктор `StaticShardRouter`:** `Map<ShardId, ShardInfo> topology, NodeRef self` (ShardInfo = leader + replicas).
- **Конструктор `LocalGateway`:** `ShardRouter router, QueryEngine engine, NodeRef self, RpcTransport rpc`.
- **Что делает:** `Gateway.execute(sql)` парсит минимум чтобы вытащить `WHERE shardKey = X` → `locate(X)` → `leaderOf`. Если self — local engine; иначе `rpc.send(leader, sql)`.
- **Self-check:** single-node config (все shards local) → execute вызывает local; two-node fake config → execute на нужный узел роутится.

### Шаг 12 — REST
- **Класс:** `rest/HttpRestServer implements RestServer`.
- **Конструктор:** `Gateway gateway, int port`.
- **Что делает:**
  - Поднимает `com.sun.net.httpserver.HttpServer` (JDK built-in, без deps) на порту.
  - `POST /sql`: читает тело как SQL, зовёт `gateway.execute(sql)`, собирает `RowCursor` в `{columns, rows, rowsAffected}` → JSON.
  - JSON сериализация — вручную (10-20 строк для `Object → String|Number|null`) или через jackson, если решите добавить зависимость.
  - Ошибки — `400 {"error":"..."}`.
- **Self-check:** start → `curl -X POST -d 'CREATE TABLE t (id INT PK SHARDKEY)' localhost:PORT/sql` → 200; `curl -d 'SELECT * FROM t'` → `{"columns":["id"],"rows":[],"rowsAffected":0}`.

### Шаг 13 — Replication (последний)
- **Классы:** `replication/SyncLogicalReplicator implements Replicator`.
- **Конструктор:** `WalReader wal, ShardRouter router, RpcTransport rpc, MutableTableStore localStorage, ClogWriter localClog, AtomicLong appliedLsn`.
- **Что делает:**
  - `replicate(change)` — на leader'е после fsync WAL: отправить всем replicas из `router.replicasOf(shard)`, подождать ack от всех.
  - `apply(change)` — на slave: применить мутации в localStorage, обновить appliedLsn.
  - `catchUp(target, fromLsn)` — читает `wal.replayFrom(fromLsn)`, группирует по xid в `LogicalChange`, шлёт target.
- **Self-check:** двухузловой тест: insert на leader → slave имеет те же данные; остановить slave, вставить 10, поднять → slave догнал через catchUp.

## Пример wiring (single-node Main)

```java
public static void main(String[] args) throws IOException {
    Path dataDir = Path.of("data"); Files.createDirectories(dataDir);
    NodeRef self = new NodeRef("node-1");

    // 1. Persistence
    Wal   wal  = new FileWal(dataDir.resolve("wal.log"));
    Clog  clog = new InMemoryClog();

    // 2+3. Storage
    ColumnStore cs = new DiskColumnStore(dataDir.resolve("columns"));
    RowStore    rs = new InMemoryRowStore(64L * 1024 * 1024);

    // 4. Buffer
    SharedBuffer buf = new InMemorySharedBuffer(16L * 1024 * 1024);

    // 5+6. Spill
    SpillPolicy policy = new WatermarkSpillPolicy(0.8);
    SpillWorker spill  = new SpillWorkerImpl(rs, cs, wal, rs);

    // 7. Transactions
    TransactionManager tm = new MvccTxnManager(wal, clog, buf);

    // 8. Facade
    HybridStorage storage = new HybridStorageImpl(rs, cs, buf, wal, tm, clog, policy, spill);

    // 9. Query
    Planner planner    = new VolcanoPlanner(storage, storage);  // Catalog + Statistics via HybridStorage
    QueryEngine engine = new LocalQueryEngine(planner, storage, tm);

    // 10. Cluster (single-node)
    ShardRouter router = StaticShardRouter.singleNode(self);
    Gateway gateway    = new LocalGateway(router, engine, self, /* rpc stub */ null);

    // 11. REST
    RestServer rest = new HttpRestServer(gateway, 8080);
    rest.start();
    Runtime.getRuntime().addShutdownHook(new Thread(rest::close));

    // 12. Replication (единственный узел — не нужен; на cluster-stage поднимем)

    // Recovery: replay WAL до steady state
    recoverFromWal(wal, clog, buf, rs, cs);

    // Готово — принимаем команды
    gateway.execute("CREATE TABLE users (id INT PK SHARDKEY, name STRING)");
    gateway.execute("INSERT INTO users VALUES (1, 'alice')");
}
```

Recovery (можно вынести в `storage/RecoveryService`):
```
try (c = wal.replay())
    while c.next() switch c.entry().record() {
        case DataChange(m)               -> buf.append(m)
        case TxnBoundary(xid, COMMITTED) -> clog.mark(xid, COMMITTED)
        case TxnBoundary(xid, ABORTED)   -> clog.mark(xid, ABORTED)
        case SpillMark(t, seg)           -> /* skip accumulated for t */
    }
// затем drain buf → rs
```

## Правила для агентов

1. **Снизу вверх, строго по шагам.** Шаг N не трогает ничего из шага N+1.
2. **Один слой = один коммит.** Коммит содержит реализацию + self-check в `src/test/java/.../XxxSelfTest.java`.
3. **Публичные интерфейсы в `storage/`/`txn/`/`query/`/`cluster/`/`replication/` — read-only.** Если не хватает — остановись, выпиши пропущенное.
4. **Конструктор принимает узкие интерфейсы.** Не весь `RowStore` если нужен только `RowDrain`.
5. **Не вводить `null`-возвраты, кроме задокументированных случаев** (snapshot=null, xid=null, min/max в ColumnStats).
6. **Thread-safety:** для MVP достаточно внешнего mutex в `HybridStorage`; concurrent collections в rowstore/buffer приветствуются.
7. **Контракты по семантике — `08-interfaces-contract.md`.** Не отступать.

## Что НЕ входит в MVP

- Compaction колоночных сегментов.
- Cross-shard агрегаты (scatter-gather).
- Reference-таблицы (non-sharded).
- Composite shard key.
- Admin API (flush/checkpoint/compact).
- HAVING, DISTINCT, LIMIT, OFFSET в SQL.
- Compression колоночных страниц (можно позже — внутренность `DiskColumnStore`).
- Rebalancing / dynamic topology discovery.
- 2PC для distributed txn.
