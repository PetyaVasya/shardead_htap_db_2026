package db.htap

import db.htap.cache.MvccRowCache
import db.htap.catalog.InMemoryCatalog
import db.htap.model.Column
import db.htap.model.DataType
import db.htap.model.Row
import db.htap.model.RowKey
import db.htap.model.Schema
import db.htap.txn.MvccTxnManager
import db.htap.txn.WriteConflictException
import db.htap.wal.FileWal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private fun fixture(dir: Path): Triple<MvccRowCache, MvccTxnManager, FileWal> {
    val catalog = InMemoryCatalog()
    catalog.create(
        Schema(
            name = "t",
            columns = listOf(
                Column("id", DataType.INT, nullable = false),
                Column("v", DataType.STRING),
            ),
            pkColumns = listOf("id"),
        )
    )
    val wal = FileWal(dir.resolve("wal.log"))
    val tm = MvccTxnManager(wal)
    return Triple(MvccRowCache(catalog, tm), tm, wal)
}

private fun row(id: Long, v: String?) = Row(listOf(id, v))
private fun key(id: Long) = RowKey(listOf(id))

class MvccRowCacheTest {

    @Test fun `read-your-writes`(@TempDir dir: Path) {
        val (c, tm) = fixture(dir)
        val t = tm.begin()
        c.insert(t, "t", row(1, "a"))
        assertEquals(row(1, "a"), c.get(t, "t", key(1)))
        tm.commit(t)
    }

    @Test fun `snapshot isolation hides uncommitted writes`(@TempDir dir: Path) {
        val (c, tm) = fixture(dir)
        val reader = tm.begin()
        val writer = tm.begin()
        c.insert(writer, "t", row(1, "a"))
        assertNull(c.get(reader, "t", key(1)))
        tm.commit(writer)
        assertNull(c.get(reader, "t", key(1)), "RR must still hide post-snapshot commit")
    }

    @Test fun `later txn sees committed writes`(@TempDir dir: Path) {
        val (c, tm) = fixture(dir)
        val w = tm.begin()
        c.insert(w, "t", row(1, "a"))
        tm.commit(w)
        val r = tm.begin()
        assertEquals(row(1, "a"), c.get(r, "t", key(1)))
        tm.commit(r)
    }

    @Test fun `delete hides row from scan`(@TempDir dir: Path) {
        val (c, tm) = fixture(dir)
        val w = tm.begin()
        c.insert(w, "t", row(1, "a"))
        c.insert(w, "t", row(2, "b"))
        tm.commit(w)
        val d = tm.begin()
        c.delete(d, "t", key(1))
        assertEquals(listOf(row(2, "b")), c.scan(d, "t").toList())
        tm.commit(d)
        val r = tm.begin()
        assertEquals(listOf(row(2, "b")), c.scan(r, "t").toList())
    }

    @Test fun `write-write conflict aborts second writer`(@TempDir dir: Path) {
        val (c, tm) = fixture(dir)
        val w0 = tm.begin()
        c.insert(w0, "t", row(1, "a"))
        tm.commit(w0)
        val a = tm.begin()
        val b = tm.begin()
        c.update(a, "t", key(1), row(1, "A"))
        assertFailsWith<WriteConflictException> { c.update(b, "t", key(1), row(1, "B")) }
    }

    @Test fun `aborted txn invisible to later readers`(@TempDir dir: Path) {
        val (c, tm) = fixture(dir)
        val w = tm.begin()
        c.insert(w, "t", row(1, "a"))
        tm.abort(w)
        val r = tm.begin()
        assertNull(c.get(r, "t", key(1)))
    }

    @Test fun `rr sees stable snapshot across repeated reads`(@TempDir dir: Path) {
        val (c, tm) = fixture(dir)
        val w0 = tm.begin()
        c.insert(w0, "t", row(1, "a"))
        tm.commit(w0)

        val r = tm.begin()
        assertEquals(row(1, "a"), c.get(r, "t", key(1)))
        val w1 = tm.begin()
        c.update(w1, "t", key(1), row(1, "b"))
        tm.commit(w1)
        assertEquals(row(1, "a"), c.get(r, "t", key(1)), "RR must not see the newer committed version")
    }
}
