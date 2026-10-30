package pub.beermat.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RegionsTest {

    private val canonical = """
        |---
        |title: Beam
        |---
        |
        |# Beam check
        |
        |<!-- #t1 x=40 y=40 w=500 -->
        |Simply supported beam, uniform load **q**.
        |
        |Second paragraph of the same region.
        |
        |```calc {#r1 x=40 y=100}
        |L := 6 m
        |q := 12 kN/m
        |```
        |
        |<!-- pagebreak y=700 -->
        |
        |<!-- #a1 area y=720 title=Checks collapsed -->
        |
        |```calc {#r2 x=40 y=760}
        |M := q*L^2/8 = ? kN*m   #= 54 kN*m
        |```
        |
        |<!-- /area -->
        |
        |```plot {#p1 x=320 y=100 w=300 h=200}
        |x: 0 m .. L
        |```
        |
        |A closing paragraph.
        |""".trimMargin()

    private fun roundTrip(text: String): String {
        val sheet = WorksheetText.read(text)
        return WorksheetText.write(Regions.sheet(sheet.frontmatter, Regions.of(sheet)))
    }

    @Test
    fun `a canonical sheet round-trips`() {
        assertEquals(canonical, roundTrip(canonical))
    }

    @Test
    fun `regions and their kinds`() {
        val regions = Regions.of(WorksheetText.read(canonical))
        assertEquals(
            listOf("TextRegion", "TextRegion", "MathRegion", "PageBreak", "AreaStart", "MathRegion", "AreaEnd", "PlotRegion", "TextRegion"),
            regions.map { it::class.simpleName },
        )
        val text = assertIs<TextRegion>(regions[1])
        assertEquals(listOf("Simply supported beam, uniform load **q**.", "", "Second paragraph of the same region."), text.lines)
        assertEquals(40f to 40f, text.x to text.y)
        val area = assertIs<AreaStart>(regions[4])
        assertTrue(area.collapsed)
        assertEquals("Checks", area.title)
        assertEquals(700f, regions[3].y)
    }

    @Test
    fun `unmarked text splits into paragraphs and headings, and gains a marker once placed`() {
        val regions = Regions.markdown(listOf("# Title", "One", "two.", "", "Three.", "```", "code", "", "more", "```"))
        assertEquals(listOf(listOf("# Title"), listOf("One", "two."), listOf("Three.", "```", "code", "", "more", "```")), regions.map { (it as TextRegion).lines })
        val moved = regions[1].movedTo(40.4f, 99.6f)
        assertEquals("x=40 y=100", moved.attributes.format())
        assertTrue((moved as TextRegion).marked)
        val text = WorksheetText.write(Regions.sheet(null, listOf(moved)))
        assertEquals("<!-- x=40 y=100 -->\nOne\ntwo.\n", text)
    }

    @Test
    fun `attribute values with spaces are quoted`() {
        assertEquals("#a1 area title=\"Load cases\" collapsed", Attributes.parse("#a1 area title=\"Load cases\" collapsed").format())
    }

    @Test
    fun `images keep their data at the end of the file`() {
        val sheet = """
            |<!-- #i1 x=40 y=100 w=120 h=80 -->
            |![Beam diagram][i1]
            |
            |```calc {#r1 x=40 y=200}
            |L := 6 m
            |```
            |
            |A note that links [elsewhere][ref].
            |
            |[ref]: data:text/plain;base64,aGk=
            |
            |<!-- #i2 x=200 y=100 -->
            |![Photo](site.jpg)
            |
            |[i1]: data:image/png;base64,iVBORw0KGgo=
            |""".trimMargin()
        val regions = Regions.of(WorksheetText.read(sheet))
        assertEquals(listOf("ImageRegion", "MathRegion", "TextRegion", "TextRegion", "ImageRegion"), regions.map { it::class.simpleName })
        val image = regions[0] as ImageRegion
        assertEquals("Beam diagram", image.alt)
        assertTrue(image.embedded)
        assertEquals(listOf(0x89, 'P'.code, 'N'.code, 'G'.code), image.bytes()!!.take(4).map { it.toInt() and 0xFF })
        assertEquals("site.jpg", (regions[4] as ImageRegion).source)
        // Written back: the reference in place, the data last; a definition no image uses stays in the text.
        assertEquals(sheet, roundTrip(sheet))
    }

    @Test
    fun `a new embedded image gets its data under its id`() {
        val r = ImageRegion(Attributes("i3", mapOf("x" to "0", "y" to "0")), "Sketch", ImageRegion.dataUri("image/png", byteArrayOf(1, 2, 3)))
        assertEquals("<!-- #i3 x=0 y=0 -->\n![Sketch][i3]\n\n[i3]: data:image/png;base64,AQID\n", WorksheetText.write(Regions.sheet(null, listOf(r))))
    }
}
