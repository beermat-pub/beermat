package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultFormat
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Angle and currency as dimensions, turned on per sheet (docs/units.md#angle-and-currency-as-dimensions). */
class AngleCurrencyTest {

    private val angle = UnitOptions(angle = true)
    private val aud = UnitOptions(currency = "AUD")

    private fun run(options: UnitOptions, vararg lines: String): List<Outcome> {
        val (scope, units) = Builtins.forSheet(options)
        return Evaluator(scope, units).run(lines.map(Parser::parseStatement))
    }

    /** The text of the last statement's result. */
    private fun text(options: UnitOptions, vararg lines: String, degrees: Boolean = true): String {
        val shown = when (val o = run(options, *lines).last()) {
            is Outcome.Evaluated -> o.shown
            is Outcome.Defined -> o.shown!!
            else -> error("no result: $o")
        }
        return ResultText.format(shown, ResultFormat(angleInDegrees = degrees, currency = options.currency))
    }

    private fun error(options: UnitOptions, vararg lines: String): String =
        assertIs<Outcome.Failed>(run(options, *lines).last(), "expected an error").error.message!!

    @Test
    fun `off by default, angles are plain numbers`() {
        assertEquals("0.524", text(UnitOptions(), "30 deg = ?"))
        assertEquals("30.524", text(UnitOptions(), "30 + 30 deg = ?"))
    }

    @Test
    fun `angles as a dimension`() {
        assertEquals("30 deg", text(angle, "θ := 30 deg", "θ = ?"))
        assertEquals("0.524 rad", text(angle, "θ := 30 deg", "θ = ?", degrees = false))
        assertEquals("100 deg", text(angle, "1/9 rev + 60 deg = ?"))
        assertEquals("90 deg", text(angle, "100 gon = ?"))
        // sin, cos and tan take an angle or a plain number (radians); the inverses give angles.
        assertEquals("0.5", text(angle, "sin(30 deg) = ?"))
        assertEquals("0.479", text(angle, "sin(0.5) = ?"))
        assertEquals("30 deg", text(angle, "asin(0.5) = ?"))
        assertEquals("45 deg", text(angle, "atan2(1, 1) = ?"))
        assertEquals("60 deg", text(angle, "acos(0.5) = ?"))
        // A rotation stiffness is not a moment; a speed of rotation is rad/s.
        assertEquals("1 kN*m/rad", text(angle, "k := 1000 N*m/rad = ?"))
        assertEquals("314.159 rad/s", text(angle, "ω := 2*π*50 Hz*rad = ?"))
        // An angle doesn't add to a length, nor to a plain number; the message says how to fix a lost radian.
        assertTrue("units do not match" in error(angle, "5 m + 30 deg = ?"))
        assertTrue("multiply or divide by rad" in error(angle, "r := 2 m", "θ := 1 rad", "r*θ + 1 m = ?"))
        assertEquals("2 m", text(angle, "r := 2 m", "θ := 1 rad", "r*θ/rad = ?"))
        assertTrue("dimensionless or an angle" in error(angle, "sin(2 m) = ?"))
    }

    @Test
    fun `a currency as a dimension`() {
        assertEquals("480 AUD", text(aud, "cost := 120 AUD/m^3", "V := 4 m^3", "cost*V = ?"))
        assertEquals("120 AUD/m^3", text(aud, "cost := 120 AUD/m^3 = ?"))
        // Other currencies are definitions, at the sheet's own rate; no prefixes on money.
        assertEquals("15200 AUD", text(aud, "USD := 1.52 AUD", "10000 USD = ?"))
        assertTrue("units do not match" in error(aud, "1 AUD + 1 m = ?"))
        // Any name, in backticks when it isn't a plain one.
        assertEquals("5 `\$`", text(UnitOptions(currency = "\$"), "5 `\$` = ?"))
        // Without one, a name like AUD means nothing.
        assertTrue("AUD" in error(UnitOptions(), "120 AUD = ?"))
    }
}
