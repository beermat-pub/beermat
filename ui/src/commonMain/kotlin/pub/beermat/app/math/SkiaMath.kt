package pub.beermat.app.math

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Typeface
import pub.beermat.app.defaultFontBytes
import pub.beermat.app.resources.Res

/**
 * Measuring and drawing math with Skia, in STIX Two Math (SIL Open Font License; the licence
 * travels with the font in `composeResources/files/fonts`).
 */
object SkiaMath : Metrics {

    /** The font file's bytes: read from the resources when first needed on the desktop; the browser shell calls [load] first. */
    var fontBytes: ByteArray? = null

    const val FONT = "files/fonts/STIXTwoMath-Regular.ttf"

    /** Reads the font from the resources, where that has to wait (in the browser, a download); before the first page is drawn. */
    suspend fun load() {
        if (fontBytes == null) fontBytes = Res.readBytes(FONT)
    }

    val typeface: Typeface by lazy {
        val bytes = fontBytes ?: defaultFontBytes() ?: error("the math font is missing from the resources")
        FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) ?: error("the math font could not be loaded")
    }

    private val fonts = HashMap<Pair<Float, Boolean>, Font>()

    fun font(size: Float, bold: Boolean = false): Font = fonts.getOrPut(size to bold) {
        Font(typeface, size).apply { isEmboldened = bold }
    }

    // From the glyphs (their advances and ink boxes) rather than Font.measureText, which the browser's
    // Skia lacks: the same width and the same ink above and below the baseline.
    override fun measure(text: String, size: Float): Extent {
        val font = font(size)
        val glyphs = font.getStringGlyphs(text)
        var top = 0f
        var bottom = 0f
        for (box in font.getBounds(glyphs)) {
            top = minOf(top, box.top)
            bottom = maxOf(bottom, box.bottom)
        }
        return Extent(font.getWidths(glyphs).sum(), -top, bottom)
    }

    /** The colours of [Ink], as ARGB. */
    var colors: (Ink) -> Int = { ink ->
        when (ink) {
            Ink.Normal -> 0xFF1A1A1A.toInt()
            Ink.Keyword -> 0xFF1A1A1A.toInt()
            Ink.Error -> 0xFFC62828.toInt()
            Ink.Faint -> 0xFF9E9E9E.toInt()
        }
    }

    /** Draws [box] with its baseline origin at ([x], [y]). */
    fun paint(canvas: Canvas, box: Box, x: Float, y: Float, ink: Int? = null) {
        when (box) {
            is TextBox -> {
                val paint = Paint().apply { color = ink ?: colors(box.ink); isAntiAlias = true }
                canvas.drawString(box.text, x, y, font(box.size, box.bold), paint)
            }
            is StrokeBox -> {
                val path = PathBuilder()
                for (op in box.path) {
                    when (op) {
                        is PathOp.Move -> path.moveTo(x + op.x, y + op.y)
                        is PathOp.Line -> path.lineTo(x + op.x, y + op.y)
                        is PathOp.Quad -> path.quadTo(x + op.cx, y + op.cy, x + op.x, y + op.y)
                    }
                }
                val paint = Paint().apply {
                    color = ink ?: colors(Ink.Normal)
                    mode = PaintMode.STROKE
                    strokeWidth = box.thickness
                    strokeCap = PaintStrokeCap.ROUND
                    strokeJoin = PaintStrokeJoin.ROUND
                    isAntiAlias = true
                }
                canvas.drawPath(path.detach(), paint)
            }
            is Group -> box.children.forEach { paint(canvas, it.box, x + it.x, y + it.y, ink) }
        }
    }
}
