package pub.beermat.app.print

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import pub.beermat.app.page.PagePainter
import pub.beermat.app.page.SheetLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * File → Print (docs/editor.md#printing): the system's print dialog, then each page drawn by
 * [PagePainter] at 300 dots per inch, on paper the size of the sheet's pages.
 */
object Printing {

    private const val DPI = 300f

    /** Asks the user, then prints; false if they cancelled. */
    fun print(layout: SheetLayout, jobName: String): Boolean {
        val job = PrinterJob.getPrinterJob()
        job.jobName = jobName
        val format = job.defaultPage().apply {
            orientation = PageFormat.PORTRAIT
            paper = Paper().apply {
                setSize(layout.format.width.toDouble(), layout.format.height.toDouble())
                // The sheet keeps its own margins, so the whole sheet is drawn.
                setImageableArea(0.0, 0.0, layout.format.width.toDouble(), layout.format.height.toDouble())
            }
        }
        job.setPrintable(pages(layout), format)
        if (!job.printDialog()) return false
        job.print()
        return true
    }

    private fun pages(layout: SheetLayout) = Printable { graphics: Graphics, _: PageFormat, index: Int ->
        if (index >= layout.pages) return@Printable Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val w = layout.format.width
        val h = layout.format.height
        g.drawImage(image(layout, index), 0, 0, w.roundToInt(), h.roundToInt(), null)
        Printable.PAGE_EXISTS
    }

    /** One page as an image at [DPI]. */
    private fun image(layout: SheetLayout, page: Int): java.awt.image.BufferedImage {
        val scale = DPI / 72f
        val surface = Surface.makeRasterN32Premul((layout.format.width * scale).roundToInt(), (layout.format.height * scale).roundToInt())
        surface.canvas.clear(0xFFFFFFFF.toInt())
        surface.canvas.scale(scale, scale)
        PagePainter.paintPage(surface.canvas, layout, page)
        val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        return ImageIO.read(png.inputStream())
    }
}
