package pub.beermat.engine.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The dimension rules table in docs/units.md, one test per row. */
class QuantityTest {

    private val m = Quantity(1.0, Dimension.LENGTH)
    private val s = Quantity(1.0, Dimension.TIME)
    private fun n(x: Double) = Quantity(x)

    @Test
    fun `addition and subtraction need matching dimensions`() {
        assertEquals(Quantity(5.0, Dimension.LENGTH), n(2.0) * m + n(3.0) * m)
        assertEquals(Quantity(-1.0, Dimension.LENGTH), n(2.0) * m - n(3.0) * m)
        val e = assertFailsWith<UnitsException> { m + s }
        assertEquals("units do not match: L vs T", e.message)
        // A bare 0 matches anything, but nothing else dimensionless does.
        assertEquals(m, m + n(0.0))
        assertEquals(m, n(0.0) + m)
        assertEquals(-m, n(0.0) - m)
        assertFailsWith<UnitsException> { m + n(1e-9) }
        // So does a zero that picked up a unit: 0/M + 1.
        assertEquals(n(1.0), n(0.0) / (m * s) + n(1.0))
        assertEquals(m, m + n(0.0) / s)
    }

    @Test
    fun `multiplication and division combine dimensions`() {
        val v = n(6.0) * m / (n(2.0) * s)
        assertEquals(3.0, v.value.re)
        assertEquals(Dimension.LENGTH / Dimension.TIME, v.dimension)
    }

    @Test
    fun `powers of dimensioned values need a dimensionless simple-fraction exponent`() {
        assertEquals(Quantity(9.0, Dimension.LENGTH.pow(2)), (n(3.0) * m).pow(n(2.0)))
        assertEquals(Quantity(0.5, Dimension.LENGTH.pow(-1)), (n(2.0) * m).pow(n(-1.0)))
        assertEquals(Dimension.LENGTH, (n(8.0) * m * m * m).pow(n(1.0 / 3)).dimension)
        // Any real exponent, as in empirical formulas: the dimension's power is the nearest fraction.
        assertEquals(Rational.of(463, 250), m.pow(n(1.852)).dimension[BaseDimension.LENGTH])
        assertEquals(Math.pow(2.0, Math.PI), (n(2.0) * m).pow(n(Math.PI)).value.re, 1e-12)
        assertFailsWith<UnitsException> { n(2.0).pow(m) }
        assertEquals(Math.pow(2.0, Math.PI), n(2.0).pow(n(Math.PI)).value.re)
    }

    @Test
    fun `roots divide exponents`() {
        val area = n(16.0) * m * m
        assertEquals(Quantity(4.0, Dimension.LENGTH), area.sqrt())
        val q = (n(27.0) * m * m * m).root(3)
        assertEquals(3.0, q.value.re, 1e-12)
        assertEquals(Dimension.LENGTH, q.dimension)
        assertEquals(Rational.of(1, 2), m.sqrt().dimension[BaseDimension.LENGTH])
    }

    @Test
    fun `comparisons need matching dimensions, except against plain zero`() {
        assertTrue(n(2.0) * m > n(1.0) * m)
        assertTrue(n(2.0) * m > n(0.0))
        assertTrue(n(-2.0) * m < n(0.0))
        assertFailsWith<UnitsException> { m < s }
        assertFailsWith<UnitsException> { m < n(1.0) }
    }

    @Test
    fun `negation keeps the dimension`() {
        assertEquals(Quantity(-1.0, Dimension.LENGTH), -m)
    }
}
