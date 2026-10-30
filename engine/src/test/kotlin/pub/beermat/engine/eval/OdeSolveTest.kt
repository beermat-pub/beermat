package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Odesolve: differential equations in a solve block, end to end. */
class OdeSolveTest {

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
    fun `a second-order equation with primes`() {
        // y'' + y = 0, y(0) = 1, y'(0) = 0: y = cos t.
        val block = arrayOf("given", "y''(t) + y(t) == 0", "y(0) == 1", "y'(0) == 0", "y := Odesolve(t, 10)")
        assertEquals("-0.839", text(*block, "y(10) = ?"))
        assertEquals("0.54", text(*block, "y(1) = ?"))
        // Accurate between the points too: cos(2.345) to six figures.
        assertEquals("-699.147", text(*block, "y(2.345)*1000 = ?"))
    }

    @Test
    fun `a first-order equation written as imported sheets store it, with diff`() {
        // y' = −2y, y(0) = 3: y(1) = 3e^−2.
        assertEquals("406.006", text("given", "diff(y(t), t) == -2*y(t)", "y(0) == 3", "y := Odesolve(t, 1)", "y(1)*1000 = ?"))
    }

    @Test
    fun `a system, with units`() {
        // A falling body with drag-free motion: x' = v, v' = −g.
        val lines = arrayOf(
            "given", "x'(t) == v(t)", "v'(t) == -9.81 m/s^2", "x(0 s) == 100 m", "v(0 s) == 0 m/s",
            "[x; v] := Odesolve([x; v], t, 4 s)",
        )
        assertEquals("21.52 m", text(*lines, "x(4 s) = ?"))
        assertEquals("-39.24 m/s", text(*lines, "v(4 s) = ?"))
        assertTrue("outside the range" in error(*lines, "x(5 s) = ?"))
    }

    @Test
    fun `what Odesolve needs is said`() {
        assertTrue("initial conditions" in error("given", "y'(t) == y(t)", "y := Odesolve(t, 1)"))
        assertTrue("one point" in error("given", "y''(t) == y(t)", "y(0) == 1", "y'(1) == 0", "y := Odesolve(t, 1)"))
        assertTrue("solve block" in error("y := Odesolve(t, 1)"))
    }
}
