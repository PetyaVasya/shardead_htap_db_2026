# Sequence: OLTP write (неявная txn)

Клиент не шлёт `BEGIN`/`COMMIT`. Их открывает Storage Manager через Transaction Manager.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant G as Gateway
    participant E as Execution
    participant S as StorageMgr
    participant T as TxnMgr
    participant W as WAL
    participant M as RowCache
    participant D as ColStore

    C->>G: UPDATE statement
    G->>E: physical plan
    E->>S: update row
    S->>T: begin
    T->>W: BEGIN T1
    S->>T: snapshot
    S->>M: read and update cache
    S->>T: log redo
    T->>W: REDO record
    Note over D: disk still has old data
    S->>T: commit
    T->>W: COMMIT T1
    T->>W: fsync
    T-->>S: committed
    S-->>E: ok
    E-->>C: ResultSet
    M->>D: later batch flush
```

## Принцип

1. DML без явной транзакции от клиента  
2. Storage Manager: `begin` → mutate cache → REDO через TxnManager → `commit`  
3. Durability коммита = fsync WAL  
4. Batch flush cache → columnar disk — асинхронно позже  
