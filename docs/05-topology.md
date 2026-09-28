# Топология: hash sharding + sync replication

Из ТЗ: шардирование Hash / Multi-Master; репликация синхронная Master-slave, логическая.

```mermaid
flowchart TB
    Client([Client SQL/gRPC]) --> Gateway[Any node / gateway]

    Gateway -->|"hash(shard_key)"| B1
    Gateway -->|"hash(shard_key)"| B2
    Gateway -->|"hash(shard_key)"| B3

    subgraph Cluster["Multi-Master buckets"]
        subgraph B1["Bucket 1"]
            L1[Leader]
            S1a[Slave]
            S1b[Slave]
            L1 -->|"sync logical"| S1a
            L1 -->|"sync logical"| S1b
        end
        subgraph B2["Bucket 2"]
            L2[Leader]
            S2a[Slave]
            S2b[Slave]
            L2 -->|"sync logical"| S2a
            L2 -->|"sync logical"| S2b
        end
        subgraph B3["Bucket 3"]
            L3[Leader]
            S3a[Slave]
            S3b[Slave]
            L3 -->|"sync logical"| S3a
            L3 -->|"sync logical"| S3b
        end
    end
```

## Заметки

- Каждый bucket — свой leader (multi-master на уровне шардов)
- Запись идёт на leader бакета, slaves получают sync logical changes
- Маршрутизация клиента: `hash(shard_key) → bucket → leader`
