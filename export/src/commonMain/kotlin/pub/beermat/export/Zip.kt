package pub.beermat.export

/**
 * Just enough of a zip writer for a `.docx` ([Docx]), in common code so the web version writes one
 * too: entries stored without compression (the XML is small, and pictures are compressed already),
 * names in UTF-8, every date 1 January 1980.
 */
internal class Zip {
    private class Entry(val name: ByteArray, val crc: Int, val size: Int, val offset: Int)

    private val out = Bytes()
    private val entries = mutableListOf<Entry>()

    fun add(name: String, data: ByteArray) {
        val n = name.encodeToByteArray()
        val e = Entry(n, crc32(data), data.size, out.size)
        entries += e
        out.int(0x04034b50)
        out.short(20); out.short(FLAGS); out.short(0) // version needed, flags, stored
        out.short(0); out.short(DATE) // time, date
        out.int(e.crc); out.int(e.size); out.int(e.size)
        out.short(n.size); out.short(0)
        out.bytes(n)
        out.bytes(data)
    }

    fun add(name: String, text: String) = add(name, text.encodeToByteArray())

    fun bytes(): ByteArray {
        val start = out.size
        for (e in entries) {
            out.int(0x02014b50)
            out.short(20); out.short(20); out.short(FLAGS); out.short(0)
            out.short(0); out.short(DATE)
            out.int(e.crc); out.int(e.size); out.int(e.size)
            out.short(e.name.size); out.short(0); out.short(0) // name, extra, comment
            out.short(0); out.short(0); out.int(0) // disk, internal and external attributes
            out.int(e.offset)
            out.bytes(e.name)
        }
        val length = out.size - start
        out.int(0x06054b50)
        out.short(0); out.short(0)
        out.short(entries.size); out.short(entries.size)
        out.int(length); out.int(start)
        out.short(0)
        return out.toByteArray()
    }

    private class Bytes {
        private var data = ByteArray(1 shl 16)
        var size = 0
            private set

        private fun room(n: Int) {
            if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
        }

        fun byte(b: Int) {
            room(1)
            data[size++] = b.toByte()
        }

        fun short(v: Int) {
            byte(v); byte(v ushr 8)
        }

        fun int(v: Int) {
            short(v); short(v ushr 16)
        }

        fun bytes(b: ByteArray) {
            room(b.size)
            b.copyInto(data, size)
            size += b.size
        }

        fun toByteArray() = data.copyOf(size)
    }

    companion object {
        /** Bit 11: names are UTF-8. */
        private const val FLAGS = 0x0800

        /** 1 January 1980, in the DOS date form. */
        private const val DATE = (0 shl 9) or (1 shl 5) or 1

        private val TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            c
        }

        fun crc32(data: ByteArray): Int {
            var c = -1
            for (b in data) c = TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
            return c.inv()
        }
    }
}
