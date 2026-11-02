package pub.beermat.symbolic

import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CasTest {

    /** The result of the last statement, a symbolic evaluation, as text. */
    private fun sym(vararg statements: String): String {
        val outcome = Evaluator(symbolic = Cas).run(statements.map(Parser::parseStatement)).last()
        if (outcome is Outcome.Failed) error("failed: ${outcome.error.message}")
        return Printer.print(assertIs<Outcome.Symbolic>(outcome).result)
    }

    private fun error(vararg statements: String): String =
        assertIs<Outcome.Failed>(Evaluator(symbolic = Cas).run(statements.map(Parser::parseStatement)).last()).error.message!!

    @Test
    fun `simplification collects like terms, exactly`() {
        assertEquals("2*x", sym("x + x -> ?"))
        assertEquals("3*x^2", sym("x*x*3 -> ?"))
        assertEquals("5*x/6", sym("x/2 + x/3 -> ?"))
        assertEquals("2/x", sym("1/x + 1/x -> ?"))
        assertEquals("0", sym("a - a -> ?"))
        assertEquals("2", sym("sqrt(2)^2 -> ?"))
        assertEquals("sqrt(2)", sym("sqrt(2) -> ?"))
        assertEquals("3/4", sym("0.75 -> ?"))
        assertEquals("x^5", sym("x^2*x^3 -> ?"))
    }

    @Test
    fun `expand`() {
        assertEquals("x^2 + 2*x + 1", sym("(x + 1)^2 -> expand -> ?"))
        assertEquals("a*c + a*d + b*c + b*d", sym("(a + b)*(c + d) -> expand -> ?"))
    }

    @Test
    fun `derivatives`() {
        assertEquals("3*x^2", sym("diff(x^3, x) -> ?"))
        assertEquals("x*cos(x) + sin(x)", sym("diff(x*sin(x), x) -> ?"))
        assertEquals("6*x", sym("diff(x^3, x, 2) -> ?"))
        assertEquals("1/x", sym("diff(ln(x), x) -> ?"))
        assertEquals("2*e^(2*t)", sym("diff(exp(2*t), t) -> ?"))
    }

    @Test
    fun `solve`() {
        assertEquals("[-2; 2]", sym("x^2 - 4 -> solve(x) -> ?"))
        assertEquals("-3", sym("2*x + 6 -> solve(x) -> ?"))
        assertEquals("[-sqrt(2); sqrt(2)]", sym("x^2 == 2 -> solve(x) -> ?"))
        assertEquals("solving with complex roots isn't available in the web version: install Beermat on your computer (beermat.pub/download) for all of it", error("x^2 + 1 -> solve(x) -> ?"))
    }

    @Test
    fun `substitute and float`() {
        assertEquals("y^2 + 1", sym("x^2 + 1 -> substitute(x == y) -> ?"))
        assertEquals("1.414213562", sym("sqrt(2) -> float -> ?"))
        assertEquals("3.142", sym("π -> float(4) -> ?"))
    }

    @Test
    fun `the sheet's definitions are put in, numbers and functions`() {
        assertEquals("y + 9", sym("x := 3", "x^2 + y -> ?"))
        assertEquals("2*t", sym("f(u) := u^2", "diff(f(t), t) -> ?"))
        // A number with units stays a symbol (for now).
        assertEquals("2*L", sym("L := 6 m", "L + L -> ?"))
    }

    @Test
    fun `unknown keywords and what isn't done yet`() {
        assertEquals("frobnicate is not a symbolic keyword", error("x -> frobnicate -> ?"))
        assertEquals("factor isn't available in the web version: install Beermat on your computer (beermat.pub/download) for all of it", error("x -> factor -> ?"))
    }

    @Test
    fun `the syntax round-trips`() {
        for (s in listOf("(x + 1)^2 -> expand -> ?", "x^2 - 4 -> solve(x) -> ?", "diff(x^3, x) -> ?", "x -> float(4) -> ?")) {
            assertEquals(s, Printer.print(Parser.parseStatement(s)))
        }
        assertEquals("x -> ?", Printer.print(Parser.parseStatement("x → ?")))
    }
}
