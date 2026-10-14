package db.htap

import db.htap.model.Row
import db.htap.model.RowKey
import db.htap.wal.FileWal
import db.htap.wal.WalRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class FileWalTest {
    @Test fun `append and readAll roundtrip`(@TempDir dir: Path) {
        val path = dir.resolve("wal.log")
        FileWal(path).use { wal ->
            wal.append(WalRecord.Begin(1))
            wal.append(WalRecord.Insert(1, "users", Row(listOf(42L, "alice", null))))
            wal.append(WalRecord.Update(1, "users", RowKey(listOf(42L)), Row(listOf(42L, "ALICE", true))))
            wal.append(WalRecord.Delete(1, "users", RowKey(listOf(42L))))
            wal.append(WalRecord.Commit(1))
            wal.flush()
        }
        val read = FileWal(path).use { it.readAll().toList() }
        assertEquals(5, read.size)
        assertEquals(WalRecord.Begin(1), read[0])
        assertEquals(WalRecord.Commit(1), read[4])
        val ins = read[1] as WalRecord.Insert
        assertEquals(listOf(42L, "alice", null), ins.row.values)
    }

    @Test fun `truncated tail is ignored`(@TempDir dir: Path) {
        val path = dir.resolve("wal.log")
        FileWal(path).use {
            it.append(WalRecord.Begin(1))
            it.append(WalRecord.Commit(1))
            it.flush()
        }
        // Append garbage to simulate a half-written frame.
        java.io.FileOutputStream(path.toFile(), true).use { fos ->
            fos.write(byteArrayOf(0, 0, 0, 50, 1, 2, 3))
        }
        val read = FileWal(path).use { it.readAll().toList() }
        assertEquals(2, read.size)
    }
}
