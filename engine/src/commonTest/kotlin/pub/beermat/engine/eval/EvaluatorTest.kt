package pub.beermat.engine.eval

import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.units.BaseDimension.LENGTH
import pub.beermat.engine.units.BaseDimension.MASS
import pub.beermat.engine.units.BaseDimension.TIME
import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class EvaluatorTest {

    private fun run(vararg lines: String): List<Outcome> = Evaluator().run(lines.map(Parser::parseStatement))

    /** The value shown by the last statement, which must have evaluated. */
    private fun last(vararg lines: String): Shown = when (val o = run(*lines).last()) {
        is Outcome.Evaluated -> o.shown
        is Outcome.Defined -> o.shown ?: fail("nothing shown")
        is Outcome.Failed -> fail("failed: ${o.error.message}")
        Outcome.NotEvaluated -> fail("not evaluated")
        is Outcome.Symbolic -> fail("symbolic")
    }

    private fun value(vararg lines: String): Quantity = (last(*lines).value as Scalar).q

    private fun real(vararg lines: String): Double = value(*lines).value.also { assertTrue(it.isReal, "$it") }.re

    private fun error(vararg lines: String): EvalException =
        assertIs<Outcome.Failed>(run(*lines).last(), "expected an error").error

    private fun assertComplex(re: Double, im: Double, actual: Quantity) {
        assertEquals(re, actual.value.re, 1e-12)
        assertEquals(im, actual.value.im, 1e-12)
    }

    @Test
    fun arithmetic() {
        assertEquals(7.0, real("1 + 2*3 = ?"))
        assertEquals(3.0, real("10 - 4 - 3 = ?"))
        assertEquals(-4.0, real("-2^2 = ?"))
        assertEquals(1024.0, real("2^10 = ?"))
        assertEquals(3.5, real("7/2 = ?"))
        assertEquals(0.5, real("2^-1 = ?"))
        assertEquals(120.0, real("5! = ?"))
        assertEquals(4.0, real("|-4| = ?"))
        assertEquals(1200.0, real("1.2E+3 = ?"))
    }

    @Test
    fun `units multiply through`() {
        val m = value("L := 6 m", "q := 12 kg/s^2", "M := q*L^2/8 = ?")
        assertEquals(54.0, m.value.re)
        assertEquals(Dimension.of(MASS to 1, LENGTH to 2, TIME to -2), m.dimension)
    }

    @Test
    fun `the unit placeholder is checked against the result`() {
        val shown = last("L := 6 m", "L = ? m")
        assertEquals(Quantity(1.0, Dimension.LENGTH), shown.unit)
        val e = error("L := 6 m", "L = ? s")
        assertEquals("units do not match: the result is L, the unit is T", e.message)
        assertEquals(Id("s"), e.node)
    }

    @Test
    fun `a shadowed unit still means the unit in a placeholder`() {
        val shown = last("L := 6 m", "m := 5", "L = ? m")
        assertEquals(Quantity(1.0, Dimension.LENGTH), shown.unit)
        // Outside the placeholder, the user's m wins.
        assertEquals(10.0, real("m := 5", "x := m*2 = ?"))
    }

    @Test
    fun `dimension errors`() {
        assertEquals("units do not match: L vs T", error("x := 3 m + 2 s").message)
        assertTrue(error("sin(30 m) = ?").message!!.contains("dimensionless"))
        assertTrue(error("3 m > 2 = ?").message!!.contains("units do not match"))
        assertEquals(1.0, real("3 m > 0 = ?"))
    }

    @Test
    fun `definitions apply from where they are made`() {
        assertEquals("y is not defined", assertIs<Outcome.Failed>(run("y = ?", "y := 1").first()).error.message)
        assertEquals(2.0, real("x := 1", "x := 2", "x = ?"))
    }

    @Test
    fun `a definition whose name is still empty defines nothing`() {
        assertEquals("this definition needs a name", error("? := 2").message)
        assertEquals(3.0, real("? := 2", "x := 3", "x = ?"))
        assertEquals("? := 2", Printer.print(Parser.parseStatement("? := 2")))
    }

    @Test
    fun `a failed definition is reported where it is used`() {
        val e = error("x := 3 m + 2 s", "y := x*2", "y = ?")
        assertEquals("y has an error: x has an error: units do not match: L vs T", e.message)
    }

    @Test
    fun `errors point at the offending node`() {
        assertEquals(Id("y"), error("x := 1 + y").node)
        assertEquals("division by zero", error("1/0 = ?").message)
        assertEquals("this expression is incomplete", error("x := 1 + ?").message)
    }

    @Test
    fun `global definitions apply to the whole sheet`() {
        assertEquals(5.0, real("a ::= 5", "a = ?"))
        val outcomes = run("b = ?", "b ::= 7")
        assertEquals(7.0, ((outcomes[0] as Outcome.Evaluated).shown.value as Scalar).q.value.re)
        // A global sees only the globals above it.
        assertIs<Outcome.Failed>(run("c ::= d", "d ::= 1").first())
    }

    @Test
    fun `a global can't be redefined`() {
        // Not by `:=`, above it or below it; the global keeps its value everywhere.
        val below = run("a ::= 5", "a := 6", "a = ?")
        assertEquals("a is defined globally (≡); it can't be redefined", (below[1] as Outcome.Failed).error.message)
        assertEquals(5.0, ((below[2] as Outcome.Evaluated).shown.value as Scalar).q.value.re)
        assertIs<Outcome.Failed>(run("a := 6", "a ::= 5")[0])
        // Nor as a function, an element or one of several names.
        assertIs<Outcome.Failed>(run("f(x) ::= x", "f(x) := 2*x")[1])
        assertIs<Outcome.Failed>(run("v ::= [1; 2]", "v[0] := 3")[1])
        assertIs<Outcome.Failed>(run("b ::= 1", "[a; b] := [1; 2]")[1])
        // A second global of the same name is an error; the first one stands.
        val twice = run("a ::= 5", "a ::= 6", "a = ?")
        assertIs<Outcome.Failed>(twice[1])
        assertEquals(5.0, ((twice[2] as Outcome.Evaluated).shown.value as Scalar).q.value.re)
        // A program's local and a function's parameter may still use the name.
        assertEquals(3.0, real("x ::= 1", "f(x) := x + 1", "f(2) = ?"))
        assertEquals(7.0, real("x ::= 1", "y := program\n  x <- 7\n  x", "y = ?"))
    }

    @Test
    fun `user functions`() {
        assertEquals(10.0, real("f(x) := x^2 + 1", "f(3) = ?"))
        val g = value("g(x, y) := sqrt(x^2 + y^2)", "g(3 m, 4 m) = ?")
        assertEquals(Quantity(5.0, Dimension.LENGTH), g)
        assertEquals(5.0, real("f(x) := x^2 + 1", "h := f", "h(2) = ?"))
        assertEquals("f takes 1 argument(s), not 2", error("f(x) := x", "f(1, 2) = ?").message)
        assertEquals("only a function can be called", error("a := 1", "a(2) = ?").message)
        assertEquals("f: a parameter name is repeated", error("f(x, x) := x").message)
        assertIs<Outcome.Failed>(run("f(x) := x = ?").last())
    }

    @Test
    fun `functions use the definitions in force where they were defined`() {
        assertEquals(2.0, real("a := 2", "f(x) := a*x", "a := 3", "f(1) = ?"))
    }

    @Test
    fun `recursion through if`() {
        assertEquals(120.0, real("fact(n) := if(n < 1, 1, n*fact(n - 1))", "fact(5) = ?"))
        assertTrue(error("r(n) := r(n)", "r(1) = ?").message!!.contains("too many nested calls"))
    }

    @Test
    fun `complex numbers`() {
        assertComplex(0.0, 2.0, value("sqrt(-4) = ?"))
        assertComplex(5.0, 5.0, value("(1 + 2i)*(3 - 1i) = ?"))
        assertComplex(1.0, sqrt(3.0), value("(-8)^(1/3) = ?"))
        assertComplex(0.0, PI, value("ln(-1) = ?"))
        assertEquals(5.0, real("|3 + 4i| = ?"))
        assertEquals(Complex(0.0, 1.0), value("1j = ?").value)
        assertEquals(1.0, real("2i == 2j = ?"))
        assertTrue(error("1i < 2 = ?").message!!.contains("ordered"))
    }

    @Test
    fun `comparisons and logic`() {
        assertEquals(1.0, real("0 < 0.5 < 1 = ?"))
        assertEquals(0.0, real("0 < 2 < 1 = ?"))
        assertEquals(1.0, real("(0 < 2) == 1 = ?"))
        assertEquals(1.0, real("\"a\" == \"a\" = ?"))
        assertEquals(0.0, real("1 and 0 = ?"))
        assertEquals(1.0, real("not 0 = ?"))
        assertEquals(1.0, real("1 xor 0 = ?"))
        assertEquals(0.0, real("0 and 1/0 = ?"))
    }

    @Test
    fun `hexadecimal, octal and binary literals`() {
        assertEquals(31.0, real("0x1F = ?"))
        assertEquals(15.0, real("0o17 = ?"))
        assertEquals(5.25, real("0b101.01 = ?"))
        assertEquals(109.40625, real("0b1101101.01101 = ?"))
    }

    @Test
    fun `comparisons have a tolerance`() {
        assertTrue(0.1 + 0.2 != 0.3) // not exactly equal in floating point …
        assertEquals(1.0, real("0.1 + 0.2 == 0.3 = ?")) // … but equal within tolerance
        assertEquals(1.0, real("0.1 + 0.2 <= 0.3 = ?"))
        assertEquals(0.0, real("0.1 + 0.2 > 0.3 = ?"))
        // TOL defaults to 10^-3, relative.
        assertEquals(1.0, real("1000 == 1000.5 = ?"))
        assertEquals(0.0, real("1 == 1.01 = ?"))
        assertEquals(0.0, real("1 < 1.0005 = ?"))
        // TOL is a variable the sheet can redefine.
        assertEquals(1e-3, real("TOL = ?"))
        assertEquals(0.0, real("TOL := 0", "0.1 + 0.2 == 0.3 = ?"))
        assertEquals("TOL must be a non-negative number", error("TOL := 1 m", "1 == 1 = ?").message)
        // An infinity equals only itself: a relative tolerance would make it equal to everything.
        assertEquals(1.0, real("5 != -∞ = ?"))
        assertEquals(0.0, real("x := -∞", "x != -∞ = ?"))
        assertEquals(1.0, real("-∞ < 5 = ?"))
    }

    @Test
    fun `built-in functions and constants`() {
        assertEquals(0.5, real("sin(π/6) = ?"), 1e-15)
        assertEquals(2.0, real("log(100) = ?"))
        assertEquals(3.0, real("log(8, 2) = ?"), 1e-15)
        assertEquals(2.0, real("nthroot(8, 3) = ?"), 1e-15)
        assertEquals(-2.0, real("nthroot(-8, 3) = ?"), 1e-15)
        assertEquals(Quantity(3.0, Dimension.LENGTH), value("max(1 m, 3 m, 2 m) = ?"))
        assertEquals(1.0, real("mod(7, 3) = ?"))
        assertEquals(3.14, real("round(π, 2) = ?"))
        assertEquals("factorial needs a non-negative integer", error("2.5! = ?").message)
        assertEquals("sqrt takes 1 argument(s), not 2", error("sqrt(1, 2) = ?").message)
    }

    @Test
    fun `strings are values`() {
        assertEquals(Text("OK"), last("s := \"OK\" = ?").value)
        assertTrue(error("s := \"OK\"", "s = ? m").message!!.contains("not a string"))
    }
}
