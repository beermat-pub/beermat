package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Eigenvalues, Fourier transforms, ODE solvers, Minimize and Maximize, end to end. */
class NumericsTest {

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
    fun eigenvalues() {
        assertEquals("[1; 3]", text("eigenvals([2, 1; 1, 2]) = ?"))
        assertEquals("[-1i; 1i]", text("eigenvals([0, -1; 1, 0]) = ?"))
        assertEquals("[2; 3; 5]*N/m", text("eigenvals([2, 0, 0; 0, 3, 4; 0, 0, 5]*1 N/m) = ?"))
        // The vectors are unit length, columns in the order of the values.
        assertEquals("[0.707, 0.707; -0.707, 0.707]", text("eigenvecs([2, 1; 1, 2]) = ?"))
        assertEquals("[0.707; 0.707]", text("eigenvec([2, 1; 1, 2], 3) = ?"))
        // A bigger symmetric one: the stiffness of a chain of 6 springs, whose eigenvalues are 2 − 2cos(kπ/7).
        val k = "K := matrix(6, 6, f)"
        assertEquals("0.753", text("f(i, j) := if(i == j, 2, if(abs(i - j) == 1, -1, 0))", k, "eigenvals(K)[1] = ?"))
    }

    @Test
    fun `fourier transforms`() {
        assertEquals("[0.5; 0.5; 0.5; 0.5]", text("cfft([1; 0; 0; 0]) = ?"))
        assertEquals("[1; 0; 0; 0]", text("CFFT([1; 1; 1; 1]) = ?"))
        assertEquals("3", text("length(fft([1; 2; 3; 4])) = ?"))
        assertEquals("[1; 2; 3; 4]", text("ifft(fft([1; 2; 3; 4])) = ?"))
        assertEquals("[1; 2; 3]", text("icfft(cfft([1; 2; 3])) = ?"))
        assertTrue("2^m values" in error("fft([1; 2; 3]) = ?"))
    }

    @Test
    fun `ode solvers`() {
        // y' = −y from y(0) = 1: y(1) = e^−1.
        assertEquals("0.368", text("D(x, y) := -y", "S := rkfixed(1, 0, 1, 10, D)", "S[10, 1] = ?"))
        // Adaptive steps reach e^−1 to many figures: 367879.441 in millionths.
        assertEquals("367879.441", text("D(x, y) := -y", "S := Rkadapt(1, 0, 1, 2, D)", "S[2, 1]*10^6 = ?"))
        // A pendulum-like system with units: x'' = −ω² x, as two first-order equations.
        assertEquals(
            "-1 m",
            text("ω := π/(1 s)", "D(t, y) := [y[1]; -ω^2*y[0]]", "S := Rkadapt([1 m; 0 m/s], 0 s, 1 s, 4, D)", "S[4, 1] = ? m"),
        )
        assertTrue("wrong units" in error("D(t, y) := y", "rkfixed(1 m, 0 s, 1 s, 2, D) = ?"))
    }

    @Test
    fun `minimize and maximize`() {
        assertEquals("3 m", text("f(x) := (x - 3 m)^2/(1 m^2) + 1", "x := 0 m", "Minimize(f, x) = ?"))
        // Trial points where the function can't be evaluated (here a complex value below x = 1) are passed over.
        assertEquals("3", text("f(x) := (x - 3)^2 + 0*max(sqrt(x - 1), 0)", "x := 1.01", "round(Minimize(f, x), 3) = ?"))
        assertEquals("2", text("g(x) := -(x - 2)^2", "x := 0", "Maximize(g, x) = ?"))
        assertEquals("[1; 1]", text("r(a, b) := (1 - a)^2 + 100*(b - a^2)^2", "a := -1", "b := 1", "Minimize(r, a, b) = ?"))
        // Within a solve block's constraints.
        assertEquals("4", text("f(x) := (x - 3)^2", "x := 10", "given", "x >= 4", "Minimize(f, x) = ?"))
    }
}
