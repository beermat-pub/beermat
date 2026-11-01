package pub.beermat.engine.eval

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser

/** Numeric derivatives and integrals, with units. */
class CalculusTest {

    private fun text(vararg statements: String): String = when (val o = Evaluator().run(statements.map(Parser::parseStatement)).last()) {
        is Outcome.Evaluated -> ResultText.format(o.shown)
        is Outcome.Failed -> "error: ${o.error.message}"
        else -> error("unexpected $o")
    }

    private fun value(vararg statements: String): Double =
        (assertIs<Outcome.Evaluated>(Evaluator().run(statements.map(Parser::parseStatement)).last()).shown.value as Scalar).q.value.re

    @Test
    fun `derivatives at the variable's value`() {
        assertEquals(12.0, value("x := 2", "diff(x^3, x) = ?"), 1e-9)
        assertEquals(12.0, value("x := 2", "diff(x^3, x, 2) = ?"), 1e-6)
        assertEquals(cos(1.0), value("x := 1", "diff(sin(x), x) = ?"), 1e-10)
        assertEquals(exp(0.5), value("t := 0.5", "diff(exp(t), t, 3) = ?"), 1e-4)
        // f(t) = t^2 / s: its derivative at 3 s is 6, per second... and the units follow.
        assertEquals("6", text("t := 3 s", "f(t) := t^2/s^2", "diff(f(t), t)*s = ?"))
        assertEquals("error: y is not defined: diff needs its value", text("diff(y^2, y) = ?"))
    }

    @Test
    fun `integrals`() {
        assertEquals(1.0 / 3, value("int(x^2, x, 0, 1) = ?"), 1e-12)
        assertEquals(2.0, value("int(sin(x), x, 0, π) = ?"), 1e-12)
        assertEquals(2.0, value("int(1/sqrt(x), x, 0, 1) = ?"), 1e-6)
        // Infinite limits.
        assertEquals(1.0, value("int(e^-x, x, 0, ∞) = ?"), 1e-9)
        assertEquals(PI, value("int(1/(1 + x^2), x, -∞, ∞) = ?"), 1e-9)
        assertEquals(sqrt(PI) / 2, value("int(e^-(x^2), x, -∞, 0) = ?"), 1e-9)
        assertEquals(-1.0 / 3, value("int(x^2, x, 1, 0) = ?"), 1e-12)
        // Work along a spring: ∫ k x dx from 0 to 0.1 m, with k = 200 N/m, is 1 J.
        assertEquals("1 J", text("k := 200 N/m", "int(k*x, x, 0 m, 0.1 m) = ? J"))
        assertEquals("error: int: the limits have different units", text("int(x, x, 0 m, 1 s) = ?"))
    }

    @Test
    fun `root solves an equation, from a guess or between limits`() {
        assertEquals(sqrt(2.0), value("x := 1", "root(x^2 - 2, x) = ?"), 1e-12)
        assertEquals(-sqrt(2.0), value("x := -3", "root(x^2 - 2, x) = ?"), 1e-12)
        // The root nearest the guess, though a wide step from it would pass two: S^3/1 - S^5/1000 from 200 is +√1000.
        assertEquals(sqrt(1000.0), value("S := 200", "root(S^3 - S^5/1000, S) = ?"), 1e-9)
        // Of two roots in the same widening, the nearer: from 0, (x - 3)(x + 4) gives 3.
        assertEquals(3.0, value("x := 0", "root((x - 3)*(x + 4), x) = ?"), 1e-9)
        // A root the widening search steps over, close to 0 (water waves at a low frequency): secant steps from the guess find it.
        val k = value("k := 1", "root(0.0025/9.81 - k*tanh(k*100), k) = ?")
        assertEquals(0.0025 / 9.81, k * tanh(k * 100), 1e-12)
        assertTrue(k != 0.0)
        assertEquals(0.7390851332151607, value("root(cos(t) - t, t, 0, 1) = ?"), 1e-12)
        // With units: the depth where a 2 m wide channel carries 3 m^2 of flow area.
        assertEquals("1.5 m", text("h := 1 m", "root(2 m*h - 3 m^2, h) = ?"))
        assertEquals("error: root: the expression must change sign between the limits", text("root(x^2 + 1, x, 0, 1) = ?"))
        assertEquals("error: root: no root found near the guess (give limits: root(f, x, a, b))", text("x := 0", "root(x^2 + 1, x) = ?"))
        assertEquals(2.0, value("nthroot(8, 3) = ?"), 1e-15)
    }

    @Test
    fun `a range variable of the same name doesn't make an integral iterate`() {
        assertEquals("0.5", text("x := 0..10", "int(x, x, 0, 1) = ?"))
    }

    @Test
    fun `a bare 0 limit takes the other limit's unit`() {
        assertEquals("2 m^2", text("int(x, x, 0, 2 m) = ?"))
        assertEquals("1.5 m", text("root(x - 1.5 m, x, 0, 2 m) = ?"))
        assertEquals("[0; 1; 2]*m", text("i := 0, 1 m..2 m", "i = ?"))
    }
}
