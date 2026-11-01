package pub.beermat.app.page

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle

/**
 * A text region's Markdown drawn as a Skia paragraph: headings, paragraphs and list items, with
 * `**bold**`, `*italic*` and `` `code` `` inline. Enough to show text regions on the page; the
 * Markdown itself stays as written.
 */
object TextBlock {

    const val SIZE = 11f
    private val FAMILIES = arrayOf("Segoe UI", "Helvetica Neue", "Arial", "Liberation Sans", "sans-serif")

    private val fonts by lazy { FontCollection().apply { setDefaultFontManager(FontMgr.default); setAssetFontManager(PageFonts.provider) } }

    fun paragraph(lines: List<String>, width: Float, color: Int = 0xFF1A1A1A.toInt()): Paragraph {
        val builder = ParagraphBuilder(ParagraphStyle(), fonts)
        fun style(size: Float, bold: Boolean = false, italic: Boolean = false, mono: Boolean = false) = TextStyle().apply {
            fontSize = size
            this.color = color
            setFontFamilies(if (mono) arrayOf("Consolas", "Menlo", "Liberation Mono", "monospace") else FAMILIES)
            fontStyle = when {
                bold && italic -> FontStyle.BOLD_ITALIC
                bold -> FontStyle.BOLD
                italic -> FontStyle.ITALIC
                else -> FontStyle.NORMAL
            }
        }
        var first = true
        fun newline() {
            if (!first) {
                builder.pushStyle(style(SIZE * 0.6f))
                builder.addText("\n")
                builder.popStyle()
            }
            first = false
        }
        var paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                newline()
                inline(builder, paragraph.toString()) { b, i, m -> style(SIZE, b, i, m) }
            }
            paragraph = StringBuilder()
        }
        for (raw in lines) {
            val line = raw.trim()
            val heading = Regex("""(#{1,6})\s+(.*)""").matchEntire(line)
            val item = Regex("""([-*+]|\d+\.)\s+(.*)""").matchEntire(line)
            when {
                heading != null -> {
                    flush()
                    newline()
                    val level = heading.groupValues[1].length
                    inline(builder, heading.groupValues[2]) { _, i, m -> style(headingSize(level), true, i, m) }
                }
                item != null -> {
                    flush()
                    newline()
                    val bullet = if (item.groupValues[1].first().isDigit()) item.groupValues[1] + " " else "•  "
                    inline(builder, bullet + item.groupValues[2]) { b, i, m -> style(SIZE, b, i, m) }
                }
                line.isEmpty() -> flush()
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return builder.build().apply { layout(width) }
    }

    fun headingSize(level: Int) = when (level) {
        1 -> 20f
        2 -> 16f
        3 -> 13.5f
        else -> 12f
    }

    /** `**bold**`, `*italic*`, `` `code` `` in [text], each run in its style. */
    private fun inline(builder: ParagraphBuilder, text: String, style: (bold: Boolean, italic: Boolean, mono: Boolean) -> TextStyle) {
        val token = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")
        var k = 0
        for (m in token.findAll(text)) {
            if (m.range.first > k) add(builder, text.substring(k, m.range.first), style(false, false, false))
            when {
                m.groups[1] != null -> add(builder, m.groupValues[1], style(true, false, false))
                m.groups[2] != null -> add(builder, m.groupValues[2], style(false, true, false))
                else -> add(builder, m.groupValues[3], style(false, false, true))
            }
            k = m.range.last + 1
        }
        if (k < text.length) add(builder, text.substring(k), style(false, false, false))
    }

    private fun add(builder: ParagraphBuilder, text: String, style: TextStyle) {
        builder.pushStyle(style)
        builder.addText(text)
        builder.popStyle()
    }
}
