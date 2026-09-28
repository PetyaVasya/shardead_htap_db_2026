# C4 — Context (L1)

Система в окружении клиентов, оператора, диска и пиров кластера.

> Рендер: Mermaid C4 (`C4Context`). В превью Cursor может не открыться — смотри на [mermaid.live](https://mermaid.live) или в VS Code с поддержкой C4.

```mermaid
C4Context
    title Custom DBMS — System Context

    Person(dev, "Разработчик / клиент", "Пишет SQL через SDK или HTTP/gRPC")
    Person(ops, "Оператор", "Деплой, шарды, реплики")

    System(db, "Custom DBMS", "CP, ACID+MVCC, OLTP+OLAP, Kotlin")

    System_Ext(disk, "Локальный диск", "Колоночные сегменты + WAL + CLOG")
    System_Ext(peers, "Пиры кластера", "Hash-шарды, sync logical replication")

    Rel(dev, db, "SQL / gRPC / REST")
    Rel(ops, db, "Конфиг, топология")
    Rel(db, disk, "Чтение/запись сегментов, WAL flush")
    Rel(db, peers, "Репликация + маршрутизация шардов")
```

## Требования (из ТЗ)

- CP
- Kotlin + Gradle
- Интерфейсы → локальная БД → gRPC/REST → репликация/шардирование
