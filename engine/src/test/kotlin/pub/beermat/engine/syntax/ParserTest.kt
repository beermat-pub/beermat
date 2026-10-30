package pub.beermat.engine.syntax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParserTest {

    private fun expr(s: String) = Parser.parseExpression(s)
    private fun stmt(s: String) = Parser.parseStatement(s)
    private fun n(s: String) = Num(s)
    private fun id(s: String) = Id(s)
    private fun op(op: Op, vararg args: Expr) = Apply(op, *args)
    private fun imp(a: Expr, b: Expr) = Apply(Op.MUL, a, b, implicit = true)

    private fun fails(s: String): ParseException = assertFailsWith<ParseException> { stmt(s) }

    @Test
    fun precedence() {
        assertEquals(op(Op.ADD, n("1"), op(Op.MUL, n("2"), n("3"))), expr("1 + 2*3"))
        assertEquals(op(Op.SUB, op(Op.SUB, id("a"), id("b")), id("c")), expr("a - b - c"))
        assertEquals(op(Op.NEG, op(Op.POW, id("x"), n("2"))), expr("-x^2"))
        assertEquals(op(Op.POW, id("a"), op(Op.POW, id("b"), id("c"))), expr("a^b^c"))
        assertEquals(op(Op.POW, id("a"), op(Op.NEG, n("1"))), expr("a^-1"))
        assertEquals(op(Op.POW, n("2"), op(Op.FACTORIAL, n("3"))), expr("2^3!"))
        assertEquals(op(Op.MUL, id("a"), op(Op.NEG, id("b"))), expr("a*-b"))
    }

    @Test
    fun `logic and comparisons`() {
        assertEquals(op(Op.LT, op(Op.LT, n("0"), id("x")), n("1")), expr("0 < x < 1"))
        assertEquals(op(Op.LT, Parens(op(Op.LT, n("0"), id("x"))), n("1")), expr("(0 < x) < 1"))
        assertEquals(op(Op.AND, op(Op.NOT, id("a")), id("b")), expr("not a and b"))
        assertEquals(op(Op.OR, id("a"), op(Op.AND, id("b"), id("c"))), expr("a or b and c"))
        assertEquals(op(Op.NOT, op(Op.EQ, id("a"), id("b"))), expr("not a == b"))
        assertEquals(op(Op.GE, id("a"), id("b")), expr("a >= b"))
        assertEquals(op(Op.NE, id("a"), id("b")), expr("a != b"))
    }

    @Test
    fun `implicit multiplication of a number by a name`() {
        assertEquals(imp(n("3"), id("m")), expr("3 m"))
        assertEquals(imp(n("3"), id("m")), expr("3m"))
        assertEquals(op(Op.DIV, imp(n("12"), id("kN")), id("m")), expr("12 kN/m"))
        assertEquals(imp(op(Op.DIV, n("1"), n("2")), id("m")), expr("1/2 m"))
        assertEquals(imp(n("8356"), op(Op.POW, id("cm"), n("4"))), expr("8356 cm^4"))
        assertEquals(imp(op(Op.NEG, n("3")), id("m")), expr("-3 m"))
        assertEquals(imp(n("2"), Call(id("f"), id("x"))), expr("2 f(x)"))
        assertEquals(op(Op.DIV, imp(n("9.81"), id("m")), op(Op.POW, id("s"), n("2"))), expr("9.81 m/s^2"))
    }

    @Test
    fun `a name after any operand multiplies, drawn with a space`() {
        assertEquals(imp(id("q"), id("L")), expr("q L"))
        assertEquals(imp(Parens(op(Op.ADD, n("1"), n("2"))), id("m")), expr("(1 + 2) m"))
        assertEquals(imp(imp(n("3"), id("m")), id("kg")), expr("3 m kg"))
        assertEquals(imp(op(Op.POW, id("x"), n("2")), id("m")), expr("x^2 m"))
        assertEquals(imp(id("q"), op(Op.POW, id("L"), n("2"))), expr("q L^2"))
        // Only a name: a number after an operand still needs an operator.
        fails("2 3")
        fails("q 3")
    }

    @Test
    fun `a dot is a multiplication always drawn with a dot`() {
        assertEquals(Apply(Op.MUL, id("q"), id("L"), dot = true), expr("q·L"))
        assertEquals(Apply(Op.MUL, id("q"), id("L"), dot = true), expr("q⋅L"))
        assertEquals("q·L + q*L + q L", Printer.print(stmt("q·L + q*L + q L")))
    }

    @Test
    fun `number literals`() {
        for (s in listOf("3", "3.5", ".5", "1.2e-3", "1.2E+3", "2i", "0.5j", "1e3i", "0x1F", "0o17", "0b101.01")) assertEquals(n(s), expr(s))
        // A prefix without a digit of its base after it is 0 times a name, as before: `0b` is 0·b.
        assertEquals(Apply(Op.MUL, listOf(n("0"), id("b")), implicit = true), expr("0b"))
        assertEquals(imp(n("2"), id("in")), expr("2in"))
        assertEquals(imp(n("2"), id("i")), expr("2 i"))
        assertEquals(imp(n("2"), id("e")), expr("2e"))
        assertEquals(imp(n("2e3"), id("x")), expr("2e3x"))
    }

    @Test
    fun identifiers() {
        assertEquals(Id("M", "max"), expr("M_max"))
        assertEquals(Id("a", "b_c"), expr("a_b_c"))
        assertEquals(Id("α", "1"), expr("\\alpha_1"))
        assertEquals(Id("Δx"), expr("\\Deltax"))
        assertEquals(Id("αβ"), expr("\\alpha\\beta"))
        assertEquals(Id("δ", "max"), expr("δ_max"))
        assertEquals(Id("f'"), expr("f'"))
        assertEquals(Id("and", "1"), expr("and_1"))
        // An empty subscript: a different name from `x`, drawn the same (a familiar trick).
        assertEquals(Id("x", ""), expr("x_"))
        assertEquals(op(Op.ADD, Id("x", ""), id("x")), expr("x_ + x"))
        fails("\\foo")
    }

    @Test
    fun `names with any character - nabla, dots in subscripts, backticks`() {
        assertEquals(Id("∇H"), expr("∇H"))
        assertEquals(Id("∂u", "x"), expr("∂u_x"))
        assertEquals(Id("int", "Ω.fo"), expr("int_Ω.fo"))
        // A dot ends the subscript unless a name character follows it.
        assertEquals(op(Op.MUL, Id("a", "b"), n(".5")), expr("a_b*.5"))
        assertEquals(Id("θ*"), expr("`θ*`"))
        assertEquals(Id("θ*", "max"), expr("`θ*`_max"))
        // `∞` is a name of its own, the built-in infinity.
        assertEquals(Apply(Op.DIV, Num("1"), Id("∞")), expr("1/∞"))
        assertEquals("∞", Id("∞").text)
        assertEquals(Id("M", "a+b"), expr("M_`a+b`"))
        assertEquals(Id("if", style = Id.Style.BOLD), expr("\\bf `if`"))
        // Backticks make a name, never a keyword.
        assertEquals(op(Op.ADD, Id("and"), n("1")), expr("`and` + 1"))
        assertEquals(imp(n("2"), Id("x%")), expr("2 `x%`"))
        fails("`open")
        fails("``")
    }

    @Test
    fun `names are written with backticks only when they need them`() {
        assertEquals("∇H", Id("∇H").text)
        assertEquals("int_Ω.fo", Id("int", "Ω.fo").text)
        assertEquals("`θ*`_max", Id("θ*", "max").text)
        assertEquals("M_`x.`", Id("M", "x.").text)
        assertEquals("`a_b`", Id("a_b").text)
        assertEquals("x_", Id("x", "").text)
        assertEquals("θ*_max", Id("θ*", "max").label)
    }

    @Test
    fun `strings, placeholders, abs and calls`() {
        assertEquals(Str("say \"hi\"\n\\"), expr("\"say \\\"hi\\\"\\n\\\\\""))
        assertEquals(Call(id("f"), Placeholder), expr("f(?)"))
        assertEquals(Call(id("g")), expr("g()"))
        assertEquals(Call(id("g"), id("x"), op(Op.ADD, id("y"), n("1"))), expr("g(x, y + 1)"))
        assertEquals(op(Op.ADD, op(Op.ABS, id("a")), op(Op.ABS, id("b"))), expr("|a| + |b|"))
        assertEquals(op(Op.ABS, op(Op.SUB, op(Op.ABS, id("a")), id("b"))), expr("||a| - b|"))
        fails("\"open")
        fails("f(x")
        fails("|x")
    }

    @Test
    fun statements() {
        assertEquals(Define(id("x"), imp(n("3"), id("m"))), stmt("x := 3 m"))
        assertEquals(
            Define(id("g"), op(Op.DIV, imp(n("9.81"), id("m")), op(Op.POW, id("s"), n("2"))), global = true),
            stmt("g ::= 9.81 m/s^2"),
        )
        assertEquals(Evaluate(id("x")), stmt("x = ?"))
        assertEquals(Evaluate(id("x"), ResultSlot(id("mm"))), stmt("x = ? mm"))
        assertEquals(
            Define(
                Id("M", "max"),
                op(Op.DIV, op(Op.MUL, id("q"), op(Op.POW, id("L"), n("2"))), n("8")),
                result = ResultSlot(op(Op.MUL, id("kN"), id("m"))),
            ),
            stmt("M_max := q*L^2/8 = ? kN*m"),
        )
        assertEquals(Define(Call(id("f"), id("x"), id("y")), id("x")), stmt("f(x, y) := x"))
        assertEquals(ExprStatement(op(Op.ADD, id("x"), id("y"))), stmt("x + y"))
    }

    @Test
    fun `statement errors`() {
        fails("x + 1 := 2")
        fails("f(2) := 2")
        assertEquals("expected '?' after '=' (write 'x = ?' to evaluate, or '==' to compare) (column 5)", fails("x = 5").message)
        fails("x := ")
        fails("x := 1 := 2")
        fails("x # comment")
    }

    @Test
    fun `errors point at the column`() {
        assertEquals(6, fails("a + b 3").column)
        assertEquals(4, fails("1 + ").column)
    }

    @Test
    fun arrays() {
        assertEquals(Matrix(listOf(listOf(n("1"), n("2")), listOf(n("3"), n("4")))), expr("[1, 2; 3, 4]"))
        assertEquals(Matrix(listOf(listOf(n("1")), listOf(n("2")))), expr("[1; 2]"))
        assertEquals(Index(id("v"), id("i")), expr("v[i]"))
        assertEquals(Index(id("M"), n("1"), op(Op.ADD, id("j"), n("1"))), expr("M[1, j + 1]"))
        assertEquals(Define(Index(id("v"), n("2")), n("5")), stmt("v[2] := 5"))
        assertEquals(imp(n("2"), Index(id("v"), n("1"))), expr("2 v[1]"))
        assertEquals("every row of a matrix needs the same number of columns (column 1)", fails("[1, 2; 3]").message)
        assertEquals("an index takes one or two subscripts: v[i] or M[i, j] (column 2)", fails("M[1, 2, 3]").message)
    }

    @Test
    fun `transpose and columns are superscripts`() {
        assertEquals(op(Op.TRANSPOSE, id("M")), expr("M^T"))
        assertEquals(op(Op.POW, op(Op.TRANSPOSE, id("M")), op(Op.NEG, n("1"))), expr("M^T^-1"))
        assertEquals(op(Op.POW, id("x"), Parens(id("T"))), expr("x^(T)"))
        assertEquals(op(Op.POW, id("x"), Id("T", "1")), expr("x^T_1"))
        assertEquals(op(Op.COLUMN, id("M"), op(Op.ADD, id("j"), n("1"))), expr("M^<j + 1>"))
        assertEquals(op(Op.LT, id("M"), id("j")), expr("M < j"))
        // A power of a variable named T keeps its parentheses when printed.
        assertEquals("x^(T)", Printer.print(op(Op.POW, id("x"), id("T"))))
        assertEquals("x^(T!)", Printer.print(op(Op.POW, id("x"), op(Op.FACTORIAL, id("T")))))
        assertEquals("M^<(a < b)>", Printer.print(op(Op.COLUMN, id("M"), op(Op.LT, id("a"), id("b")))))
    }
}
