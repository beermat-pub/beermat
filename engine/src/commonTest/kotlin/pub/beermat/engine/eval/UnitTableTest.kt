package pub.beermat.engine.eval

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.units.BaseDimension.CURRENT
import pub.beermat.engine.units.BaseDimension.LENGTH
import pub.beermat.engine.units.BaseDimension.MASS
import pub.beermat.engine.units.BaseDimension.TIME
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity

class UnitTableTest {

    private fun unit(name: String): Quantity = (Builtins.units.getValue(Id(name)) as Scalar).q

    private fun assertUnit(expectedSi: Double, name: String, relTol: Double = 1e-12) {
        val actual = unit(name).value.re
        assertEquals(expectedSi, actual, expectedSi * relTol, name)
    }

    @Test
    fun `the table loads and every entry is a number`() {
        assertTrue(Builtins.units.size > 60)
        assertTrue(Builtins.units.values.all { it is Scalar })
    }

    @Test
    fun `conversion factors`() {
        assertUnit(1000.0, "kN")
        assertUnit(0.0254, "in")
        assertUnit(0.3048, "ft")
        assertUnit(1609.344, "mi")
        assertUnit(1000.0, "t")
        assertUnit(9.80665, "g")
        assertUnit(4.4482216152605, "lbf")
        assertUnit(4448.2216152605, "kip")
        assertUnit(6894.757293168, "psi", 1e-10)
        assertUnit(0.001, "L")
        assertUnit(3.785411784e-3, "gal")
        assertUnit(745.69987158227, "hp", 1e-10)
        assertUnit(PI / 180, "deg")
        assertUnit(3600.0, "hr")
    }

    @Test
    fun dimensions() {
        val force = Dimension.of(MASS to 1, LENGTH to 1, TIME to -2)
        assertEquals(force, unit("N").dimension)
        assertEquals(force / Dimension.LENGTH.pow(2), unit("MPa").dimension)
        assertEquals(force * Dimension.LENGTH, unit("J").dimension)
        assertEquals(Dimension.of(MASS to 1, LENGTH to 2, TIME to -3, CURRENT to -2), unit("Ω").dimension)
        assertEquals(unit("Ω"), unit("ohm"))
        assertTrue(unit("deg").dimension.isNone)
    }

    @Test
    fun `fence extraction`() {
        val text = "# Title\n\n```calc\na := 1\n\nb := 2\n```\n\ntext\n```plot\nx: 1\n```\n"
        assertEquals(listOf("a := 1", "b := 2"), UnitTable.calcLines(text))
    }

    /** The full example from docs/syntax.md §13, minus the program block (milestone 3). */
    @Test
    fun `beam example`() {
        val sheet = listOf(
            "L := 6 m",
            "q := 12 kN/m",
            "E := 210 GPa",
            "I := 8356 cm^4",
            "M_max := q*L^2/8 = ? kN*m",
            "δ_max := 5*q*L^4/(384*E*I) = ? mm",
            "check(δ, L) := if(δ <= L/250, \"OK\", \"FAIL\")",
            "check(δ_max, L) = ?",
        ).map(Parser::parseStatement)
        val out = Evaluator().run(sheet)

        val m = out[4] as Outcome.Defined
        assertEquals(54.0, ((m.value as Scalar).q / m.shown!!.unit!!).value.re, 1e-12)

        val d = out[5] as Outcome.Defined
        assertEquals(11.540, ((d.value as Scalar).q / d.shown!!.unit!!).value.re, 1e-3)

        assertEquals(Text("OK"), (out[7] as Outcome.Evaluated).shown.value)
    }
}
