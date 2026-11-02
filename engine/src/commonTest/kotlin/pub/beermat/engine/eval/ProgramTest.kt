package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Otherwise
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Program blocks: parsing, printing and running. */
class ProgramTest {

    private fun run(vararg statements: String): List<Outcome> = Evaluator().run(statements.map { Parser.parseStatement(it.trimIndent()) })

    private fun text(vararg statements: String): String = when (val o = run(*statements).last()) {
        is Outcome.Evaluated -> ResultText.format(o.shown)
        is Outcome.Defined -> ResultText.format(o.shown ?: fail("nothing shown"))
        is Outcome.Failed -> fail("failed: ${o.error.message}")
        Outcome.NotEvaluated -> fail("not evaluated")
        is Outcome.Symbolic -> fail("symbolic")
    }

    private fun error(vararg statements: String): String =
        assertIs<Outcome.Failed>(run(*statements).last(), "expected an error").error.message!!

    private fun parseError(s: String): String = assertFailsWith<ParseException> { Parser.parseStatement(s.trimIndent()) }.message!!

    private val abs = """
        f(x) :=
          -x if x < 0
          x otherwise
        """

    private val fib = """
        fib(n) :=
          a <- 0
          b <- 1
          for k in 1..n
            t <- a + b
            a <- b
            b <- t
          a
        """

    @Test
    fun `parses into the tree`() {
        val s = Parser.parseStatement(fib.trimIndent()) as Define
        val p = s.value as Program
        assertEquals(LocalDefine(Id("a"), Num("0")), p.lines[0])
        val loop = p.lines[2] as For
        assertEquals(Id("k"), loop.variable)
        assertEquals(Range(Num("1"), null, Id("n")), loop.over)
        assertEquals(3, loop.body.size)
        assertEquals(Line(Id("a")), p.lines[3])

        val f = (Parser.parseStatement(abs.trimIndent()) as Define).value as Program
        assertIs<IfThen>(f.lines[0])
        assertEquals(Otherwise(listOf(Line(Id("x")))), f.lines[1])
    }

    @Test
    fun `prints back as written`() {
        val samples = listOf(
            abs,
            fib,
            """
            g(x) :=
              if x > 10
                y <- x/2
                return y
              otherwise
                s <- 0
                for k ∈ [1; 2; 3]
                  continue if k == 2
                  s <- s + k
                while s < 100
                  s <- 2*s
                  break if s > 50
                try s on error 0
            """,
            """
            v :=
              w[2] <- 5
              (break)
            """,
        )
        for (sample in samples) {
            val text = sample.trimIndent().replace("∈", "in")
            val parsed = Parser.parseStatement(sample.trimIndent())
            assertEquals(text, Printer.print(parsed))
            assertEquals(parsed, Parser.parseStatement(Printer.print(parsed)))
        }
    }

    @Test
    fun `syntax errors`() {
        assertEquals("missing value after ':=' (a program's lines go below, indented) (column 8)", parseError("f(x) :="))
        assertEquals("a statement with indented lines below it must end with ':=', or hold the word 'program' where they go (column 10)", parseError("f(x) := 1\n  2"))
        assertEquals("program lines must be indented below the definition (program line 1, column 1)", parseError("f(x) :=\nx"))
        assertEquals("the indentation doesn't line up with the lines above (program line 2, column 4)", parseError("f(x) :=\n    a <- 1\n   a"))
        assertEquals("a for loop needs lines indented below it (program line 1, column 16)", parseError("f(x) :=\n  for k in 1..3"))
        assertEquals("only if, otherwise, for and while lines, or a line holding the word 'program', can have lines indented below them (program line 2, column 5)", parseError("f(x) :=\n  a <- 1\n    b <- 2"))
        assertEquals("expected 'in' after the loop variable: for k in 1..n (program line 1, column 9)", parseError("f(x) :=\n  for k = 1..3\n    k"))
        assertEquals("'<-' defines a local variable inside a program; elsewhere use ':=' (column 3)", parseError("a <- 1"))
        assertEquals("a program can't contain a blank line (program line 2, column 1)", parseError("f(x) :=\n  a <- 1\n\n  a"))
    }

    @Test
    fun conditionals() {
        assertEquals("3", text(abs, "f(-3) = ?"))
        assertEquals("4", text(abs, "f(4) = ?"))
        // A block if: the program continues after it.
        val clamp = """
            c(x) :=
              y <- x
              if x > 1
                y <- 1
              y*10
            """
        assertEquals("10", text(clamp, "c(5) = ?"))
        assertEquals("5", text(clamp, "c(0.5) = ?"))
        // otherwise runs only when no if in the run just above it ran.
        val sign = """
            sgn(x) :=
              -1 if x < 0
              1 if x > 0
              0 otherwise
            """
        assertEquals("[-1; 0; 1]", text(sign, "[sgn(-2); sgn(0); sgn(7)] = ?"))
    }

    @Test
    fun loops() {
        assertEquals("55", text(fib, "fib(10) = ?"))
        // 1..0 counts down (1, then 0), so the loop runs twice: a known trap.
        assertEquals("1", text(fib, "fib(0) = ?"))
        val firstOver = """
            first(v, limit) :=
              for x in v
                return x if x > limit
              -1
            """
        assertEquals("7", text(firstOver, "first([1; 7; 9], 5) = ?"))
        assertEquals("-1", text(firstOver, "first([1; 2], 5) = ?"))
        val collatz = """
            steps(n) :=
              k <- 0
              while n != 1
                n <- n/2 if mod(n, 2) == 0
                n <- 3*n + 1 otherwise
                k <- k + 1
              k
            """
        assertEquals("111", text(collatz, "steps(27) = ?"))
        val evens = """
            evens(n) :=
              s <- 0
              for k in 0..n
                continue if mod(k, 2) == 1
                break if k > 6
                s <- s + k
              s
            """
        assertEquals("12", text(evens, "evens(100) = ?"))
        assertEquals("[0; 0.5; 1; 1.5]*m", text("steps :=\n  for k in 0 m, 0.5 m..1.5 m\n    v[k/(0.5 m)] <- k\n  v", "steps = ?"))
    }

    @Test
    fun `local elements and outer names`() {
        val build = """
            build(n) :=
              for k in 0..n - 1
                v[k] <- k^2
              v
            """
        assertEquals("[0; 1; 4; 9]", text(build, "build(4) = ?"))
        // Setting an element gives the whole array, so a program can end with it.
        assertEquals("[0; 0; 5]", text("w :=\n  v[2] <- 5", "w = ?"))
        // An element of a name the program hasn't assigned starts a new array, not a copy of the
        // sheet's (the results saved in imported sheets show it: a table rebuilt from new data in the same name).
        assertEquals("[9]", text("C := [1; 2; 3; 4]", "C :=\n  C[0] <- 9\n  C", "C = ?"))
        // But a program that reads the sheet's array before it first assigns it changes a copy of it.
        assertEquals("[1000; 2000; 3]", text("S := [1; 2; 3]", "S :=\n  for k in 0..1\n    S[k] <- S[k]*1000\n  S", "S = ?"))
        // A bare 0 put into an array of lengths stays a length, where an array's elements shared one unit.
        assertEquals("[0; 0; 3]*m", text("v :=\n  v[2] <- 3 m\n  v[0] <- 0\n  v", "v = ?"))
        assertEquals("1 N", text("v :=\n  v[2] <- 3 m\n  v[0] <- 0\n  v", "2 N/m*v[0] + 1 N = ?"))
        // A local function, seeing the program's names as they stand at its line.
        val local = "t :=\n  k <- 10\n  f(i, j) <- i*k\n  matrix(3, 1, f)"
        assertEquals(local, Printer.print(Parser.parseStatement(local)))
        assertEquals("[0; 10; 20]", text(local, "t = ?"))
        // A function's parameters are the program's own.
        assertEquals("[1; 2; 5]", text("f(v) :=\n  v[2] <- 5\n  v", "f([1; 2; 3]) = ?"))
        // Locals don't leak, and outer names are read but not changed.
        assertEquals("2", text("a := 2", "p :=\n  a <- 5\n  a", "a = ?"))
        assertEquals("15", text("a := 2", "q(x) :=\n  b <- a + x\n  b*3", "q(3) = ?"))
    }

    @Test
    fun `program errors`() {
        assertEquals("the program ended without a value", error("p(x) :=\n  1 if x > 0", "p(-1) = ?"))
        assertEquals("break is only allowed inside a loop", error("p :=\n  break"))
        assertEquals("otherwise must come straight after an if line", error("p :=\n  a <- 1\n  2 otherwise"))
        assertEquals("the while loop ran more than 10000000 times (does its condition ever become false?)", error("p :=\n  while 1\n    a <- 1"))
        assertEquals("x is not defined", error("p :=\n  y <- 1\n  x + y"))
    }

    @Test
    fun `on error`() {
        assertEquals("0", text("try 1/0 on error 0 = ?"))
        assertEquals("2", text("try 4/2 on error 0 = ?"))
        assertEquals("\"bad\"", text("try 1 m + 1 s on error \"bad\" = ?"))
        assertEquals("[1; \"bad\"]", text("r(x) := try sqrt(x) on error \"bad\"", "[r(1); r(\"a\")] = ?"))
        assertEquals("Apply(op=ON_ERROR, args=[Id(x), Id(y)], implicit=false, dot=false, division=STACKED)", Parser.parseExpression("try x on error y").toString())
        assertEquals("[Id(on)]", listOf(Parser.parseExpression("on")).toString())
        assertEquals("-1", text("try try 1/0 on error 1/0 on error -1 = ?"))
        assertEquals("3", text("1 + (try 1/0 on error 2) = ?"))
        for (s in listOf("try a on error try b on error c", "1 + (try x on error 0)", "try (try a on error b) + 1 on error c")) {
            assertEquals(s, Printer.print(Parser.parseExpression(s)))
        }
        val e = assertFailsWith<ParseException> { Parser.parseExpression("1/x on error 0") }
        assertEquals("'on error' needs 'try' before the expression it guards: try a on error b", e.message?.substringBefore(" (column"))
    }

    @Test
    fun `contextual in`() {
        // `in` is the loop keyword after `for k`, and the inch everywhere else.
        assertEquals("25.4 mm", text("p :=\n  for k in [1]\n    d <- k*1 in\n  d", "p = ? mm"))
        assertIs<Break>((((Parser.parseStatement("p :=\n  for in in 1..2\n    break") as Define).value as Program).lines[0] as For).body[0])
    }

    @Test
    fun `a program inside an expression, where the word program stands`() {
        val s = "M := program*1 kN*m = ? kN*m\n  a <- 6\n  a^2/8"
        assertEquals(s, Printer.print(Parser.parseStatement(s)))
        assertEquals("4.5 kN*m", text(s))
        // Evaluated directly, and as a function's argument.
        assertEquals("3", text("program = ?\n  x <- 1\n  x + 2"))
        assertEquals("5", text("max(program, 5) = ?\n  2"))
        // Without the word, the lines below need ':=' at the end.
        assertTrue("hold the word 'program'" in parseError("x + 1 = ?\n  2"))
    }

    @Test
    fun `a program inside a program line takes the lines below it`() {
        val s = "f(x) :=\n  a <- program*2\n    b <- x + 1\n    b^2\n  a + 1"
        assertEquals(s, Printer.print(Parser.parseStatement(s)))
        assertEquals("33", text(s, "f(3) = ?"))
        // In an inline if too.
        val t = "g(x) :=\n  return program if x > 0\n    x*10\n  -1"
        assertEquals(t, Printer.print(Parser.parseStatement(t)))
        assertEquals("20", text(t, "g(2) = ?"))
        assertEquals("-1", text(t, "g(-2) = ?"))
    }
}
