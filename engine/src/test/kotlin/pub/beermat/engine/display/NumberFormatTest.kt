package pub.beermat.engine.display

import pub.beermat.engine.units.Complex
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {

    /** No exponents, so the rounding examples of docs/results.md read as written. */
    private fun plain(r: Rounding) = ResultFormat(rounding = r, exponentialThreshold = 99)

    private fun dec(x: Double, places: Int = 3, zeros: Boolean = false) =
        NumberFormat.format(x, plain(Rounding.Decimals(places, zeros)))

    private fun sig(x: Double, figures: Int, strict: Boolean = false) =
        NumberFormat.format(x, plain(Rounding.Significant(figures, strict)))

    @Test
    fun `decimal places`() {
        assertEquals("1234.567", dec(1234.567))
        assertEquals("2.5", dec(2.5))
        assertEquals("2.500", dec(2.5, zeros = true))
        assertEquals("0.333", dec(1.0 / 3))
        assertEquals("2.68", dec(2.675, 2))
        assertEquals("-1.5", dec(-1.4999999, 1))
        assertEquals("1200", dec(1200.0))
        assertEquals("1e-4", NumberFormat.format(0.0001, plain(Rounding.Decimals(3))))
    }

    @Test
    fun `significant figures, Dan's examples`() {
        assertEquals("1235", sig(1234.567, 4))
        assertEquals("1234.6", sig(1234.567, 5))
        assertEquals("1235", sig(1234.567, 3))
        assertEquals("1230", sig(1234.567, 3, strict = true))
    }

    @Test
    fun `significant figures keep significant trailing zeros`() {
        assertEquals("2.50", sig(2.5, 3))
        assertEquals("5.00", sig(5.0, 3))
        assertEquals("0.0123", sig(0.012345, 3))
        assertEquals("10.0", sig(9.996, 3))
        assertEquals("1000", sig(999.6, 3))
        assertEquals("1000", sig(999.6, 3, strict = true))
        assertEquals("1200", sig(1200.0, 3, strict = true))
        assertEquals("-0.500", sig(-0.5, 3))
        assertEquals("54.0", sig(54.0, 3))
    }

    @Test
    fun `exponents by default from a million`() {
        val f = ResultFormat()
        assertEquals("1234.567", NumberFormat.format(1234.567, f))
        assertEquals("999999.5", NumberFormat.format(999999.5, f))
        assertEquals("1.235e6", NumberFormat.format(1234567.0, f))
        assertEquals("1e-6", NumberFormat.format(0.000001, f)) // would round to 0: exponent instead
        assertEquals("0.001", NumberFormat.format(0.0012345, f))
        assertEquals("1.2e-7", NumberFormat.format(1.2e-7, f))
    }

    @Test
    fun `exponents with the usual threshold of 3`() {
        val f = ResultFormat(exponentialThreshold = 3)
        assertEquals("1.235e3", NumberFormat.format(1234.567, f))
        assertEquals("999.5", NumberFormat.format(999.5, f))
        assertEquals("1e3", NumberFormat.format(999.9996, f))
        assertEquals("0.001", NumberFormat.format(0.001, f))
        assertEquals("1.235e-4", NumberFormat.format(0.0001234567, f))
        assertEquals("5.4e4", NumberFormat.format(54000.0, f))
        assertEquals("1.23e3", NumberFormat.format(1234.567, f.copy(rounding = Rounding.Significant(3))))
        assertEquals("5.40e4", NumberFormat.format(54000.0, f.copy(rounding = Rounding.Significant(3))))
    }

    @Test
    fun thresholds() {
        val f = ResultFormat()
        assertEquals("0", NumberFormat.format(1e-16, f))
        assertEquals("1e-15", NumberFormat.format(1e-15, f))
        assertEquals("3", NumberFormat.format(Complex(3.0, 1e-12), f))
        assertEquals("2i", NumberFormat.format(Complex(1e-14, 2.0), f))
        assertEquals("3 - 4i", NumberFormat.format(Complex(3.0, -4.0), f))
        assertEquals("-2i", NumberFormat.format(Complex(0.0, -2.0), f))
        assertEquals("∞", NumberFormat.format(Double.POSITIVE_INFINITY, f))
    }
}
