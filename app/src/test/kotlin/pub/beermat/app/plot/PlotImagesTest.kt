package pub.beermat.app.plot

import pub.beermat.export.SheetExport
import pub.beermat.format.WorksheetText
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

/** Plots in the Markdown export: an SVG drawn by the page's painter, embedded so the export is one file. */
class PlotImagesTest {

    private val sheet = WorksheetText.read(
        """
        |```calc
        |L := 6 m
        |```
        |
        |```plot {#p1 w=300 h=200}
        |title: Sine
        |x: 0 m .. L
        |traces:
        |  - y: sin(x/(1 m))
        |```
        |""".trimMargin(),
    )

    @Test
    fun `a plot becomes an embedded svg`() {
        val md = SheetExport.markdown(sheet, PlotImages::dataUri)
        val uri = Regex("""!\[Plot]\((data:image/svg\+xml;base64,[A-Za-z0-9+/=]+)\)""").find(md)?.groupValues?.get(1)
        assertTrue(uri != null, md)
        val svg = String(Base64.getDecoder().decode(uri.substringAfter("base64,")))
        assertTrue(svg.startsWith("<?xml") && "<svg" in svg && "width=\"300\"" in svg, svg.take(200))
        // Without a painter (the bare command line), the placeholder line as before.
        assertTrue("*(A plot: not exported yet.)*" in SheetExport.markdown(sheet))
    }
}
