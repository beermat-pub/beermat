package pub.beermat.engine.syntax

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PrinterTest {

    private fun id(s: String) = Id(s)
    private fun n(s: String) = Num(s)
    private fun op(op: Op, vararg args: Expr) = Apply(op, *args)

    @Test
    fun `canonical text survives parse and print unchanged`() {
        val lines = listOf(
            "L := 6 m",
            "q := 12 kN/m",
            "I := 8356 cm^4",
            "g ::= 9.81 m/s^2",
            "M_max := q*L^2/8 = ? kN*m",
            "δ_max := 5*q*L^4/(384*E*I) = ? mm",
            "check(δ_max, L) = ?",
            "x = ? mm",
            "f(x, y) := sqrt(x^2 + y^2)",
            "a := 1/2 m",
            "b := -3 m + 2i - .5 - 1.2E+3",
            "c := ((a)) + |b - |c||",
            "d := a^-1*n!^2",
            "e := 0 < x <= 1 and not y or z xor w",
            "s := \"OK \\\"quoted\\\"\\n\"",
            "f(?) + -x",
            "α_1 := (-x)^2 - -y",
        )
        for (line in lines) assertEquals(line, Printer.print(Parser.parseStatement(line)), line)
    }

    @Test
    fun `spacing is canonical`() {
        assertEquals("x := 3 m*2 + y", Printer.print(Parser.parseStatement("x:=3m * 2+y")))
    }

    @Test
    fun `Greek escapes are written back as Unicode`() {
        assertEquals("Δ_max := α", Printer.print(Parser.parseStatement("\\Delta_max := \\alpha")))
    }

    @Test
    fun `missing parentheses are added`() {
        assertEquals("a + (b + c)", Printer.print(op(Op.ADD, id("a"), op(Op.ADD, id("b"), id("c")))))
        assertEquals("a*(b + c)", Printer.print(op(Op.MUL, id("a"), op(Op.ADD, id("b"), id("c")))))
        assertEquals("(-x)^2", Printer.print(op(Op.POW, op(Op.NEG, id("x")), n("2"))))
        assertEquals("(a^b)^c", Printer.print(op(Op.POW, op(Op.POW, id("a"), id("b")), id("c"))))
        assertEquals("a < (b < c)", Printer.print(op(Op.LT, id("a"), op(Op.LT, id("b"), id("c")))))
        assertEquals("not (a and b)", Printer.print(op(Op.NOT, op(Op.AND, id("a"), id("b")))))
        assertEquals("(a + b)!", Printer.print(op(Op.FACTORIAL, op(Op.ADD, id("a"), id("b")))))
        assertEquals("(-f)(x)", Printer.print(Call(op(Op.NEG, id("f")), id("x"))))
    }

    @Test
    fun `juxtaposition that would not read back is printed with a star`() {
        assertEquals("2 x", Printer.print(Apply(Op.MUL, n("2"), id("x"), implicit = true)))
        assertEquals("x y", Printer.print(Apply(Op.MUL, id("x"), id("y"), implicit = true)))
        assertEquals("x·y", Printer.print(Apply(Op.MUL, id("x"), id("y"), dot = true)))
        assertEquals("2*3", Printer.print(Apply(Op.MUL, n("2"), n("3"), implicit = true)))
        assertEquals("2*(x)", Printer.print(Apply(Op.MUL, n("2"), Parens(id("x")), implicit = true)))
    }

    /** The round-trip property `parse(print(t)) == t`, over random canonical trees. */
    @Test
    fun `round trip on random trees`() {
        val random = Random(20261027)
        repeat(5000) {
            val tree = Printer.canonical(RandomTrees(random).expr(depth = 5))
            val text = Printer.print(tree)
            assertEquals(tree, try { Parser.parseExpression(text) } catch (x: ParseException) { throw AssertionError("$text: ${x.message}") }, text)
        }
    }
}

private class RandomTrees(private val r: Random) {
    private val numbers = listOf("0", "3", "3.5", ".5", "1.2e-3", "1.2E+3", "2i", "0.5j", "10", "0x1F", "0b0.11")
    private val names = listOf("x", "m", "kN", "f'", "α", "i", "e", "in", "δ", "T", "if", "on", "otherwise", "∇H", "θ*", "2x", "a b", "x%", "a_b", "∞")
    private val subscripts = listOf(null, null, "max", "1", "b_c", "Ω.fo", "a*", ".x", "x.", "")
    private val strings = listOf("", "OK", "a \"b\"", "back\\slash", "line\nbreak")

    fun expr(depth: Int): Expr {
        if (depth == 0 || r.nextInt(4) == 0) return leaf()
        return when (r.nextInt(12)) {
            0 -> Parens(expr(depth - 1))
            10 -> {
                val cols = 1 + r.nextInt(2)
                Matrix(List(1 + r.nextInt(2)) { List(cols) { expr(depth - 1) } })
            }
            11 -> Index(if (r.nextInt(4) == 0) expr(depth - 1) else Id(names.random(r)), List(1 + r.nextInt(2)) { expr(depth - 1) })
            1 -> Call(if (r.nextInt(4) == 0) expr(depth - 1) else Id(names.random(r)), List(r.nextInt(3)) { expr(depth - 1) })
            else -> {
                val op = Op.entries.random(r)
                val style = if (op == Op.MUL) r.nextInt(3) else 0
                // A module's member is a plain name: `bt.f`.
                if (op == Op.MEMBER) return Apply(op, expr(depth - 1), Id((names - Id.INFINITY).random(r), subscripts.random(r)))
                Apply(op, List(op.arity) { expr(depth - 1) }, implicit = style == 1, dot = style == 2)
            }
        }
    }

    private fun leaf(): Expr = when (r.nextInt(5)) {
        0, 1 -> Num(numbers.random(r))
        2, 3 -> {
            // A styled name may be a keyword: `\bf and` is a variable.
            val style = listOf(Id.Style.PLAIN, Id.Style.PLAIN, Id.Style.UPRIGHT, Id.Style.BOLD).random(r)
            Id((if (style == Id.Style.PLAIN) names else names + listOf("and", "try")).random(r), subscripts.random(r), style)
        }
        4 -> if (r.nextBoolean()) Str(strings.random(r)) else Placeholder
        else -> error("unreachable")
    }
}
