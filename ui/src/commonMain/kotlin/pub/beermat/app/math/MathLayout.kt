package pub.beermat.app.math

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Multiplication
import pub.beermat.engine.syntax.Continue
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Division
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
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.ProgramLine
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.Return
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.syntax.Symbolic
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.engine.syntax.While
import pub.beermat.export.Units

/** The extent of a run of text: its advance [width], and how far it reaches above and below the baseline. */
data class Extent(val width: Float, val ascent: Float, val descent: Float)

/** Measures text in the math font. The app uses Skia; tests use a fake. */
fun interface Metrics {
    fun measure(text: String, size: Float): Extent
}

/**
 * Lays out the expression tree as 2D math (docs/syntax.md shows what each node looks like):
 * `/` as a stacked fraction, `^` as a superscript, `sqrt` as a radical, `sum` as Σ with limits,
 * matrices in square brackets, programs with a vertical bar.
 *
 * Parentheses that only the linear form needs (around a numerator, an exponent, a radicand…)
 * are dropped, because the 2D structure already groups those.
 *
 * Variables are italic; units, built-in functions, numbers and literal subscripts are upright.
 * [units] tells which names are units in this sheet.
 *
 * [size] is the font size in pixels; sizes inside scale from it.
 *
 * For the statement being edited, the layout is [tagged]: every node's box is wrapped in a [Group]
 * tagged with its index among its parent's children, so the editor can find a node's place
 * ([BoxPaths]). It also shows the empty result unit slot, and draws leaves still being typed
 * from [drafts] (by node identity), so `1.` shows as typed.
 */
class MathLayout(
    private val metrics: Metrics,
    val size: Float = 18f,
    private val units: Units = Units.BUILT_IN,
    private val tagged: Boolean = false,
    private val drafts: Map<Expr, String> = emptyMap(),
    /** How `*` is drawn, from the sheet's settings (docs/syntax.md#multiplication-style). */
    private val multiplication: Multiplication = Multiplication.DOT,
    /** Where a big array in the statement is scrolled to (docs/results.md#big-arrays). */
    private val scroll: Scroll = Scroll(),
) {

    /** The first row and column a big array's table shows, and the sheet's ORIGIN to number them from. */
    data class Scroll(val row: Int = 0, val col: Int = 0, val origin: Int = 0)

    /** This layout with big arrays scrolled to [scroll]. */
    fun scrolled(scroll: Scroll) = MathLayout(metrics, size, units, tagged, drafts, multiplication, scroll)

    /** This layout for a sheet whose units are [units] and whose `*` is drawn as [multiplication]. */
    fun forSheet(units: Units, multiplication: Multiplication) = MathLayout(metrics, size, units, tagged, drafts, multiplication, scroll)

    /** This layout for the statement being edited (see the class comment); [drafts] should be an identity map. */
    fun editing(drafts: Map<Expr, String>) = MathLayout(metrics, size, units, true, drafts, multiplication, scroll)

    /** [b] marked as child [k] of the node around it, when tagging. */
    private fun tag(k: Int, b: Box): Box = if (!tagged) b else Group(b.width, b.ascent, b.descent, listOf(Placed(b, 0f, 0f)), k)

    /** Child [k] of the node being laid out; with [unwrap], parentheses the 2D form makes redundant are dropped. */
    private fun child(k: Int, e: Expr, s: Float, unwrap: Boolean = false): Box =
        tag(k, if (unwrap && e is Parens) tag(0, expr(e.inner, s)) else expr(e, s))

    // Spacing, in fractions of the font size (TeX's thin, medium and thick math spaces).
    private fun thin(s: Float) = Group.space(0.17f * s)
    private fun medium(s: Float) = Group.space(0.22f * s)
    private fun thick(s: Float) = Group.space(0.28f * s)

    /** The math axis: the height of the middle of `+`, where fraction rules sit. */
    private fun axis(s: Float): Float = metrics.measure("+", s).let { (it.ascent - it.descent) / 2 }

    private fun scriptSize(s: Float) = maxOf(s * 0.7f, size * 0.5f)

    private fun text(t: String, s: Float, ink: Ink = Ink.Normal, bold: Boolean = false): TextBox {
        val e = metrics.measure(t, s)
        return TextBox(t, s, e.width, e.ascent, e.descent, ink, bold)
    }

    private fun keyword(t: String, s: Float) = text(t, s, Ink.Keyword, bold = true)

    private fun row(vararg boxes: Box) = Group.row(boxes.toList())

    // Statements.

    /**
     * A statement, with its result after `=` when it shows one. [number] and [unit] are the
     * result's parts in linear syntax (see `ResultText.Parts`).
     */
    fun statement(st: Statement, number: String? = null, unit: String? = null, table: Table? = null): Box {
        // A symbolic statement carries no units, so every name in it is a variable (`t` in `laplace(t)`).
        if (st is Symbolic && units !== Units.NONE) return MathLayout(metrics, size, Units.NONE, tagged, drafts, multiplication, scroll).statement(st, number, unit)
        val s = size
        // The result unit slot: its index among the statement's children, and the unit typed into it.
        val (slot, slotUnit) = when (st) {
            is Define -> (if (st.result != null) 2 else null) to st.result?.unit
            is Evaluate -> 1 to st.result.unit
            else -> null to null
        }
        val result = when {
            number != null -> result(number, unit, slot, slotUnit, table)
            st is Evaluate || (tagged && slot != null) -> resultSlotOnly(slot, slotUnit, s)
            else -> null
        }
        fun withResult(left: Box): Box = if (result == null) left else row(left, thick(s), text("=", s), thick(s), result)
        return when (st) {
            is Define -> {
                val sign = if (st.global) "≡" else ":="
                withResult(row(child(0, st.target, s), thick(s), text(sign, s), thick(s), child(1, st.value, s)))
            }
            is Evaluate -> withResult(child(0, st.expr, s))
            // In a row, so the expression's tag is inside the statement's box, as for the others (BoxPaths looks inside the root).
            is ExprStatement -> if (tagged) row(child(0, st.expr, s)) else expr(st.expr, s)
            Given -> keyword("Given", s)
            // `expr → expand → result`: the result is an expression, given as [number].
            is Symbolic -> {
                val parts = mutableListOf(child(0, st.expr, s))
                st.keywords.forEachIndexed { k, kw ->
                    val shown = if (kw is Id || kw is Call) tag(k + 1, keywordOrExpr(kw, s)) else child(k + 1, kw, s)
                    parts += listOf(thick(s), text("→", s), thick(s), shown)
                }
                parts += listOf(thick(s), text("→", s), thick(s), number?.let { parsedOrText(it, s) } ?: placeholder(s))
                Group.row(parts)
            }
            is Unparsed -> text(st.message, s * 0.8f, Ink.Error)
        }
    }

    /** A symbolic keyword in bold (`expand`, `solve, x`). */
    private fun keywordOrExpr(kw: Expr, s: Float): Box = when {
        kw is Id -> keyword(kw.text, s)
        kw is Call && kw.fn is Id -> row(keyword((kw.fn as Id).text, s), text(",", s), thin(s), commaList(kw.args, s, first = 1))
        else -> expr(kw, s)
    }

    /**
     * A big array result shown a window at a time (docs/results.md#big-arrays): [rows] by [cols] in
     * all, the window from [firstRow], [firstCol]; indices counted from [origin].
     */
    data class Table(val rows: Int, val cols: Int, val firstRow: Int, val firstCol: Int, val origin: Int)

    /** A result: the number (or matrix…) and its unit, each parsed back from linear syntax; a big array as a [table]. */
    fun result(number: String, unit: String?, slot: Int? = null, slotUnit: Expr? = null, table: Table? = null): Box {
        val s = size
        val window = table?.let { runCatching { pub.beermat.engine.syntax.Parser.parseExpression(number) as? Matrix }.getOrNull() }
        val n = if (table != null && window != null) table(window.rows.map { row -> row.map { expr(it, s) } }, table, s) else parsedOrText(number, s)
        // In the unit part every name is a unit.
        val units = MathLayout(metrics, size, Units.ALL, tagged, drafts, multiplication, scroll)
        val u = when {
            !tagged || slot == null -> unit?.let { units.parsedOrText(it, s) }
            slotUnit != null -> tag(slot, units.expr(slotUnit, s))
            unit != null -> tag(slot, units.parsedOrText(unit, s))
            else -> tag(slot, placeholder(s * 0.75f))
        }
        return if (u == null) n else row(n, thin(s), u)
    }

    /** No result yet: a placeholder, and the unit slot when editing. */
    private fun resultSlotOnly(slot: Int?, slotUnit: Expr?, s: Float): Box {
        if (!tagged || slot == null) return placeholder(s)
        val u = slotUnit?.let { MathLayout(metrics, size, Units.ALL, true, drafts, multiplication, scroll).expr(it, s) } ?: placeholder(s * 0.75f)
        return row(placeholder(s), thin(s), tag(slot, u))
    }

    /**
     * An error message, smaller and in [Ink.Error]. The variables it quotes, [names] (written
     * in linear syntax in the text: `\bf F_max is not defined`), are drawn as they are in the sheet.
     */
    fun message(t: String, names: List<Id> = emptyList(), s: Float = size * 0.72f): Box {
        val parts = mutableListOf<Box>()
        var k = 0
        for ((range, id) in quoted(t, names)) {
            if (range.first > k) parts += text(t.substring(k, range.first), s, Ink.Error)
            parts += identifier(id, s, ink = Ink.Error)
            k = range.last + 1
        }
        if (k < t.length) parts += text(t.substring(k), s, Ink.Error)
        return Group.row(parts)
    }

    /** Where each of [names] appears in [t] as a whole name, left to right, longest first where they overlap. */
    private fun quoted(t: String, names: List<Id>): List<Pair<IntRange, Id>> {
        fun nameChar(c: Char?) = c != null && (Id.isNameChar(c) || c == '_' || c == '\\')
        val found = names.distinct().sortedByDescending { it.text.length }.flatMap { id ->
            Regex.escape(id.text).toRegex().findAll(t)
                .filter { !nameChar(t.getOrNull(it.range.first - 1)) && !nameChar(t.getOrNull(it.range.last + 1)) }
                .map { it.range to id }
        }
        val taken = mutableListOf<IntRange>()
        return found.filter { (r, _) -> taken.none { r.first <= it.last && it.first <= r.last }.also { if (it) taken += r } }
            .sortedBy { it.first.first }
    }

    private fun parsedOrText(t: String, s: Float): Box = try {
        expr(Parser.parseExpression(t), s)
    } catch (_: ParseException) {
        text(t, s)
    }

    // Expressions.

    fun expr(e: Expr, s: Float = size): Box {
        drafts[e]?.let { return text(it, s) }
        return when (e) {
            is Num -> number(e.text, s)
            is Id -> identifier(e, s)
            is Str -> text("“${e.value}”", s)
            Placeholder -> placeholder(s)
            is Parens -> fenced('(', ')', child(0, e.inner, s), s)
            is Apply -> apply(e, s)
            is Call -> call(e, s)
            is Matrix -> matrix(e, s)
            is Index -> script(child(0, e.target, s), sub = commaList(e.indices, scriptSize(s), first = 1, unwrap = true), s = s)
            is Range -> {
                val parts = mutableListOf(child(0, e.first, s))
                e.second?.let { parts += listOf(text(",", s), thin(s), child(1, it, s)) }
                parts += listOf(text("..", s), child(if (e.second == null) 1 else 2, e.last, s))
                Group.row(parts)
            }
            is Program -> program(e.lines, s)
        }
    }

    private fun placeholder(s: Float) = text("■", s * 0.8f, Ink.Faint)

    /** `1.2e-3` as 1.2 × 10⁻³; imaginary numbers keep their `i`. */
    private fun number(t: String, s: Float): Box {
        val m = Regex("""([0-9.]+)[eE]([+-]?)([0-9]+)([ij]?)""").matchEntire(t) ?: return text(t, s)
        val (mantissa, sign, exponent, imaginary) = m.destructured
        val power = script(text("10", s), sup = text((if (sign == "-") "−" else "") + exponent.trimStart('0').ifEmpty { "0" }, scriptSize(s)), s = s)
        val parts = listOf(text(mantissa, s), thin(s), text("×", s), thin(s), power)
        return Group.row(if (imaginary.isEmpty()) parts else parts + text(imaginary, s))
    }

    /** In its style; a plain name is italic, except units and built-in functions ([function]: the name of a call). */
    private fun identifier(id: Id, s: Float, function: Boolean = false, ink: Ink = Ink.Normal): Box {
        val shown = when (id.style) {
            Id.Style.BOLD -> MathAlphabet.bold(id.name)
            Id.Style.UPRIGHT -> id.name
            Id.Style.PLAIN -> if (units.isUnit(id) || (function && id in Builtins.functions)) id.name else MathAlphabet.italic(id.name)
        }
        val name = text(shown, s, ink)
        val sub = id.subscript ?: return name
        return script(name, sub = text(sub, scriptSize(s), ink), s = s)
    }

    private fun apply(e: Apply, s: Float): Box {
        val a = e.args
        fun x(k: Int) = child(k, a[k], s)
        fun binary(symbol: String, space: (Float) -> Box, bold: Boolean = false) =
            row(x(0), space(s), text(symbol, s, bold = bold), space(s), x(1))
        return when (e.op) {
            Op.ADD -> binary("+", ::medium)
            Op.SUB -> binary("−", ::medium)
            // A thin space when written so (`q L`), a dot when written `·`; `*` follows the sheet.
            Op.MUL -> if (e.implicit || (!e.dot && multiplication == Multiplication.SPACE)) row(x(0), thin(s), x(1)) else binary("·", ::medium)
            // Stacked when written `/`; on the line, with its parentheses, when written `÷` or `∕`.
            Op.DIV -> when (e.division) {
                Division.STACKED -> fraction(child(0, a[0], s, unwrap = true), child(1, a[1], s, unwrap = true), s)
                Division.OBELUS -> binary("÷", ::medium)
                Division.SLASH -> row(x(0), text("/", s), x(1))
            }
            Op.POW -> script(x(0), sup = child(1, a[1], scriptSize(s), unwrap = true), s = s)
            Op.NEG -> row(text("−", s), x(0))
            Op.POS -> row(text("+", s), x(0))
            Op.FACTORIAL -> row(x(0), text("!", s))
            Op.MEMBER -> row(x(0), text(".", s), x(1))
            Op.TRANSPOSE -> script(x(0), sup = text("T", scriptSize(s)), s = s)
            Op.COLUMN -> script(x(0), sup = row(text("⟨", scriptSize(s)), child(1, a[1], scriptSize(s), unwrap = true), text("⟩", scriptSize(s))), s = s)
            Op.ABS -> fenced('|', '|', x(0), s)
            Op.EQ -> binary("=", ::thick, bold = true)
            Op.NE -> binary("≠", ::thick)
            Op.LT -> binary("<", ::thick)
            Op.LE -> binary("≤", ::thick)
            Op.GT -> binary(">", ::thick)
            Op.GE -> binary("≥", ::thick)
            Op.AND -> binary("∧", ::medium)
            Op.OR -> binary("∨", ::medium)
            Op.XOR -> binary("⊕", ::medium)
            Op.NOT -> row(text("¬", s), x(0))
            Op.ON_ERROR -> row(keyword("try", s), thick(s), x(0), thick(s), keyword("on error", s), thick(s), x(1))
        }
    }

    private fun call(e: Call, s: Float): Box {
        val name = (e.fn as? Id)?.takeIf { it.subscript == null && it.style == Id.Style.PLAIN }?.name
        val args = e.args
        // Arguments are children 1, 2, …: child 0 is the function's name.
        return when {
            // `use "file.py"`, as written: the word and the file's name, no parentheses.
            name == "use" && args.size == 1 && args[0] is Str -> row(keyword("use", s), thick(s), child(1, args[0], s))
            name == "sqrt" && args.size == 1 -> radical(child(1, args[0], s, unwrap = true), null, s)
            name == "nthroot" && args.size == 2 -> radical(child(1, args[0], s, unwrap = true), child(2, args[1], scriptSize(s), unwrap = true), s)
            (name == "sum" || name == "prod") && args.size in setOf(2, 4) && (args[1] is Id || args[1] == Placeholder) -> {
                val ss = scriptSize(s)
                val below = if (args.size == 4) row(child(2, args[1], ss), text("=", ss), child(3, args[2], ss, unwrap = true)) else child(2, args[1], ss)
                val above = if (args.size == 4) child(4, args[3], ss, unwrap = true) else null
                bigOperator(if (name == "sum") "∑" else "∏", below, above, child(1, args[0], s), s)
            }
            (name == "sum" || name == "prod") && args.size == 1 ->
                bigOperator(if (name == "sum") "∑" else "∏", null, null, child(1, args[0], s), s)
            // diff(f, x) as d/dx f, diff(f, x, n) as dⁿ/dxⁿ f (the derivative operators).
            name == "diff" && args.size in 2..3 -> {
                val n = args.getOrNull(2)
                val d = if (n == null) text("d", s) else script(text("d", s), sup = child(3, n, scriptSize(s)), s = s)
                val dx = row(text("d", s), child(2, args[1], s)).let { if (n == null) it else script(it, sup = expr(n, scriptSize(s)), s = s) }
                row(fraction(d, dx, s), thin(s), child(1, args[0], s, unwrap = true))
            }
            // int(f, x, a, b) as ∫ from a to b of f dx.
            name == "int" && args.size == 4 -> {
                val ss = scriptSize(s)
                row(
                    bigOperator("∫", child(3, args[2], ss, unwrap = true), child(4, args[3], ss, unwrap = true), child(1, args[0], s), s),
                    thin(s), text("d", s), child(2, args[1], s),
                )
            }
            // int(f, x), indefinite (a symbolic result): ∫ f dx.
            name == "int" && args.size == 2 ->
                row(bigOperator("∫", null, null, child(1, args[0], s), s), thin(s), text("d", s), child(2, args[1], s))
            // lim(f, x, a) as lim with x→a below; "+" or "-" after a for one side.
            name == "lim" && args.size in 3..4 -> {
                val ss = scriptSize(s)
                val side = (args.getOrNull(3) as? Str)?.value
                val point = child(3, args[2], ss, unwrap = true)
                val below = row(child(2, args[1], ss), text("→", ss), if (side == "+" || side == "-") script(point, sup = text(if (side == "+") "+" else "−", ss), s = ss) else point)
                val lim = text("lim", s)
                val width = maxOf(lim.width, below.width)
                val op = Group.of(listOf(Placed(lim, (width - lim.width) / 2, 0f), Placed(below, (width - below.width) / 2, lim.descent + 0.1f * s + below.ascent)), width)
                row(op, thin(s), child(1, args[0], s))
            }
            name == "vec" && args.size == 1 -> arrowOver(child(1, args[0], s, unwrap = true), s)
            else -> row(tag(0, (e.fn as? Id)?.let { identifier(it, s, function = true) } ?: expr(e.fn, s)), fenced('(', ')', commaList(args, s, first = 1), s))
        }
    }

    /** [items] separated by commas; they are children [first], [first] + 1, … of the node. */
    private fun commaList(items: List<Expr>, s: Float, first: Int = 0, unwrap: Boolean = false): Box =
        Group.row(
            items.flatMapIndexed { k, item ->
                val b = child(first + k, item, s, unwrap)
                if (k == 0) listOf(b) else listOf(text(",", s), thin(s), b)
            },
        )

    /** Parentheses the 2D layout makes redundant. */
    private fun unwrap(e: Expr): Expr = if (e is Parens) e.inner else e

    // Building blocks.

    fun fraction(num: Box, den: Box, s: Float): Box {
        val pad = 0.12f * s
        val gap = 0.15f * s
        val t = 0.055f * s
        val axis = axis(s)
        val width = maxOf(num.width, den.width) + 2 * pad
        val rule = StrokeBox(width, axis + t, 0f, listOf(PathOp.Move(0f, -axis), PathOp.Line(width, -axis)), t)
        return Group.of(
            listOf(
                Placed(num, (width - num.width) / 2, -axis - t / 2 - gap - num.descent),
                Placed(rule, 0f, 0f),
                Placed(den, (width - den.width) / 2, -axis + t / 2 + gap + den.ascent),
            ),
        )
    }

    /** [base] with a superscript and/or subscript. */
    fun script(base: Box, sup: Box? = null, sub: Box? = null, s: Float): Box {
        val children = mutableListOf(Placed(base, 0f, 0f))
        var width = base.width
        if (sup != null) {
            val up = maxOf(0.38f * s, base.ascent - 0.55f * sup.ascent)
            children += Placed(sup, base.width + 0.05f * s, -up)
            width = maxOf(width, base.width + 0.05f * s + sup.width)
        }
        if (sub != null) {
            val down = maxOf(0.2f * s, base.descent * 0.9f)
            children += Placed(sub, base.width + 0.03f * s, down)
            width = maxOf(width, base.width + 0.03f * s + sub.width)
        }
        return Group.of(children, width + 0.04f * s)
    }

    /**
     * [body] between delimiters: the font's glyphs for a single line of text, drawn strokes
     * stretched to fit anything taller (fractions, matrices, programs).
     */
    fun fenced(left: Char, right: Char, body: Box, s: Float): Box {
        val line = metrics.measure("(", s)
        if (body.ascent <= line.ascent * 1.05f && body.descent <= line.descent * 1.3f) {
            return row(text(left.toString(), s), body, text(right.toString(), s))
        }
        val axis = axis(s)
        val half = maxOf(body.ascent - axis, body.descent + axis) + 0.1f * s
        val l = delimiter(left, -axis - half, -axis + half, s, opening = true)
        val r = delimiter(right, -axis - half, -axis + half, s, opening = false)
        return row(l, thin(s), body, thin(s), r)
    }

    private fun delimiter(c: Char, top: Float, bottom: Float, s: Float, opening: Boolean): Box {
        // Taller delimiters are wider, so a tall parenthesis still curves.
        val w = minOf(0.3f * s + 0.06f * (bottom - top), 0.9f * s)
        val t = 0.06f * s
        val mid = (top + bottom) / 2
        fun x(f: Float) = if (opening) f * w else (1 - f) * w
        val path = when (c) {
            '(', ')' -> listOf(PathOp.Move(x(0.85f), top), PathOp.Quad(x(-0.05f), mid, x(0.85f), bottom))
            '[', ']' -> listOf(PathOp.Move(x(0.85f), top), PathOp.Line(x(0.3f), top), PathOp.Line(x(0.3f), bottom), PathOp.Line(x(0.85f), bottom))
            else -> listOf(PathOp.Move(w / 2, top), PathOp.Line(w / 2, bottom))
        }
        return StrokeBox(w, -top, bottom, path, t)
    }

    fun radical(body: Box, index: Box?, s: Float): Box {
        val gap = 0.12f * s
        val t = 0.055f * s
        val top = -(body.ascent + gap + t)
        val bottom = body.descent + 0.05f * s
        val sign = 0.55f * s
        val shift = maxOf(0f, (index?.width ?: 0f) - 0.25f * sign)
        val mid = top + (bottom - top) * 0.55f
        val end = shift + sign + body.width + gap
        val path = listOf(
            PathOp.Move(shift, mid + 0.05f * s),
            PathOp.Line(shift + 0.2f * sign, mid - 0.03f * s),
            PathOp.Line(shift + 0.5f * sign, bottom),
            PathOp.Line(shift + sign, top),
            PathOp.Line(end, top),
        )
        val children = mutableListOf(
            Placed(StrokeBox(end, -top, bottom, path, t), 0f, 0f),
            Placed(body, shift + sign + gap / 2, 0f),
        )
        if (index != null) children += Placed(index, 0f, mid - 0.1f * s - index.descent)
        return Group.of(children, end + 0.08f * s)
    }

    private fun bigOperator(symbol: String, below: Box?, above: Box?, body: Box, s: Float): Box {
        val sym = text(symbol, s * 1.5f)
        val axis = axis(s)
        val y = (sym.ascent - sym.descent) / 2 - axis
        val width = maxOf(sym.width, below?.width ?: 0f, above?.width ?: 0f)
        val children = mutableListOf(Placed(sym, (width - sym.width) / 2, y))
        if (below != null) children += Placed(below, (width - below.width) / 2, y + sym.descent + 0.1f * s + below.ascent)
        if (above != null) children += Placed(above, (width - above.width) / 2, y - sym.ascent - 0.1f * s - above.descent)
        return row(Group.of(children, width), thin(s), body)
    }

    private fun arrowOver(body: Box, s: Float): Box {
        val y = -body.ascent - 0.15f * s
        val head = 0.15f * s
        val path = listOf(
            PathOp.Move(0f, y), PathOp.Line(body.width, y),
            PathOp.Move(body.width - head, y - 0.6f * head), PathOp.Line(body.width, y), PathOp.Line(body.width - head, y + 0.6f * head),
        )
        return Group.of(listOf(Placed(body, 0f, 0f), Placed(StrokeBox(body.width, -y + head, 0f, path, 0.05f * s), 0f, 0f)), body.width)
    }

    /** A matrix: centred cells in a grid, centred on the axis, in square brackets. */
    private fun matrix(m: Matrix, s: Float): Box {
        val cols0 = m.rows[0].size
        // A big one (an imported input table) shows a window at a time, as a big result does (docs/results.md#big-arrays).
        if (m.rows.size > ResultText.Window.SIZE || cols0 > ResultText.Window.SIZE) {
            val w = ResultText.Window(ResultText.Window.first(scroll.row, m.rows.size), ResultText.Window.first(scroll.col, cols0))
            val cells = w.rows(m.rows.size).map { r -> w.cols(cols0).map { c -> child(r * cols0 + c, m.rows[r][c], s) } }
            return table(cells, Table(m.rows.size, cols0, w.row, w.col, scroll.origin), s)
        }
        val cells = m.rows.mapIndexed { r, row -> row.mapIndexed { c, cell -> child(r * cols0 + c, cell, s) } }
        val cols = cells[0].indices.map { c -> cells.maxOf { it[c].width } }
        val colGap = 0.8f * s
        val rowGap = 0.55f * s
        val rowAscent = cells.map { r -> maxOf(r.maxOf { it.ascent }, 0.7f * s) }
        val rowDescent = cells.map { r -> maxOf(r.maxOf { it.descent }, 0.2f * s) }
        val height = rowAscent.sum() + rowDescent.sum() + rowGap * (cells.size - 1)
        var y = -axis(s) - height / 2
        val placed = mutableListOf<Placed>()
        cells.forEachIndexed { r, row ->
            y += rowAscent[r]
            var x = 0f
            row.forEachIndexed { c, cell ->
                placed += Placed(cell, x + (cols[c] - cell.width) / 2, y)
                x += cols[c] + colGap
            }
            y += rowDescent[r] + rowGap
        }
        val grid = Group.of(placed, cols.sum() + colGap * (cols.size - 1))
        return fenced('[', ']', grid, s)
    }

    /**
     * A window of a big array: its cells under column numbers and beside row numbers, with scroll
     * bars that show where the window is in the whole (docs/results.md#big-arrays).
     */
    private fun table(cells: List<List<Box>>, t: Table, s: Float): Box {
        val small = 0.7f * s
        val nr = cells.size
        val nc = cells[0].size
        val rowHeads = (0 until nr).map { text((t.origin + t.firstRow + it).toString(), small, Ink.Faint) }
        val colHeads = (0 until nc).map { text((t.origin + t.firstCol + it).toString(), small, Ink.Faint) }
        val pad = 0.4f * s
        val gap = 0.3f * s
        val colW = (0 until nc).map { c -> maxOf(cells.maxOf { it[c].width }, colHeads[c].width) + 2 * pad }
        val headW = rowHeads.maxOf { it.width } + 2 * pad
        val rowA = cells.map { r -> maxOf(r.maxOf { it.ascent }, 0.75f * s) }
        val rowD = cells.map { r -> maxOf(r.maxOf { it.descent }, 0.25f * s) }
        val rowH = (0 until nr).map { rowA[it] + rowD[it] + gap }
        val headH = 1.2f * small + gap
        val bar = 0.6f * s
        val bodyW = colW.sum()
        val bodyH = rowH.sum()
        val width = headW + bodyW + (if (t.rows > nr) bar else 0f)
        val height = headH + bodyH + (if (t.cols > nc) bar else 0f)
        val top = -axis(s) - height / 2
        val placed = mutableListOf<Placed>()
        var x = headW
        colHeads.forEachIndexed { c, h ->
            placed += Placed(h, x + colW[c] - pad - h.width, top + small)
            x += colW[c]
        }
        var y = top + headH
        cells.forEachIndexed { r, row ->
            val baseline = y + gap / 2 + rowA[r]
            placed += Placed(rowHeads[r], headW - pad - rowHeads[r].width, baseline)
            var cx = headW
            row.forEachIndexed { c, cell ->
                placed += Placed(cell, cx + colW[c] - pad - cell.width, baseline)
                cx += colW[c]
            }
            y += rowH[r]
        }
        val thin = 0.03f * s
        fun stroke(path: List<PathOp>, thickness: Float) = Placed(StrokeBox(width, -top, top + height, path, thickness), 0f, 0f)
        // The lines between the numbers and the cells.
        placed += stroke(listOf(PathOp.Move(headW, top), PathOp.Line(headW, top + headH + bodyH), PathOp.Move(0f, top + headH), PathOp.Line(headW + bodyW, top + headH)), thin)
        // Scroll bars: a track, and a thumb as long as the window's share of the whole (at least half a line).
        fun thumb(first: Int, shown: Int, all: Int, length: Float): Pair<Float, Float> {
            val a = first.toFloat() / all * length
            return a to maxOf((first + shown).toFloat() / all * length, a + 0.5f * s)
        }
        if (t.rows > nr) {
            val bx = headW + bodyW + bar / 2
            val (a, b) = thumb(t.firstRow, nr, t.rows, bodyH)
            placed += stroke(listOf(PathOp.Move(bx, top + headH), PathOp.Line(bx, top + headH + bodyH)), thin)
            placed += stroke(listOf(PathOp.Move(bx, top + headH + a), PathOp.Line(bx, top + headH + b)), 0.15f * s)
        }
        if (t.cols > nc) {
            val by = top + headH + bodyH + bar / 2
            val (a, b) = thumb(t.firstCol, nc, t.cols, bodyW)
            placed += stroke(listOf(PathOp.Move(headW, by), PathOp.Line(headW + bodyW, by)), thin)
            placed += stroke(listOf(PathOp.Move(headW + a, by), PathOp.Line(headW + b, by)), 0.15f * s)
        }
        return Group.of(placed, width)
    }

    // Programs.

    /** Program lines stacked, with a vertical bar on the left, centred on the axis. The lines are children [first], [first] + 1, …. */
    private fun program(lines: List<ProgramLine>, s: Float, first: Int = 0): Box {
        val boxes = lines.mapIndexed { k, line -> tag(first + k, programLine(line, s)) }
        val gap = 0.25f * s
        val barGap = 0.4f * s
        val placed = mutableListOf<Placed>()
        var y = 0f
        boxes.forEachIndexed { k, b ->
            val ascent = maxOf(b.ascent, 0.75f * s)
            y += if (k == 0) ascent else ascent + gap
            placed += Placed(b, barGap, y)
            y += maxOf(b.descent, 0.25f * s)
        }
        val shift = -axis(s) - y / 2
        val bar = StrokeBox(0.06f * s, 0f, y, listOf(PathOp.Move(0.03f * s, 0f), PathOp.Line(0.03f * s, y)), 0.06f * s)
        return Group.of(listOf(Placed(bar, 0f, shift)) + placed.map { it.copy(y = it.y + shift) }, barGap + boxes.maxOf { it.width })
    }

    private fun programLine(line: ProgramLine, s: Float): Box = when (line) {
        is Line -> child(0, line.expr, s)
        is LocalDefine -> row(child(0, line.target, s), thick(s), text("←", s), thick(s), child(1, line.value, s))
        is IfThen -> row(body(line.body, s, first = 1), thick(s), keyword("if", s), thick(s), child(0, line.condition, s))
        is Otherwise -> row(body(line.body, s, first = 0), thick(s), keyword("otherwise", s))
        is For -> block(row(keyword("for", s), thick(s), child(0, line.variable, s), thick(s), text("∈", s), thick(s), child(1, line.over, s)), line.body, s, first = 2)
        is While -> block(row(keyword("while", s), thick(s), child(0, line.condition, s)), line.body, s, first = 1)
        is Return -> row(keyword("return", s), thick(s), child(0, line.value, s))
        Break -> keyword("break", s)
        Continue -> keyword("continue", s)
    }

    /** An `if` or `otherwise` body: one line as it is, several as a nested program. */
    private fun body(lines: List<ProgramLine>, s: Float, first: Int): Box =
        if (lines.size == 1 && lines[0] !is For && lines[0] !is While) tag(first, programLine(lines[0], s)) else program(lines, s, first)

    /** A loop: its header, and the body below it, indented, as a nested program. */
    private fun block(header: Box, lines: List<ProgramLine>, s: Float, first: Int): Box {
        val inner = program(lines, s, first)
        val y = header.descent + 0.25f * s + inner.ascent
        return Group.of(listOf(Placed(header, 0f, 0f), Placed(inner, 0.8f * s, y)))
    }
}
