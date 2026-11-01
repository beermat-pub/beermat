package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** polyroots, interpolation, regression and statistics, end to end. */
class FittingTest {

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
    fun polyroots() {
        assertEquals("[1; 2; 3]", text("polyroots([-6; 11; -6; 1]) = ?"))
        assertEquals("[-1i; 1i]", text("polyroots([1; 0; 1]) = ?"))
        assertEquals("[0; 0; 2]", text("polyroots([0; 0; -2; 1]) = ?"))
        // Zero coefficients at the top don't count; a row vector works too.
        assertEquals("[-0.5]", text("polyroots([1, 2, 0]) = ?"))
        assertEquals("[1; 1; 1]", text("polyroots([-1; 3; -3; 1]) = ?"))
        assertEquals("[-1; 0.5]", text("polyroots([-1; 1; 2]) = ?"))
        // A pair this close to real is still a pair: x² − 2x + 1 + 10⁻¹⁰ has roots 1 ± 10⁻⁵ i.
        assertEquals("1e-5", text("r := polyroots([1 + 10^-10; -2; 1])", "Im(r[1])*10^0 = ?"))
    }

    @Test
    fun `polyroots with units`() {
        assertEquals("[-2; 2]*m", text("polyroots([-4 m^2; 0 m; 1]) = ?"))
        assertTrue("units" in error("polyroots([1 m; 1; 1]) = ?"))
        assertTrue("degree" in error("polyroots([3; 0]) = ?"))
    }

    @Test
    fun `polyroots of a high degree`() {
        // (x − 1)(x − 2)…(x − 8), from its expanded coefficients.
        assertEquals(
            "[1; 2; 3; 4; 5; 6; 7; 8]",
            text("polyroots([40320; -109584; 118124; -67284; 22449; -4536; 546; -36; 1]) = ?"),
        )
    }

    @Test
    fun linterp() {
        val data = arrayOf("vx := [1; 2; 3]", "vy := [10; 20; 40]")
        assertEquals("30", text(*data, "linterp(vx, vy, 2.5) = ?"))
        assertEquals("20", text(*data, "linterp(vx, vy, 2) = ?"))
        // Zeros in the data take the others' units, as arrays a program starts with zeros have them.
        assertEquals("2.5 N", text("linterp([0; 1 m; 2 m], [0; 1 N; 4 N], 1.5 m) = ?"))
        // A zero with another unit too: an array of forces whose zero element was divided along with the rest.
        assertEquals("2.5 N", text("linterp([0/(1 N); 1 m; 2 m], [0; 1 N; 4 N], 1.5 m) = ?"))
        // Beyond the ends, the line through the end points carries on.
        assertEquals("60", text(*data, "linterp(vx, vy, 4) = ?"))
        assertEquals("0", text(*data, "linterp(vx, vy, 0) = ?"))
        assertEquals("15 kN", text("linterp([0; 2]*1 m, [10; 20]*1 kN, 1 m) = ? kN"))
        assertTrue("ascending" in error("linterp([2; 1], [1; 2], 1) = ?"))
        assertTrue("units of vx" in error("linterp([1; 2]*1 m, [1; 2], 1 s) = ?"))
    }

    @Test
    fun splines() {
        val x = "vx := [0; 1; 2; 3; 4]"
        // Each spline is exact for its kind of curve: cubic ends for a cubic, parabolic for a parabola, linear for a line.
        assertEquals("15.625", text(x, "vy := [0; 1; 8; 27; 64]", "interp(cspline(vx, vy), vx, vy, 2.5) = ?"))
        assertEquals("6.25", text(x, "vy := [0; 1; 4; 9; 16]", "interp(pspline(vx, vy), vx, vy, 2.5) = ?"))
        assertEquals("5", text(x, "vy := [0; 2; 4; 6; 8]", "interp(lspline(vx, vy), vx, vy, 2.5) = ?"))
        // The natural spline through (0, 0), (1, 1), (2, 0): M1 = -3.
        assertEquals("0.688", text("vx := [0; 1; 2]", "vy := [0; 1; 0]", "interp(lspline(vx, vy), vx, vy, 0.5) = ?"))
        // At the points themselves, the data.
        assertEquals("9", text(x, "vy := [0; 1; 4; 9; 16]", "interp(cspline(vx, vy), vx, vy, 3) = ?"))
    }

    @Test
    fun `cspline returns three header values and the second derivatives`() {
        assertEquals("[0; 3; 0; 0; 0]", text("lspline([0; 1], [0; 1]) = ?"))
        assertEquals("6", text("length(cspline([0; 1; 2], [0; 1; 4])) = ?"))
        assertEquals("[0; 3; 2; 0 m/s^2; 6 m/s^2; 12 m/s^2; 18 m/s^2]", text("cspline([0; 1; 2; 3]*1 s, [0; 1; 8; 27]*1 m) = ?"))
    }

    @Test
    fun `cubic splines with units`() {
        assertEquals("3.375 m", text("vx := [0; 1; 2; 3]*1 s", "vy := [0; 1; 8; 27]*1 m", "interp(cspline(vx, vy), vx, vy, 1.5 s) = ?"))
    }

    @Test
    fun regress() {
        val data = arrayOf("vx := [0; 1; 2; 3; 4]", "vy := [1; 6; 17; 34; 57]")
        assertEquals("[3; 3; 2; 1; 2; 3]", text(*data, "regress(vx, vy, 2) = ?"))
        assertEquals("86", text(*data, "interp(regress(vx, vy, 2), vx, vy, 5) = ?"))
        assertTrue("at least 3 points" in error("regress([0; 1], [0; 1], 2) = ?"))
    }

    @Test
    fun `regress with units`() {
        assertEquals(
            "[3; 3; 1; 2 m; 3 m/s]",
            text("vx := [0; 1; 2]*1 s", "vy := [2; 5; 8]*1 m", "regress(vx, vy, 1) = ?"),
        )
    }

    @Test
    fun linfit() {
        val data = arrayOf("vx := [1; 2; 3; 4]", "vy := 2 + 3*vx")
        assertEquals("[2; 3]", text(*data, "F(x) := [1; x]", "linfit(vx, vy, F) = ?"))
        // A least-squares fit, not through every point: y = a·x through (1, 1), (2, 3) gives a = 7/5.
        assertEquals("[1.4]", text("G(x) := [x]", "linfit([1; 2], [1; 3], G) = ?"))
        assertEquals("[2 m; 3 m/s]", text("F(t) := [1; t]", "linfit([1; 2; 3]*1 s, [5; 8; 11]*1 m, F) = ?"))
        assertTrue("not independent" in error("F(x) := [x; 2*x]", "linfit([1; 2; 3], [1; 2; 3], F) = ?"))
    }

    @Test
    fun `slope, intercept and corr`() {
        val data = arrayOf("vx := [1; 2; 3]*1 s", "vy := [5; 8; 11]*1 m")
        assertEquals("3 m/s", text(*data, "slope(vx, vy) = ?"))
        // Fits take data in any order; only interpolation needs vx ascending.
        assertEquals("2", text("slope([3; 1; 2], [6; 2; 4]) = ?"))
        assertEquals("0", text("intercept([3; 1; 2], [6; 2; 4]) = ?"))
        assertEquals("2 m", text(*data, "intercept(vx, vy) = ?"))
        assertEquals("1", text(*data, "corr(vx, vy) = ?"))
        assertEquals("-1", text("corr([1; 2; 3], [3; 2; 1]) = ?"))
    }

    @Test
    fun statistics() {
        assertEquals("1.25", text("var([1, 2, 3, 4]) = ?"))
        assertEquals("1.667", text("Var([1, 2, 3, 4]) = ?"))
        assertEquals("1.118", text("stdev([1, 2, 3, 4]) = ?"))
        assertEquals("1.291", text("Stdev([1, 2; 3, 4]) = ?"))
        assertEquals("1.291 m", text("Stdev([1; 2; 3; 4]*1 m) = ?"))
        assertEquals("2", text("median([3; 1; 2]) = ?"))
        assertEquals("2.5", text("median([4; 1; 3; 2]) = ?"))
        assertTrue("at least 2" in error("Var(5) = ?"))
    }
}
