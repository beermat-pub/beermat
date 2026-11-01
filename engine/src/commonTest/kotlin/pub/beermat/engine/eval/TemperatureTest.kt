package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** °C and °F as scales, Δ°C and Δ°F as differences (docs/units.md#temperature). */
class TemperatureTest {

    private fun run(vararg lines: String): List<Outcome> = Evaluator().run(lines.map(Parser::parseStatement))

    private fun text(vararg lines: String): String = when (val o = run(*lines).last()) {
        is Outcome.Evaluated -> ResultText.format(o.shown)
        is Outcome.Defined -> ResultText.format(o.shown ?: fail("nothing shown"))
        is Outcome.Failed -> fail("failed: ${o.error.message}")
        Outcome.NotEvaluated -> fail("not evaluated")
        is Outcome.Symbolic -> fail("symbolic")
    }

    private fun error(vararg lines: String): String =
        assertIs<Outcome.Failed>(run(*lines).last(), "expected an error").error.message!!

    @Test
    fun `a temperature on a scale is absolute`() {
        assertEquals("293.15 K", text("20 °C = ?"))
        assertEquals("273.15 K", text("32 °F = ?"))
        assertEquals("233.15 K", text("-40 °F = ?"))
        assertEquals("293.15 K", text("20°C = ?"))
        assertEquals("[273.15; 373.15]*K", text("[0; 100] °C = ?"))
        assertEquals("298.15 K", text("T := 25", "T °C = ?"))
    }

    @Test
    fun `a result shown on a scale`() {
        assertEquals("20 °C", text("T := 293.15 K", "T = ? °C"))
        assertEquals("68 °F", text("T := 20 °C", "T = ? °F"))
        assertEquals("-40 °C", text("-40 °F = ? °C"))
        assertEquals("[0, 100]*°C", text("[273.15, 373.15]*1 K = ? °C"))
        assertTrue("units do not match" in error("3 m = ? °C"))
    }

    @Test
    fun `differences are in delta degrees`() {
        assertEquals("10 K", text("T1 := 20 °C", "T2 := 30 °C", "T2 - T1 = ?"))
        assertEquals("18 Δ°F", text("T1 := 20 °C", "T2 := 30 °C", "T2 - T1 = ? Δ°F"))
        assertEquals("25 °C", text("T := 20 °C + 5 Δ°C = ? °C"))
        assertEquals("50 W/(m*Δ°C)", text("k := 50 W/(m*K) = ? W/(m*Δ°C)"))
        assertEquals("9 R", text("5 K = ? R"))
    }

    @Test
    fun `a scale anywhere but after a plain number is one degree`() {
        // Thermal expansion and conductivity, as imported sheets write them.
        assertEquals("1.2e-5/K", text("α := 12*10^-6*°C^-1 = ?"))
        assertEquals("50 W/(m*K)", text("k := 50 W/(m*°C) = ? W/(m*K)"))
        assertEquals("1.111 m*K", text("2 m °F = ? m*K"))
    }

    @Test
    fun `a scale as a function, as imported sheets write it`() {
        assertEquals("293.15 K", text("°C(20) = ?"))
        assertEquals("[273.15; 373.15]*K", text("°C([0; 100]) = ?"))
        assertEquals("273.15 K", text("°F(32) = ?"))
    }

    @Test
    fun `inside a product or quotient a scale is one degree, as a gradient reads`() {
        // A geothermal gradient: 100 degrees per 1.85 km (the original worksheet's result, cached in one of Dan's sheets).
        assertEquals("0.116", text("(10*(°C/km))/(1.59362426*(100*°C/(1.85*km))) = ?"))
        assertEquals("54.054 K/km", text("100*°C/(1.85*km) = ? K/km"))
        // A function's argument starts afresh.
        assertEquals("586.3 K", text("f(T) := 2*T", "f(20 °C) = ?"))
    }

    @Test
    fun `percent`() {
        assertEquals("0.05", text("ξ := 5*`%` = ?"))
        assertEquals("5 `%`", text("ξ := 0.05 = ? `%`"))
    }

    @Test
    fun `a sheet can still use the names for its own values`() {
        assertEquals("2", text("°C := 2", "3 °C - 4 = ?"))
        // Older imported sheets made °C a plain degree, which is the same as K: no longer a scale.
        assertEquals("50 K", text("°C := Δ°C", "50 °C = ? K"))
        // Then a placeholder in °C uses the sheet's °C as well.
        assertEquals("94.4 °C", text("°C ::= K", "T := 94.4 °C", "T = ? °C"))
    }

    @Test
    fun `the names print back as typed`() {
        for (s in listOf("T := 20 °C", "ΔT = ? Δ°F", "x := -40 °F = ? °C")) {
            assertEquals(s, Printer.print(Parser.parseStatement(s)))
        }
    }
}
