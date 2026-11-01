package pub.beermat.app

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.plot.PlotPainter

/** Renders sheet items to a PNG, without a window: for checking the layout, and later for export. */
object SheetImage {

    fun render(items: List<SheetItem>, width: Int = 1000): ByteArray {
        val margin = 32f
        val gap = 12f
        val heights = items.map { height(it) }
        val total = (margin * 2 + heights.sum() + gap * items.size).toInt()
        val surface = Surface.makeRasterN32Premul(width, maxOf(total, 1))
        val canvas = surface.canvas
        canvas.clear(0xFFFFFFFF.toInt())
        var y = margin
        items.forEachIndexed { k, item ->
            when (item) {
                is SheetItem.Text -> {
                    val font = SkiaMath.font(textSize(item.level), bold = item.level > 0)
                    canvas.drawString(item.text, margin, y + textSize(item.level), font, Paint().apply { color = 0xFF1A1A1A.toInt() })
                }
                is SheetItem.Math -> {
                    SkiaMath.paint(canvas, item.box, margin, y + item.box.ascent)
                    item.errorBox?.let { SkiaMath.paint(canvas, it, margin, y + item.box.height + 4f + it.ascent) }
                }
                is SheetItem.Plot -> {
                    item.data?.let { PlotPainter.paint(canvas, it, margin, y, item.width, item.height) }
                    item.problems.forEachIndexed { k, p ->
                        canvas.drawString(p, margin, y + (if (item.data != null) item.height else 0f) + 15f + 16f * k, SkiaMath.font(13f), Paint().apply { color = 0xFFC62828.toInt() })
                    }
                }
                is SheetItem.Pending -> canvas.drawString("[${item.what}: not drawn yet]", margin, y + 14f, SkiaMath.font(13f), Paint().apply { color = 0xFF9E9E9E.toInt() })
            }
            y += heights[k] + gap
        }
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private fun textSize(level: Int) = when (level) {
        0 -> 15f
        1 -> 24f
        2 -> 19f
        else -> 16f
    }

    private fun height(item: SheetItem): Float = when (item) {
        is SheetItem.Text -> textSize(item.level) * 1.4f
        is SheetItem.Math -> item.box.height + (item.errorBox?.let { it.height + 6f } ?: 0f)
        is SheetItem.Plot -> (if (item.data != null) item.height else 0f) + 16f * item.problems.size
        is SheetItem.Pending -> 18f
    }
}
