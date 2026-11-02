package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/** Range variables, range sums and products, and vectorize. */
class RangeTest {

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
    fun syntax() {
        for (s in listOf("i := 0..10", "x := 0, 0.1..1", "n := 1..N - 1", "x := 0 m, 0.5 m..L = ? m")) {
            assertEquals(s, Printer.print(Parser.parseStatement(s)))
        }
        assertEquals(
            "expected '..': a stepped range is written first, second..last (column 10)",
            assertIs<pub.beermat.engine.syntax.ParseException>(runCatching { Parser.parseStatement("x := 0, 1") }.exceptionOrNull()).message,
        )
    }

    @Test
    fun `range variables evaluate once per value`() {
        assertEquals("[0; 1; 2; 3]", text("i := 0..3", "i = ?"))
        assertEquals("[3; 2; 1]", text("i := 3..1", "i = ?"))
        assertEquals("[0; 0.1; 0.2; 0.3]", text("x := 0, 0.1..0.3", "x = ?"))
        assertEquals("[0; 250; 500]*mm", text("x := 0 m, 0.25 m..0.5 m", "x = ?"))
        assertEquals("[0; 1; 4; 9]", text("i := 0..3", "i^2 = ?"))
        assertEquals("[1; 2; 5]", text("f(x) := x^2 + 1", "i := 0..2", "f(i) = ?"))
        assertEquals("[0; 1; 2]", text("i := 0..2", "i := 0..2 = ?"))
        assertEquals("[11; 12; 21; 22]", text("i := 1..2", "j := 1..2", "10*i + j = ?"))
    }

    @Test
    fun `indexed definitions iterate`() {
        assertEquals("[0; 1; 4; 9]", text("i := 0..3", "v[i] := i^2", "v = ?"))
        assertEquals("[2, 3; 3, 4]", text("i := 0..1", "j := 0..1", "M[i, j] := i + j + 2", "M = ?"))
        // Each element sees the ones before it.
        assertEquals("[1; 2; 4; 8]", text("x[0] := 1", "i := 0..2", "x[i + 1] := 2*x[i]", "x = ?"))
        assertEquals("[1; 1; 2; 3; 5; 8]", text("F[0] := 1", "F[1] := 1", "k := 2..5", "F[k] := F[k - 1] + F[k - 2]", "F = ?"))
    }

    @Test
    fun `range errors`() {
        assertEquals("i is a range variable: it can define the elements of an array (v[i] := …), not a single value", error("i := 0..3", "y := i^2"))
        assertEquals("a range with units needs a step, as in 0 m, 0.1 m..1 m", error("x := 0 m..1 m"))
        assertEquals("the step goes away from the last value, so the range is empty", error("x := 0, -1..10"))
        assertEquals("the range's step is zero", error("x := 0, 0..10"))
        assertEquals("units do not match in the range: L, T", error("x := 0 m..1 s"))
        assertEquals(RangeValue.MISUSE, error("i := 0..3", "f(x) := x*i", "f(2) = ?"))
    }

    @Test
    fun `sums and products`() {
        assertEquals("55", text("sum(k, k, 1, 10) = ?"))
        assertEquals("120", text("prod(k, k, 1, 5) = ?"))
        assertEquals("0", text("sum(k, k, 1, 0) = ?"))
        assertEquals("14", text("i := 0..2", "v[i] := i + 1", "sum(v[i]^2, i) = ?"))
        assertEquals("6 m", text("sum(k*1 m, k, 1, 3) = ?"))
        // The sum's variable doesn't make the statement iterate.
        assertEquals("6", text("i := 1..3", "sum(i, i) = ?"))
        assertEquals("[6; 7]", text("i := 1..3", "j := 0..1", "sum(i, i) + j = ?"))
        assertEquals("sum: n is not a range variable (or give the limits: sum(expr, n, a, b))", error("n := 3", "sum(n, n) = ?"))
        assertEquals("sum takes 1, 2 or 4 arguments: sum(v), sum(expr, i) or sum(expr, i, a, b)", error("sum(k, k, 1) = ?"))
        assertEquals("24", text("prod([1; 2; 3; 4]) = ?"))
    }

    @Test
    fun vectorize() {
        assertEquals("[1; 2; 3]", text("v := [1; 4; 9]", "vec(sqrt(v)) = ?"))
        assertEquals("[3; 8]", text("vec([1; 2]*[3; 4]) = ?"))
        assertEquals("[2; 5]", text("f(x) := x^2 + 1", "vec(f([1; 2])) = ?"))
        assertEquals("[0; 1]", text("vec([1; 2] > 1) = ?"))
        assertEquals("[1, 3; 2, 4]", text("vec([1, 2; 3, 4]^T) = ?"))
        assertEquals("vec: array sizes do not match: 2×1 and 3×1", error("vec([1; 2]*[1; 2; 3]) = ?"))
        // Outside vec, the same product is the dot product.
        assertEquals("11", text("[1; 2]*[3; 4] = ?"))
    }
}
