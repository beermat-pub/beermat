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
                assertEquals(layout.printed.size, doc.numberOfPages, f.name)
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
    fun `what is past the right margin prints on a page of its own, unless the sheet says single width`() {
        val body = "```calc {#a x=0 y=0}\na := 111\n```\n```calc {#b x=700 y=0}\nb := 222\n```\n```calc {#c x=0 y=900}\nc := 333\n```\n"
        val layout = Session(body, math).layout
        // Page 1 and its part to the right; page 2 has nothing past its margin.
        assertEquals(listOf(pub.beermat.app.page.PrintedPage(0, 0), pub.beermat.app.page.PrintedPage(0, 1), pub.beermat.app.page.PrintedPage(1, 0)), layout.printed)
        Loader.loadPDF(Pdf.write(layout)).use { doc ->
            assertEquals(3, doc.numberOfPages)
            val stripper = org.apache.pdfbox.text.PDFTextStripper()
            fun page(n: Int) = stripper.apply { startPage = n; endPage = n }.getText(doc)
            assertTrue("111" in page(1) && "222" !in page(1), page(1))
            assertTrue("222" in page(2) && "111" !in page(2), page(2))
            assertTrue("333" in page(3), page(3))
        }
        val single = Session("---\nsingle_page_width: true\n---\n$body", math).layout
        assertEquals(2, single.printed.size)
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
