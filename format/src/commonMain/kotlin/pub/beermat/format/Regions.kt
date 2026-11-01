package pub.beermat.format

import kotlin.io.encoding.Base64
import kotlin.math.roundToInt

/**
 * A worksheet seen as regions on a page (docs/file-format.md#regions, docs/editor.md): what the
 * editor places, moves and edits. [Regions.of] reads them from a [Worksheet]; [Regions.sheet]
 * writes them back in the canonical form, one blank line between regions.
 */
sealed interface Region {
    val attributes: Attributes

    /** The position in points, if the region has one. */
    val x: Float? get() = attributes["x"]?.toFloatOrNull()
    val y: Float? get() = attributes["y"]?.toFloatOrNull()

    fun withAttributes(a: Attributes): Region = when (this) {
        is MathRegion -> copy(attributes = a)
        is TextRegion -> copy(attributes = a, marked = true)
        is PlotRegion -> copy(attributes = a)
        is TableRegion -> copy(attributes = a)
        is ImageRegion -> copy(attributes = a)
        is PageBreak -> copy(attributes = a)
        is AreaStart -> copy(attributes = a)
        is AreaEnd -> copy(attributes = a)
    }

    /** This region at ([x], [y]), in whole points. */
    fun movedTo(x: Float, y: Float): Region = withAttributes(attributes.with("x", fmt(x)).with("y", fmt(y)))

    private fun fmt(v: Float) = v.roundToInt().toString()
}

/** A ```` ```calc ```` fence: its lines, with their cached results. */
data class MathRegion(override val attributes: Attributes, val lines: List<CalcLine>) : Region

/**
 * Markdown text. A [marked] region has a `<!-- #t1 x=… -->` marker and runs to the next marker,
 * fence or heading; unmarked text (a heading, a paragraph) flows in the column.
 */
data class TextRegion(override val attributes: Attributes, val lines: List<String>, val marked: Boolean = attributes != Attributes.NONE) : Region

/** A ```` ```plot ```` fence (docs/plots.md). */
data class PlotRegion(override val attributes: Attributes, val body: List<String>) : Region

/** A ```` ```table ```` fence (docs/tables.md). */
data class TableRegion(override val attributes: Attributes, val body: List<String>) : Region

/**
 * A picture (docs/file-format.md#images): `<!-- #i1 x=… y=… w=… h=… -->` then `![alt][i1]`, its
 * data a base64 `data:` URI at the end of the file; or `![alt](file.png)`, a file beside the sheet.
 * [source] is the URI or the path.
 */
data class ImageRegion(override val attributes: Attributes, val alt: String, val source: String) : Region {
    val embedded: Boolean get() = source.startsWith("data:")

    /** The image's bytes, for an embedded image; null for a file or a URI that doesn't decode. */
    fun bytes(): ByteArray? = source.takeIf { embedded }?.substringAfter("base64,", "")?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(it) }.getOrNull() }

    companion object {
        /** An embedded image of [bytes], of the type [mime] (`image/png`). */
        fun dataUri(mime: String, bytes: ByteArray) = "data:$mime;base64," + Base64.encode(bytes)
    }
}

/** `<!-- pagebreak y=… -->`: the sheet continues on a new page. */
data class PageBreak(override val attributes: Attributes = Attributes.NONE) : Region

/** `<!-- #a1 area y=… title="…" collapsed -->`: the start of a collapsible area, up to its [AreaEnd]. */
data class AreaStart(override val attributes: Attributes) : Region {
    val collapsed: Boolean get() = attributes.has("collapsed")
    val title: String get() = attributes["title"] ?: ""
}

/** `<!-- /area y=… -->`: the end of an area. */
data class AreaEnd(override val attributes: Attributes = Attributes.NONE) : Region

object Regions {

    fun of(sheet: Worksheet): List<Region> {
        // The images' data, `[i1]: data:…` lines anywhere in the text (the app writes them at the end).
        // Only those a picture refers to belong to images; any other stays in the text.
        val text = sheet.parts.filterIsInstance<Markdown>().flatMap { it.lines }
        val pictured = text.mapNotNull { PICTURE.matchEntire(it)?.groupValues?.get(2)?.ifEmpty { null } }.toSet()
        fun ref(line: String) = DEFINITION.matchEntire(line)?.groupValues?.takeIf { it[1] in pictured }
        val definitions = text.mapNotNull { line -> ref(line)?.let { it[1] to it[2] } }.toMap()
        return sheet.parts.flatMap { part ->
            when (part) {
                is CalcBlock -> listOf(MathRegion(part.attributes, part.lines.dropLastWhile { it.isBlank }))
                is PlotBlock -> listOf(PlotRegion(part.attributes, part.body))
                is TableBlock -> listOf(TableRegion(part.attributes, part.body))
                is Markdown -> markdown(part.lines.filter { ref(it) == null }).map { image(it, definitions) }
            }
        }
    }

    /** `[i1]: data:image/png;base64,…`: an image's data, by its reference. */
    private val DEFINITION = Regex("""\s{0,3}\[([^\]]+)]:\s*(data:\S+)\s*""")

    /** `![alt][ref]` or `![alt](path)`, alone in a marked region. */
    private val PICTURE = Regex("""\s*!\[([^\]]*)](?:\[([^\]]+)]|\(([^)\s]+)\))\s*""")

    /** A marked text region that holds just a picture is an [ImageRegion]. */
    private fun image(r: Region, definitions: Map<String, String>): Region {
        if (r !is TextRegion || !r.marked) return r
        val m = r.lines.filter { it.isNotBlank() }.singleOrNull()?.let(PICTURE::matchEntire) ?: return r
        val (alt, ref, path) = m.destructured
        val source = if (ref.isNotEmpty()) definitions[ref] ?: return r else path
        return ImageRegion(r.attributes, alt, source)
    }

    /** The sheet these regions make, with [frontmatter]: canonical, one blank line between regions. */
    fun sheet(frontmatter: List<String>?, regions: List<Region>): Worksheet {
        val parts = mutableListOf<Part>()
        val text = mutableListOf<String>()
        val data = mutableListOf<Pair<String, String>>()
        fun flush() {
            if (text.isNotEmpty()) parts += Markdown(text.toList())
            text.clear()
        }
        regions.forEachIndexed { k, r ->
            if (k > 0 || frontmatter != null) text += ""
            when (r) {
                is MathRegion -> {
                    flush()
                    parts += CalcBlock(fence("calc", r.attributes), r.attributes, r.lines, "```")
                }
                is PlotRegion -> {
                    flush()
                    parts += PlotBlock(fence("plot", r.attributes), r.attributes, r.body, "```")
                }
                is TableRegion -> {
                    flush()
                    parts += TableBlock(fence("table", r.attributes), r.attributes, r.body, "```")
                }
                is TextRegion -> {
                    if (r.marked) text += "<!-- ${r.attributes.format()} -->"
                    text += r.lines
                }
                is ImageRegion -> {
                    text += "<!-- ${r.attributes.format()} -->"
                    if (r.embedded) {
                        // The data goes at the end, under the region's id, so the sheet's body stays readable.
                        val ref = r.attributes.id ?: "image${data.size + 1}"
                        text += "![${r.alt}][$ref]"
                        data += ref to r.source
                    } else {
                        text += "![${r.alt}](${r.source})"
                    }
                }
                is PageBreak -> text += "<!-- ${("pagebreak " + r.attributes.format()).trim()} -->"
                is AreaStart -> text += "<!-- ${r.attributes.format()} -->"
                is AreaEnd -> text += "<!-- ${("/area " + r.attributes.format()).trim()} -->"
            }
        }
        if (data.isNotEmpty()) {
            text += ""
            data.forEach { (ref, uri) -> text += "[$ref]: $uri" }
        }
        flush()
        return Worksheet(frontmatter, parts)
    }

    private fun fence(kind: String, a: Attributes) = if (a == Attributes.NONE) "```$kind" else "```$kind {${a.format()}}"

    private val marker = Regex("""\s{0,3}<!--\s*(.*?)\s*-->\s*""")

    /** Splits Markdown into text regions, page breaks and area markers. */
    internal fun markdown(lines: List<String>): List<Region> {
        val out = mutableListOf<Region>()
        var current: TextRegion? = null
        val body = mutableListOf<String>()
        var fence: String? = null
        fun close() {
            val text = body.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            current?.let { out += it.copy(lines = text) } ?: run { if (text.isNotEmpty()) out += TextRegion(Attributes.NONE, text) }
            current = null
            body.clear()
        }
        for (line in lines) {
            val t = line.trim()
            // A code sample in the text is kept whole, whatever it contains.
            if (fence != null) {
                body += line
                if (t.startsWith(fence)) fence = null
                continue
            }
            if (t.startsWith("```") || t.startsWith("~~~")) {
                if (current == null && body.all { it.isBlank() }) close()
                fence = t.takeWhile { it == t[0] }
                body += line
                continue
            }
            val m = marker.matchEntire(line)
            val content = m?.groupValues?.get(1)
            when {
                content != null && content.startsWith("pagebreak") -> {
                    close()
                    out += PageBreak(Attributes.parse(content.removePrefix("pagebreak")))
                }
                content != null && (content == "/area" || content.startsWith("/area ")) -> {
                    close()
                    out += AreaEnd(Attributes.parse(content.removePrefix("/area")))
                }
                content != null && content.startsWith("#") -> {
                    close()
                    val a = Attributes.parse(content)
                    if (a.has("area")) out += AreaStart(a) else current = TextRegion(a, emptyList(), marked = true)
                }
                t.startsWith("#") -> {
                    // A heading: on its own, or the first line of a marked region.
                    if (current != null && body.all { it.isBlank() }) {
                        body += line
                        close()
                    } else {
                        close()
                        out += TextRegion(Attributes.NONE, listOf(line))
                    }
                }
                t.isEmpty() && current == null -> close()
                else -> body += line
            }
        }
        close()
        return out
    }
}

/** `#r1 x=40 y=120 strict`: the text between the braces of a fence, or inside a marker. */
fun Attributes.format(): String = buildList {
    id?.let { add("#$it") }
    values.forEach { (k, v) ->
        add(
            when {
                v == null -> k
                v.isEmpty() || v.any { it.isWhitespace() || it == '"' || it == '}' } -> "$k=\"${v.replace("\"", "'")}\""
                else -> "$k=$v"
            },
        )
    }
}.joinToString(" ")

/** These attributes with [key] set to [value] (null for a bare flag). */
fun Attributes.with(key: String, value: String?): Attributes = copy(values = LinkedHashMap(values).also { it[key] = value })

fun Attributes.without(key: String): Attributes = copy(values = LinkedHashMap(values).also { it.remove(key) })
