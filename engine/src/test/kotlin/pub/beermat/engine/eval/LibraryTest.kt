package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** The functions of `Library`, end to end. */
class LibraryTest {

    private fun run(vararg lines: String): List<Outcome> = Evaluator().run(lines.map(Parser::parseStatement))

    private fun text(vararg lines: String): String = when (val o = run(*lines).last()) {
        is Outcome.Evaluated -> ResultText.format(o.shown)
        is Outcome.Defined -> ResultText.format(o.shown ?: fail("nothing shown"))
        is Outcome.Failed -> fail("failed: ${o.error.message}")
        Outcome.NotEvaluated -> fail("not evaluated")
        is Outcome.Symbolic -> fail("symbolic")
    }

    private fun error(vararg lines: String): String = assertIs<Outcome.Failed>(run(*lines).last()).error.message!!

    @Test
    fun rounding() {
        assertEquals("2.5 m", text("Floor(2.7 m, 0.5 m) = ?"))
        assertEquals("3 m", text("Ceil(2.7 m, 0.5 m) = ?"))
        assertEquals("2.5", text("Round(2.7, 0.5) = ?"))
        assertEquals("-2", text("trunc(-2.7) = ?"))
        assertTrue("same units" in error("Floor(2 m, 1 s) = ?"))
    }

    @Test
    fun `trigonometry and special functions`() {
        assertEquals("1.313", text("coth(1) = ?"))
        assertEquals("2", text("sec(π/3) = ?"))
        assertEquals("0.843", text("erf(1) = ?"))
        assertEquals("0.157", text("erfc(1) = ?"))
        assertEquals("24", text("Γ(5) = ?"))
        assertEquals("1.772", text("Γ(0.5) = ?"))
        assertEquals("0.881", text("asinh(1) = ?"))
        // erf through the switch from series to continued fraction, and far out.
        assertTrue(abs(Library.erf(2.5) - 0.999593047982555) < 1e-14)
        assertTrue(abs(Library.erfc(5.0) - 1.5374597944280349e-12) < 1e-24)
    }

    @Test
    fun `the normal distribution`() {
        assertEquals("0.975", text("pnorm(1.96, 0, 1) = ?"))
        assertEquals("0.841", text("cnorm(1) = ?"))
        assertEquals("1.96", text("qnorm(0.975, 0, 1) = ?"))
        assertEquals("119.6 MPa", text("qnorm(0.975, 100 MPa, 10 MPa) = ? MPa"))
        assertEquals("0.399", text("dnorm(0, 0, 1) = ?"))
        assertTrue(abs(Library.inverseNormal(1e-10) + 6.361340902404056) < 1e-9)
    }

    @Test
    fun `random numbers are the same at every evaluation`() {
        val first = text("v := rnorm(3, 10 m, 1 m) = ?")
        assertEquals(first, text("v := rnorm(3, 10 m, 1 m) = ?"))
        assertEquals("3", text("length(runif(3, 0, 1)) = ?"))
        assertTrue(text("x := rnd(5) = ?").toDouble() in 0.0..5.0)
    }

    @Test
    fun histograms() {
        val data = "d := [1; 2; 2; 3; 3; 3; 4]"
        assertEquals("[1; 2; 3; 1]", text(data, "hist([1; 2; 3; 4; 5], d) = ?"))
        assertEquals("[1.375, 1; 2.125, 2; 2.875, 3; 3.625, 1]", text(data, "histogram(4, d) = ?"))
    }

    @Test
    fun lookup() {
        assertEquals("[2]", text("match(30, [10; 20; 30]) = ?"))
        assertEquals("[1]", text("match(21, [10; 20; 30], \"near\") = ?"))
        assertEquals("[\"b\"]", text("vlookup(2, [1, \"a\"; 2, \"b\"], 1) = ?"))
        assertTrue("not found" in error("match(5, [1; 2]) = ?"))
    }

    @Test
    fun `sorting and spacing`() {
        assertEquals("[1; 2; 3]", text("sort([3; 1; 2]) = ?"))
        assertEquals("[3; 2; 1]", text("reverse([1; 2; 3]) = ?"))
        assertEquals("[1, 5; 2, 9; 3, 7]", text("csort([3, 7; 1, 5; 2, 9], 0) = ?"))
        assertEquals("[1; 10; 100]", text("logspace(1, 100, 3) = ?"))
        assertEquals("[0; 0.5; 1]*m", text("linspace(0 m, 1 m, 3) = ?"))
    }

    @Test
    fun `cross product`() {
        assertEquals("[0; 0; 1]", text("cross([1; 0; 0], [0; 1; 0]) = ?"))
        assertEquals("[0; 0; 2]*N*m", text("cross([2; 0; 0]*1 m, [0; 1; 0]*1 N) = ?"))
    }

    @Test
    fun strings() {
        assertEquals("\"ab12\"", text("concat(\"ab\", num2str(12)) = ?"))
        assertEquals("4", text("strlen(\"abcd\") = ?"))
        assertEquals("\"bc\"", text("substr(\"abcd\", 1, 2) = ?"))
        assertEquals("2", text("search(\"abcd\", \"cd\", 0) = ?"))
        assertEquals("3.5", text("str2num(\"3.5\") = ?"))
        assertEquals("too big", error("error(\"too big\") = ?"))
    }

    @Test
    fun `trace and pause pass their first argument on`() {
        assertEquals("3", text("f(x) :=\n  trace(\"x is {0}\", x)\n  pause(x + 1)\n  x + 1", "f(2) = ?"))
        assertEquals("\"x\"", text("trace(\"x\", 1) = ?"))
        // time(z): seconds now, for timing; a sheet takes differences.
        assertEquals("0", text("t0 := time(0)", "round(time(0) - t0) = ?"))
        assertEquals("1 N", text("SIUnitsOf(3 kN) = ?"))
        assertEquals("3000", text("3 kN/UnitsOf(3 kN) = ?"))
    }

    @Test
    fun `a unit named like a function is the function when called`() {
        assertEquals("2", text("min(3, 2) = ?"))
        assertEquals("120 s", text("2 min = ? s"))
        assertEquals("5 s", text("5 sec = ? s"))
        assertEquals("2", text("sec(0) + 1 = ?"))
        // Unless the sheet redefined the name.
        assertEquals("only a function can be called", error("min := 4", "min(3, 2) = ?"))
    }

    @Test
    fun `a bare 0 in max and min takes the others' unit`() {
        assertEquals("2 m", text("max(0, 2 m) = ?"))
        // Complex values: the real and imaginary parts separately.
        assertEquals("1 + 2i", text("min(3 + 2i, 1 + 5i) = ?"))
        assertEquals("0 m", text("max(0, -2 m) = ?"))
        assertEquals("-1 s", text("min([0; -1 s]) = ?"))
    }

    @Test
    fun `units from imported sheets`() {
        assertEquals("9806.65 N", text("1 tonnef = ? N"))
        assertEquals("0.447 m/s", text("1 mph = ? m/s"))
        assertEquals("0.001 H", text("1 mH = ? H"))
    }
}
