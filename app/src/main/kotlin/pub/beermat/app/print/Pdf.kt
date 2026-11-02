package pub.beermat.app.print

import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import org.w3c.dom.Element
import pub.beermat.app.page.PagePainter
import pub.beermat.app.page.SheetLayout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The sheet's pages as a PDF (docs/editor.md#printing). Each page is drawn by [PagePainter]
 * onto Skia's SVG canvas, with text turned into outlines, so the PDF looks exactly as the screen
 * does; the few SVG elements Skia writes (paths, rectangles, circles, clips) then become PDF
 * drawing operators. No library: the PDF is small enough to write by hand.
 */
object Pdf {

    fun write(layout: SheetLayout, title: String? = null): ByteArray {
        val resources = Resources()
        val pages = layout.printed.map { (page, column) ->
            // The page as drawn, then its text again, invisible, so it can be selected, copied and searched.
            content(svg(layout, page, column = column), layout.format.height, resources) +
                textLayer(svg(layout, page, outlines = false, column = column), layout.format.width, layout.format.height, resources)
        }
        return file(layout.format.width, layout.format.height, pages, resources, title)
    }

    /** One page as SVG, its text turned into outlines (or, with [outlines] false, kept as text). */
    fun svg(layout: SheetLayout, page: Int, outlines: Boolean = true, column: Int = 0): String {
        val out = ByteArrayOutputStream()
        val stream = OutputWStream(out)
        val canvas = SVGCanvas.make(Rect.makeWH(layout.format.width, layout.format.height), stream, outlines, false)
        PagePainter.paintPage(canvas, layout, page, column)
        canvas.close()
        stream.close()
        return out.toString(Charsets.UTF_8)
    }

    // --- SVG to PDF operators -----------------------------------------------------------------

    /**
     * The PDF content stream of one SVG page [height] points tall. [resources] collects the opacities
     * used, (fill, stroke) → the name of their graphics state, shared by every page.
     */
    internal fun content(svg: String, height: Float, resources: Resources): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = false
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray())).documentElement
        val clips = mutableMapOf<String, Element>()
        val out = StringBuilder()
        // SVG's y runs down from the top; flipping once lets every coordinate go through as it is.
        out.append("1 0 0 -1 0 ${n(height)} cm\n")
        Converter(out, clips, resources).children(root)
        return out.toString()
    }

    private class Converter(val out: StringBuilder, val clips: MutableMap<String, Element>, val resources: Resources) {
        val states get() = resources.states

        /** Pictures defined in `<defs>`, drawn where a `<use>` refers to them. */
        val defined = mutableMapOf<String, Element>()

        fun children(e: Element) {
            val nodes = e.childNodes
            for (k in 0 until nodes.length) (nodes.item(k) as? Element)?.let(::element)
        }

        fun element(e: Element) {
            when (e.tagName) {
                "clipPath" -> clips[e.getAttribute("id")] = e
                // Definitions are drawn only where used.
                "defs" -> {
                    val nodes = e.childNodes
                    for (k in 0 until nodes.length) {
                        val d = nodes.item(k) as? Element ?: continue
                        if (d.tagName == "clipPath") clips[d.getAttribute("id")] = d else defined[d.getAttribute("id")] = d
                    }
                }
                "g", "svg" -> {
                    out.append("q\n")
                    transform(e)
                    clip(e)
                    children(e)
                    out.append("Q\n")
                }
                "path", "rect", "circle", "ellipse", "line" -> shape(e)
                "image" -> image(e, e)
                "use" -> {
                    val target = defined[href(e).removePrefix("#")] ?: return
                    if (target.tagName == "image") image(target, e) else {
                        out.append("q\n")
                        transform(e)
                        element(target)
                        out.append("Q\n")
                    }
                }
            }
        }

        fun href(e: Element) = e.getAttribute("xlink:href").ifEmpty { e.getAttribute("href") }

        /** A picture [img] (an `<image>` with a `data:` URI), placed by [at]'s transform. */
        fun image(img: Element, at: Element) {
            val name = resources.image(href(img)) ?: return
            fun f(key: String) = img.getAttribute(key).toFloatOrNull() ?: 0f
            out.append("q\n")
            transform(at)
            if (at !== img) transform(img)
            // The image fills a unit square from its bottom-left corner; our y runs down, so flip it back.
            out.append("${n(f("width"))} 0 0 ${n(-f("height"))} ${n(f("x"))} ${n(f("y") + f("height"))} cm /$name Do\nQ\n")
        }

        fun clip(e: Element) {
            val id = Regex("""url\(#([^)]+)\)""").find(e.getAttribute("clip-path"))?.groupValues?.get(1) ?: return
            val c = clips[id] ?: return
            // Skia's clips are rectangles in page coordinates, without transforms of their own.
            val nodes = c.childNodes
            for (k in 0 until nodes.length) (nodes.item(k) as? Element)?.let(::geometry)
            out.append("W n\n")
        }

        fun shape(e: Element) {
            val fill = color(e.getAttribute("fill").ifEmpty { "black" })
            val stroke = color(e.getAttribute("stroke").ifEmpty { "none" })
            if (fill == null && stroke == null) return
            out.append("q\n")
            transform(e)
            val fo = e.getAttribute("fill-opacity").toFloatOrNull() ?: 1f
            val so = e.getAttribute("stroke-opacity").toFloatOrNull() ?: 1f
            if (fo < 1f || so < 1f) out.append("/${states.getOrPut(fo to so) { "GS${states.size}" }} gs\n")
            fill?.let { (r, g, b) -> out.append("${n(r)} ${n(g)} ${n(b)} rg\n") }
            stroke?.let { (r, g, b) ->
                out.append("${n(r)} ${n(g)} ${n(b)} RG\n")
                out.append("${n(e.getAttribute("stroke-width").toFloatOrNull() ?: 1f)} w\n")
                when (e.getAttribute("stroke-linecap")) {
                    "round" -> out.append("1 J\n")
                    "square" -> out.append("2 J\n")
                }
                when (e.getAttribute("stroke-linejoin")) {
                    "round" -> out.append("1 j\n")
                    "bevel" -> out.append("2 j\n")
                }
                e.getAttribute("stroke-miterlimit").toFloatOrNull()?.let { out.append("${n(it)} M\n") }
            }
            geometry(e)
            val evenOdd = e.getAttribute("fill-rule") == "evenodd"
            out.append(
                when {
                    fill != null && stroke != null -> if (evenOdd) "B*" else "B"
                    fill != null -> if (evenOdd) "f*" else "f"
                    else -> "S"
                },
            ).append("\nQ\n")
        }

        fun transform(e: Element) {
            val t = e.getAttribute("transform").ifEmpty { return }
            for (m in Regex("""(\w+)\(([^)]*)\)""").findAll(t)) {
                val a = numbers(m.groupValues[2])
                val matrix = when (m.groupValues[1]) {
                    "translate" -> listOf(1f, 0f, 0f, 1f, a[0], a.getOrElse(1) { 0f })
                    "scale" -> listOf(a[0], 0f, 0f, a.getOrElse(1) { a[0] }, 0f, 0f)
                    "matrix" -> a
                    "rotate" -> {
                        val r = Math.toRadians(a[0].toDouble())
                        val (c, s) = kotlin.math.cos(r).toFloat() to kotlin.math.sin(r).toFloat()
                        listOf(c, s, -s, c, 0f, 0f)
                    }
                    else -> continue
                }
                out.append(matrix.joinToString(" ") { n(it) }).append(" cm\n")
            }
        }

        fun geometry(e: Element) {
            fun f(name: String) = e.getAttribute(name).toFloatOrNull() ?: 0f
            when (e.tagName) {
                "rect" -> out.append("${n(f("x"))} ${n(f("y"))} ${n(f("width"))} ${n(f("height"))} re\n")
                "circle" -> ellipse(f("cx"), f("cy"), f("r"), f("r"))
                "ellipse" -> ellipse(f("cx"), f("cy"), f("rx"), f("ry"))
                "line" -> out.append("${n(f("x1"))} ${n(f("y1"))} m ${n(f("x2"))} ${n(f("y2"))} l\n")
                "path" -> path(e.getAttribute("d"))
            }
        }

        /** Four cubic arcs; 0.5523 is the usual control-point distance for a quarter circle. */
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
            val k = 0.5523f
            out.append("${n(cx + rx)} ${n(cy)} m\n")
            out.append("${n(cx + rx)} ${n(cy + k * ry)} ${n(cx + k * rx)} ${n(cy + ry)} ${n(cx)} ${n(cy + ry)} c\n")
            out.append("${n(cx - k * rx)} ${n(cy + ry)} ${n(cx - rx)} ${n(cy + k * ry)} ${n(cx - rx)} ${n(cy)} c\n")
            out.append("${n(cx - rx)} ${n(cy - k * ry)} ${n(cx - k * rx)} ${n(cy - ry)} ${n(cx)} ${n(cy - ry)} c\n")
            out.append("${n(cx + k * rx)} ${n(cy - ry)} ${n(cx + rx)} ${n(cy - k * ry)} ${n(cx + rx)} ${n(cy)} c h\n")
        }

        /** SVG path data: M L H V C Q Z, absolute or relative. A quadratic becomes the equal cubic. */
        fun path(d: String) {
            val tokens = Regex("""[MmLlHhVvCcQqZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""").findAll(d).map { it.value }.toList()
            var k = 0
            var command = 'M'
            var x = 0f
            var y = 0f
            var startX = 0f
            var startY = 0f
            fun next() = tokens[k++].toFloat()
            while (k < tokens.size) {
                val t = tokens[k]
                if (t[0].isLetter()) {
                    command = t[0]
                    k++
                    if (command == 'Z' || command == 'z') {
                        out.append("h\n")
                        x = startX
                        y = startY
                        continue
                    }
                }
                val rel = command.isLowerCase()
                val ox = if (rel) x else 0f
                val oy = if (rel) y else 0f
                when (command.uppercaseChar()) {
                    'M' -> {
                        x = ox + next(); y = oy + next()
                        startX = x; startY = y
                        out.append("${n(x)} ${n(y)} m\n")
                        command = if (rel) 'l' else 'L'
                    }
                    'L' -> { x = ox + next(); y = oy + next(); out.append("${n(x)} ${n(y)} l\n") }
                    'H' -> { x = ox + next(); out.append("${n(x)} ${n(y)} l\n") }
                    'V' -> { y = oy + next(); out.append("${n(x)} ${n(y)} l\n") }
                    'C' -> {
                        val c = List(6) { next() }
                        x = ox + c[4]; y = oy + c[5]
                        out.append("${n(ox + c[0])} ${n(oy + c[1])} ${n(ox + c[2])} ${n(oy + c[3])} ${n(x)} ${n(y)} c\n")
                    }
                    'Q' -> {
                        val qx = ox + next()
                        val qy = oy + next()
                        val ex = ox + next()
                        val ey = oy + next()
                        out.append("${n(x + 2f / 3 * (qx - x))} ${n(y + 2f / 3 * (qy - y))} ${n(ex + 2f / 3 * (qx - ex))} ${n(ey + 2f / 3 * (qy - ey))} ${n(ex)} ${n(ey)} c\n")
                        x = ex; y = ey
                    }
                    else -> k++
                }
            }
        }
    }

    // --- The text layer -------------------------------------------------------------------------

    /**
     * The page's text as invisible PDF text (render mode 3), each character where it is drawn and as
     * wide as its advance, as scanned documents carry their recognised text. The glyphs come from
     * [Resources.glyph]: fonts that draw nothing, whose `ToUnicode` maps give back the characters.
     * [svg] is the page drawn with its text kept as `<text>` elements; their clip rectangles are kept,
     * so a region split over two pages gives each page only its own part.
     */
    internal fun textLayer(svg: String, width: Float, height: Float, resources: Resources): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = false
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray())).documentElement
        val clips = mutableMapOf<String, Element>()
        val out = StringBuilder()
        var font: String? = null

        /** An affine matrix [a b c d e f], applied as SVG does. */
        fun times(m: List<Float>, t: List<Float>) = listOf(
            m[0] * t[0] + m[2] * t[1], m[1] * t[0] + m[3] * t[1], m[0] * t[2] + m[2] * t[3], m[1] * t[2] + m[3] * t[3],
            m[0] * t[4] + m[2] * t[5] + m[4], m[1] * t[4] + m[3] * t[5] + m[5],
        )
        fun matrix(e: Element, m: List<Float>): List<Float> {
            var r = m
            for (t in Regex("""(\w+)\(([^)]*)\)""").findAll(e.getAttribute("transform"))) {
                val a = numbers(t.groupValues[2])
                r = times(r, when (t.groupValues[1]) {
                    "translate" -> listOf(1f, 0f, 0f, 1f, a[0], a.getOrElse(1) { 0f })
                    "scale" -> listOf(a[0], 0f, 0f, a.getOrElse(1) { a[0] }, 0f, 0f)
                    "matrix" -> a
                    else -> continue
                })
            }
            return r
        }
        fun point(m: List<Float>, x: Float, y: Float) = (m[0] * x + m[2] * y + m[4]) to (m[1] * x + m[3] * y + m[5])

        /** The clip rectangle [e] sets, intersected with [clip], in page coordinates. */
        fun clipped(e: Element, m: List<Float>, clip: FloatArray): FloatArray {
            val id = Regex("""url\(#([^)]+)\)""").find(e.getAttribute("clip-path"))?.groupValues?.get(1) ?: return clip
            val rect = clips[id]?.getElementsByTagName("rect")?.item(0) as? Element ?: return clip
            fun f(name: String) = rect.getAttribute(name).toFloatOrNull() ?: 0f
            val rm = matrix(rect, m)
            val (x0, y0) = point(rm, f("x"), f("y"))
            val (x1, y1) = point(rm, f("x") + f("width"), f("y") + f("height"))
            return floatArrayOf(maxOf(clip[0], minOf(x0, x1)), maxOf(clip[1], minOf(y0, y1)), minOf(clip[2], maxOf(x0, x1)), minOf(clip[3], maxOf(y0, y1)))
        }

        fun text(e: Element, m: List<Float>, clip: FloatArray) {
            val size = (e.getAttribute("font-size").toFloatOrNull() ?: return) * kotlin.math.hypot(m[0], m[1])
            val chars = e.textContent.codePoints().toArray().map { String(Character.toChars(it)) }
            val xs = numbers(e.getAttribute("x"))
            val y = numbers(e.getAttribute("y")).firstOrNull() ?: return
            if (chars.isEmpty() || xs.isEmpty()) return
            for ((k, ch) in chars.withIndex()) {
                val x = xs.getOrElse(k) { xs.last() }
                val (px, py) = point(m, x, y)
                if (px < clip[0] || px > clip[2] || py < clip[1] || py > clip[3] + size) continue
                val next = xs.getOrNull(k + 1)?.let { point(m, it, y).first }
                val advance = (next?.minus(px))?.takeIf { it > 0f } ?: (0.5f * size)
                if (ch.isBlank()) continue
                val (name, code) = resources.glyph(ch)
                if (name != font) out.append("/$name 1 Tf\n").also { font = name }
                // One unit of the font is one em; this matrix makes it the glyph's advance wide and the font's size tall.
                out.append("${n(advance)} 0 0 ${n(-size)} ${n(px)} ${n(py)} Tm <%02X> Tj\n".format(code))
            }
        }

        fun walk(e: Element, m: List<Float>, clip: FloatArray) {
            when (e.tagName) {
                "clipPath" -> { clips[e.getAttribute("id")] = e; return }
                "defs" -> {
                    val nodes = e.childNodes
                    for (k in 0 until nodes.length) (nodes.item(k) as? Element)?.takeIf { it.tagName == "clipPath" }?.let { clips[it.getAttribute("id")] = it }
                    return
                }
            }
            val here = matrix(e, m)
            val c = clipped(e, here, clip)
            if (e.tagName == "text") return text(e, here, c)
            val nodes = e.childNodes
            for (k in 0 until nodes.length) (nodes.item(k) as? Element)?.let { walk(it, here, c) }
        }

        walk(root, listOf(1f, 0f, 0f, 1f, 0f, 0f), floatArrayOf(0f, 0f, width, height))
        return if (out.isEmpty()) "" else "BT 3 Tr\n$out ET\n"
    }

    private fun numbers(s: String) = s.split(Regex("[ ,]+")).filter { it.isNotBlank() }.map { it.toFloat() }

    /** `#rgb`, `#rrggbb` or a basic colour name, as 0–1 components; null for none. */
    internal fun color(s: String): Triple<Float, Float, Float>? {
        val hex = when {
            s == "none" || s.isEmpty() -> return null
            s.startsWith("#") && s.length == 4 -> s.drop(1).map { "$it$it" }.joinToString("")
            s.startsWith("#") -> s.drop(1)
            else -> NAMED[s.lowercase()] ?: "000000"
        }
        val v = hex.toInt(16)
        return Triple((v shr 16 and 255) / 255f, (v shr 8 and 255) / 255f, (v and 255) / 255f)
    }

    private val NAMED = mapOf(
        "black" to "000000", "white" to "ffffff", "red" to "ff0000", "green" to "008000", "blue" to "0000ff",
        "gray" to "808080", "grey" to "808080", "yellow" to "ffff00", "orange" to "ffa500", "purple" to "800080",
    )

    /** A number in a content stream: at most three decimals, no exponent. */
    private fun n(v: Float): String {
        val s = String.format(Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    // --- The file ------------------------------------------------------------------------------

    /** What the pages share, each written once: opacity states, and pictures by their `data:` URI. */
    internal class Resources {
        val states = linkedMapOf<Pair<Float, Float>, String>()
        val images = linkedMapOf<String, Picture>()

        /** The text layer's characters: each has a code (1–255) in one of the fonts T0, T1, …. */
        val glyphs = linkedMapOf<String, Pair<Int, Int>>()

        /** The font and code that stand for [ch] in the text layer. */
        fun glyph(ch: String): Pair<String, Int> {
            val (font, code) = glyphs.getOrPut(ch) { glyphs.size / 255 to glyphs.size % 255 + 1 }
            return "T$font" to code
        }

        /** The characters of each font, by code. */
        val fonts: List<List<String>> get() = glyphs.keys.chunked(255)

        /** A picture as PDF wants it: RGB rows, and its alpha as a separate grey image if it has any; both deflated. */
        class Picture(val name: String, val width: Int, val height: Int, val rgb: ByteArray, val alpha: ByteArray?)

        /** The resource name of the picture at [uri], decoded on first use; null if it can't be read. */
        fun image(uri: String): String? {
            images[uri]?.let { return it.name }
            val data = uri.takeIf { it.startsWith("data:") }?.substringAfter("base64,", "")?.ifEmpty { null } ?: return null
            val img = runCatching { ImageIO.read(Base64.getDecoder().decode(data).inputStream()) }.getOrNull() ?: return null
            val (w, h) = img.width to img.height
            val rgb = ByteArray(w * h * 3)
            val alpha = ByteArray(w * h)
            var translucent = false
            for (y in 0 until h) for (x in 0 until w) {
                val argb = img.getRGB(x, y)
                val k = y * w + x
                rgb[3 * k] = (argb shr 16).toByte()
                rgb[3 * k + 1] = (argb shr 8).toByte()
                rgb[3 * k + 2] = argb.toByte()
                alpha[k] = (argb ushr 24).toByte()
                if (argb ushr 24 != 255) translucent = true
            }
            val p = Picture("Im${images.size}", w, h, deflate(rgb), if (translucent) deflate(alpha) else null)
            images[uri] = p
            return p.name
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { b ->
        DeflaterOutputStream(b, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(bytes) }
    }.toByteArray()

    private fun file(width: Float, height: Float, pages: List<String>, resources: Resources, title: String?): ByteArray {
        val states = resources.states
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun raw(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: () -> Unit) {
            offsets += out.size()
            raw("${offsets.size} 0 obj\n")
            body()
            raw("\nendobj\n")
        }
        fun stream(dictionary: String, packed: ByteArray) = obj {
            raw("<< $dictionary /Length ${packed.size} /Filter /FlateDecode >>\nstream\n")
            out.write(packed)
            raw("\nendstream")
        }
        // 1 catalog, 2 page tree, 3 resources, 4 info, then each picture (and its transparency),
        // then each text-layer font (the font, its one empty glyph, its ToUnicode map), then a page
        // and its contents for each page.
        val pictures = resources.images.values.toList()
        val pictureIds = pictures.runningFold(5) { id, p -> id + (if (p.alpha != null) 2 else 1) }
        val fonts = resources.fonts
        val fontIds = fonts.indices.map { pictureIds.last() + 3 * it }
        val pageIds = pages.indices.map { pictureIds.last() + 3 * fonts.size + 2 * it }
        out.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        obj { raw("<< /Type /Catalog /Pages 2 0 R >>") }
        obj { raw("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>") }
        obj {
            val gs = states.entries.joinToString(" ") { (o, name) -> "/$name << /Type /ExtGState /ca ${n(o.first)} /CA ${n(o.second)} >>" }
            val xo = pictures.withIndex().joinToString(" ") { (k, p) -> "/${p.name} ${pictureIds[k]} 0 R" }
            val fo = fontIds.withIndex().joinToString(" ") { (k, id) -> "/T$k $id 0 R" }
            raw("<< /ExtGState << $gs >> /XObject << $xo >> /Font << $fo >> >>")
        }
        obj { raw("<< /Producer (Beermat)${title?.let { " /Title ${text(it)}" } ?: ""} >>") }
        for ((k, p) in pictures.withIndex()) {
            val image = "/Type /XObject /Subtype /Image /Width ${p.width} /Height ${p.height} /BitsPerComponent 8"
            val mask = if (p.alpha != null) " /SMask ${pictureIds[k] + 1} 0 R" else ""
            stream("$image /ColorSpace /DeviceRGB$mask", p.rgb)
            p.alpha?.let { stream("$image /ColorSpace /DeviceGray", it) }
        }
        for ((k, chars) in fonts.withIndex()) {
            val id = fontIds[k]
            // A Type 3 font whose every code is one glyph, an em wide, that draws nothing.
            obj {
                raw("<< /Type /Font /Subtype /Type3 /FontBBox [0 0 1000 1000] /FontMatrix [0.001 0 0 0.001 0 0]")
                raw(" /CharProcs << /g ${id + 1} 0 R >> /Encoding << /Type /Encoding /Differences [1${" /g".repeat(chars.size)}] >>")
                raw(" /FirstChar 1 /LastChar ${chars.size} /Widths [${" 1000".repeat(chars.size)} ] /ToUnicode ${id + 2} 0 R /Resources << >> >>")
            }
            stream("", deflate("1000 0 0 0 0 0 d1".toByteArray(Charsets.ISO_8859_1)))
            stream("", deflate(toUnicode(chars).toByteArray(Charsets.ISO_8859_1)))
        }
        for ((k, content) in pages.withIndex()) {
            obj { raw("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(width)} ${n(height)}] /Resources 3 0 R /Contents ${pageIds[k] + 1} 0 R >>") }
            stream("", deflate(content.toByteArray(Charsets.ISO_8859_1)))
        }
        val xref = out.size()
        raw("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { raw(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        raw("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Info 4 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    /** A ToUnicode map: code k + 1 is [chars]`[k]`, in UTF-16 (so 𝑀, outside the basic plane, comes back as itself). */
    private fun toUnicode(chars: List<String>): String = buildString {
        append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
        append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
        append("1 begincodespacerange\n<01> <FF>\nendcodespacerange\n")
        // At most 100 entries in a block.
        for (block in chars.withIndex().chunked(100)) {
            append("${block.size} beginbfchar\n")
            for ((k, ch) in block) append("<%02X> <%s>\n".format(k + 1, ch.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }))
            append("endbfchar\n")
        }
        append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
    }

    /** A PDF text string: UTF-16 with a byte-order mark, in hex, so any title survives. */
    private fun text(s: String) = "<FEFF" + s.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) } + ">"
}
