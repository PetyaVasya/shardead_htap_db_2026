# C4 — Container (L2)

Контейнеры одного DB-узла (shared-nothing per core).

Явных `BEGIN`/`COMMIT` у клиента нет: SQL-запрос идёт в execution → **Storage Manager**, а тот внутри ходит в **Transaction Manager**.

> Рендер: Mermaid C4 (`C4Container`). Если превью Cursor пустое — [mermaid.live](https://mermaid.live).

```mermaid
C4Container
    title Custom DBMS — Containers

    Person(client, "Клиент", "SQL / SDK")

    System_Boundary(node, "DB Node (shared-nothing per core)") {
        Container(api, "RPC Gateway", "gRPC / REST", "Принимает SQL DDL/DML")
        Container(sql, "SQL Engine", "Kotlin", "Parser, binder, vectorized volcano planner")
        Container(exec, "Execution Runtime", "Kotlin", "Векторные операторы, colocated JOIN")
        Container(storage, "Storage Manager", "Kotlin", "Catalog, row cache, columnar disk, batch write, compaction")
        Container(txn, "Transaction Manager", "Kotlin", "Неявные txn, MVCC RR/Serializable, CLOG")
        Container(wal, "WAL", "Kotlin", "REDO-лог, fsync до durable commit")
        Container(cluster, "Cluster Coord", "Kotlin", "Hash multi-master, sync logical replication")
    }

    ContainerDb(files, "Data files", "Column segments + indexes + CLOG + WAL")

    Rel(client, api, "SQL over gRPC / REST")
    Rel(api, sql, "SQL text")
    Rel(sql, exec, "Physical plan")
    Rel(exec, storage, "get / scan / insert / update / delete")
    Rel(storage, txn, "begin / snapshot / commit / abort")
    Rel(txn, wal, "append REDO + commit record")
    Rel(storage, files, "read/write segments via cache & batch flush")
    Rel(wal, files, "append + fsync")
    Rel(cluster, api, "route by shard key")
    Rel(cluster, txn, "logical changes for slaves")
```

## Поток записи (кратко)

1. Клиент шлёт DML (без явной транзакции).  
2. Exec вызывает Storage Manager.  
3. Storage Manager открывает неявную txn через Transaction Manager.  
4. Transaction Manager пишет WAL / CLOG; Storage Manager меняет row cache / готовит batch на диск.  
5. Commit → fsync WAL; flush сегментов на диск — позже (batch).

## Контейнеры

| Контейнер | Роль |
|-----------|------|
| RPC Gateway | Вход SQL |
| SQL Engine | Parser, binder, volcano planner |
| Execution Runtime | Исполнение плана |
| Storage Manager | Данные + каталог; **использует** Transaction Manager |
| Transaction Manager | Неявные ACID-транзакции, MVCC, CLOG |
| WAL | REDO, durability коммита |
| Cluster Coord | Шарды и sync logical replication |
