package pub.beermat.export

import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Multiplication
import pub.beermat.engine.syntax.Continue
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.Given
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Otherwise
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.ProgramLine
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.Return
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.syntax.Symbolic
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.engine.syntax.While

/**
 * Writes statements as presentation MathML (docs/export.md). Pasted into Word, it becomes an
 * editable equation. Variables are italic, multi-letter ones too; units and built-in functions
 * are upright (`mathvariant="normal"`).
 */
class MathMl(private val units: Units = Units.BUILT_IN, private val multiplication: Multiplication = Multiplication.DOT) {

    /** A whole `<math>` element, in display style. */
    fun math(st: Statement, result: Result? = null): String =
        """<math xmlns="http://www.w3.org/1998/Math/MathML" display="block">${statement(st, result)}</math>"""

    fun statement(st: Statement, result: Result? = null): String {
        val r = result?.let { mo("=") + result(it) } ?: ""
        return row(
            when (st) {
                is Define -> expr(st.target) + mo(if (st.global) "≡" else ":=") + expr(st.value) + r
                is Evaluate -> expr(st.expr) + (r.ifEmpty { mo("=") + mo("■") })
                is ExprStatement -> expr(st.expr)
                Given -> "<mtext mathvariant=\"bold\">Given</mtext>"
                // A symbolic statement carries no units, so every name in it is a variable (`t` in `laplace(t)`).
                is Symbolic -> if (units !== Units.NONE) return MathMl(Units.NONE, multiplication).statement(st, result) else
                    expr(st.expr) + st.keywords.joinToString("") { mo("→") + "<mtext mathvariant=\"bold\">${xml(Printer.print(it))}</mtext>" } +
                    mo("→") + (result?.let { result(it) } ?: mo("■"))
                is Unparsed -> "<mtext>${xml(st.message)}</mtext>"
            },
        )
    }

    fun result(r: Result): String {
        val number = r.number?.let { expr(it) } ?: "<mtext>${xml(r.numberText)}</mtext>"
        val unit = r.unit ?: return number
        return number + "<mspace width=\"0.2em\"/>" + MathMl(Units.ALL).expr(unit)
    }

    fun expr(e: Expr): String = when (e) {
        is Num -> number(e.text)
        is Id -> identifier(e)
        is Str -> "<mtext>“${xml(e.value)}”</mtext>"
        Placeholder -> mo("■")
        is Parens -> fenced("(", ")", expr(e.inner))
        is Apply -> apply(e)
        is Call -> call(e)
        is Matrix -> fenced("[", "]", "<mtable>" + e.rows.joinToString("") { row -> "<mtr>" + row.joinToString("") { "<mtd>${expr(it)}</mtd>" } + "</mtr>" } + "</mtable>")
        is Index -> "<msub>${expr(e.target)}${row(e.indices.mapIndexed { k, x -> (if (k > 0) mo(",") else "") + expr(unwrap(x)) }.joinToString(""))}</msub>"
        is Range -> row(expr(e.first) + (e.second?.let { mo(",") + expr(it) } ?: "") + mo("..") + expr(e.last))
        is Program -> program(e.lines)
    }

    private fun number(t: String): String {
        val m = Regex("""([0-9.]+)[eE]([+-]?)([0-9]+)([ij]?)""").matchEntire(t) ?: return mn(t)
        val (mantissa, sign, exponent, imaginary) = m.destructured
        val power = "<msup>${mn("10")}${mn((if (sign == "-") "−" else "") + exponent.trimStart('0').ifEmpty { "0" })}</msup>"
        return row(mn(mantissa) + mo("×") + power + (if (imaginary.isEmpty()) "" else mi(imaginary)))
    }

    /** Plain variables italic; units and built-in functions ([function]) upright; styled names in their style. */
    private fun identifier(id: Id, function: Boolean = false): String {
        val upright = id.style == Id.Style.UPRIGHT || units.isUnit(id) || (function && id in Builtins.functions)
        val name = when {
            id.style == Id.Style.BOLD -> """<mi mathvariant="bold">${xml(id.name)}</mi>"""
            upright -> """<mi mathvariant="normal">${xml(id.name)}</mi>"""
            id.name.length > 1 -> """<mi mathvariant="italic">${xml(id.name)}</mi>"""
            else -> mi(id.name)
        }
        return id.subscript?.let { "<msub>$name<mi mathvariant=\"normal\">${xml(it)}</mi></msub>" } ?: name
    }

    private fun apply(e: Apply): String {
        val a = e.args
        fun x(k: Int) = expr(a[k])
        fun binary(op: String) = row(x(0) + mo(op) + x(1))
        return when (e.op) {
            Op.ADD -> binary("+")
            Op.SUB -> binary("−")
            Op.MUL -> if (e.implicit || (!e.dot && multiplication == Multiplication.SPACE)) row(x(0) + "<mspace width=\"0.17em\"/>" + x(1)) else binary("·")
            Op.DIV -> "<mfrac>${expr(unwrap(a[0]))}${expr(unwrap(a[1]))}</mfrac>"
            Op.POW -> "<msup>${x(0)}${expr(unwrap(a[1]))}</msup>"
            Op.NEG -> row(mo("−") + x(0))
            Op.POS -> row(mo("+") + x(0))
            Op.FACTORIAL -> row(x(0) + mo("!"))
            Op.MEMBER -> row(x(0) + mo(".") + x(1))
            Op.TRANSPOSE -> "<msup>${x(0)}${mi("T")}</msup>"
            Op.COLUMN -> "<msup>${x(0)}${row(mo("⟨") + expr(unwrap(a[1])) + mo("⟩"))}</msup>"
            Op.ABS -> fenced("|", "|", x(0))
            Op.EQ -> binary("=")
            Op.NE -> binary("≠")
            Op.LT -> binary("&lt;")
            Op.LE -> binary("≤")
            Op.GT -> binary("&gt;")
            Op.GE -> binary("≥")
            Op.AND -> binary("∧")
            Op.OR -> binary("∨")
            Op.XOR -> binary("⊕")
            Op.NOT -> row(mo("¬") + x(0))
            Op.ON_ERROR -> row(keyword("try") + x(0) + keyword("on error") + x(1))
        }
    }

    private fun call(e: Call): String {
        val name = (e.fn as? Id)?.takeIf { it.subscript == null && it.style == Id.Style.PLAIN }?.name
        val args = e.args
        fun big(symbol: String) = """<mo movablelimits="false">$symbol</mo>"""
        return when {
            name == "sqrt" && args.size == 1 -> "<msqrt>${expr(unwrap(args[0]))}</msqrt>"
            name == "nthroot" && args.size == 2 -> "<mroot>${expr(unwrap(args[0]))}${expr(unwrap(args[1]))}</mroot>"
            (name == "sum" || name == "prod") && args.size == 4 && args[1] is Id -> {
                val symbol = big(if (name == "sum") "∑" else "∏")
                row("<munderover>$symbol${row(expr(args[1]) + mo("=") + expr(unwrap(args[2])))}${expr(unwrap(args[3]))}</munderover>" + expr(args[0]))
            }
            (name == "sum" || name == "prod") && args.size == 2 && args[1] is Id ->
                row("<munder>${big(if (name == "sum") "∑" else "∏")}${expr(args[1])}</munder>" + expr(args[0]))
            (name == "sum" || name == "prod") && args.size == 1 -> row(big(if (name == "sum") "∑" else "∏") + expr(args[0]))
            name == "vec" && args.size == 1 -> "<mover>${expr(unwrap(args[0]))}${mo("→")}</mover>"
            name == "diff" && args.size == 2 -> row("<mfrac>${mi("d")}${row(mi("d") + expr(args[1]))}</mfrac>" + expr(args[0]))
            name == "int" && args.size == 4 ->
                row("<munderover>${big("∫")}${expr(unwrap(args[2]))}${expr(unwrap(args[3]))}</munderover>" + expr(args[0]) + mi("d") + expr(args[1]))
            else -> row(((e.fn as? Id)?.let { identifier(it, function = true) } ?: expr(e.fn)) + mo("&#x2061;") + fenced("(", ")", args.mapIndexed { k, x -> (if (k > 0) mo(",") else "") + expr(x) }.joinToString("")))
        }
    }

    private fun program(lines: List<ProgramLine>): String =
        row(mo("|") + "<mtable columnalign=\"left\">" + lines.joinToString("") { "<mtr><mtd>${programLine(it)}</mtd></mtr>" } + "</mtable>")

    private fun programLine(line: ProgramLine): String = when (line) {
        is Line -> expr(line.expr)
        is LocalDefine -> row(expr(line.target) + mo("←") + expr(line.value))
        is IfThen -> row(body(line.body) + keyword("if") + expr(line.condition))
        is Otherwise -> row(body(line.body) + keyword("otherwise"))
        is For -> row(keyword("for") + expr(line.variable) + mo("∈") + expr(line.over) + program(line.body))
        is While -> row(keyword("while") + expr(line.condition) + program(line.body))
        is Return -> row(keyword("return") + expr(line.value))
        Break -> keyword("break")
        Continue -> keyword("continue")
    }

    private fun body(lines: List<ProgramLine>) = if (lines.size == 1) programLine(lines[0]) else program(lines)

    private fun keyword(k: String) = """<mspace width="0.3em"/><mtext mathvariant="bold">$k</mtext><mspace width="0.3em"/>"""

    private fun fenced(open: String, close: String, inner: String) = row(mo(open) + inner + mo(close))

    private fun row(s: String) = "<mrow>$s</mrow>"
    private fun mi(s: String) = "<mi>${xml(s)}</mi>"
    private fun mn(s: String) = "<mn>${xml(s)}</mn>"
    private fun mo(s: String) = "<mo>$s</mo>"

    private fun unwrap(e: Expr): Expr = if (e is Parens) e.inner else e

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
