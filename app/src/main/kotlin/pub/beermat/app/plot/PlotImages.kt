package pub.beermat.app.plot

import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import pub.beermat.format.PlotOutcome
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Plots as images for exports (docs/export.md#plots-and-pictures): drawn by [PlotPainter], as on the page. */
object PlotImages {

    /** The plot as an SVG, its text as outlines so it looks the same without our fonts; null if it can't be drawn. */
    fun svg(p: PlotOutcome): String? {
        val data = p.data ?: return null
        val w = p.block.attributes["w"]?.toFloatOrNull() ?: 420f
        val h = p.block.attributes["h"]?.toFloatOrNull() ?: 260f
        val out = ByteArrayOutputStream()
        val stream = OutputWStream(out)
        val canvas = SVGCanvas.make(Rect.makeWH(w, h), stream, true, false)
        // White paper, so the plot reads in a dark viewer too.
        canvas.drawRect(Rect.makeWH(w, h), org.jetbrains.skia.Paint().apply { color = 0xFFFFFFFF.toInt() })
        PlotPainter.paint(canvas, data, 0f, 0f, w, h, 9f)
        canvas.close()
        stream.close()
        return out.toString(Charsets.UTF_8)
    }

    /** The plot as a `data:` URI, to embed in Markdown. */
    fun dataUri(p: PlotOutcome): String? = svg(p)?.let { "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(it.toByteArray()) }
}
