# Диаграмма классов

Скелет под лабораторные 1–2: интерфейсы, затем локальные реализации.

Явных транзакций в API клиента нет. `StorageManager` сам использует `TransactionManager` (неявная txn на операцию/запрос).

```mermaid
classDiagram
    direction TB

    class SqlGateway {
        <<interface>>
        +execute(sql: String): ResultSet
        +execute(request: QueryRequest): ResultSet
    }

    class QueryEngine {
        <<interface>>
        +prepare(sql: String): PreparedQuery
        +run(plan: PhysicalPlan): ResultSet
    }

    class Catalog {
        <<interface>>
        +createTable(ddl: CreateTable): TableMeta
        +getTable(name: String): TableMeta
    }

    class TransactionManager {
        <<interface>>
        +begin(): TxnContext
        +commit(txn: TxnContext)
        +abort(txn: TxnContext)
        +snapshot(txn: TxnContext): Snapshot
    }

    class StorageManager {
        <<interface>>
        +get(key: RowKey): Row?
        +insert(row: Row)
        +update(key: RowKey, row: Row)
        +delete(key: RowKey)
        +scan(pred: Predicate): RowCursor
    }

    class Wal {
        <<interface>>
        +append(record: WalRecord)
        +flush()
        +recover()
    }

    class Planner {
        <<interface>>
        +plan(query: BoundQuery): PhysicalPlan
    }

    class Operator {
        <<interface>>
        +open()
        +nextVector(): VectorBatch?
        +close()
    }

    class ShardRouter {
        <<interface>>
        +locate(shardKey: Any): ShardId
        +route(op: DistributedOp): List~NodeRef~
    }

    class Replicator {
        <<interface>>
        +replicate(change: LogicalChange)
        +apply(change: LogicalChange)
    }

    class GrpcSqlService
    class LocalQueryEngine
    class MvccTxnManager
    class HybridStorage
    class FileWal
    class VolcanoPlanner
    class HashShardRouter
    class SyncLogicalReplicator

    SqlGateway <|.. GrpcSqlService
    QueryEngine <|.. LocalQueryEngine
    TransactionManager <|.. MvccTxnManager
    StorageManager <|.. HybridStorage
    Wal <|.. FileWal
    Planner <|.. VolcanoPlanner
    ShardRouter <|.. HashShardRouter
    Replicator <|.. SyncLogicalReplicator

    GrpcSqlService --> QueryEngine
    LocalQueryEngine --> Planner
    LocalQueryEngine --> StorageManager
    LocalQueryEngine --> Catalog
    HybridStorage --> TransactionManager
    HybridStorage --> Catalog
    MvccTxnManager --> Wal
    VolcanoPlanner --> Operator
    Operator --> StorageManager
    GrpcSqlService --> ShardRouter
    SyncLogicalReplicator --> TransactionManager
```

## Зависимости

- `QueryEngine` / `Operator` → `StorageManager` (данные)  
- `StorageManager` → `TransactionManager` (неявные txn, visibility)  
- `TransactionManager` → `Wal` (REDO + fsync)  
- Клиент **не** вызывает `TransactionManager`

## Привязка к лабам

1. Интерфейсы (`StorageManager`, `TransactionManager`, `QueryEngine`, …)  
2. Локальные реализации без сети  
3. `GrpcSqlService` / REST поверх `SqlGateway`  
4–5. `HashShardRouter` + `SyncLogicalReplicator`  
