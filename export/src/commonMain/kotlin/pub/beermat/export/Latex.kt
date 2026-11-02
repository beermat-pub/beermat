package pub.beermat.export

import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.syntax.Division
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
 * Writes statements as LaTeX math (docs/export.md): `M_{max} := \frac{q \cdot L^{2}}{8} = 54\ \mathrm{kN \cdot m}`.
 * The output needs no packages beyond plain LaTeX math (and works in Markdown renderers).
 *
 * Variables are italic, as is usual in LaTeX; units, multi-letter names' subscripts and
 * keywords are upright. [units] tells which names are units in this sheet.
 */
class Latex(private val units: Units = Units.BUILT_IN, private val multiplication: Multiplication = Multiplication.DOT) {

    fun statement(st: Statement, result: Result? = null): String {
        val r = result?.let { " = " + result(it) } ?: ""
        return when (st) {
            is Define -> expr(st.target) + (if (st.global) " \\equiv " else " := ") + expr(st.value) + r
            is Evaluate -> expr(st.expr) + (r.ifEmpty { " = \\blacksquare" })
            is ExprStatement -> expr(st.expr)
            Given -> "\\textbf{Given}"
            // A symbolic statement carries no units, so every name in it is a variable (`t` in `laplace(t)`).
            is Symbolic -> if (units !== Units.NONE) Latex(Units.NONE, multiplication).statement(st, result) else
                expr(st.expr) + st.keywords.joinToString("") { " \\rightarrow \\mathbf{" + text(Printer.print(it)) + "}" } +
                " \\rightarrow " + (result?.let { result(it) } ?: "\\blacksquare")
            is Unparsed -> "\\text{${text(st.message)}}"
        }
    }

    /** `54\ \mathrm{kN \cdot m}`: the number, a space, and the unit with every name upright. */
    fun result(r: Result): String {
        val number = r.number?.let { expr(it) } ?: "\\text{${text(r.numberText)}}"
        val unit = r.unit ?: return number
        return "$number\\ " + Latex(Units.ALL).expr(unit)
    }

    fun expr(e: Expr): String = when (e) {
        is Num -> number(e.text)
        is Id -> identifier(e)
        is Str -> "\\text{“${text(e.value)}”}"
        Placeholder -> "\\blacksquare"
        is Parens -> "\\left(${expr(e.inner)}\\right)"
        is Apply -> apply(e)
        is Call -> call(e)
        is Matrix -> "\\begin{bmatrix}" + e.rows.joinToString(" \\\\ ") { row -> row.joinToString(" & ") { expr(it) } } + "\\end{bmatrix}"
        is Index -> group(expr(e.target)) + "_{" + e.indices.joinToString(",") { expr(unwrap(it)) } + "}"
        is Range -> expr(e.first) + (e.second?.let { ", " + expr(it) } ?: "") + " .. " + expr(e.last)
        is Program -> program(e.lines)
    }

    private fun number(t: String): String {
        val m = Regex("""([0-9.]+)[eE]([+-]?)([0-9]+)([ij]?)""").matchEntire(t) ?: return t
        val (mantissa, sign, exponent, imaginary) = m.destructured
        return "$mantissa \\times 10^{${if (sign == "-") "-" else ""}${exponent.trimStart('0').ifEmpty { "0" }}}$imaginary"
    }

    private fun identifier(id: Id): String {
        val name = name(id.name, units.isUnit(id) || id.style == Id.Style.UPRIGHT, id.style == Id.Style.BOLD)
        return id.subscript?.let { "${name}_{\\mathrm{${text(it)}}}" } ?: name
    }

    private fun name(n: String, upright: Boolean, bold: Boolean = false): String {
        val body = n.map { GREEK[it] ?: SYMBOLS[it] ?: it.toString() }.joinToString("") { if (it.startsWith("\\") && n.length > 1) "$it " else it }.trim()
        return when {
            // \mathbf doesn't reach lowercase Greek; \boldsymbol (amsmath, KaTeX, MathJax) does.
            bold -> if (n.any { it in GREEK }) "\\boldsymbol{$body}" else "\\mathbf{$body}"
            upright -> "\\mathrm{$body}"
            n.length == 1 -> body
            else -> "\\mathit{$body}"
        }
    }

    private fun apply(e: Apply): String {
        val a = e.args
        fun x(k: Int) = expr(a[k])
        return when (e.op) {
            Op.ADD -> "${x(0)} + ${x(1)}"
            Op.SUB -> "${x(0)} - ${x(1)}"
            Op.MUL -> if (e.implicit || (!e.dot && multiplication == Multiplication.SPACE)) "${x(0)}\\,${x(1)}" else "${x(0)} \\cdot ${x(1)}"
            Op.DIV -> when (e.division) {
                Division.STACKED -> "\\frac{${expr(unwrap(a[0]))}}{${expr(unwrap(a[1]))}}"
                Division.OBELUS -> "${x(0)} \\div ${x(1)}"
                Division.SLASH -> "${x(0)}/${x(1)}"
            }
            Op.POW -> "${group(x(0))}^{${expr(unwrap(a[1]))}}"
            Op.NEG -> "-${x(0)}"
            Op.POS -> "+${x(0)}"
            Op.FACTORIAL -> "${x(0)}!"
            Op.MEMBER -> "${x(0)}.${x(1)}"
            Op.TRANSPOSE -> "${group(x(0))}^{\\mathsf{T}}"
            Op.COLUMN -> "${group(x(0))}^{\\langle ${expr(unwrap(a[1]))} \\rangle}"
            Op.ABS -> "\\left|${x(0)}\\right|"
            Op.EQ -> "${x(0)} = ${x(1)}"
            Op.NE -> "${x(0)} \\neq ${x(1)}"
            Op.LT -> "${x(0)} < ${x(1)}"
            Op.LE -> "${x(0)} \\leq ${x(1)}"
            Op.GT -> "${x(0)} > ${x(1)}"
            Op.GE -> "${x(0)} \\geq ${x(1)}"
            Op.AND -> "${x(0)} \\land ${x(1)}"
            Op.OR -> "${x(0)} \\lor ${x(1)}"
            Op.XOR -> "${x(0)} \\oplus ${x(1)}"
            Op.NOT -> "\\lnot ${x(0)}"
            Op.ON_ERROR -> "\\mathbf{try\\ } ${x(0)} \\mathbf{\\ on\\ error\\ } ${x(1)}"
        }
    }

    private fun call(e: Call): String {
        val name = (e.fn as? Id)?.takeIf { it.subscript == null && it.style == Id.Style.PLAIN }?.name
        val args = e.args
        return when {
            name == "sqrt" && args.size == 1 -> "\\sqrt{${expr(unwrap(args[0]))}}"
            name == "nthroot" && args.size == 2 -> "\\sqrt[${expr(unwrap(args[1]))}]{${expr(unwrap(args[0]))}}"
            (name == "sum" || name == "prod") && args.size == 4 && args[1] is Id ->
                "\\${name}_{${expr(args[1])}=${expr(unwrap(args[2]))}}^{${expr(unwrap(args[3]))}} ${expr(args[0])}"
            (name == "sum" || name == "prod") && args.size == 2 && args[1] is Id -> "\\${name}_{${expr(args[1])}} ${expr(args[0])}"
            (name == "sum" || name == "prod") && args.size == 1 -> "\\$name ${expr(args[0])}"
            name == "vec" && args.size == 1 -> "\\overrightarrow{${expr(unwrap(args[0]))}}"
            name == "diff" && args.size == 2 -> "\\frac{d}{d${expr(args[1])}} ${expr(args[0])}"
            name == "diff" && args.size == 3 -> "\\frac{d^{${expr(args[2])}}}{d${expr(args[1])}^{${expr(args[2])}}} ${expr(args[0])}"
            name == "int" && args.size == 4 -> "\\int_{${expr(unwrap(args[2]))}}^{${expr(unwrap(args[3]))}} ${expr(args[0])}\\,d${expr(args[1])}"
            else -> {
                val fn = when {
                    name != null && name in FUNCTIONS -> "\\$name"
                    name != null && Id(name) in Builtins.functions -> "\\mathrm{${text(name)}}"
                    else -> expr(e.fn)
                }
                "$fn\\left(${args.joinToString(", ") { expr(it) }}\\right)"
            }
        }
    }

    private fun program(lines: List<ProgramLine>): String =
        "\\left|\\begin{array}{l}" + lines.joinToString(" \\\\ ") { programLine(it) } + "\\end{array}\\right."

    private fun programLine(line: ProgramLine): String = when (line) {
        is Line -> expr(line.expr)
        is LocalDefine -> "${expr(line.target)} \\leftarrow ${expr(line.value)}"
        is IfThen -> "${body(line.body)} \\quad \\mathbf{if}\\ ${expr(line.condition)}"
        is Otherwise -> "${body(line.body)} \\quad \\mathbf{otherwise}"
        is For -> "\\mathbf{for}\\ ${expr(line.variable)} \\in ${expr(line.over)} \\\\ \\quad ${program(line.body)}"
        is While -> "\\mathbf{while}\\ ${expr(line.condition)} \\\\ \\quad ${program(line.body)}"
        is Return -> "\\mathbf{return}\\ ${expr(line.value)}"
        Break -> "\\mathbf{break}"
        Continue -> "\\mathbf{continue}"
    }

    private fun body(lines: List<ProgramLine>) = if (lines.size == 1) programLine(lines[0]) else program(lines)

    /** Braces around anything more than one symbol, so a superscript applies to all of it. */
    private fun group(s: String) = if (s.length == 1 || (s.startsWith("\\") && s.drop(1).all(Char::isLetter))) s else "{$s}"

    private fun unwrap(e: Expr): Expr = if (e is Parens) e.inner else e

    private fun text(s: String) = s.replace("\\", "\\textbackslash{}").replace(Regex("([{}#$%&_^~])"), "\\\\$1")

    companion object {
        /** What a name may hold (with backticks, `` `x%` ``) that LaTeX reads as markup, or has a command for. */
        val SYMBOLS = mapOf(
            '∇' to "\\nabla", '∂' to "\\partial", '°' to "{}^{\\circ}", '*' to "\\ast", '%' to "\\%", '#' to "\\#", '&' to "\\&",
            '$' to "\\$", '_' to "\\_", '{' to "\\{", '}' to "\\}", '~' to "\\sim", '^' to "\\hat{}", '\\' to "\\backslash",
        )

        /** LaTeX commands for the Greek letters; capitals without one (Α, Β, …) are Latin lookalikes. */
        val GREEK: Map<Char, String> = run {
            val names = listOf(
                "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa",
                "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi",
                "chi", "psi", "omega",
            )
            val capitals = setOf("Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega")
            names.flatMapIndexed { k, name ->
                val offset = if (k >= names.indexOf("sigma")) k + 1 else k
                val lower = ('α' + offset) to (if (name == "omicron") "o" else "\\$name")
                val cap = name.replaceFirstChar(Char::uppercaseChar)
                listOfNotNull(lower, if (cap in capitals) ('Α' + offset) to "\\$cap" else null)
            }.toMap()
        }

        /** Built-ins that LaTeX sets upright with their own command. */
        private val FUNCTIONS = setOf("sin", "cos", "tan", "sinh", "cosh", "tanh", "exp", "ln", "log", "max", "min", "det", "arg")
    }
}
