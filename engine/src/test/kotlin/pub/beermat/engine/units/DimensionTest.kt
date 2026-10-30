package pub.beermat.engine.units

import pub.beermat.engine.units.BaseDimension.LENGTH
import pub.beermat.engine.units.BaseDimension.MASS
import pub.beermat.engine.units.BaseDimension.TIME
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DimensionTest {

    private val force = Dimension.of(MASS to 1, LENGTH to 1, TIME to -2)

    @Test
    fun `multiplication adds exponents and division subtracts them`() {
        assertEquals(force, Dimension.MASS * Dimension.LENGTH / Dimension.TIME.pow(2))
        assertEquals(Dimension.NONE, force / force)
        assertTrue((force / force).isNone)
    }

    @Test
    fun `powers can be fractional`() {
        val sqrtM = Dimension.LENGTH.pow(Rational.of(1, 2))
        assertEquals(Rational.of(1, 2), sqrtM[LENGTH])
        assertEquals(Dimension.LENGTH, sqrtM.pow(2))
    }

    @Test
    fun `of sums repeated bases`() {
        assertEquals(Dimension.LENGTH.pow(2), Dimension.of(LENGTH to 1, LENGTH to 1))
    }

    @Test
    fun printing() {
        assertEquals("L·M·T^-2", force.toString())
        assertEquals("L^(1/2)", Dimension.LENGTH.pow(Rational.of(1, 2)).toString())
        assertEquals("1", Dimension.NONE.toString())
    }
}
