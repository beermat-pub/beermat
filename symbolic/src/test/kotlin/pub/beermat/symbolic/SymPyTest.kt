package pub.beermat.symbolic

import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What SymPy adds behind our own engine, through the bundled Python (docs/symbolic.md#bundled-sympy). */
class SymPyTest {

    private fun outcome(vararg statements: String) = Evaluator(symbolic = Symbolics).run(statements.map(Parser::parseStatement)).last()

    /** The result of the last statement, a symbolic evaluation, as text. */
    private fun sym(vararg statements: String): String {
        val outcome = outcome(*statements)
        if (outcome is Outcome.Failed) kotlin.error("failed: ${outcome.error.message}")
        return Printer.print(assertIs<Outcome.Symbolic>(outcome).result)
    }

    private fun error(vararg statements: String): String = assertIs<Outcome.Failed>(outcome(*statements)).error.message!!

    @Test
    fun `SymPy is there in tests`() {
        assertTrue(Symbolics.hasSymPy)
    }

    @Test
    fun `integrals`() {
        assertEquals("sin(x) - x*cos(x)", sym("int(x*sin(x), x) -> ?"))
        assertEquals("1/3", sym("int(x^2, x, 0, 1) -> ?"))
        assertEquals("sqrt(π)", sym("int(e^(-x^2), x, -∞, ∞) -> ?"))
        // The variable is the integral's own, whatever the sheet gives it.
        assertEquals("1/2", sym("x := 5", "int(x, x, 0, 1) -> ?"))
    }

    @Test
    fun `limits and series`() {
        assertEquals("1", sym("lim(sin(x)/x, x, 0) -> ?"))
        assertEquals("0", sym("lim(1/x, x, ∞) -> ?"))
        assertEquals("∞", sym("lim(1/x, x, 0, \"+\") -> ?"))
        assertEquals("x^5/120 - x^3/6 + x", sym("sin(x) -> series(x, 6) -> ?"))
        assertEquals("x + (x - 1)^2/2", sym("e^(x - 1) -> series(x == 1, 3) -> ?"))
    }

    @Test
    fun `factor, collect, parfrac, coeffs`() {
        assertEquals("(x - 1)*(x + 1)", sym("x^2 - 1 -> factor -> ?"))
        assertEquals("c*x + x^2*(a + b)", sym("a*x^2 + b*x^2 + c*x -> collect(x) -> ?"))
        assertEquals("1/(x - 1) - 1/x", sym("1/(x*(x - 1)) -> parfrac(x) -> ?"))
        assertEquals("[-6; 11; -6; 1]", sym("(x - 1)*(x - 2)*(x - 3) -> coeffs(x) -> ?"))
    }

    @Test
    fun `solve beyond our own engine`() {
        // Degree 2 stays with our engine; degree 3 and complex roots go to SymPy.
        assertEquals("[-2; 2]", sym("x^2 - 4 -> solve(x) -> ?"))
        assertEquals("[1; 2; 3]", sym("x^3 - 6*x^2 + 11*x - 6 -> solve(x) -> ?"))
        assertEquals("[-1i; 1i]", sym("x^2 + 1 -> solve(x) -> ?"))
        assertEquals("[1, 2]", sym("[x + y == 3; x - y == -1] -> solve(x, y) -> ?"))
        assertEquals("no solution found", error("e^x == 0 -> solve(x) -> ?"))
    }

    @Test
    fun `transforms`() {
        assertEquals("1/s^2", sym("t -> laplace(t) -> ?"))
        assertEquals("1/(a + s)", sym("e^(-a*t) -> laplace(t) -> ?"))
        assertEquals("sin(t)", sym("1/(s^2 + 1) -> invlaplace(s) -> ?"))
    }

    @Test
    fun `the examples in docs-symbolic`() {
        assertEquals("π^2/6", sym("sum(1/k^2, k, 1, ∞) -> ?"))
        assertEquals("[2; -3; 1]", sym("(x - 1)*(x - 2) -> coeffs(x) -> ?"))
        assertEquals("2/(ω^2 + 1)", sym("e^(-|t|) -> fourier(t) -> ?"))
        assertEquals("z/(z - 1)", sym("1 -> ztrans(n) -> ?"))
    }

    @Test
    fun `a stopped Python starts again`() {
        val sympy = SymPy(Python.home()!!)
        val request = Json.write(mapOf("expr" to listOf("f", "int", listOf("v", 0L), listOf("v", 0L)), "keywords" to emptyList<Any>()))
        val first = sympy.ask(request)
        sympy.stop()
        assertEquals(first, sympy.ask(request + " "))
        assertEquals("""{"error": "unknown tree nonsense"}""", sympy.ask("""{"expr": ["nonsense"], "keywords": []}"""))
        sympy.stop()
    }

    @Test
    fun `assume`() {
        assertEquals("x", sym("sqrt(x^2) -> assume(x > 0) -> simplify -> ?"))
        assertEquals("|x|", sym("sqrt(x^2) -> assume(x, real) -> simplify -> ?"))
    }

    @Test
    fun `names of any kind, and the sheet's definitions`() {
        assertEquals("`θ*`^2/2", sym("int(`θ*`, `θ*`) -> ?"))
        assertEquals("M_max^3/3", sym("int(M_max^2, M_max) -> ?"))
        assertEquals("\\bf F^2 - 1", sym("(\\bf F - 1)*(\\bf F + 1) -> expand -> factor -> expand -> ?"))
        assertEquals("y^3/3 + 9*y", sym("x := 3", "int(y^2 + x^2, y) -> ?"))
        assertEquals("t^3/3", sym("f(u) := u^2", "int(f(t), t) -> ?"))
        // A function the sheet doesn't define stays a function.
        assertEquals("2*g(x)*diff(g(x), x)", sym("diff(g(x)^2, x) -> ?"))
    }

    @Test
    fun `float, after SymPy`() {
        assertEquals("0.7468241328", sym("int(e^(-x^2), x, 0, 1) -> float -> ?"))
        assertEquals("[1.41; -0.707 - 1.22i; -0.707 + 1.22i]", sym("x^3 - 2*sqrt(2) -> solve(x) -> float(3) -> ?"))
    }

    @Test
    fun `errors come back as messages`() {
        assertEquals("frobnicate is not a symbolic keyword", error("x -> frobnicate -> ?"))
        assertTrue(error("x -> series(2) -> ?").contains("series needs a variable"))
    }
}
