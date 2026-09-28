# Data flow: OLTP cache vs OLAP disk

Гибридное хранение из ТЗ. Мутации идут через Storage Manager → Transaction Manager, не напрямую cache → WAL.

```mermaid
flowchart LR
    Q[Query / Operator] --> SM[Storage Manager]
    SM --> TM[Transaction Manager]
    TM --> WAL[WAL REDO]
    TM --> CLOG[CLOG]

    SM --> P{Тип доступа}
    P -->|GET / UPDATE / точечный| RC[Row Cache<br/>построчный]
    P -->|Scan / GROUP BY / OLAP| CS[Column Segments<br/>disk + compression]

    RC -->|miss / eviction| CS
    RC -->|batch write| CS
    CS --> COMP[Async Compaction]
```

## SQL-поверхность (из ТЗ)

**DML:** colocational JOIN, INSERT, UPDATE, GET, DELETE, WHERE, GROUP BY  

**DDL:** CREATE TABLE  
