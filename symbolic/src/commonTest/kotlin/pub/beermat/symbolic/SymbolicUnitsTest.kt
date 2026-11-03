package pub.beermat.symbolic

import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Units through symbolic evaluation (docs/symbolic.md#units), with our own engine. */
class SymbolicUnitsTest {

    private fun sym(vararg statements: String): String {
        val outcome = Evaluator(symbolic = Symbolics).run(statements.map(Parser::parseStatement)).last()
        if (outcome is Outcome.Failed) error("failed: ${outcome.error.message}")
        return Printer.print(assertIs<Outcome.Symbolic>(outcome).result)
    }

    private fun error(vararg statements: String): String =
        assertIs<Outcome.Failed>(Evaluator(symbolic = Symbolics).run(statements.map(Parser::parseStatement)).last()).error.message!!

    @Test
    fun `names with units are put in, and the result shown in one unit`() {
        assertEquals("54 \\rm kN*\\rm m", sym("L := 6 m", "q := 12 kN/m", "q*L^2/8 -> ?"))
        assertEquals("36 \\rm m^2", sym("L := 6 m", "L^2 -> ?"))
        // A name not defined stays, with the unit beside it.
        assertEquals("6/x \\rm m", sym("L := 6 m", "L/x -> ?"))
        // Units that cancel disappear.
        assertEquals("3", sym("L := 6 m", "a := 2 m", "L/a -> ?"))
        // A temperature goes in absolute.
        assertEquals("5863/20 \\rm K", sym("T := 20 °C", "T -> ?"))
    }

    @Test
    fun `known units are checked`() {
        assertEquals("units do not match: L vs T", error("L := 6 m", "t := 2 s", "L + t -> ?"))
        assertEquals("sin needs a number without units, not one in m", error("L := 6 m", "sin(L) -> ?"))
        // Names not defined pass: their units aren't known.
        assertEquals("6*\\rm m + x", sym("L := 6 m", "x + L -> ?"))
    }

    @Test
    fun `a unit variable is not a variable of the same name`() {
        assertEquals("6*m \\rm m", sym("L := 6 m", "L*m -> ?"))
    }
}
