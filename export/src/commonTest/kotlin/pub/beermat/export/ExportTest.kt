package pub.beermat.export

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import pub.beermat.format.WorksheetText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {

    private val latex = Latex()
    private val mathMl = MathMl()

    private fun tex(s: String) = latex.statement(Parser.parseStatement(s))

    /** The LaTeX of the last statement with its result, evaluated after the ones before. */
    private fun texWithResult(vararg lines: String): String {
        val statements = lines.map(Parser::parseStatement)
        val o = Evaluator().run(statements).last()
        val shown = (o as? Outcome.Evaluated)?.shown ?: (o as Outcome.Defined).shown!!
        return Latex(Units.of(statements)).statement(statements.last(), Result.of(ResultText.parts(shown)))
    }

    @Test
    fun `the SPEC example`() {
        assertEquals(
            "M_{\\mathrm{max}} := \\frac{q \\cdot L^{2}}{8} = 54\\ \\mathrm{kN} \\cdot \\mathrm{m}",
            texWithResult("q := 12 kN/m", "L := 6 m", "M_max := q*L^2/8 = ? kN*m"),
        )
    }

    @Test
    fun `latex for each construct`() {
        assertEquals("x := \\frac{-b + \\sqrt{b^{2} - 4 \\cdot a \\cdot c}}{2 \\cdot a}", tex("x := (-b + sqrt(b^2 - 4*a*c))/(2*a)"))
        assertEquals("\\sum_{k=1}^{10} k^{2} = \\blacksquare", tex("sum(k^2, k, 1, 10) = ?"))
        assertEquals("\\begin{bmatrix}1 & 2 \\\\ 3 & 4\\end{bmatrix}", tex("[1, 2; 3, 4]"))
        assertEquals("\\mathrm{rows}\\left(M\\right)", tex("rows(M)"))
        assertEquals("\\mathbf{F} \\cdot \\mathrm{x} \\cdot \\boldsymbol{\\alpha}_{\\mathrm{1}}", tex("\\bf F*\\rm x*\\bf α_1"))
        assertEquals("\\mathbf{try\\ } 1 \\mathbf{\\ on\\ error\\ } 0", tex("try 1 on error 0"))
        assertEquals("v_{i} := i^{2}", tex("v[i] := i^2"))
        assertEquals("M^{\\mathsf{T}} \\cdot M^{\\langle 1 \\rangle}", tex("M^T*M^<1>"))
        assertEquals("1.2 \\times 10^{-3}\\,\\mathrm{Pa}", tex("1.2e-3 Pa"))
        assertEquals("\\alpha \\leq \\Delta", tex("α <= Δ"))
        assertEquals("20\\,\\mathrm{{}^{\\circ}C} - 5\\,\\mathrm{\\Delta {}^{\\circ}F}", tex("20 °C - 5 Δ°F"))
        assertEquals("\\mathit{fib}\\left(n\\right)", tex("fib(n)"))
        assertEquals("\\sin\\left(x\\right)", tex("sin(x)"))
        assertEquals("\\sqrt[3]{27}", tex("nthroot(27, 3)"))
        assertEquals("\\frac{d}{dx} x^{3} = \\blacksquare", tex("diff(x^3, x) = ?"))
        assertEquals("\\int_{0}^{1} x^{2}\\,dx = \\blacksquare", tex("int(x^2, x, 0, 1) = ?"))
        assertEquals("{\\left(x + 1\\right)}^{2} \\rightarrow \\mathbf{expand} \\rightarrow \\blacksquare", tex("(x + 1)^2 -> expand -> ?"))
        // A symbolic statement has no units: `t` is a variable there, tonne elsewhere.
        assertEquals("t \\rightarrow \\mathbf{laplace(t)} \\rightarrow \\frac{1}{s^{2}}", latex.statement(Parser.parseStatement("t -> laplace(t) -> ?"), Result("1/s^2", Parser.parseExpression("1/s^2"), null)))
        assertTrue(mathMl.statement(Parser.parseStatement("t -> laplace(t) -> ?")).startsWith("<mrow><mi>t</mi>"))
        assertEquals("3\\,\\mathrm{t}", tex("3 t"))
        assertEquals("\\left|x\\right| + \\overrightarrow{v \\cdot w}", tex("|x| + vec(v*w)"))
        assertEquals("i := 0, 2 .. 10", tex("i := 0, 2..10"))
        assertEquals("\\text{“OK”}", tex("\"OK\""))
        assertEquals(
            "f\\left(x\\right) := \\left|\\begin{array}{l}-x \\quad \\mathbf{if}\\ x < 0 \\\\ x \\quad \\mathbf{otherwise}\\end{array}\\right.",
            tex("f(x) :=\n  -x if x < 0\n  x otherwise"),
        )
    }

    @Test
    fun `multiplication follows the sheet, unless an operator has its own style`() {
        val e = Parser.parseExpression("a*b + c·d + e f")
        assertEquals("a \\cdot b + c \\cdot d + e\\,f", Latex().expr(e))
        assertEquals("a\\,b + c \\cdot d + e\\,f", Latex(multiplication = pub.beermat.engine.syntax.Multiplication.SPACE).expr(e))
        assertTrue("<mspace" in MathMl(multiplication = pub.beermat.engine.syntax.Multiplication.SPACE).expr(Parser.parseExpression("a*b")))
    }

    @Test
    fun `a sheet's own definitions are not units`() {
        val units = Units.of(listOf(Parser.parseStatement("L := 6 m")))
        assertEquals("L \\cdot \\mathrm{m}", Latex(units).expr(Parser.parseExpression("L*m")))
        assertEquals("\\mathrm{L} \\cdot \\mathrm{m}", Latex().expr(Parser.parseExpression("L*m")))
    }

    @Test
    fun `MathML for each construct`() {
        // Well formed too: MathMlXmlTest parses it, on the JVM.
        assertTrue("<mfrac>" in mathMl.math(Parser.parseStatement("a/b")))
        assertTrue("""<mi mathvariant="normal">kN</mi>""" in mathMl.math(Parser.parseStatement("3 kN")))
        assertTrue("""<mi mathvariant="italic">fib</mi>""" in mathMl.math(Parser.parseStatement("fib(n)")))
        assertTrue("""<mi mathvariant="normal">rows</mi>""" in mathMl.math(Parser.parseStatement("rows(M)")))
        assertTrue("<mo>[</mo><mtable>" in mathMl.math(Parser.parseStatement("[1; 2]")))
        assertTrue("""<mi mathvariant="bold">F</mi>""" in mathMl.math(Parser.parseStatement("\\bf F")))
        assertTrue("""<mi mathvariant="normal">x</mi>""" in mathMl.math(Parser.parseStatement("\\rm x")))
    }

    @Test
    fun `worksheet to markdown`() {
        val sheet = WorksheetText.read(
            """
            |---
            |title: Beam
            |---
            |# Beam
            |
            |<!-- #t1 x=40 y=40 -->
            |A **simple** beam.
            |
            |```calc {#r1}
            |L := 6 m
            |q := 12 kN/m
            |M := q*L^2/8 = ? kN*m
            |y := z
            |```
            |
            |```plot {#p1}
            |x: 0 m .. L
            |```
            |""".trimMargin(),
        )
        assertEquals(
            """
            |# Beam
            |
            |A **simple** beam.
            |
            |$$
            |L := 6\,\mathrm{m}
            |$$
            |
            |$$
            |q := \frac{12\,\mathrm{kN}}{\mathrm{m}}
            |$$
            |
            |$$
            |M := \frac{q \cdot L^{2}}{8} = 54\ \mathrm{kN} \cdot \mathrm{m}
            |$$
            |
            |$$
            |y := z
            |$$
            |*Error: z is not defined*
            |
            |*(A plot: not exported yet.)*
            |""".trimMargin(),
            SheetExport.markdown(sheet),
        )
        val tex = SheetExport.latex(sheet)
        assertTrue(tex.startsWith("\\documentclass{article}"))
        assertTrue("\\section*{Beam}" in tex && "A \\textbf{simple} beam." in tex && "\\[ L := 6\\,\\mathrm{m} \\]" in tex, tex)
    }
}
