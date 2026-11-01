package pub.beermat.app.math

import kotlin.test.Test
import kotlin.test.assertEquals

/** Measuring from the glyphs gives what Font.measureText gave (which the browser's Skia lacks). */
class SkiaMathTest {

    @Test
    fun `the same extent as measureText`() {
        val samples = listOf("x", "M_max", "q·L²", "12 kN", "αβγΔΩ", "∑∫√", "(a + b)", " ", "", "fib(n)", "𝑀𝑞", "→ ≤ ≥ ≠", "mm", "°C")
        for (size in listOf(8f, 12f, 17.5f)) {
            for (text in samples) {
                val font = SkiaMath.font(size)
                val bounds = font.measureText(text)
                val extent = SkiaMath.measure(text, size)
                assertEquals(font.measureTextWidth(text), extent.width, 1e-3f, "width of '$text' at $size")
                assertEquals(maxOf(-bounds.top, 0f), extent.ascent, 1e-3f, "ascent of '$text' at $size")
                assertEquals(maxOf(bounds.bottom, 0f), extent.descent, 1e-3f, "descent of '$text' at $size")
            }
        }
    }
}
