package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Variable styles: plain `F`, upright `\rm F` and bold `\bf F` are three variables. */
class StyleTest {

    private fun run(vararg statements: String) = Evaluator().run(statements.map(Parser::parseStatement))

    private fun text(vararg statements: String): String =
        ResultText.format(assertIs<Outcome.Evaluated>(run(*statements).last()).shown)

    private fun error(vararg statements: String): String =
        assertIs<Outcome.Failed>(run(*statements).last()).error.message!!

    @Test
    fun `styles parse and print`() {
        assertEquals(Id("F", style = Id.Style.BOLD), Parser.parseExpression("\\bf F"))
        assertEquals(Id("M", "max", Id.Style.UPRIGHT), Parser.parseExpression("\\rm   M_max"))
        assertEquals(Id("α", style = Id.Style.BOLD), Parser.parseExpression("\\bf \\alpha"))
        for (s in listOf("\\bf F", "\\rm x_1", "2 \\bf x", "\\bf f(x)", "\\bf v[1]", "x^\\bf T", "\\bf and + \\rm try", "-\\bf x")) {
            assertEquals(s, Printer.print(Parser.parseExpression(s)))
        }
        // A styled T is a name, not a transpose.
        assertEquals("Apply(op=POW, args=[Id(x), Id(\\bf T)], implicit=false, dot=false)", Parser.parseExpression("x^\\bf T").toString())
        assertFailsWith<ParseException> { Parser.parseExpression("\\bf 2") }
        assertFailsWith<ParseException> { Parser.parseExpression("\\bf \\rm x") }
    }

    @Test
    fun `each style is its own variable`() {
        assertEquals("6", text("F := 1", "\\rm F := 2", "\\bf F := 3", "F + \\rm F + \\bf F = ?"))
        // Messages name a variable as it is written.
        assertEquals("\\bf F is not defined", error("F := 1", "\\bf F = ?"))
        assertEquals("\\rm x_1 is not defined", error("\\rm x_1 = ?"))
    }

    @Test
    fun `errors list the names they quote`() {
        fun names(vararg statements: String) = assertIs<Outcome.Failed>(run(*statements).last()).error.names
        assertEquals(listOf(Id("F", style = Id.Style.BOLD)), names("\\bf F = ?"))
        assertEquals(listOf(Id("g"), Id("h")), names("g := h + 1", "g*2 = ?"))
        assertEquals(listOf(Id("i")), names("i := 0..2", "y := i^2"))
        assertEquals(listOf(Id("f")), names("f(x) := x", "f(1, 2) = ?"))
    }

    @Test
    fun `built-in names are plain`() {
        assertEquals("2 m", text("2 m = ?"))
        assertEquals("\\rm m is not defined", error("2 \\rm m = ?"))
        assertEquals("\\bf sin is not defined", error("\\bf sin(1) = ?"))
    }

    @Test
    fun `the gram is the upright g, gravity the plain one`() {
        assertEquals("0.25 kg", text("250 \\rm g = ? kg"))
        assertEquals("250 \\rm g", text("250 \\rm g = ?"))
        assertEquals("250 \\rm g", text("0.25 kg = ? \\rm g"))
        assertEquals("1500 mg", text("1.5 \\rm g = ? mg"))
        assertEquals("9.807 m/s^2", text("g = ? m/s^2"))
        assertEquals("24.517 N", text("2.5 kg*g = ?"))
    }

    @Test
    fun `a marked name is never a keyword`() {
        assertEquals("1", text("\\bf if := 1", "\\bf if = ?"))
        assertEquals("1", text("f(x) :=\n  \\bf for <- x\n  \\bf for", "f(1) = ?"))
        // `\it` marks a plain (italic) name: the same variable as an unmarked one would be.
        assertEquals(Id("if"), Parser.parseExpression("\\it if"))
        assertEquals(Id("x"), Parser.parseExpression("\\it x"))
        assertEquals("3", text("\\it if := 1", "\\it and := 2", "\\it if + \\it and = ?"))
        assertEquals("2", text("f(x) :=\n  \\it break <- x\n  \\it break", "f(2) = ?"))
        assertEquals("\\it and is not defined", error("\\it and + 1 = ?"))
        // Printed back with the mark only where it is needed: `if` is also the built-in if(c, a, b).
        for (s in listOf("\\it and + x", "2 \\it try", "\\it try(1)", "if_1", "if(x > 0, 1, 2)", "2*if")) {
            assertEquals(s, Printer.print(Parser.parseExpression(s)))
        }
        assertEquals("x", Printer.print(Parser.parseExpression("\\it x")))
        assertEquals("if + x", Printer.print(Parser.parseExpression("\\it if + x")))
        assertEquals("p :=\n  \\it break", Printer.print(Parser.parseStatement("p :=\n  \\it break")))
    }
}
