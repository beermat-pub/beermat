package pub.beermat.app

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import pub.beermat.app.page.Pictures
import pub.beermat.app.plot.PlotPainter
import pub.beermat.engine.HostFile
import pub.beermat.export.Docx
import pub.beermat.format.ImageRegion
import pub.beermat.format.PlotOutcome

/**
 * Plots and pictures for Export to Word (docs/export.md#word), in the desktop app and the web
 * version: a plot drawn by [PlotPainter] as on the page, at print resolution; a picture as it is when
 * Word shows its format (PNG, JPEG, GIF), otherwise (SVG, WebP) drawn to a PNG.
 */
class WordPictures(private val base: HostFile?) {

    fun picture(item: Any): Docx.Picture? = when (item) {
        is PlotOutcome -> plot(item)
        is ImageRegion -> picture(item)
        else -> null
    }

    private fun plot(p: PlotOutcome): Docx.Picture? {
        val data = p.data ?: return null
        val w = p.block.attributes["w"]?.toFloatOrNull() ?: 420f
        val h = p.block.attributes["h"]?.toFloatOrNull() ?: 260f
        // A margin, for the tick labels that stand out past the frame, as they may on the page.
        val m = 12f
        return Docx.Picture(draw(w + 2 * m, h + 2 * m) { canvas -> PlotPainter.paint(canvas, data, m, m, w, h, 9f) }, "png", w + 2 * m, h + 2 * m)
    }

    private fun picture(r: ImageRegion): Docx.Picture? {
        Docx.embedded(r)?.let { return it }
        val p = Pictures.of(r, base) ?: return null
        val w = r.attributes["w"]?.toFloatOrNull() ?: p.width
        val h = r.attributes["h"]?.toFloatOrNull() ?: p.height
        return Docx.Picture(draw(w, h) { canvas -> Pictures.paint(canvas, p, w, h, r.alt) }, "png", w, h)
    }

    /** [paint] on white paper of [w] × [h] points, as a PNG at [DPI]. */
    private fun draw(w: Float, h: Float, paint: (org.jetbrains.skia.Canvas) -> Unit): ByteArray {
        val scale = DPI / 72f
        val surface = Surface.makeRasterN32Premul((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        val canvas = surface.canvas
        canvas.drawRect(Rect.makeWH(w * scale, h * scale), Paint().apply { color = 0xFFFFFFFF.toInt() })
        canvas.scale(scale, scale)
        paint(canvas)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private companion object {
        /** Print resolution: sharp on paper, a few hundred KB a plot. */
        const val DPI = 200f
    }
}
