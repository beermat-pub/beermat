package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Given
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Solve blocks: `given`, constraints, then `find` or `minerr` (Given … Find). */
class SolveBlockTest {

    private fun text(vararg statements: String): String = when (val o = Evaluator().run(statements.map(Parser::parseStatement)).last()) {
        is Outcome.Evaluated -> ResultText.format(o.shown)
        is Outcome.Defined -> o.shown?.let { ResultText.format(it) } ?: "defined"
        is Outcome.Failed -> "error: ${o.error.message}"
        else -> error("unexpected $o")
    }

    @Test
    fun `given parses and prints, and a variable named given still can be written`() {
        assertEquals(Given, Parser.parseStatement("given"))
        assertEquals("given", Printer.print(Given))
        assertEquals("\\it given", Printer.print(Parser.parseStatement("\\it given")))
        assertEquals("given + 1", Printer.print(Parser.parseStatement("given + 1")))
    }

    @Test
    fun `find solves the constraints from the guesses`() {
        // A circle and a line: two solutions; the guesses pick one.
        assertEquals(
            "[1.823; 0.823]",
            text("x := 1", "y := 1", "given", "x^2 + y^2 == 4", "x - y == 1", "find(x, y) = ?"),
        )
        assertEquals(
            "[-0.823; -1.823]",
            text("x := -1", "y := -1", "given", "x^2 + y^2 == 4", "x - y == 1", "find(x, y) = ?"),
        )
        assertEquals("1.414", text("x := 1", "given", "x^2 == 2", "find(x) = ?"))
    }

    @Test
    fun `units, inequalities and a definition from the result`() {
        // A beam depth for a given stress: M/(b d²/6) = σ.
        assertEquals(
            "300 mm",
            text("M := 30 kN*m", "b := 0.2 m", "σ := 10 MPa", "d := 0.5 m", "given", "M/(b*d^2/6) == σ", "d_req := find(d) = ?"),
        )
        // Many solutions (x + y = 10); the inequality, active at the solution, picks one end.
        assertEquals("7", text("x := 1", "y := 1", "given", "x + y == 10", "x >= 7", "y >= 3", "find(x, y)[0] = ?"))
        assertEquals("1.414", text("x := 0.5", "given", "x^2 == 2", "x >= 0", "find(x) = ?"))
    }

    @Test
    fun `a chained constraint holds every link`() {
        // `0 < x < 5` is 0 < x and x < 5, not (0 < x) < 5, which is always true.
        assertEquals("5", text("f(x) := -x", "x := 1", "given", "0 < x < 5", "Minimize(f, x) = ?"))
        // x = 2 would solve x² = 4, but the chain's upper link rules it out.
        assertTrue("no solution" in text("x := 1", "given", "x^2 == 4", "0 < x < 1.5", "find(x) = ?"))
    }

    @Test
    fun `a step onto a pole is shortened, not an error`() {
        // Γ(1 + 1/k) has poles the iteration can land on, from these guesses.
        assertEquals("[-0.908; -2.163]", text("λ := 1", "k := 1", "given", "λ*Γ(1 + 1/k) == -1.5", "λ^2*(Γ(1 + 2/k) - Γ(1 + 1/k)^2) == 8.286", "find([λ; k]) = ?"))
    }

    @Test
    fun `steps stay near the guesses, so a fit stays on their side of a pole`() {
        // A Weibull fit (mean 120, variance 900) from k = 1: the solution with k > 0, as imported sheets' saved results have it for such fits,
        // not one with k < 0 that a first, undamped step across k = 0 used to reach.
        assertEquals("[131.425; 4.542]", text("λ := 1", "k := 1", "given", "λ*Γ(1 + 1/k) == 120", "λ^2*(Γ(1 + 2/k) - Γ(1 + 1/k)^2) == 900", "find([λ; k]) = ?"))
    }

    @Test
    fun `minerr gives the closest values when the constraints can't all hold`() {
        assertEquals("error: find: no solution found from these guesses (try other guesses, or minerr for the closest values)", text("x := 1", "given", "x == 1", "x == 3", "find(x) = ?"))
        assertEquals("2", text("x := 1", "given", "x == 1", "x == 3", "minerr(x) = ?"))
    }

    @Test
    fun `what find needs`() {
        assertEquals("error: find needs a solve block: a 'given' line above it, then the constraints", text("x := 1", "find(x) = ?"))
        assertEquals("error: y needs a guess: define it above 'given'", text("x := 1", "given", "x + y == 2", "find(x, y) = ?"))
        // A guess whose definition failed says why, not that it is missing.
        assertEquals("error: y has an error: z is not defined", text("x := 1", "y := z", "given", "x + y == 2", "find(x, y) = ?"))
        // A block ends with the statement that solves it: a later comparison is not a constraint.
        assertEquals("1", text("x := 0", "given", "x == 1", "a := find(x)", "x == 5", "find(x) = ?"))
    }

    @Test
    fun `the unknowns as a vector of names, defining them together`() {
        assertEquals("2", text("x := 1", "y := 1", "given", "x + y == 3", "x - y == 1", "[x; y] := find([x; y])", "x = ?"))
    }
}
