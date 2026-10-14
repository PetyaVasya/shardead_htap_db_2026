package db.htap

import db.htap.cache.MvccRowCache
import db.htap.catalog.InMemoryCatalog
import db.htap.model.Column
import db.htap.model.DataType
import db.htap.model.Row
import db.htap.model.RowKey
import db.htap.model.Schema
import db.htap.txn.MvccTxnManager
import db.htap.wal.FileWal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecoveryTest {

    private fun schema() = Schema(
        "t",
        listOf(Column("id", DataType.INT, nullable = false), Column("v", DataType.STRING)),
        listOf("id"),
    )

    @Test fun `committed mutations replayed, aborted dropped`(@TempDir dir: Path) {
        val walPath = dir.resolve("wal.log")

        // Phase 1: write some data, commit some, abort one, crash.
        run {
            val cat = InMemoryCatalog().apply { create(schema()) }
            val wal = FileWal(walPath)
            val tm = MvccTxnManager(wal)
            val cache = MvccRowCache(cat, tm)

            val a = tm.begin()
            cache.insert(a, "t", Row(listOf(1L, "alice")))
            cache.insert(a, "t", Row(listOf(2L, "bob")))
            tm.commit(a)

            val b = tm.begin()
            cache.insert(b, "t", Row(listOf(3L, "ghost")))
            tm.abort(b)

            val c = tm.begin()
            cache.update(c, "t", RowKey(listOf(1L)), Row(listOf(1L, "ALICE")))
            cache.delete(c, "t", RowKey(listOf(2L)))
            tm.commit(c)

            wal.close()
        }

        // Phase 2: fresh objects, replay WAL.
        val cat = InMemoryCatalog().apply { create(schema()) }
        val wal = FileWal(walPath)
        val tm = MvccTxnManager(wal)
        val cache = MvccRowCache(cat, tm)
        Recovery.replay(wal, cache, tm)

        val reader = tm.begin()
        assertEquals(Row(listOf(1L, "ALICE")), cache.get(reader, "t", RowKey(listOf(1L))))
        assertNull(cache.get(reader, "t", RowKey(listOf(2L))), "deleted row stays gone")
        assertNull(cache.get(reader, "t", RowKey(listOf(3L))), "aborted insert must not replay")
        wal.close()
    }
}
