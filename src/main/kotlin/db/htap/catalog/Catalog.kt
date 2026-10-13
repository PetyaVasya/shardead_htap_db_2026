package db.htap.catalog

import db.htap.model.Schema
import java.util.concurrent.ConcurrentHashMap

interface Catalog {
    fun create(schema: Schema): Schema
    fun get(name: String): Schema
    fun exists(name: String): Boolean
    fun tables(): List<String>
}

class InMemoryCatalog : Catalog {
    private val tables = ConcurrentHashMap<String, Schema>()
    override fun create(schema: Schema): Schema {
        require(tables.putIfAbsent(schema.name, schema) == null) {
            "table '${schema.name}' already exists"
        }
        return schema
    }
    override fun get(name: String): Schema = tables[name] ?: error("table '$name' not found")
    override fun exists(name: String) = tables.containsKey(name)
    override fun tables(): List<String> = tables.keys.sorted()
}
