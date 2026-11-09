package pub.beermat.app.math

import pub.beermat.app.SheetItem
import pub.beermat.app.SheetItems
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Parser
import pub.beermat.export.Units
import pub.beermat.format.WorksheetText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Layout geometry with fake metrics: every character is half the font size wide. */
class MathLayoutTest {

    private val fake = Metrics { text, size -> Extent(0.5f * size * text.length, 0.7f * size, 0.2f * size) }
    private val layout = MathLayout(fake, 20f)

    private fun box(s: String) = layout.statement(Parser.parseStatement(s))

    /** The text runs of a box, left to right as placed. */
    private fun texts(b: Box): List<String> = when (b) {
        is TextBox -> listOf(b.text)
        is StrokeBox -> emptyList()
        is Group -> b.children.sortedBy { it.x }.flatMap { texts(it.box) }
    }

    private fun strokes(b: Box): Int = when (b) {
        is TextBox -> 0
        is StrokeBox -> 1
        is Group -> b.children.sumOf { strokes(it.box) }
    }

    /** Where each text run sits: its text and the y of its baseline. */
    private fun baselines(b: Box, y: Float = 0f): List<Pair<String, Float>> = when (b) {
        is TextBox -> listOf(b.text to y)
        is StrokeBox -> emptyList()
        is Group -> b.children.flatMap { baselines(it.box, y + it.y) }
    }

    @Test
    fun `a fraction stacks its parts and drops the parentheses the text needed`() {
        val f = box("(a + b)/c")
        assertEquals(listOf(i("a"), "+", i("b"), i("c")).sorted(), texts(f).sorted())
        assertEquals(1, strokes(f))
        val ys = baselines(f).toMap()
        assertTrue(ys.getValue(i("a")) < 0 && ys.getValue(i("c")) > 0, "numerator above, denominator below: $ys")
    }

    @Test
    fun `superscripts are raised and smaller`() {
        val b = box("x^2") as Group
        val sup = b.children.single { texts(it.box) == listOf("2") }
        assertTrue(sup.y < 0)
        assertEquals(14f, (sup.box as TextBox).size)
    }

    /** [name] in math italic. */
    private fun i(name: String) = MathAlphabet.italic(name)

    @Test
    fun `symbols as drawn`() {
        assertEquals(listOf(i("a"), "·", i("b")), texts(box("a*b")))
        assertEquals(listOf("3", "m"), texts(box("3 m")))
        assertEquals(listOf(i("x"), "≤", "1"), texts(box("x <= 1")))
        assertEquals(listOf(i("M"), "max", ":=", "1"), texts(box("M_max := 1")))
        assertEquals(listOf("1.2", "×", "10", "−3"), texts(box("1.2e-3")))
        assertEquals(listOf(i("v"), i("i"), ":=", "1"), texts(box("v[i] := 1")))
        assertEquals(listOf(i("M"), "T"), texts(box("M^T")))
        assertEquals(listOf("try", i("x"), "on error", "0"), texts(box("try x on error 0")))
    }

    @Test
    fun `variables are italic, units and built-in functions upright`() {
        assertEquals("𝑥", i("x"))
        assertEquals("𝛼ℎ", i("αh"))
        assertEquals(listOf(i("fib"), "(", i("n"), ")"), texts(box("fib(n)")))
        assertEquals(listOf("rows", "(", i("M"), ")"), texts(box("rows(M)")))
        assertEquals(listOf("2", "kN"), texts(box("2 kN")))
        // Gravity is a constant, italic; the gram is the upright g.
        assertEquals(listOf("250", "g", "·", i("g")), texts(box("250 \\rm g*g")))
        assertEquals(listOf(MathAlphabet.bold("F"), "·", "x", "·", i("x")), texts(box("\\bf F*\\rm x*x")))
        assertEquals("𝐅𝟏", MathAlphabet.bold("F1"))
        // A sheet that defines L means a length by it, not litres.
        val sheet = MathLayout(fake, 20f, Units.of(listOf(Parser.parseStatement("L := 6 m"))))
        assertEquals(listOf(i("L"), ":=", "6", "m"), texts(sheet.statement(Parser.parseStatement("L := 6 m"))))
    }

    @Test
    fun `big operators, radicals and matrices draw strokes`() {
        assertEquals(listOf("∑", i("k"), "=", "1", "10", i("k")).sorted(), texts(box("sum(k, k, 1, 10)")).sorted())
        assertEquals(1, strokes(box("sqrt(x)")))
        // A two-row matrix is taller than a line, so its brackets are drawn, not typed.
        assertEquals(2, strokes(box("[1; 2]")))
        assertEquals(listOf("[", "1", "2", "]"), texts(box("[1, 2]")))
    }

    @Test
    fun `programs have a bar and keywords`() {
        val p = box("f(x) :=\n  -x if x < 0\n  x otherwise")
        assertTrue("if" in texts(p) && "otherwise" in texts(p))
        assertEquals(1, strokes(p))
        val loop = box("g(n) :=\n  s <- 0\n  for k in 1..n\n    s <- s + k\n  s")
        assertEquals(2, strokes(loop), "an outer bar and the loop body's bar")
    }

    @Test
    fun `error messages draw the names they quote as in the sheet`() {
        val bold = Id("F", "max", Id.Style.BOLD)
        assertEquals(listOf(MathAlphabet.bold("F"), "max", " is not defined"), texts(layout.message("\\bf F_max is not defined", listOf(bold))))
        // Whole names only: the x in "matrix" and "xy" stays text.
        val x = Id("x")
        assertEquals(
            listOf(i("x"), " is a matrix: use ", i("x"), "[i, j], not xy"),
            texts(layout.message("x is a matrix: use x[i, j], not xy", listOf(x))),
        )
        assertEquals(listOf(i("a"), " has an error: ", i("b"), " is not defined"), texts(layout.message("a has an error: b is not defined", listOf(Id("a"), Id("b")))))
        assertEquals(listOf("x is not defined"), texts(layout.message("x is not defined")))
    }

    @Test
    fun `a sheet's errors carry their names to the view`() {
        val sheet = WorksheetText.read("```calc\ng := w + 1\ny := \\bf g\nz := g*2\n```\n")
        val errors = SheetItems.of(sheet, layout).filterIsInstance<SheetItem.Math>().map { texts(it.errorBox!!) }
        assertEquals(listOf(i("w"), " is not defined"), errors[0])
        assertEquals(listOf(MathAlphabet.bold("g"), " is not defined"), errors[1])
        assertEquals(listOf(i("g"), " has an error: ", i("w"), " is not defined"), errors[2])
    }

    @Test
    fun `a tagged layout finds every node, and a click on a leaf finds it back`() {
        val editing = layout.editing(java.util.IdentityHashMap())
        val samples = listOf(
            "M_max := q*L^2/8 = ? kN*m", "x := (-b + sqrt(b^2 - 4*a*c))/(2*a)", "sum(k^2, k, 1, 10) = ?", "v[i, j] := [1, 2; 3, 4]",
            "i := 0, 2..10", "f(x, y) := nthroot(x, 3) + |y| + vec(x)^T", "y := try 1/x on error 0",
            "g(n) :=\n  s <- 0\n  for k in 1..n\n    s <- s + k\n  return s if s > 10\n  s",
        )
        for (src in samples) {
            val st = Parser.parseStatement(src)
            val box = editing.statement(st)
            for (path in pub.beermat.app.editor.Nodes.paths(st)) {
                val node = pub.beermat.app.editor.Nodes.at(st, path)
                // A definition's unit slot is only drawn when it has a result; calls' names are drawn as notation.
                val r = BoxPaths.rect(box, path) ?: if (node is pub.beermat.engine.syntax.Id && path.last() == 0) continue else error("$src: no box for $path ($node)")
                if (pub.beermat.app.editor.Nodes.isLeaf(node) && pub.beermat.app.editor.Nodes.children(node).isEmpty()) {
                    val hit = BoxPaths.at(box, r.x + r.width / 2, r.y - (r.ascent - r.descent) / 2)
                    assertEquals(path, hit, "$src: click on $node")
                }
            }
        }
    }

    @Test
    fun `the result unit slot is a child of the statement`() {
        val editing = layout.editing(java.util.IdentityHashMap())
        val box = editing.statement(Parser.parseStatement("x = ?"))
        assertTrue(BoxPaths.rect(box, listOf(1)) != null, "the empty unit slot is drawn while editing")
        assertTrue(BoxPaths.rect(layout.statement(Parser.parseStatement("x = ?")), listOf(1)) == null, "and not otherwise")
    }

    @Test
    fun `results lay out as number and unit`() {
        val b = layout.statement(Parser.parseStatement("M = ?"), "54", "kN*m")
        assertEquals(listOf(i("M"), "=", "54", "kN", "·", "m"), texts(b))
    }

    @Test
    fun `every statement of the examples lays out`() {
        for (name in listOf("beam.bbm", "beam-table.bbm")) {
            val text = java.io.File("../examples/$name").readText()
            val items = SheetItems.of(WorksheetText.read(text), layout)
            assertTrue(items.filterIsInstance<SheetItem.Math>().isNotEmpty())
            assertTrue(items.filterIsInstance<SheetItem.Math>().all { it.error == null && it.box.width > 0 }, name)
        }
    }

    @Test
    fun markdown() {
        val items = SheetItems.markdown(listOf("# Title", "", "<!-- #t1 x=1 -->", "Some **bold** text", "on two lines.", "```plot {#p1}", "x: 1", "```"))
        assertEquals(
            listOf(SheetItem.Text("Title", 1), SheetItem.Text("Some bold text on two lines."), SheetItem.Pending("plot")),
            items,
        )
    }
}
