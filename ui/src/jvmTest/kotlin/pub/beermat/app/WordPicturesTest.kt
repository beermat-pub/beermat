package pub.beermat.app

import pub.beermat.export.Docx
import pub.beermat.format.ImageRegion
import pub.beermat.format.WorksheetText
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plots and pictures in Export to Word (docs/export.md#word): drawn as on the page, as PNG. */
class WordPicturesTest {

    private fun media(docx: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(docx)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.startsWith("word/media/")) out[e.name] = zip.readBytes()
            }
        }
        return out
    }

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    @Test
    fun `plots are drawn as PNG images, and the examples export for opening in Word by hand`() {
        val out = File("build/docx").apply { mkdirs() }
        val sheet = WorksheetText.read(File("../examples/plots.bbm").readText())
        val plots = sheet.plots.size
        val docx = Docx.write(sheet, WordPictures(null)::picture)
        File(out, "plots.docx").writeBytes(docx)
        val images = media(docx)
        assertEquals(plots, images.size, images.keys.toString())
        assertTrue(images.values.all { it.copyOf(4).contentEquals(png) })
    }

    @Test
    fun `an SVG picture is drawn to a PNG, at the region's size`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="40" height="20"><rect width="40" height="20" fill="red"/></svg>"""
        val r = ImageRegion(pub.beermat.format.Attributes.NONE, "red", ImageRegion.dataUri("image/svg+xml", svg.encodeToByteArray()))
        val p = WordPictures(null).picture(r)!!
        assertEquals("png", p.type)
        assertTrue(p.bytes.copyOf(4).contentEquals(png))
        // 40 px at 96 per inch is 30 points.
        assertEquals(30f, p.width)
    }
}
