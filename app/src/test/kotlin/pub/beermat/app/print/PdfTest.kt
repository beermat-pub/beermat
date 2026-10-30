package pub.beermat.app.print

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.Session
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The PDF of every example sheet reads back, page by page, with ink where the sheet has it. */
class PdfTest {

    private val math = MathLayout(SkiaMath, 12f)

    @Test
    fun `every example prints, and each page reads back`() {
        val out = System.getProperty("beermat.pdfOut")?.let(::File)?.apply { mkdirs() }
        File("../examples").listFiles()!!.filter { it.name.endsWith(".bbm") }.forEach { f ->
            val layout = Session(f.readText(), math).layout
            val bytes = Pdf.write(layout, f.nameWithoutExtension)
            Loader.loadPDF(bytes).use { doc ->
                assertEquals(layout.pages, doc.numberOfPages, f.name)
                assertEquals(f.nameWithoutExtension, doc.documentInformation.title)
                val box = doc.getPage(0).mediaBox
                assertEquals(layout.format.width, box.width, f.name)
                val image = PDFRenderer(doc).renderImageWithDPI(0, 72f)
                val dark = (0 until image.width step 2).sumOf { x -> (0 until image.height step 2).count { y -> image.getRGB(x, y) and 0xFF < 128 } }
                assertTrue(dark > 50, "${f.name}: page 1 is blank")
                out?.let {
                    File(it, "${f.nameWithoutExtension}.pdf").writeBytes(bytes)
                    for (p in 0 until doc.numberOfPages) ImageIO.write(PDFRenderer(doc).renderImageWithDPI(p, 110f), "png", File(it, "${f.nameWithoutExtension}-${p + 1}.png"))
                }
            }
        }
    }

    @Test
    fun `the text can be selected and searched`() {
        val sheet = "---\nbeermat: 1\nfooter: Page {page}\n---\n\nSome *text* here.\n\n```calc\nM := 12 kN*m\nα := M/2 = ? kN*m\n```\n"
        val layout = Session(sheet, math).layout
        Loader.loadPDF(Pdf.write(layout)).use { doc ->
            val text = org.apache.pdfbox.text.PDFTextStripper().getText(doc)
            for (s in listOf("Some", "text", "here.", "12", "kN", "6", "Page 1")) assertTrue(s in text, "'$s' in: $text")
            // Math letters come back as the characters drawn: italic M and α, from outside the basic plane.
            assertTrue("𝑀" in text && "𝛼" in text, text)
            // Still drawn the same: the invisible layer adds no ink.
            val image = PDFRenderer(doc).renderImageWithDPI(0, 72f)
            assertTrue((0 until image.width).any { x -> (0 until image.height).any { y -> image.getRGB(x, y) and 0xFF < 128 } })
        }
    }

    @Test
    fun colours() {
        assertEquals(Triple(1f, 1f, 1f), Pdf.color("white"))
        assertEquals(Triple(0x55 / 255f, 0x55 / 255f, 0x55 / 255f), Pdf.color("#555"))
        assertEquals(null, Pdf.color("none"))
    }
}
