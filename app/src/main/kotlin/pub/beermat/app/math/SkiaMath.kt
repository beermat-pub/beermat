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

/**
 * Measuring and drawing math with Skia, in STIX Two Math (SIL Open Font License; the licence
 * travels with the font in `resources/fonts`).
 */
object SkiaMath : Metrics {

    val typeface: Typeface by lazy {
        val bytes = SkiaMath::class.java.getResourceAsStream("/fonts/STIXTwoMath-Regular.ttf")?.readBytes()
            ?: error("the math font is missing from the resources")
        FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) ?: error("the math font could not be loaded")
    }

    private val fonts = HashMap<Pair<Float, Boolean>, Font>()

    fun font(size: Float, bold: Boolean = false): Font = fonts.getOrPut(size to bold) {
        Font(typeface, size).apply { isEmboldened = bold }
    }

    override fun measure(text: String, size: Float): Extent {
        val font = font(size)
        val bounds = font.measureText(text)
        return Extent(font.measureTextWidth(text), maxOf(-bounds.top, 0f), maxOf(bounds.bottom, 0f))
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
