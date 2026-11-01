package pub.beermat.engine.units

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplexTest {

    @Test
    fun `negative zero is normalised`() {
        val minusEight = -Complex(8.0)
        assertEquals(0.0, minusEight.im)
        assertEquals("-8.0", minusEight.toString())
        assertEquals(PI, minusEight.arg())
        assertEquals(Complex(0.0), Complex(-0.0))
        assertEquals(Complex(0.0).hashCode(), Complex(-0.0).hashCode())
    }

    @Test
    fun `real operands take the real path`() {
        assertEquals(2.0.pow(0.5), Complex(2.0).pow(Complex(0.5)).re)
        assertEquals(-8.0, Complex(-2.0).pow(Complex(3.0)).re)
        assertTrue(Complex(-2.0).pow(Complex(3.0)).isReal)
    }

    @Test
    fun `principal values`() {
        val root = Complex(-8.0).pow(Complex(1.0 / 3))
        assertEquals(1.0, root.re, 1e-12)
        assertEquals(sqrt(3.0), root.im, 1e-12)
        assertEquals(Complex(0.0, 2.0), Complex(-4.0).sqrt())
        assertEquals(Complex(0.0, PI), Complex(-1.0).ln())
        assertEquals(Complex(0.0), Complex(0.0).pow(Complex(2.0)))
    }

    @Test
    fun arithmetic() {
        assertEquals(Complex(5.0, 5.0), Complex(1.0, 2.0) * Complex(3.0, -1.0))
        assertEquals(Complex(1.0, 2.0), Complex(5.0, 5.0) / Complex(3.0, -1.0))
        assertEquals(5.0, Complex(3.0, 4.0).abs())
        assertEquals("1.0-2.0i", Complex(1.0, -2.0).toString())
    }
}
