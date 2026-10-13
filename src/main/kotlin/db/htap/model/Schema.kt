package db.htap.model

enum class DataType { INT, DOUBLE, STRING, BOOL }

data class Column(val name: String, val type: DataType, val nullable: Boolean = true)

data class Schema(
    val name: String,
    val columns: List<Column>,
    val pkColumns: List<String>,
) {
    val columnIndex: Map<String, Int> = columns.withIndex().associate { (i, c) -> c.name to i }
    val pkIndices: IntArray = pkColumns.map {
        columnIndex[it] ?: error("pk column '$it' not in schema '$name'")
    }.toIntArray()

    init {
        require(pkColumns.isNotEmpty()) { "schema '$name' needs at least one pk column" }
        pkIndices.forEach { i ->
            require(!columns[i].nullable) { "pk column '${columns[i].name}' cannot be nullable" }
        }
    }
}

data class RowKey(val values: List<Any>) {
    init { require(values.isNotEmpty()) { "RowKey must have at least one component" } }
}

data class Row(val values: List<Any?>) {
    fun keyFor(schema: Schema): RowKey =
        RowKey(schema.pkIndices.map {
            values[it] ?: error("pk column '${schema.columns[it].name}' is null")
        })
}
