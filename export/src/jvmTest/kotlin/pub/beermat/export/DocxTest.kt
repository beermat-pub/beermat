package pub.beermat.export

import pub.beermat.format.ImageRegion
import pub.beermat.format.WorksheetText
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Export to Word (docs/export.md#word): a `.docx` whose parts are well-formed, with equations, text, tables and pictures. */
class DocxTest {

    /** The parts of a `.docx`, read back by the JDK's zip reader, which checks each entry's CRC. */
    private fun parts(docx: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(docx)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                out[e.name] = zip.readBytes()
            }
        }
        return out
    }

    private fun wellFormed(name: String, xml: ByteArray) {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        runCatching { f.newDocumentBuilder().parse(ByteArrayInputStream(xml)) }.onFailure { throw AssertionError("$name: ${it.message}", it) }
    }

    private fun document(text: String): Pair<String, Map<String, ByteArray>> {
        val p = parts(Docx.write(WorksheetText.read(text)))
        p.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (n, x) -> wellFormed(n, x) }
        return p.getValue("word/document.xml").decodeToString() to p
    }

    @Test
    fun `a sheet becomes a document of well-formed parts, its statements equations with their results`() {
        val (doc, p) = document(
            """
            |# A beam
            |
            |The load, **factored**, on a *simple* span (see [the code](https://example.com/code)); M_max and N_min are results.
            |
            |```calc
            |q := 12 kN/m
            |L := 6 m
            |M := q*L^2/8 = ? kN*m
            |x := sqrt(L) + y
            |```
            """.trimMargin(),
        )
        assertTrue(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/numbering.xml", "docProps/core.xml").all { it in p }, p.keys.toString())
        assertTrue("<w:pStyle w:val=\"Heading1\"/>" in doc)
        assertTrue("<w:b/></w:rPr><w:t xml:space=\"preserve\">factored" in doc)
        assertTrue("<w:i/></w:rPr><w:t xml:space=\"preserve\">simple" in doc)
        // `_` inside a word stays as it is.
        assertTrue("M_max and N_min" in doc)
        assertTrue("<w:hyperlink r:id=\"rLink1\">" in doc)
        assertTrue("https://example.com/code" in p.getValue("word/_rels/document.xml.rels").decodeToString())
        // q·L²/8 as a fraction with a power, its result 54 kN·m after it.
        assertTrue("<m:f><m:num>" in doc && "<m:sSup>" in doc, doc)
        assertTrue(">54</m:t>" in doc, doc)
        assertTrue("<m:rad><m:radPr><m:degHide m:val=\"1\"/>" in doc)
        // An error is written after its equation.
        assertTrue("BeermatError" in doc && "Error: " in doc)
        assertEquals(4, Regex("<m:oMathPara>").findAll(doc).count())
    }

    @Test
    fun `lists, tables, page breaks and collapsed areas`() {
        val (doc, p) = document(
            """
            |- one
            |- two
            |
            |1. first
            |2. second
            |
            |```table
            || Span | Load [kN/m] |
            ||---|--:|
            || A | 12 |
            |```
            |
            |<!-- pagebreak -->
            |
            |<!-- #a1 area collapsed -->
            |
            |```calc
            |hidden := 1
            |```
            |
            |<!-- /area -->
            """.trimMargin(),
        )
        assertEquals(2, Regex("<w:numId w:val=\"1\"/>").findAll(doc).count())
        assertEquals(2, Regex("<w:numId w:val=\"2\"/>").findAll(doc).count())
        assertTrue("<w:startOverride w:val=\"1\"/>" in p.getValue("word/numbering.xml").decodeToString())
        assertTrue("<w:tbl>" in doc && "Load [kN/m]" in doc && ">12</w:t>" in doc, doc)
        assertTrue("<w:br w:type=\"page\"/>" in doc)
        assertTrue("hidden" !in doc)
    }

    @Test
    fun `an embedded picture goes in as an image of its size`() {
        val png = javaClass.getResourceAsStream("/pixel.png")?.readBytes() ?: tinyPng()
        val uri = ImageRegion.dataUri("image/png", png)
        val (doc, p) = document("<!-- #i1 w=144 h=72 -->\n![A sketch][i1]\n\n[i1]: $uri\n")
        assertTrue("word/media/image1.png" in p, p.keys.toString())
        // 144 pt is 2 inches, 1828800 EMU.
        assertTrue("<wp:extent cx=\"1828800\" cy=\"914400\"/>" in doc, doc)
        assertTrue("descr=\"A sketch\"" in doc)
        assertEquals("png" to (1 to 1), Images.size(png))
    }

    /** A 1×1 PNG. */
    private fun tinyPng(): ByteArray {
        val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun `the examples export, for opening in Word by hand`() {
        val out = File("build/docx").apply { mkdirs() }
        for (name in listOf("beam", "beam-table", "tables", "symbolic")) {
            val sheet = WorksheetText.read(File("../examples/$name.bbm").readText())
            val docx = Docx.write(sheet, folder = null)
            parts(docx).filterKeys { it.endsWith(".xml") }.forEach { (n, x) -> wellFormed("$name: $n", x) }
            File(out, "$name.docx").writeBytes(docx)
        }
    }
}
