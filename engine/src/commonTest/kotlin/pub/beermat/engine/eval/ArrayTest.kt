package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Arrays, end to end: the text of the last result, as the CLI shows it. */
class ArrayTest {

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
    fun literals() {
        assertEquals("[1, 2; 3, 4]", text("[1, 2; 3, 4] = ?"))
        assertEquals("[1; 2; 3]*m", text("[1; 2; 3]*1 m = ?"))
        assertEquals("[3 kN; 2 kN*m]", text("[3 kN; 2 kN*m] = ?"))
        assertEquals("[\"a\", 1]", text("[\"a\", 1] = ?"))
        assertEquals("[1000, 2000]*mm", text("[1, 2]*1 m = ? mm"))
    }

    @Test
    fun indexing() {
        assertEquals("20", text("v := [10; 20; 30]", "v[1] = ?"))
        assertEquals("4", text("M := [1, 2; 3, 4]", "M[1, 1] = ?"))
        assertEquals("[2; 4]", text("M := [1, 2; 3, 4]", "M^<1> = ?"))
        assertEquals("10", text("ORIGIN := 1", "v := [10; 20; 30]", "v[1] = ?"))
        assertEquals("element 3 is out of range: there are 3, numbered from ORIGIN = 0", error("v := [10; 20; 30]", "v[3] = ?"))
        assertEquals("a 2×2 matrix needs two indices, as in M[i, j]", error("M := [1, 2; 3, 4]", "M[1] = ?"))
        assertEquals("an index must be a whole number", error("v := [10; 20]", "v[0.5] = ?"))
    }

    @Test
    fun `element definitions grow the array with zeros`() {
        assertEquals("[0; 0; 5]*m", text("v[2] := 5 m", "v = ?"))
        assertEquals("[1; 7; 3]", text("v := [1; 2; 3]", "v[1] := 7", "v = ?"))
        assertEquals("[1, 2, 0; 3, 4, 9]", text("M := [1, 2; 3, 4]", "M[1, 2] := 9", "M = ?"))
        assertEquals("[1, 2, 5]", text("r := [1, 2]", "r[2] := 5", "r = ?"))
        // The original is unchanged: definitions make copies.
        assertEquals("[1; 2]", text("v := [1; 2]", "w := v", "w[0] := 9", "v = ?"))
    }

    @Test
    fun `arrays grown at the end share their elements but never see each other's`() {
        // v and w grow from the same u: each keeps its own last element, and u stays as it was.
        assertEquals("[1; 2; 3; 0]", text("u := [1; 2]", "v := u", "v[2] := 3", "w := u", "w[2] := 4", "w[3] := 5", "v[3] := 0", "v = ?"))
        assertEquals("[1; 2; 4; 5]", text("u := [1; 2]", "v := u", "v[2] := 3", "w := u", "w[2] := 4", "w[3] := 5", "v[3] := 0", "w = ?"))
        assertEquals("[1; 2]", text("u := [1; 2]", "v := u", "v[2] := 3", "u = ?"))
        assertEquals("[1, 2; 3, 4; 0, 6]", text("M := [1, 2; 3, 4]", "M[2, 1] := 6", "M = ?"))
        // A program's loop, element by element.
        assertEquals("[0; 1; 19999]", text("v :=\n    for i in 0..19999\n        a[i] <- i\n    a", "[v[0]; v[1]; v[19999]] = ?"))
    }

    @Test
    fun arithmetic() {
        assertEquals("[4; 6]", text("[1; 2] + [3; 4] = ?"))
        assertEquals("[2, 4; 6, 8]", text("2*[1, 2; 3, 4] = ?"))
        assertEquals("[0.5; 1]", text("[1; 2]/2 = ?"))
        assertEquals("[-1; -2]", text("-[1; 2] = ?"))
        assertEquals("11", text("[1; 2]*[3; 4] = ?"))
        // A row times a column is a number too: no 1×1 matrix.
        assertEquals("11", text("[1, 2]*[3; 4] = ?"))
        assertEquals("[3, 4; 6, 8]", text("[1; 2]*[3, 4] = ?"))
        assertEquals("[7, 10; 15, 22]", text("M := [1, 2; 3, 4]", "M*M = ?"))
        assertEquals("[7, 10; 15, 22]", text("M := [1, 2; 3, 4]", "M^2 = ?"))
        assertEquals("[1, 3; 2, 4]", text("[1, 2; 3, 4]^T = ?"))
        assertEquals("[1, 2, 3]", text("[1; 2; 3]^T = ?"))
        assertEquals("addition: matrix sizes do not match: 2×1 and 3×1", error("[1; 2] + [1; 2; 3] = ?"))
        assertEquals("cannot multiply a 2×1 matrix by a 2×2 matrix: the columns of the first must match the rows of the second", error("[1; 2]*[1, 2; 3, 4] = ?"))
        assertEquals("cannot divide by a matrix: multiply by its inverse, M^-1", error("1/[1, 2; 3, 4] = ?"))
    }

    @Test
    fun `units element by element`() {
        assertEquals("[4 m; 6 s]", text("[1 m; 2 s] + [3 m; 4 s] = ?"))
        assertEquals("units do not match: L vs T", error("[1 m; 2 s] + [3 s; 4 m] = ?"))
        assertEquals("[2, 3]*m", text("[2 m, 3000 mm] = ?"))
    }

    @Test
    fun `a bare 0 beside elements with one unit takes it`() {
        assertEquals("[0; 5]*N", text("ΔT := [0; 5 K]", "α := 2/K", "ΔT*α*0.5 N = ?"))
        // Mixed units stay as they are.
        assertEquals("[0; 1 m; 2 s]", text("[0; 1 m; 2 s] = ?"))
    }

    @Test
    fun `inverse and determinant`() {
        assertEquals("[-2, 1; 1.5, -0.5]", text("[1, 2; 3, 4]^-1 = ?"))
        assertEquals("-2", text("|[1, 2; 3, 4]| = ?"))
        assertEquals("[1, 0; 0, 1]", text("M := [4, 7; 2, 6]", "M*M^-1 = ?"))
        assertEquals("[1, 0; 0, 1]", text("[1, 2; 3, 4]^0 = ?"))
        assertEquals("the matrix is singular: it has no inverse", error("[1, 2; 2, 4]^-1 = ?"))
        assertEquals("0", text("|[1, 2; 2, 4]| = ?"))
        assertEquals("5", text("|[3; 4]| = ?"))
        assertEquals("5 m", text("|[3 m; 4 m]| = ?"))
        assertEquals("|M| needs a square matrix (determinant) or a vector (length), not 2×3", error("|[1, 2, 3; 4, 5, 6]| = ?"))
    }

    @Test
    fun `a stiffness matrix with mixed units inverts`() {
        // [F; M] = K [u; θ]: K has units [N/m, N; N, N*m], so K^-1 has [m/N, 1/N; 1/N, 1/(N*m)].
        val k = "K := [12 N/m, 6 N; 6 N, 4 N*m]"
        assertEquals("[333.333 mm/N, -0.5/N; -0.5/N, 1/(N*m)]", text(k, "K^-1 = ?"))
        assertEquals("[1, 0/m; 0 m, 1]", text(k, "K*K^-1 = ?")) // the zeros keep their units
        assertEquals("12 N^2", text(k, "|K| = ?"))
        assertEquals("the inverse: the units of the matrix are not consistent (element [1, 1] is L)", error("[1, 1 m; 1 m, 1 m]^-1 = ?"))
    }

    @Test
    fun functions() {
        assertEquals("2", text("rows([1, 2; 3, 4]) = ?"))
        assertEquals("3", text("cols([1, 2, 3]) = ?"))
        assertEquals("3", text("length([1; 2; 3]) = ?"))
        assertEquals("2", text("last([1; 2; 3]) = ?"))
        assertEquals("3", text("ORIGIN := 1", "last([1; 2; 3]) = ?"))
        assertEquals("[1, 0; 0, 1]", text("identity(2) = ?"))
        assertEquals("[0, 1, 2; 10, 11, 12]", text("f(i, j) := 10*i + j", "matrix(2, 3, f) = ?"))
        assertEquals("[1, 2, 5; 3, 4, 6]", text("augment([1, 2; 3, 4], [5; 6]) = ?"))
        assertEquals("[1, 2; 3, 4; 5, 6]", text("stack([1, 2; 3, 4], [5, 6]) = ?"))
        assertEquals("[5, 6; 8, 9]", text("submatrix([1, 2, 3; 4, 5, 6; 7, 8, 9], 1, 2, 1, 2) = ?"))
        assertEquals("5", text("tr([1, 2; 3, 4]) = ?"))
        assertEquals("6 m", text("sum([1; 2; 3]*1 m) = ?"))
        assertEquals("2.5", text("mean([1, 2; 3, 4]) = ?"))
        assertEquals("9", text("max([1; 9; 3], 4) = ?"))
        // A one-number built-in goes element by element (docs/evaluation.md); a string still is not a number.
        assertEquals("sqrt: expected a number, not a string", error("sqrt(\"a\") = ?"))
    }

    @Test
    fun `names in a matrix each get their element`() {
        assertEquals("2", text("[a; b] := [1; 2]", "b = ?"))
        assertEquals("3 m", text("[x, y] := [3, 4]*1 m", "x = ?"))
        assertEquals("[1; 5]", text("f(t) := [t; 5*t]", "[p; q] := f(1)", "[p; q] = ?"))
        assertTrue("2×1 names" in error("[a; b] := [1, 2]"))
        assertTrue("b has an error" in error("[a; b] := 1", "b = ?"))
        // In a program too.
        assertEquals("3", text("s :=\n    [a; b] <- [1; 2]\n    a + b", "s = ?"))
    }

    @Test
    fun `a column is defined whole, growing the matrix`() {
        assertEquals("[1, 0; 2, 0; 0, 7]", text("M^<0> := [1; 2]", "M^<1> := [0; 0; 7]", "M = ?"))
        assertEquals("[1, 5; 2, 6]", text("M := [1, 2; 2, 3]", "M^<1> := [5; 6]", "M = ?"))
        assertEquals("[4; 4]", text("ORIGIN := 1", "M^<1> := [4; 4]", "M = ?"))
    }

    @Test
    fun `the new targets print back as written`() {
        for (s in listOf("[a; b] := f(x)", "[x, y] := v", "M^<j> := v", "M^<2> := [1; 2]")) assertEquals(s, pub.beermat.engine.syntax.Printer.print(Parser.parseStatement(s)))
    }

    @Test
    fun `a number over a vector is element by element`() {
        assertEquals("[0.5; 0.25]/Pa", text("1/([2; 4]*1 Pa) = ?"))
        assertTrue("inverse" in error("1/[1, 2; 3, 4] = ?"))
    }

    @Test
    fun `vectors divide, and arrays that are not square take powers, element by element`() {
        assertEquals("[2, 0.5]*m", text("[4 m, 2 m]/[2, 4] = ?"))
        assertEquals("[4, 9]*m^2", text("[2 m, 3 m]^2 = ?"))
        assertEquals("[1; 8]", text("[1; 2]^3 = ?"))
        assertEquals("[7, 10; 15, 22]", text("[1, 2; 3, 4]^2 = ?"))
        assertTrue("sizes do not match" in error("[1, 2]/[1, 2, 3] = ?") || "inverse" in error("[1, 2]/[1, 2, 3] = ?"))
    }
}
