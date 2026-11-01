package pub.beermat.symbolic

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Python functions in a sheet (docs/symbolic.md#python-functions-in-a-sheet), through the bundled Python. */
class PythonModulesTest {

    private val folder: File = Files.createTempDirectory("beermat-py").toFile().apply { deleteOnExit() }

    init {
        File(folder, "beam_tools.py").writeText(
            """
            |import math
            |
            |def moment(q: "kN/m", L: "m") -> "kN*m":
            |    '''The largest bending moment of a simply supported beam under a uniform load.'''
            |    return q * L**2 / 8
            |
            |def hyp(a, b=4):
            |    return math.hypot(a, b)
            |
            |def table(n):
            |    return [[i, i * i] for i in range(int(n))]
            |
            |def label(name):
            |    return "beam " + name
            |
            |def broken(x):
            |    return 1 / x
            |
            |def _private():
            |    return 0
            |""".trimMargin(),
        )
    }

    private var allowed = true
    private val modules = Symbolics.modules { allowed }!!

    private fun outcome(vararg statements: String) =
        Evaluator(folder = folder, modules = modules).run(statements.map(Parser::parseStatement)).last()

    private fun text(vararg statements: String): String = when (val o = outcome(*statements)) {
        is Outcome.Evaluated -> ResultText.format(o.shown)
        is Outcome.Defined -> ResultText.format(o.shown!!)
        is Outcome.Failed -> "error: ${o.error.message}"
        else -> kotlin.error("unexpected $o")
    }

    private val use = "bt := use \"beam_tools.py\""

    @Test
    fun `use and the dot parse and print back`() {
        for (s in listOf(use, "M := bt.moment(12 kN/m, 6 m) = ? kN*m", "g(x) := bt.hyp(x)", "y := (bt_1).f")) {
            assertEquals(s, Printer.print(Parser.parseStatement(s)))
        }
        // In a subscript the dot stays part of the name, as before.
        assertEquals("x_a.b", Printer.print(Parser.parseExpression("x_a.b")))
    }

    @Test
    fun `functions are called with their units`() {
        assertEquals("54 kN*m", text(use, "bt.moment(12 kN/m, 6 m) = ? kN*m"))
        // A plain number in a parameter without a unit, and default arguments.
        assertEquals("5", text(use, "bt.hyp(3) = ?"))
        assertEquals("13", text(use, "bt.hyp(5, 12) = ?"))
        // A function value like any other.
        assertEquals("5", text(use, "h := bt.hyp", "h(3) = ?"))
        // Lists of lists are matrices; strings are strings.
        assertEquals("[0, 0; 1, 1; 2, 4]", text(use, "bt.table(3) = ?"))
        assertEquals("\"beam B1\"", text(use, "bt.label(\"B1\") = ?"))
    }

    @Test
    fun `errors say what went wrong`() {
        assertTrue("moment: q is in kN/m" in text(use, "bt.moment(12 kN, 6 m) = ?"))
        assertTrue("hyp: a takes a plain number" in text(use, "bt.hyp(3 m) = ?"))
        assertEquals("error: ZeroDivisionError: float division by zero (line 17 of beam_tools.py)", text(use, "bt.broken(0) = ?"))
        // Names starting with `_` can't even be written after the dot; private functions aren't listed either.
        assertTrue("has no function missing (it has broken, hyp, label, moment, table)" in text(use, "bt.missing() = ?"))
        assertTrue("there is no file nothing.py" in text("m := use \"nothing.py\""))
        allowed = false
        assertTrue("hasn't been allowed yet" in text(use))
    }

    @Test
    fun `the module is described for completion and hover`() {
        val functions = (modules as PythonModules).describe(File(folder, "beam_tools.py"))
        assertEquals(listOf("broken", "hyp", "label", "moment", "table"), functions.map { it.name })
        val moment = functions.first { it.name == "moment" }
        assertEquals("moment(q: 'kN/m', L: 'm') -> 'kN*m'", moment.signature)
        assertTrue(moment.doc.startsWith("The largest bending moment"))
    }
}
