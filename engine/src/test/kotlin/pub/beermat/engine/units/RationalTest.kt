package pub.beermat.engine.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RationalTest {

    @Test
    fun `normalises to lowest terms with a positive denominator`() {
        assertEquals(Rational.of(1, 2), Rational.of(2, 4))
        assertEquals(Rational.of(-1, 2), Rational.of(1, -2))
        assertEquals(Rational.of(1, 2), Rational.of(-3, -6))
        assertEquals(Rational.ZERO, Rational.of(0, -5))
        assertEquals(2, Rational.of(-1, 2).den)
    }

    @Test
    fun arithmetic() {
        assertEquals(Rational.of(5, 6), Rational.of(1, 2) + Rational.of(1, 3))
        assertEquals(Rational.of(1, 6), Rational.of(1, 2) - Rational.of(1, 3))
        assertEquals(Rational.of(1, 6), Rational.of(1, 2) * Rational.of(1, 3))
        assertEquals(Rational.of(3, 2), Rational.of(1, 2) / Rational.of(1, 3))
        assertEquals(Rational.of(-1, 2), -Rational.of(1, 2))
    }

    @Test
    fun `zero denominators and overflow throw`() {
        assertFailsWith<ArithmeticException> { Rational.of(1, 0) }
        assertFailsWith<ArithmeticException> { Rational.ONE / Rational.ZERO }
        assertFailsWith<ArithmeticException> { Rational.of(Int.MAX_VALUE) + Rational.ONE }
        assertFailsWith<ArithmeticException> { Rational.of(Int.MIN_VALUE, -1) }
    }

    @Test
    fun `compares by value`() {
        assertTrue(Rational.of(1, 3) < Rational.of(1, 2))
        assertTrue(Rational.of(-1, 2) < Rational.ZERO)
    }

    @Test
    fun `prints integers bare and fractions with a slash`() {
        assertEquals("3", Rational.of(3).toString())
        assertEquals("-1/2", Rational.of(-1, 2).toString())
    }

    @Test
    fun `approximates computed exponents`() {
        assertEquals(Rational.of(1, 3), Rational.approximate(1.0 / 3))
        assertEquals(Rational.of(2), Rational.approximate(2.0))
        assertEquals(Rational.of(-3, 2), Rational.approximate(-1.5))
        assertEquals(Rational.of(1, 2), Rational.approximate(0.5))
        assertEquals(Rational.ZERO, Rational.approximate(0.0))
        assertNull(Rational.approximate(Math.PI))
        assertNull(Rational.approximate(Double.NaN))
        assertNull(Rational.approximate(1e300))
    }
}
