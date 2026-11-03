package pub.beermat.export

import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Continue
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Division
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.Given
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Multiplication
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
 * Writes statements as Office Math (OMML), the equations of a Word document (docs/export.md#word),
 * built from the expression tree as [MathMl] is, so they can be edited in Word. Variables are
 * italic, units and built-in functions upright, as on the page. The XML uses the `m:` and `w:`
 * prefixes of the document it goes in ([Docx]).
 */
class Omml(private val units: Units = Units.BUILT_IN, private val multiplication: Multiplication = Multiplication.DOT) {

    /** A whole `<m:oMath>`. */
    fun math(st: Statement, result: Result? = null): String = "<m:oMath>${statement(st, result)}</m:oMath>"

    fun statement(st: Statement, result: Result? = null): String {
        val r = result?.let { op("=") + result(it) } ?: ""
        return when (st) {
            // ≔ is one symbol: Word spaces `:` and `=` apart as two operators.
            is Define -> expr(st.target) + op(if (st.global) "≡" else "≔") + expr(st.value) + r
            is Evaluate -> expr(st.expr) + r.ifEmpty { op("=") + op("■") }
            is ExprStatement -> expr(st.expr)
            Given -> run("Given", BOLD)
            // A symbolic statement carries no units, so every name in it is a variable.
            is Symbolic -> if (units !== Units.NONE) Omml(Units.NONE, multiplication).statement(st, result) else
                expr(st.expr) + st.keywords.joinToString("") { op("→") + run(Printer.print(it), BOLD) } +
                    op("→") + (result?.let { result(it) } ?: op("■"))
            is Unparsed -> text(st.message)
        }
    }

    fun result(r: Result): String {
        val number = r.number?.let { expr(it) } ?: text(r.numberText)
        val unit = r.unit ?: return number
        return number + op(THIN) + Omml(Units.ALL).expr(unit)
    }

    fun expr(e: Expr): String = when (e) {
        is Num -> number(e.text)
        is Id -> identifier(e)
        is Str -> text("“${e.value}”")
        Placeholder -> op("■")
        is Parens -> delimited("(", ")", expr(e.inner))
        is Apply -> apply(e)
        is Call -> call(e)
        is Matrix -> delimited("[", "]", "<m:m>" + e.rows.joinToString("") { row -> "<m:mr>" + row.joinToString("") { "<m:e>${expr(it)}</m:e>" } + "</m:mr>" } + "</m:m>")
        is Index -> sub(expr(e.target), e.indices.mapIndexed { k, x -> (if (k > 0) op(",") else "") + expr(unwrap(x)) }.joinToString(""))
        is Range -> expr(e.first) + (e.second?.let { op(",") + expr(it) } ?: "") + op("..") + expr(e.last)
        is Program -> program(e.lines)
    }

    private fun number(t: String): String {
        val m = Regex("""([0-9.]+)[eE]([+-]?)([0-9]+)([ij]?)""").matchEntire(t) ?: return run(t, PLAIN)
        val (mantissa, sign, exponent, imaginary) = m.destructured
        val power = sup(run("10", PLAIN), run((if (sign == "-") "−" else "") + exponent.trimStart('0').ifEmpty { "0" }, PLAIN))
        return run(mantissa, PLAIN) + op("×") + power + (if (imaginary.isEmpty()) "" else run(imaginary, ITALIC))
    }

    /** Plain variables italic; units and built-in functions ([function]) upright; styled names in their style. */
    private fun identifier(id: Id, function: Boolean = false): String {
        val upright = id.style == Id.Style.UPRIGHT || units.isUnit(id) || (function && id in Builtins.functions)
        val style = when {
            id.style == Id.Style.BOLD -> BOLD
            upright -> PLAIN
            else -> ITALIC
        }
        val name = run(id.name, style)
        return id.subscript?.let { sub(name, run(it, PLAIN)) } ?: name
    }

    private fun apply(e: Apply): String {
        val a = e.args
        fun x(k: Int) = expr(a[k])
        fun binary(o: String) = x(0) + op(o) + x(1)
        return when (e.op) {
            Op.ADD -> binary("+")
            Op.SUB -> binary("−")
            Op.MUL -> if (e.implicit || (!e.dot && multiplication == Multiplication.SPACE)) x(0) + op(THIN) + x(1) else binary("·")
            Op.DIV -> when (e.division) {
                Division.STACKED -> "<m:f><m:num>${expr(unwrap(a[0]))}</m:num><m:den>${expr(unwrap(a[1]))}</m:den></m:f>"
                Division.OBELUS -> binary("÷")
                Division.SLASH -> binary("/")
            }
            Op.POW -> sup(x(0), expr(unwrap(a[1])))
            Op.NEG -> op("−") + x(0)
            Op.POS -> op("+") + x(0)
            Op.FACTORIAL -> x(0) + op("!")
            Op.MEMBER -> x(0) + op(".") + x(1)
            Op.TRANSPOSE -> sup(x(0), run("T", PLAIN))
            Op.COLUMN -> sup(x(0), op("⟨") + expr(unwrap(a[1])) + op("⟩"))
            Op.ABS -> delimited("|", "|", x(0))
            Op.EQ -> binary("=")
            Op.NE -> binary("≠")
            Op.LT -> binary("<")
            Op.LE -> binary("≤")
            Op.GT -> binary(">")
            Op.GE -> binary("≥")
            Op.AND -> binary("∧")
            Op.OR -> binary("∨")
            Op.XOR -> binary("⊕")
            Op.NOT -> op("¬") + x(0)
            Op.ON_ERROR -> keyword("try") + x(0) + keyword("on error") + x(1)
        }
    }

    private fun call(e: Call): String {
        val name = (e.fn as? Id)?.takeIf { it.subscript == null && it.style == Id.Style.PLAIN }?.name
        val args = e.args
        return when {
            name == "sqrt" && args.size == 1 -> "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:deg/><m:e>${expr(unwrap(args[0]))}</m:e></m:rad>"
            name == "nthroot" && args.size == 2 -> "<m:rad><m:deg>${expr(unwrap(args[1]))}</m:deg><m:e>${expr(unwrap(args[0]))}</m:e></m:rad>"
            (name == "sum" || name == "prod") && args.size == 4 && args[1] is Id ->
                nary(if (name == "sum") "∑" else "∏", expr(args[1]) + op("=") + expr(unwrap(args[2])), expr(unwrap(args[3])), expr(args[0]))
            (name == "sum" || name == "prod") && args.size == 2 && args[1] is Id ->
                nary(if (name == "sum") "∑" else "∏", expr(args[1]), null, expr(args[0]))
            (name == "sum" || name == "prod") && args.size == 1 -> nary(if (name == "sum") "∑" else "∏", null, null, expr(args[0]))
            name == "vec" && args.size == 1 -> "<m:acc><m:accPr><m:chr m:val=\"→\"/></m:accPr><m:e>${expr(unwrap(args[0]))}</m:e></m:acc>"
            name == "diff" && args.size == 2 -> "<m:f><m:num>${run("d", ITALIC)}</m:num><m:den>${run("d", ITALIC)}${expr(args[1])}</m:den></m:f>" + expr(args[0])
            name == "int" && args.size == 4 -> nary("∫", expr(unwrap(args[2])), expr(unwrap(args[3])), expr(args[0]) + op(THIN) + run("d", ITALIC) + expr(args[1]))
            else -> {
                val fn = (e.fn as? Id)?.let { identifier(it, function = true) } ?: expr(e.fn)
                val list = "<m:d><m:dPr><m:sepChr m:val=\",\"/></m:dPr>" + args.joinToString("") { "<m:e>${expr(it)}</m:e>" } + "</m:d>"
                if ((e.fn as? Id)?.let { it in Builtins.functions } == true) "<m:func><m:fName>$fn</m:fName><m:e>$list</m:e></m:func>" else fn + list
            }
        }
    }

    /** A sum, product or integral: its symbol, with limits below and above when given. */
    private fun nary(symbol: String, below: String?, above: String?, body: String): String {
        val hide = (if (below == null) "<m:subHide m:val=\"1\"/>" else "") + (if (above == null) "<m:supHide m:val=\"1\"/>" else "")
        val place = if (symbol == "∫") "subSup" else "undOvr"
        return "<m:nary><m:naryPr><m:chr m:val=\"$symbol\"/><m:limLoc m:val=\"$place\"/>$hide</m:naryPr>" +
            "<m:sub>${below ?: ""}</m:sub><m:sup>${above ?: ""}</m:sup><m:e>$body</m:e></m:nary>"
    }

    /** A program: its lines one below another, aligned left (a one-column matrix), with the bar on their left. */
    private fun program(lines: List<ProgramLine>): String =
        "<m:d><m:dPr><m:begChr m:val=\"|\"/><m:endChr m:val=\"\"/></m:dPr><m:e><m:m>" +
            "<m:mPr><m:mcs><m:mc><m:mcPr><m:count m:val=\"1\"/><m:mcJc m:val=\"left\"/></m:mcPr></m:mc></m:mcs></m:mPr>" +
            lines.joinToString("") { "<m:mr><m:e>${programLine(it)}</m:e></m:mr>" } + "</m:m></m:e></m:d>"

    private fun programLine(line: ProgramLine): String = when (line) {
        is Line -> expr(line.expr)
        is LocalDefine -> expr(line.target) + op("←") + expr(line.value)
        is IfThen -> body(line.body) + keyword("if") + expr(line.condition)
        is Otherwise -> body(line.body) + keyword("otherwise")
        is For -> keyword("for") + expr(line.variable) + op("∈") + expr(line.over) + program(line.body)
        is While -> keyword("while") + expr(line.condition) + program(line.body)
        is Return -> keyword("return") + expr(line.value)
        Break -> keyword("break")
        Continue -> keyword("continue")
    }

    private fun body(lines: List<ProgramLine>) = if (lines.size == 1) programLine(lines[0]) else program(lines)

    private fun keyword(k: String) = run(" $k ", BOLD)

    private fun delimited(open: String, close: String, inner: String) =
        if (open == "(" && close == ")") "<m:d><m:e>$inner</m:e></m:d>"
        else "<m:d><m:dPr><m:begChr m:val=\"${xml(open)}\"/><m:endChr m:val=\"${xml(close)}\"/></m:dPr><m:e>$inner</m:e></m:d>"

    private fun sup(base: String, power: String) = "<m:sSup><m:e>$base</m:e><m:sup>$power</m:sup></m:sSup>"
    private fun sub(base: String, index: String) = "<m:sSub><m:e>$base</m:e><m:sub>$index</m:sub></m:sSub>"

    private fun op(s: String) = run(s, PLAIN)

    /** Text in an equation, not math: an error message, a string. */
    private fun text(s: String) = "<m:r><m:rPr><m:nor/></m:rPr>$FONT<m:t xml:space=\"preserve\">${xml(s)}</m:t></m:r>"

    private fun run(s: String, style: String) =
        "<m:r><m:rPr><m:sty m:val=\"$style\"/></m:rPr>$FONT<m:t xml:space=\"preserve\">${xml(s)}</m:t></m:r>"

    private fun unwrap(e: Expr): Expr = if (e is Parens) e.inner else e

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val PLAIN = "p"
        const val ITALIC = "i"
        const val BOLD = "b"

        /** A thin space, between a number and its unit. */
        const val THIN = " "

        /** Word draws a math run in the font its run properties name. */
        const val FONT = "<w:rPr><w:rFonts w:ascii=\"Cambria Math\" w:hAnsi=\"Cambria Math\"/></w:rPr>"
    }
}
