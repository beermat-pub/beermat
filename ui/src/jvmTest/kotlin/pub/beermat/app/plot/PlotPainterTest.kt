package pub.beermat.app.plot

import pub.beermat.format.Axis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlotPainterTest {

    @Test
    fun `a linear axis rounds out to its ticks, at a given step too`() {
        val s = PlotPainter.Scale(Axis("x", null), listOf(0.3, 9.2))
        assertEquals(0.0 to 10.0, s.lo to s.hi)
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0), s.ticks())
        val stepped = PlotPainter.Scale(Axis("x", null, step = 2.5), listOf(0.3, 9.2))
        assertEquals(listOf(0.0, 2.5, 5.0, 7.5, 10.0), stepped.ticks())
        assertEquals("2.5", stepped.label(2.5))
    }

    @Test
    fun `a log axis spans whole decades, with 2 and 5 when it spans few`() {
        val s = PlotPainter.Scale(Axis("f", null, log = true), listOf(3.0, 700.0))
        assertEquals(0.0 to 3.0, s.lo to s.hi)
        assertEquals(listOf(1.0, 10.0, 100.0, 1000.0), s.ticks())
        assertEquals(0.5, s.fraction(10.0.pow(1.5)), 1e-12)
        assertTrue(s.fraction(-1.0).isNaN())
        assertTrue(20.0 in s.minor() && 10.0 !in s.minor())
        val narrow = PlotPainter.Scale(Axis("f", null, log = true), listOf(0.02, 0.9))
        assertEquals(listOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0), narrow.ticks().map { Math.round(it * 1e9) / 1e9 })
        assertEquals("0.01", narrow.label(0.01))
    }

    private fun Double.pow(e: Double) = Math.pow(this, e)
}
