package pub.beermat.xmcd

import org.apache.poi.poifs.filesystem.DocumentEntry
import org.apache.poi.poifs.filesystem.DocumentInputStream
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Str

/**
 * An `.xmcd` control (docs/xmcd.md#import-milestone-8): a list box, check box, radio buttons or text
 * box, made as a small web page. Its binary item holds an OLE compound file whose `Contents`
 * stream is that page's HTML (plain bytes or UTF-16), with the control's state in it: the
 * `selected` option, a `checked` box. Imported as the value it outputs (not live: a control on
 * the page is a feature for later, if ever).
 */
internal object ControlComponent {

    /** What the control is, and the value it outputs in linear syntax; null if the item doesn't hold one we can read. */
    class Control(val kind: String, val value: String)

    fun read(item: ByteArray?, inputs: List<pub.beermat.engine.syntax.Expr> = emptyList(), kind: String = ""): Control? {
        if (item == null) return null
        // Only the data table itself: a file-reading component keeps a stale matrix the same way.
        // Read from the OLE file's `Contents` stream: its sectors needn't follow each other in the item.
        // Older `.xmcd` files write no clsid-buddy for it.
        if (kind.isEmpty() || kind.lowercase().startsWith(DATA_TABLE)) (contents(item) + listOf(item)).firstNotNullOfOrNull(::table)?.let { return Control("data table", it) }
        scripted(item, inputs)?.let { return it }
        val html = html(item) ?: return null
        // A list box (a drop-down too): the selected option's value, or the first's.
        SELECT.find(html)?.let { select ->
            val options = OPTION.findAll(select.value).map { it.groupValues[1] to it.groupValues[2].trim() }.toList()
            val chosen = options.firstOrNull { (attrs, _) -> SELECTED.containsMatchIn(attrs) } ?: options.firstOrNull() ?: return null
            return Control("list box", value(attribute(chosen.first, "value") ?: chosen.second))
        }
        val inputs = INPUT.findAll(html).map { it.value }.toList()
        inputs.firstOrNull { attribute(it, "type").equals("checkbox", ignoreCase = true) }?.let { box ->
            return Control("check box", if (CHECKED.containsMatchIn(box)) "1" else "0")
        }
        inputs.filter { attribute(it, "type").equals("radio", ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { radios ->
            val chosen = radios.firstOrNull { CHECKED.containsMatchIn(it) } ?: radios.first()
            return Control("radio buttons", value(attribute(chosen, "value") ?: ""))
        }
        inputs.firstOrNull { attribute(it, "type")?.lowercase() in setOf(null, "text") }?.let { text ->
            return Control("text box", value(attribute(text, "value") ?: ""))
        }
        TEXTAREA.find(html)?.let { return Control("text box", value(it.groupValues[1])) }
        return null
    }

    /** A value as the original worksheet gives it: a number where the text reads as one (the results saved in imported sheets show `0.95`, not `"0.95"`), else the text. */
    private fun value(text: String): String = text.trim().toDoubleOrNull()?.let { if (it < 0) "($it)" else text.trim() } ?: Printer.print(Str(text))

    /**
     * The control's data table: after the marker [TABLE], nine 32-bit numbers (the
     * last three are the last column's index, the last row's and the count), then the count of doubles,
     * column by column. Checked against the results saved in imported sheets for four sheets' tables.
     */
    private fun table(item: ByteArray): String? {
        val b = java.nio.ByteBuffer.wrap(item).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // The marker can occur more than once: the table is where the header's fixed fields are 1, 1, 8 (a double's size), 2.
        val at = (0..item.size - TABLE.size - 36).firstOrNull { k ->
            TABLE.indices.all { item[k + it] == TABLE[it] } && (0 until 4).map { b.getInt(k + TABLE.size + 4 * it) } == listOf(1, 1, 8, 2)
        } ?: return null
        val header = (0 until 9).map { b.getInt(at + TABLE.size + 4 * it) }
        val (cols, rows, count) = Triple(header[6] + 1, header[7] + 1, header[8])
        val start = at + TABLE.size + 36
        if (cols < 1 || rows < 1 || count != rows * cols || start + 8L * count > item.size) return null
        val values = DoubleArray(count) { b.getDouble(start + 8 * it) }
        if (values.any { !it.isFinite() }) return null
        fun number(d: Double) = java.math.BigDecimal(d.toString()).stripTrailingZeros().toPlainString().let { if (d < 0) "($it)" else it }
        return "[" + (0 until rows).joinToString("; ") { r -> (0 until cols).joinToString(", ") { c -> number(values[c * rows + r]) } } + "]"
    }

    /**
     * The scripted controls of `.xmcd` files (their script and state in two OLE files): a text box's text, kept
     * in its state; a list box whose script sets its selection from its input on loading, which is then that input.
     */
    private fun scripted(item: ByteArray, inputs: List<pub.beermat.engine.syntax.Expr>): Control? {
        val streams = contents(item).takeIf { it.size >= 2 } ?: return null
        val script = String(streams[0], Charsets.UTF_16LE) + String(streams[0], Charsets.ISO_8859_1)
        return when {
            "TextBox.Text" in script -> mfcString(streams[1])?.let { Control("text box", value(it)) }
            "ListBox.CurSel = Inputs(0).Value" in script && inputs.isNotEmpty() -> Control("list box", Printer.print(inputs[0]))
            else -> null
        }
    }

    /** The first MFC Unicode string in [bytes]: FF FE FF, a length byte, then that many UTF-16 characters. */
    private fun mfcString(bytes: ByteArray): String? {
        val k = (0..bytes.size - 4).firstOrNull { bytes[it] == 0xFF.toByte() && bytes[it + 1] == 0xFE.toByte() && bytes[it + 2] == 0xFF.toByte() } ?: return null
        val n = bytes[k + 3].toInt() and 0xFF
        if (k + 4 + 2 * n > bytes.size) return null
        return String(bytes, k + 4, 2 * n, Charsets.UTF_16LE)
    }

    /** The `Contents` stream of every OLE file in the item, in order. */
    private fun contents(item: ByteArray): List<ByteArray> =
        (0 until item.size - 8).filter { k -> OLE.indices.all { item[k + it] == OLE[it] } }.mapNotNull { start ->
            val fs = runCatching { POIFSFileSystem(item.copyOfRange(start, item.size).inputStream()) }.getOrNull() ?: return@mapNotNull null
            (fs.root.entries.asSequence().firstOrNull { it.name.equals("Contents", ignoreCase = true) } as? DocumentEntry)?.let { DocumentInputStream(it).readAllBytes() }
        }

    /** The page in the first OLE file's `Contents` stream, decoded. */
    private fun html(item: ByteArray): String? {
        val start = (0 until item.size - 8).firstOrNull { k -> OLE.indices.all { item[k + it] == OLE[it] } } ?: return null
        val fs = runCatching { POIFSFileSystem(item.copyOfRange(start, item.size).inputStream()) }.getOrNull() ?: return null
        val entry = fs.root.entries.asSequence().firstOrNull { it.name.equals("Contents", ignoreCase = true) } as? DocumentEntry ?: return null
        val bytes = DocumentInputStream(entry).readAllBytes()
        val utf16 = String(bytes, Charsets.UTF_16LE)
        return when {
            "<HTML" in utf16.uppercase() -> utf16
            else -> String(bytes, Charsets.ISO_8859_1).takeIf { "<HTML" in it.uppercase() }
        }
    }

    /** An HTML attribute's value: quoted, or up to the next space or `>`. */
    private fun attribute(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            ?: Regex("""\b$name\s*=\s*(""|'')""", RegexOption.IGNORE_CASE).find(tag)?.let { "" }

    private val SELECT = Regex("""<SELECT\b.*?</SELECT>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val OPTION = Regex("""<OPTION\b([^>]*)>([^<]*)""", RegexOption.IGNORE_CASE)
    private val INPUT = Regex("""<INPUT\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val TEXTAREA = Regex("""<TEXTAREA\b[^>]*>(.*?)</TEXTAREA>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SELECTED = Regex("""\bselected\b""", RegexOption.IGNORE_CASE)
    private val CHECKED = Regex("""\bchecked\b""", RegexOption.IGNORE_CASE)
    /** The data table's clsid-buddy, its first part. */
    private const val DATA_TABLE = "a6cfc4d6"

    /** What comes before a data table's matrix: most of a GUID (31c98c54-d063-11d0-a68d-00a024cf05d1), as stored. */
    private val TABLE = byteArrayOf(0x54, 0x8c.toByte(), 0xc9.toByte(), 0x31, 0x63, 0xd0.toByte(), 0x11, 0xa6.toByte(), 0x8d.toByte(), 0x00, 0xa0.toByte(), 0x24, 0xcf.toByte(), 0x05, 0xd1.toByte())
    private val OLE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
}
