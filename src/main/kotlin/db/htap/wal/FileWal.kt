package db.htap.wal

import db.htap.model.Row
import db.htap.model.RowKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32

class FileWal(private val path: Path) : Wal {

    private val raf: RandomAccessFile = run {
        path.parent?.let { Files.createDirectories(it) }
        if (!Files.exists(path)) Files.createFile(path)
        RandomAccessFile(path.toFile(), "rw")
    }
    init { raf.seek(raf.length()) }

    @Synchronized
    override fun append(record: WalRecord) {
        val body = ByteArrayOutputStream().also { encode(DataOutputStream(it), record) }.toByteArray()
        val crc = CRC32().apply { update(body) }.value.toInt()
        val frame = ByteArrayOutputStream(body.size + 8).apply {
            DataOutputStream(this).run { writeInt(body.size); write(body); writeInt(crc) }
        }
        raf.write(frame.toByteArray())
    }

    @Synchronized
    override fun flush() { raf.fd.sync() }

    override fun readAll(): Sequence<WalRecord> = sequence {
        RandomAccessFile(path.toFile(), "r").use { r ->
            val end = r.length()
            while (r.filePointer < end) {
                if (end - r.filePointer < 8) break
                val len = r.readInt()
                if (len <= 0 || end - r.filePointer < len.toLong() + 4) break
                val body = ByteArray(len).also { r.readFully(it) }
                val crc = r.readInt()
                val expected = CRC32().apply { update(body) }.value.toInt()
                if (crc != expected) break
                yield(decode(DataInputStream(ByteArrayInputStream(body))))
            }
        }
    }

    @Synchronized
    override fun close() {
        try { raf.fd.sync() } catch (_: Exception) {}
        raf.close()
    }

    private fun encode(o: DataOutputStream, r: WalRecord) {
        when (r) {
            is WalRecord.Begin  -> { o.writeByte(1); o.writeLong(r.xid) }
            is WalRecord.Commit -> { o.writeByte(2); o.writeLong(r.xid) }
            is WalRecord.Abort  -> { o.writeByte(3); o.writeLong(r.xid) }
            is WalRecord.Insert -> {
                o.writeByte(4); o.writeLong(r.xid); o.writeUTF(r.table); writeRow(o, r.row)
            }
            is WalRecord.Update -> {
                o.writeByte(5); o.writeLong(r.xid); o.writeUTF(r.table)
                writeKey(o, r.key); writeRow(o, r.row)
            }
            is WalRecord.Delete -> {
                o.writeByte(6); o.writeLong(r.xid); o.writeUTF(r.table); writeKey(o, r.key)
            }
        }
    }

    private fun decode(i: DataInputStream): WalRecord {
        val kind = i.readByte().toInt()
        val xid = i.readLong()
        return when (kind) {
            1 -> WalRecord.Begin(xid)
            2 -> WalRecord.Commit(xid)
            3 -> WalRecord.Abort(xid)
            4 -> WalRecord.Insert(xid, i.readUTF(), readRow(i))
            5 -> WalRecord.Update(xid, i.readUTF(), readKey(i), readRow(i))
            6 -> WalRecord.Delete(xid, i.readUTF(), readKey(i))
            else -> error("unknown WAL record kind: $kind")
        }
    }

    private fun writeRow(o: DataOutputStream, row: Row) {
        o.writeShort(row.values.size)
        row.values.forEach { writeValue(o, it) }
    }
    private fun writeKey(o: DataOutputStream, key: RowKey) {
        o.writeShort(key.values.size)
        key.values.forEach { writeValue(o, it) }
    }
    private fun readRow(i: DataInputStream): Row {
        val n = i.readUnsignedShort()
        return Row(List(n) { readValue(i) })
    }
    private fun readKey(i: DataInputStream): RowKey {
        val n = i.readUnsignedShort()
        return RowKey(List(n) { readValue(i) ?: error("NULL in key") })
    }

    private fun writeValue(o: DataOutputStream, v: Any?) {
        when (v) {
            null       -> o.writeByte(0)
            is Long    -> { o.writeByte(1); o.writeLong(v) }
            is Int     -> { o.writeByte(1); o.writeLong(v.toLong()) }
            is Double  -> { o.writeByte(2); o.writeDouble(v) }
            is String  -> { o.writeByte(3); o.writeUTF(v) }
            is Boolean -> { o.writeByte(4); o.writeByte(if (v) 1 else 0) }
            else -> error("unsupported WAL value type: ${v::class}")
        }
    }
    private fun readValue(i: DataInputStream): Any? = when (val tag = i.readByte().toInt()) {
        0 -> null
        1 -> i.readLong()
        2 -> i.readDouble()
        3 -> i.readUTF()
        4 -> i.readByte().toInt() != 0
        else -> error("unknown value tag: $tag")
    }
}
