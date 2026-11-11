package pub.beermat.export

import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LatexInputTest {

    private val input = LatexInput()

    /** The LaTeX read as the linear syntax prints it. */
    private fun read(latex: String) = Printer.print(input.statement(latex))

    @Test
    fun `what the LaTeX export writes reads back as the same statement`() {
        val sheets = listOf(
            "M := q*L^2/8",
            "M_max := q L^2/8",
            "x = ?",
            "y := sqrt(a^2 + b^2)",
            "r := nthroot(x + 1, 3)",
            "f(x, y) := x^2 + 2*x*y - y/3",
            "F := 12 kN",
            "q := 12 kN/m",
            "σ := F/(b*h)",
            "A := [1, 2; 3, 4]",
            "v := [1; 2; 3]",
            "B := A^T",
            "c := M^<1>",
            "e := v[i + 1]",
            "m := M[i, j]",
            "s := sum(v[k + 1], k, 0, 2)",
            "p := prod(k, k, 1, n)",
            "I := int(x^2, x, 0, 1)",
            "d := diff(x^3, x)",
            "d2 := diff(x^3, x, 2)",
            "a := |x - 3|",
            "g ::= 9.81 m/s^2",
            "t := try 1/x on error 0",
            "z := x ÷ y",
            "w := x ∕ y",
            "k := (a + b)/(c - d)",
            "u := -x^2",
            "n := 5!",
            "x + y == 10",
            "x < 3",
            "a <= b",
            "a != b",
            "small := 1.5e-3",
            "big := 2e6",
            "i := 0..10",
            "h := 1, 1.5..3",
            "\\rm P := 3",
            "\\bf F := [1; 2]",
            "α := 30 deg",
            "Δx := 2",
            "width := 3",
            "s := \"hello\"",
            "x^3 - 1 -> ?",
            "(x + 1)^2 -> expand -> ?",
            "v2 := vec(a*b)",
            "c := max(a, b)",
            "e := e^(x + 1)",
            "ok := not a",
            "both := a and b",
            "f'(x) := 2*x",
        )
        for (text in sheets) {
            val st = Parser.parseStatement(text)
            val latex = Latex().statement(st)
            val back = try {
                input.statement(latex)
            } catch (e: LatexInput.Error) {
                throw AssertionError("$text → $latex: ${e.message}")
            }
            assertEquals(Printer.print(st), Printer.print(back), "$text → $latex")
            assertEquals(st, back, "$text → $latex")
        }
    }

    @Test
    fun `LaTeX as someone types it`() {
        assertEquals("M := q L^2/8", read("M = \\frac{q L^2}{8}"))
        assertEquals("M := q L^2/8", read("\$M = \\frac{qL^2}{8}\$"))
        assertEquals("M_max := q L^2/8", read("M_{max} := \\frac{q L^2}{8}"))
        assertEquals("x_1 := 2", read("x_1 = 2"))
        assertEquals("F := 12 kN", read("F = 12\\,\\mathrm{kN}"))
        assertEquals("F := 12 kN", read("F = 12 kN"))
        assertEquals("x = ?", read("x ="))
        assertEquals("x = ? mm", read("x = \\blacksquare\\ \\mathrm{mm}"))
        assertEquals("y := sin(x) + cos(2 x)", read("y = \\sin x + \\cos(2x)"))
        assertEquals("a := (b + c) d", read("a = (b + c) d"))
        assertEquals("a := 2*(b + c)", read("a = 2\\left(b + c\\right)"))
        assertEquals("A := [1, 2; 3, 4]", read("A = \\begin{bmatrix} 1 & 2 \\\\ 3 & 4 \\end{bmatrix}"))
        assertEquals("s := sum(i^2, i, 1, n)", read("s = \\sum_{i=1}^{n} i^2"))
        assertEquals("I := int(x^2, x, 0, 1)", read("I = \\int_0^1 x^2 \\, dx"))
        assertEquals("v := v[i]", read("v = v_{[i]}"))
        assertEquals("T := 20 °C", read("T = 20^{\\circ}C"))
        assertEquals("α := 30 deg", read("\\alpha = 30\\,\\mathrm{deg}"))
        assertEquals("x + y == 10", read("x + y = 10"))
        assertEquals("q := 1.5e-3", read("q = 1.5 \\times 10^{-3}"))
        assertEquals("σ := M/W", read("\\sigma = M / W").replace("∕", "/"))
        assertEquals("x^2 -> ?", read("x^2 \\to"))
        assertEquals("y := x^-1", read("y = x^{-1}"))
    }

    @Test
    fun `what can't be read says why`() {
        assertFailsWith<LatexInput.Error> { input.statement("") }
        assertFailsWith<LatexInput.Error> { input.statement("\\frac{a}{") }
        assertFailsWith<LatexInput.Error> { input.statement("x = \\unknowncommand") }
        assertFailsWith<LatexInput.Error> { input.statement("\\int x") }
        assertFailsWith<LatexInput.Error> { input.statement("a \\pm b") }
    }
}
