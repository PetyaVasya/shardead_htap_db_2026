# C4 — Component (L3)

Компоненты SQL, Storage Manager и Transaction Manager.

Optimizer убран: план строит Volcano Planner.  
Row Cache **не** ходит в WAL — только Transaction Manager пишет лог.

> Рендер: Mermaid C4 (`C4Component`). Если превью Cursor пустое — [mermaid.live](https://mermaid.live).

```mermaid
C4Component
    title Custom DBMS — Components

    Container_Boundary(sql, "SQL Engine") {
        Component(parser, "Parser", "Kotlin", "SQL → AST")
        Component(binder, "Binder", "Kotlin", "Имена → catalog")
        Component(planner, "Volcano Planner", "Kotlin", "Bound query → physical plan, vectorized")
    }

    Container_Boundary(execBound, "Execution Runtime") {
        Component(ops, "Operators", "Kotlin", "Scan / Join / Aggregate / DML operators")
    }

    Container_Boundary(storage, "Storage Manager") {
        Component(sm, "Storage Manager", "Kotlin", "Фасад CRUD/scan; открывает неявные txn")
        Component(catalog, "Catalog", "Kotlin", "Таблицы, колонки, shard key")
        Component(rowCache, "Row Cache", "Kotlin", "Построчный hot-path OLTP")
        Component(colStore, "Column Store", "Kotlin", "Колоночные сегменты + compression")
        Component(bufmgr, "Batch Writer", "Kotlin", "Пачками сбрасывает cache → disk")
        Component(compactor, "Compactor", "Kotlin", "Async compaction сегментов")
    }

    Container_Boundary(txnBound, "Transaction Manager") {
        Component(tm, "Transaction Manager", "Kotlin", "begin / commit / abort / snapshot")
        Component(mvcc, "MVCC", "Kotlin", "Версии, RR / Serializable")
        Component(clog, "CLOG", "Kotlin", "Статус commit xid")
        Component(walComp, "WAL", "Kotlin", "REDO records + fsync")
    }

    Rel(parser, binder, "AST")
    Rel(binder, planner, "Bound query")
    Rel(binder, catalog, "resolve schema")
    Rel(planner, ops, "physical plan")
    Rel(ops, sm, "read / write API")
    Rel(sm, tm, "implicit txn + visibility")
    Rel(sm, catalog, "meta")
    Rel(sm, rowCache, "OLTP point access")
    Rel(sm, colStore, "OLAP scan / miss")
    Rel(tm, mvcc, "versioning")
    Rel(tm, clog, "commit status")
    Rel(tm, walComp, "REDO before durable commit")
    Rel(mvcc, clog, "is committed?")
    Rel(bufmgr, rowCache, "dirty pages")
    Rel(bufmgr, colStore, "batch flush")
    Rel(compactor, colStore, "rewrite segments")
```

## Что было не так раньше

| Было | Стало |
|------|--------|
| Optimizer → Row Cache / Column Store | Planner → Operators → Storage Manager |
| Row Cache → WAL | Storage Manager → Transaction Manager → WAL |
| SQL Engine → Txn (явные begin/commit) | Только Storage Manager → Transaction Manager |

## Принципы ТЗ

- Планировщик — vectorized volcano  
- Кеш — построчный (OLTP)  
- Диск — колоночный + сжатие (OLAP)  
- Batch write + async compaction  
- WAL (REDO) + CLOG внутри Transaction Manager  
