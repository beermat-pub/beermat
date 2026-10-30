package pub.beermat.app.page

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.print.Pdf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Pictures: inserted, embedded in the file, read back, drawn and printed. */
class PicturesTest {

    private val math = MathLayout(SkiaMath, 12f)

    /** A 40 × 20 pixel PNG: red on the left half, blue on the right. */
    private fun png(): ByteArray {
        val s = Surface.makeRasterN32Premul(40, 20)
        s.canvas.drawRect(Rect.makeWH(20f, 20f), Paint().apply { color = 0xFFFF0000.toInt() })
        s.canvas.drawRect(Rect.makeXYWH(20f, 0f, 20f, 20f), Paint().apply { color = 0xFF0000FF.toInt() })
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40"><rect width="80" height="40" fill="green"/></svg>""".toByteArray()

    @Test
    fun `an inserted picture is embedded, and reads back`() {
        val s = Session("", math)
        s.click(0f, 0f)
        assertTrue(s.insertImage(png(), "image/png", "Two colours"))
        val saved = s.save()
        assertTrue(saved.contains("<!-- #i1 x=0 y=0 w=30 h=15 -->\n![Two colours][i1]"), saved)
        assertTrue(saved.trimEnd().lines().last().startsWith("[i1]: data:image/png;base64,iVBOR"), saved)
        val again = Session(saved, math)
        val view = assertIs<ImageView>(again.layout.views.single())
        assertIs<Pictures.Picture.Raster>(view.picture)
        assertEquals(30f to 15f, view.width to view.height)
    }

    @Test
    fun `a selected picture resizes from its corner handle`() {
        val s = Session("", math)
        s.click(0f, 0f)
        s.insertImage(png(), "image/png", "Two colours")
        val v = s.layout.views.single()
        val (ox, oy) = s.layout.origin(v, s.layout.pieces[0].single())
        // Selected after inserting: the handle is on the bottom-right corner.
        assertEquals(Triple(0, 30f, 15f), s.resizeHandle(ox + 30f, oy + 15f))
        assertEquals(null, s.resizeHandle(ox + 10f, oy + 5f))
        s.resize(0, 60f, 30f)
        assertTrue("<!-- #i1 x=0 y=0 w=60 h=30 -->" in s.save(), s.save())
        s.undo()
        assertTrue("w=30 h=15" in s.save())
    }

    @Test
    fun `svg pictures, and a missing file shown as its alt text`() {
        val p = assertIs<Pictures.Picture.Vector>(Pictures.decode(svg))
        assertEquals(60f to 30f, p.width to p.height)
        val s = Session("<!-- #i1 x=0 y=0 -->\n![Not here](nowhere.png)\n", math)
        val view = assertIs<ImageView>(s.layout.views.single())
        assertEquals(null, view.picture)
    }

    @Test
    fun `pictures print to PDF`() {
        val s = Session("", math)
        s.click(0f, 0f)
        s.insertImage(png(), "image/png", "Two colours")
        s.click(0f, s.layout.format.margin + 60f)
        s.insertImage(svg, "image/svg+xml", "Green")
        val svgPage = Pdf.svg(s.layout, 0)
        System.getProperty("beermat.pdfOut")?.let { File(it).apply { mkdirs() }.resolve("pictures.svg").writeText(svgPage) }
        val pdf = Pdf.write(s.layout)
        System.getProperty("beermat.pdfOut")?.let { File(it).resolve("pictures.pdf").writeBytes(pdf) }
        org.apache.pdfbox.Loader.loadPDF(pdf).use { doc ->
            val image = org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0, 72f)
            val m = s.layout.format.margin.toInt()
            // The PNG's two halves, and the SVG's green below them.
            assertEquals(0xFF0000, image.getRGB(m + 5, m + 7) and 0xFFFFFF, "red")
            assertEquals(0x0000FF, image.getRGB(m + 25, m + 7) and 0xFFFFFF, "blue")
            assertEquals(0x008000, image.getRGB(m + 20, m + 75) and 0xFFFFFF, "green")
        }
    }
}
