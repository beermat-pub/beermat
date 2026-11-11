package pub.beermat.export

import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.Given
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.ResultSlot
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.syntax.Symbolic
import pub.beermat.engine.syntax.Unparsed

/**
 * Reads LaTeX math as a statement (docs/export.md#latex-input): what someone used to LaTeX types,
 * and everything [Latex] writes. The LaTeX becomes the linear syntax, which the parser reads, so
 * precedence and implicit multiplication are the parser's. Braces that only group (`\frac{a+b}{2}`)
 * go through a marker function taken out afterwards, so they add no parentheses of their own; the
 * parentheses the structure needs come from [Printer.canonical].
 *
 * Reading follows how LaTeX draws: letters side by side are a product (`qL` is q·L) unless together
 * they name a unit or a built-in function (`kN`, `max`); `\mathit{width}` and `\mathrm{...}` hold one
 * name. A subscript of letters and digits is part of the name (`M_{max}`, `x_1`); one with an
 * expression in it is an index (`v_{i+1}`, `M_{i,j}`, or `v_{[i]}` for a single name). `x = 5` with a
 * name on the left defines it, `x =` evaluates, and `a + b = 10` is a constraint (`==`).
 */
class LatexInput(private val units: Units = Units.BUILT_IN) {

    class Error(message: String) : Exception(message)

    fun statement(latex: String): Statement {
        val items = Reader(prepare(latex)).all()
        if (isGiven(items)) return Given
        val pieces = convert(items, top = true)
        val text = assemble(pieces)
        val parsed = try {
            Parser.parseStatement(text)
        } catch (e: ParseException) {
            throw Error("This LaTeX doesn't make a statement Beermat can read (${e.message?.substringBefore(" (column")}).")
        }
        return clean(parsed)
    }

    fun expression(latex: String): Expr {
        val text = join(convert(Reader(prepare(latex)).all(), top = false))
        val parsed = try {
            Parser.parseExpression(text)
        } catch (e: ParseException) {
            throw Error("This LaTeX doesn't make an expression Beermat can read (${e.message?.substringBefore(" (column")}).")
        }
        return Printer.canonical(ungroup(parsed))
    }

    // ---- From LaTeX text to items -------------------------------------------------------------

    private sealed interface Item
    private data class Cmd(val name: String) : Item
    private data class Ch(val c: Char) : Item
    private data class Group(val items: List<Item>) : Item
    private data class Env(val name: String, val rows: List<List<List<Item>>>) : Item
    private data object Space : Item

    /** Takes off `$…$`, `$$…$$`, `\(…\)`, `\[…\]`, and turns `1.2 \times 10^{-3}` back into a number. */
    private fun prepare(latex: String): String {
        var s = latex.trim()
        for ((open, close) in listOf("$$" to "$$", "$" to "$", "\\(" to "\\)", "\\[" to "\\]")) {
            if (s.length >= open.length + close.length && s.startsWith(open) && s.endsWith(close)) {
                s = s.substring(open.length, s.length - close.length).trim()
                break
            }
        }
        if (s.isEmpty()) throw Error("There is no LaTeX to read.")
        return NUMBER_TIMES_TEN.replace(s) { m ->
            val (mantissa, exponent, imaginary) = m.destructured
            "${mantissa}e$exponent$imaginary"
        }
    }

    private class Reader(private val s: String) {
        private var k = 0

        fun all(): List<Item> = items(null).also { if (k < s.length) throw Error("A '}' has no '{' to close.") }

        private fun items(end: String?): List<Item> {
            val out = mutableListOf<Item>()
            while (k < s.length) {
                if (end != null && s.startsWith(end, k)) {
                    k += end.length
                    return out
                }
                val c = s[k]
                when {
                    c == '}' -> throw Error("A '}' has no '{' to close.")
                    c == '{' -> { k++; out += Group(items("}")) }
                    c == '%' -> while (k < s.length && s[k] != '\n') k++
                    c.isWhitespace() -> { while (k < s.length && s[k].isWhitespace()) k++; if (out.lastOrNull() != Space) out += Space }
                    c == '\\' -> {
                        k++
                        if (k >= s.length) throw Error("A '\\' ends the LaTeX.")
                        if (s[k].isLetter()) {
                            val start = k
                            while (k < s.length && s[k].isLetter()) k++
                            val name = s.substring(start, k)
                            if (name == "begin") out += environment() else out += Cmd(name)
                        } else {
                            out += Cmd(s[k].toString())
                            k++
                        }
                    }
                    else -> { out += Ch(c); k++ }
                }
            }
            if (end == "}") throw Error("A '{' isn't closed.")
            if (end != null) throw Error("'$end' is missing.")
            return out
        }

        private fun environment(): Env {
            while (k < s.length && s[k].isWhitespace()) k++
            if (k >= s.length || s[k] != '{') throw Error("\\begin needs the name of its environment.")
            val close = s.indexOf('}', k)
            if (close < 0) throw Error("A '{' isn't closed.")
            val name = s.substring(k + 1, close)
            k = close + 1
            val body = items("\\end{$name}")
            val rows = mutableListOf<MutableList<List<Item>>>(mutableListOf())
            var cell = mutableListOf<Item>()
            for (item in body) when (item) {
                Ch('&') -> { rows.last() += cell; cell = mutableListOf() }
                Cmd("\\") -> { rows.last() += cell; cell = mutableListOf(); rows += mutableListOf<List<Item>>() }
                else -> cell += item
            }
            rows.last() += cell
            // A last `\\` before \end leaves an empty row.
            if (rows.size > 1 && rows.last().all { c -> c.all { it == Space } }) rows.removeAt(rows.lastIndex)
            return Env(name, rows)
        }
    }

    // ---- From items to the linear syntax ------------------------------------------------------

    private enum class Kind { OPERAND, INFIX, PREFIX, POSTFIX, SIGN, EQUALS, DEFINE, GLOBAL, ARROW }

    /** A piece of linear text; a name keeps its [id], so a subscript or prime can still be added to it. */
    private data class Piece(val text: String, val kind: Kind, val id: Id? = null, val callable: Boolean = false)

    private fun nameOf(id: Id, callable: Boolean = true) = Piece(Printer.print(id), Kind.OPERAND, id, callable)

    private fun operand(text: String) = Piece(text, Kind.OPERAND)

    /** Grouped: the parser sees one operand, and [ungroup] takes the marker out again. */
    private fun grouped(text: String) = operand("$GROUP($text)")

    private fun convert(items: List<Item>, top: Boolean): List<Piece> {
        val out = mutableListOf<Piece>()
        var k = 0
        fun next(): Item? {
            while (k < items.size && items[k] == Space) k++
            return items.getOrNull(k)?.also { k++ }
        }
        fun peek(): Item? {
            var j = k
            while (j < items.size && items[j] == Space) j++
            return items.getOrNull(j)
        }
        fun arg(what: String): List<Item> = when (val a = next() ?: throw Error("$what needs an argument.")) {
            is Group -> a.items
            else -> listOf(a)
        }
        /** The rest of a product (the body of a sum, a derivative or a function without parentheses): up to the next + or −, relation or comma. */
        fun term(): List<Item> {
            val start = k
            while (k < items.size) {
                val it = items[k]
                val first = items.subList(start, k).all { x -> x == Space }
                if (!first && (it is Ch && it.c in "+-=<>,;" || it is Cmd && it.name in RELATIONS)) break
                k++
            }
            return items.subList(start, k)
        }
        fun lastName(): Piece? = out.lastOrNull()?.takeIf { it.id != null }

        while (true) {
            val item = next() ?: break
            when (item) {
                Space -> {}
                is Group -> if (item.items.isNotEmpty()) out += grouped(join(convert(item.items, false)))
                is Env -> out += environment(item)
                is Ch -> when (val c = item.c) {
                    in '0'..'9', '.' -> {
                        // `bt.f`: a function of a Python module.
                        val member = (items.getOrNull(k) as? Ch)?.c
                        if (c == '.' && member != null && member.isLetter() && out.lastOrNull()?.kind == Kind.OPERAND) {
                            val b = StringBuilder()
                            while (k < items.size && (items[k] as? Ch)?.c?.let(Id.Companion::isNameChar) == true) {
                                b.append((items[k] as Ch).c)
                                k++
                            }
                            out[out.lastIndex] = Piece(out.last().text + "." + b, Kind.OPERAND, callable = true)
                            continue
                        }
                        if (c == '.' && items.getOrNull(k) == Ch('.')) {
                            k++
                            out += Piece("..", Kind.INFIX)
                            continue
                        }
                        val b = StringBuilder().append(c)
                        while (k < items.size) {
                            val n = items[k] as? Ch ?: break
                            if (n.c.isDigit() || (n.c == '.' && items.getOrNull(k + 1) != Ch('.'))) b.append(n.c)
                            else if (n.c in "eE" && exponentAt(items, k + 1)) {
                                b.append(n.c)
                                val sign = items[k + 1] as Ch
                                if (sign.c in "+-") { b.append(sign.c); k++ }
                            } else break
                            k++
                        }
                        // `2i`: the imaginary unit, as the linear syntax writes it.
                        val after = items.getOrNull(k) as? Ch
                        if (after != null && after.c in "ij" && (items.getOrNull(k + 1) as? Ch)?.c?.isLetterOrDigit() != true) {
                            b.append(after.c)
                            k++
                        }
                        out += operand(b.toString())
                    }
                    '+', '-' -> out += Piece(c.toString(), Kind.SIGN)
                    '*' -> out += Piece("*", Kind.INFIX)
                    '/' -> out += Piece("∕", Kind.INFIX)
                    '<', '>' -> out += Piece(if (items.getOrNull(k) == Ch('=')) { k++; "$c=" } else c.toString(), Kind.INFIX)
                    '=' -> out += if (top) Piece("=", Kind.EQUALS) else Piece("==", Kind.INFIX)
                    ':' -> if (items.getOrNull(k) == Ch('=')) {
                        k++
                        out += Piece(":=", Kind.DEFINE)
                    } else throw Error("':' is read only in ':='.")
                    ',' -> out += Piece(",", Kind.INFIX)
                    ';' -> out += Piece(";", Kind.INFIX)
                    '!' -> out += Piece("!", Kind.POSTFIX)
                    '\'' -> {
                        val last = lastName() ?: throw Error("A prime (') needs a name before it.")
                        out[out.lastIndex] = nameOf(last.id!!.copy(name = last.id.name + "'"))
                    }
                    '(', '[' -> {
                        val close = if (c == '(') ')' else ']'
                        val inner = matching(items, k, Ch(c), Ch(close), "'$c' isn't closed.")
                        k += inner.size + 1
                        parenthesised(out, inner, call = true)
                    }
                    ')', ']' -> throw Error("A '${c}' has nothing to close.")
                    '|' -> {
                        val inner = matching(items, k, null, Ch('|'), "'|' isn't closed.")
                        k += inner.size + 1
                        out += operand("|" + join(convert(inner, false)) + "|")
                    }
                    '^' -> superscript(out, arg("^"))
                    '_' -> subscript(out, arg("_"))
                    '&' -> throw Error("'&' belongs inside a matrix (\\begin{bmatrix} … \\end{bmatrix}).")
                    '"' -> {
                        val end = items.subList(k, items.size).indexOf(Ch('"'))
                        if (end < 0) throw Error("A string's '\"' isn't closed.")
                        out += operand(Printer.print(Str(items.subList(k, k + end).joinToString("") { (it as? Ch)?.c?.toString() ?: " " })))
                        k += end + 1
                    }
                    else -> if (Id.isNameStart(c)) {
                        val b = StringBuilder().append(c)
                        while (k < items.size) {
                            val n = items[k] as? Ch ?: break
                            if (!(Id.isNameChar(n.c))) break
                            b.append(n.c)
                            k++
                        }
                        letters(b.toString()).forEach { out += it }
                    } else throw Error("'$c' isn't read in LaTeX math.")
                }
                is Cmd -> command(item.name, out, ::next, ::peek, ::arg, ::term, items, { k }, { k = it }, top)
            }
        }
        return out
    }

    /** Whether `e` at [k] starts an exponent: a digit, or a sign and a digit. */
    private fun exponentAt(items: List<Item>, k: Int): Boolean {
        val a = (items.getOrNull(k) as? Ch)?.c ?: return false
        if (a.isDigit()) return true
        return a in "+-" && (items.getOrNull(k + 1) as? Ch)?.c?.isDigit() == true
    }

    /** The items from [start] up to the [close] that matches, counting nested [open]s. */
    private fun matching(items: List<Item>, start: Int, open: Item?, close: Item, missing: String): List<Item> {
        var depth = 0
        for (j in start until items.size) {
            val it = items[j]
            if (open != null && it == open) depth++
            else if (it == close) {
                if (depth == 0) return items.subList(start, j)
                depth--
            }
        }
        throw Error(missing)
    }

    /** Parentheses after a name are a call, `f(x)`; elsewhere, parentheses. */
    private fun parenthesised(out: MutableList<Piece>, inner: List<Item>, call: Boolean) {
        val text = join(convert(inner, false))
        val last = out.lastOrNull()
        if (call && last != null && last.kind == Kind.OPERAND && last.callable) {
            out[out.lastIndex] = Piece(last.text + "(" + text + ")", Kind.OPERAND)
        } else {
            out += operand("($text)")
        }
    }

    /** A run of letters: one name when it names a unit or a built-in, or is one letter (and digits); otherwise one name per letter, as LaTeX draws them. */
    private fun letters(run: String): List<Piece> {
        if (known(run) || run.count { it.isLetter() } <= 1) return listOf(nameOf(Id(run)))
        val names = mutableListOf<String>()
        for (c in run) if (c.isLetter() || names.isEmpty()) names += c.toString() else names[names.lastIndex] += c
        return names.map { nameOf(Id(it)) }
    }

    private fun known(n: String): Boolean {
        val id = Id(n)
        return units.isUnit(id) || id in Builtins.functions || id in Builtins.constants || id in Builtins.units || n in SPECIAL_FUNCTIONS
    }

    private fun superscript(out: MutableList<Piece>, arg: List<Item>) {
        val solid = arg.filter { it != Space }
        val only = solid.singleOrNull()
        when {
            out.isEmpty() -> throw Error("'^' needs something before it.")
            // A transpose, `M^T`.
            only == Ch('T') || only == Cmd("top") || (solid.size == 2 && solid[0] == Cmd("mathsf") && (solid[1] as? Group)?.items == listOf(Ch('T'))) ->
                out += Piece("^T", Kind.POSTFIX)
            // A column, `M^{\langle j \rangle}`.
            solid.firstOrNull() == Cmd("langle") && solid.lastOrNull() == Cmd("rangle") ->
                out += Piece("^<" + join(convert(solid.subList(1, solid.size - 1), false)) + ">", Kind.POSTFIX)
            // Degrees, `20^{\circ}C`: the ° starts the name after it.
            only == Cmd("circ") -> out += Piece("°", Kind.POSTFIX)
            else -> {
                out += Piece("^", Kind.INFIX)
                out += grouped(join(convert(arg, false)))
            }
        }
    }

    private fun subscript(out: MutableList<Piece>, arg: List<Item>) {
        val last = out.lastOrNull() ?: throw Error("'_' needs something before it.")
        val solid = arg.filter { it != Space }
        // `v_{[i]}`: an index, even of one name.
        if (solid.size >= 2 && solid.first() == Ch('[') && solid.last() == Ch(']')) {
            out[out.lastIndex] = Piece(last.text + "[" + join(convert(solid.subList(1, solid.size - 1), false)) + "]", Kind.OPERAND)
            return
        }
        val literal = literalText(solid)
        if (literal != null && last.id != null && last.id.subscript == null) {
            out[out.lastIndex] = nameOf(last.id.copy(subscript = literal))
        } else {
            out[out.lastIndex] = Piece(last.text + "[" + join(convert(arg, false)) + "]", Kind.OPERAND)
        }
    }

    /** A subscript's text, when it is one: `\mathrm{max}`, `max`, `1`, `x.1`; null for an expression. */
    private fun literalText(items: List<Item>): String? {
        if (items.size == 2 && items[0] is Cmd && (items[0] as Cmd).name in UPRIGHT && items[1] is Group) {
            return flatText((items[1] as Group).items)
        }
        val text = flatText(items) ?: return null
        return text.takeIf { it.isNotEmpty() && it.all { c -> Id.isNameChar(c) || c == '.' } }
    }

    /** The plain text of [items], when they are only characters, Greek letters and symbols; null otherwise. */
    private fun flatText(items: List<Item>): String? {
        val b = StringBuilder()
        var k = 0
        while (k < items.size) {
            when (val it = items[k]) {
                Space -> {}
                is Ch -> b.append(it.c)
                is Group -> b.append(flatText(it.items) ?: return null)
                is Cmd -> when {
                    it.name in GREEK -> b.append(GREEK.getValue(it.name))
                    it.name in SYMBOL_CHARS -> b.append(SYMBOL_CHARS.getValue(it.name))
                    it.name == " " -> b.append(' ')
                    it.name == "textbackslash" -> b.append('\\')
                    it.name in setOf(",", ";", ":", "!") -> {}
                    else -> return null
                }
                is Env -> return null
            }
            k++
        }
        return b.toString()
    }

    private fun environment(env: Env): Piece {
        if (env.name == "array" || env.name == "cases") throw Error("Programs and cases can't be read from LaTeX yet.")
        if (env.name !in MATRICES) throw Error("The environment '${env.name}' isn't read; a matrix is \\begin{bmatrix} … \\end{bmatrix}.")
        val rows = env.rows.map { row -> row.map { cell -> join(convert(cell, false)).ifEmpty { "?" } } }
        if (rows.any { it.size != rows[0].size }) throw Error("Every row of a matrix needs the same number of columns.")
        val text = "[" + rows.joinToString("; ") { it.joinToString(", ") } + "]"
        return if (env.name == "vmatrix") operand("|$text|") else operand(text)
    }

    private fun command(
        name: String,
        out: MutableList<Piece>,
        next: () -> Item?,
        peek: () -> Item?,
        arg: (String) -> List<Item>,
        term: () -> List<Item>,
        items: List<Item>,
        at: () -> Int,
        moveTo: (Int) -> Unit,
        top: Boolean,
    ) {
        when (name) {
            in GREEK, in SYMBOL_LETTERS -> {
                val c = GREEK[name]?.toString() ?: SYMBOL_LETTERS.getValue(name)
                // `\alpha_1` and `\Delta x` stay separate names, as LaTeX draws them.
                out += nameOf(Id(c))
            }
            "infty" -> out += nameOf(Id("∞"), callable = false)
            "cdot", "times", "ast" -> out += Piece("*", Kind.INFIX)
            "div" -> out += Piece("÷", Kind.INFIX)
            "le", "leq", "leqslant" -> out += Piece("<=", Kind.INFIX)
            "ge", "geq", "geqslant" -> out += Piece(">=", Kind.INFIX)
            "ne", "neq" -> out += Piece("!=", Kind.INFIX)
            "lt" -> out += Piece("<", Kind.INFIX)
            "gt" -> out += Piece(">", Kind.INFIX)
            "land", "wedge" -> out += Piece("and", Kind.INFIX)
            "lor", "vee" -> out += Piece("or", Kind.INFIX)
            "oplus" -> out += Piece("xor", Kind.INFIX)
            "lnot", "neg" -> out += Piece("not", Kind.PREFIX)
            "coloneqq", "coloneq" -> out += Piece(":=", Kind.DEFINE)
            "equiv" -> out += if (top) Piece("::=", Kind.GLOBAL) else throw Error("\\equiv is read only as a global definition.")
            "rightarrow", "to" -> out += Piece("->", Kind.ARROW)
            "blacksquare" -> out += operand("?")
            "ldots", "dots", "cdots" -> out += Piece("..", Kind.INFIX)
            "pm", "mp" -> throw Error("± can't be calculated; write the two cases separately.")
            "%" -> out += operand("%")
            // Spacing, sizes and styles that draw differently but mean nothing more.
            ",", ";", ":", " ", "!", "quad", "qquad", "displaystyle", "textstyle", "limits", "nolimits",
            "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "biggl", "biggr" -> {}
            "frac", "dfrac", "tfrac", "cfrac" -> {
                val top = arg("\\frac")
                val bottom = arg("\\frac")
                derivative(top, bottom)?.let { (variable, order) ->
                    val body = join(convert(term(), false))
                    if (body.isEmpty()) throw Error("A derivative needs something after it to differentiate.")
                    out += grouped("diff($body, $variable" + (order?.let { ", $it" } ?: "") + ")")
                    return
                }
                out += grouped("$GROUP(${join(convert(top, false))})/$GROUP(${join(convert(bottom, false))})")
            }
            "sqrt" -> {
                var index: List<Item>? = null
                if (peek() == Ch('[')) {
                    next()
                    val start = at()
                    val inner = matching(items, start, Ch('['), Ch(']'), "\\sqrt[ isn't closed.")
                    moveTo(start + inner.size + 1)
                    index = inner
                }
                val body = join(convert(arg("\\sqrt"), false))
                out += if (index == null) operand("sqrt($body)") else operand("nthroot($body, ${join(convert(index, false))})")
            }
            "overrightarrow", "vec" -> out += operand("vec(${join(convert(arg("\\$name"), false))})")
            "left" -> {
                val open = next() ?: throw Error("\\left needs a bracket.")
                val start = at()
                var depth = 0
                var end = -1
                for (j in start until items.size) {
                    if (items[j] == Cmd("left")) depth++
                    if (items[j] == Cmd("right")) {
                        if (depth == 0) { end = j; break }
                        depth--
                    }
                }
                if (end < 0) throw Error("\\left has no \\right.")
                val inner = items.subList(start, end)
                moveTo(end + 2)
                when (open) {
                    Ch('('), Ch('['), Cmd("{"), Cmd("lbrack") -> parenthesised(out, inner, call = open == Ch('('))
                    Ch('|'), Cmd("|"), Cmd("lvert"), Cmd("vert") -> {
                        if (inner.any { it is Env && it.name == "array" }) throw Error("Programs can't be read from LaTeX yet.")
                        out += operand("|" + join(convert(inner, false)) + "|")
                    }
                    Ch('.') -> out += grouped(join(convert(inner, false)))
                    else -> throw Error("\\left${(open as? Ch)?.c ?: ""} isn't read.")
                }
            }
            "right" -> throw Error("\\right has no \\left.")
            "lvert", "vert", "|" -> {
                val k = at()
                val inner = matching(items, k, null, Cmd(name), "'|' isn't closed.")
                moveTo(k + inner.size + 1)
                out += operand("|" + join(convert(inner, false)) + "|")
            }
            "{", "lbrack" -> {
                val close = if (name == "{") Cmd("}") else Cmd("rbrack")
                val k = at()
                val inner = matching(items, k, Cmd(name), close, "A bracket isn't closed.")
                moveTo(k + inner.size + 1)
                parenthesised(out, inner, call = false)
            }
            "sum", "prod" -> {
                var lower: List<Item>? = null
                var upper: List<Item>? = null
                repeat(2) {
                    when (peek()) {
                        Ch('_') -> { next(); lower = arg("_") }
                        Ch('^') -> { next(); upper = arg("^") }
                        else -> {}
                    }
                }
                val body = join(convert(term(), false))
                if (body.isEmpty()) throw Error("\\$name needs something after it to add up.")
                val low = lower?.filter { it != Space }
                val eq = low?.indexOf(Ch('=')) ?: -1
                out += grouped(
                    when {
                        low == null -> "$name($body)"
                        eq < 0 -> "$name($body, ${join(convert(low, false))})"
                        upper == null -> throw Error("\\$name from a value needs its last value: \\${name}_{i=1}^{n}.")
                        else -> "$name($body, ${join(convert(low.subList(0, eq), false))}, ${join(convert(low.subList(eq + 1, low.size), false))}, ${join(convert(upper!!, false))})"
                    },
                )
            }
            "int" -> {
                var lower: List<Item>? = null
                var upper: List<Item>? = null
                repeat(2) {
                    when (peek()) {
                        Ch('_') -> { next(); lower = arg("_") }
                        Ch('^') -> { next(); upper = arg("^") }
                        else -> {}
                    }
                }
                val start = at()
                var d = -1
                for (j in start until items.size) {
                    if (isD(items[j]) && j + 1 < items.size && variableAt(items, j + 1) != null) { d = j; break }
                }
                if (d < 0) throw Error("An integral needs its dx at the end.")
                val (variable, after) = variableAt(items, d + 1)!!
                val body = join(convert(items.subList(start, d), false))
                moveTo(after)
                if (body.isEmpty()) throw Error("An integral needs something to integrate.")
                out += grouped(
                    if (lower == null && upper == null) "int($body, $variable)"
                    else "int($body, $variable, ${join(convert(lower ?: throw Error("The integral needs its lower limit."), false))}, ${join(convert(upper ?: throw Error("The integral needs its upper limit."), false))})",
                )
            }
            in UPRIGHT, "mathit", "mathbf", "boldsymbol", "textit", "textbf", "mathsf" -> {
                val content = arg("\\$name")
                styled(name, content, out)
            }
            else -> if (name in FUNCTION_COMMANDS) {
                out += nameOf(Id(name))
                // `\sin x`: without parentheses, the function takes the next factor.
                val after = peek()
                val bare = after != null && after != Ch('(') && after != Cmd("left") && !(after is Ch && after.c in "^+-*/=<>,;)]|!")
                if (bare) out[out.lastIndex] = operand("$name(${join(convert(listOf(next()!!), false))})")
            } else throw Error("\\$name isn't read.")
        }
    }

    /** `\frac{d}{dx}`, `\frac{d^{2}}{dx^{2}}`: the variable and the order (null for the first). */
    private fun derivative(top: List<Item>, bottom: List<Item>): Pair<String, String?>? {
        val t = top.filter { it != Space }
        val b = bottom.filter { it != Space }
        if (t.isEmpty() || !isD(t[0]) || b.isEmpty() || !isD(b[0])) return null
        val order = when {
            t.size == 1 -> null
            t.size == 3 && t[1] == Ch('^') -> flatText(listOf(t[2]))
            else -> return null
        }
        val (variable, end) = variableAt(b, 1) ?: return null
        val rest = b.subList(end, b.size)
        if (order == null && rest.isNotEmpty()) return null
        if (order != null && !(rest.size == 2 && rest[0] == Ch('^') && flatText(listOf(rest[1])) == order)) return null
        return variable to order
    }

    private fun isD(item: Item) = item == Ch('d') || (item is Cmd && item.name == "partial") ||
        (item is Group && item.items.size == 2 && item.items[0] == Cmd("mathrm") && (item.items[1] as? Group)?.items == listOf(Ch('d')))

    /** A variable at [k] (a letter, or a Greek one, with a subscript), as linear text, and where it ends. */
    private fun variableAt(items: List<Item>, k: Int): Pair<String, Int>? {
        var j = k
        while (j < items.size && (items[j] == Space || items[j] == Cmd(","))) j++
        val first = items.getOrNull(j) ?: return null
        val letter = when (first) {
            is Ch -> first.c.takeIf { it.isLetter() }?.toString()
            is Cmd -> GREEK[first.name]?.toString()
            else -> null
        } ?: return null
        j++
        var id = Id(letter)
        if (items.getOrNull(j) == Ch('_')) {
            val sub = items.getOrNull(j + 1)?.let { s -> literalText(if (s is Group) s.items else listOf(s)) } ?: return null
            id = id.copy(subscript = sub)
            j += 2
        }
        return Printer.print(id) to j
    }

    /** `\mathrm{kN}`, `\mathit{width}`, `\mathbf{F}`, `\text{“s”}`, and the keywords [Latex] writes in bold. */
    private fun styled(command: String, content: List<Item>, out: MutableList<Piece>) {
        val text = flatText(content)
        val bold = command == "mathbf" || command == "boldsymbol" || command == "textbf"
        if (text != null) {
            val words = text.trim()
            // After an arrow, a symbolic keyword, written as the linear syntax writes it.
            if (bold && out.lastOrNull()?.kind == Kind.ARROW) {
                out += operand(words)
                return
            }
            when {
                bold && words == "try" -> { out += Piece("try", Kind.PREFIX); return }
                bold && words == "on error" -> { out += Piece("on error", Kind.INFIX); return }
                command == "text" && words.length >= 2 && words.first() in "“\"" && words.last() in "”\"" -> {
                    out += operand(Printer.print(Str(words.substring(1, words.length - 1))))
                    return
                }
            }
            if (words.isNotEmpty() && words.none { it.isWhitespace() }) {
                val style = when {
                    bold -> Id.Style.BOLD
                    command in UPRIGHT && !known(words) -> Id.Style.UPRIGHT
                    else -> Id.Style.PLAIN
                }
                out += nameOf(Id(words, style = style))
                return
            }
        }
        // A unit made of several, `\mathrm{kN \cdot m}`: each name in it whole.
        if (command in UPRIGHT) {
            out += grouped(join(wholeNames(content) ?: convert(content, false)))
            return
        }
        throw Error("\\$command{…} holds something that isn't a name.")
    }

    /** The pieces of a unit expression with each run of letters one name: `kN`, not k·N. */
    private fun wholeNames(content: List<Item>): List<Piece>? {
        val out = mutableListOf<Piece>()
        var k = 0
        while (k < content.size) {
            val it = content[k]
            if (it is Ch && Id.isNameStart(it.c)) {
                val b = StringBuilder()
                while (k < content.size && (content[k] as? Ch)?.c?.let(Id.Companion::isNameChar) == true) {
                    b.append((content[k] as Ch).c)
                    k++
                }
                val word = b.toString()
                out += nameOf(Id(word, style = if (known(word)) Id.Style.PLAIN else Id.Style.UPRIGHT), callable = false)
                continue
            }
            val rest = convert(listOf(it), false)
            if (it is Ch && it.c == '^' ) {
                // A power of a unit: `m^{2}`.
                val power = content.getOrNull(k + 1) ?: return null
                out += Piece("^", Kind.INFIX)
                out += grouped(join(convert(if (power is Group) power.items else listOf(power), false)))
                k += 2
                continue
            }
            out += rest
            k++
        }
        return out
    }

    // ---- Joining pieces, and the statement ----------------------------------------------------

    /** The pieces as text, with a multiplication where LaTeX puts two operands side by side. */
    private fun join(pieces: List<Piece>): String {
        val b = StringBuilder()
        var operandBefore = false
        var degree = false
        for (p in pieces) {
            when (p.kind) {
                Kind.OPERAND -> {
                    var text = p.text
                    if (degree) {
                        // `20^{\circ}C`: the degree sign starts the unit's name.
                        val id = p.id
                        if (id != null) text = Printer.print(id.copy(name = "°" + id.name)) else throw Error("'°' needs a unit after it, as in °C.")
                        degree = false
                        if (operandBefore) b.append(' ')
                        b.append(text)
                        operandBefore = true
                        continue
                    }
                    if (operandBefore) b.append(if (startsWithName(text)) " " else " * ")
                    b.append(text)
                    operandBefore = true
                }
                Kind.POSTFIX -> if (p.text == "°") {
                    degree = true
                } else {
                    b.append(p.text)
                    operandBefore = true
                }
                Kind.PREFIX -> {
                    if (operandBefore) b.append(" * ")
                    b.append(p.text).append(' ')
                    operandBefore = false
                }
                Kind.SIGN -> {
                    b.append(if (operandBefore) " ${p.text} " else p.text)
                    operandBefore = false
                }
                Kind.INFIX, Kind.EQUALS, Kind.DEFINE, Kind.GLOBAL, Kind.ARROW -> {
                    b.append(if (p.text == "," || p.text == ";") "${p.text} " else " ${p.text} ")
                    operandBefore = false
                }
            }
        }
        if (degree) b.append(if (operandBefore) " deg" else "deg")
        return b.toString().replace(Regex(" {2,}"), " ").trim()
    }

    private fun startsWithName(text: String) = text.firstOrNull()?.let { Id.isNameStart(it) || it == '`' || it == '\\' } == true

    /** The statement's linear text: a definition, an evaluation, a constraint, a symbolic evaluation, or an expression. */
    private fun assemble(pieces: List<Piece>): String {
        val define = pieces.indexOfFirst { it.kind == Kind.DEFINE || it.kind == Kind.GLOBAL }
        if (define >= 0) {
            val head = join(pieces.subList(0, define))
            val rest = pieces.subList(define + 1, pieces.size)
            val eq = rest.indexOfFirst { it.kind == Kind.EQUALS }
            val value = join(if (eq < 0) rest else rest.subList(0, eq)).ifEmpty { "?" }
            // `= ?` after a definition shows its value too; a number there is a result written down, left out.
            val shown = if (eq >= 0) join(rest.subList(eq + 1, rest.size)).takeIf { it.isEmpty() || it.startsWith("?") } else null
            return "$head ${pieces[define].text} $value" + (shown?.let { " = " + it.ifEmpty { "?" } } ?: "")
        }
        val arrow = pieces.indexOfFirst { it.kind == Kind.ARROW }
        if (arrow >= 0) {
            val parts = mutableListOf<List<Piece>>()
            var start = 0
            pieces.forEachIndexed { k, p -> if (p.kind == Kind.ARROW) { parts += pieces.subList(start, k); start = k + 1 } }
            parts += pieces.subList(start, pieces.size)
            val texts = parts.map(::join).toMutableList()
            if (texts.last().isEmpty() || texts.last() == "?") texts[texts.lastIndex] = "?" else texts += "?"
            return texts.joinToString(" -> ")
        }
        val eq = pieces.indexOfFirst { it.kind == Kind.EQUALS }
        if (eq < 0) return join(pieces)
        val left = join(pieces.subList(0, eq))
        val after = pieces.subList(eq + 1, pieces.size)
        val second = after.indexOfFirst { it.kind == Kind.EQUALS }
        val right = join(if (second < 0) after else after.subList(0, second))
        if (right.isEmpty() || right.startsWith("?")) return "$left = " + right.ifEmpty { "?" }
        // A name (or a function, or an element) on the left: its definition, as `x = 5` means in LaTeX.
        val target = runCatching { ungroup(Parser.parseExpression(left)) }.getOrNull()
        if (target != null && target != Placeholder && Define.isDefinitionTarget(target)) {
            val shown = if (second >= 0) join(after.subList(second + 1, after.size)).takeIf { it.isEmpty() || it.startsWith("?") } else null
            return "$left := $right" + (shown?.let { " = " + it.ifEmpty { "?" } } ?: "")
        }
        return "$left == $right" + (if (second >= 0) " == " + join(after.subList(second + 1, after.size)) else "")
    }

    private fun isGiven(items: List<Item>): Boolean {
        val solid = items.filter { it != Space }
        return solid.size == 2 && solid[0] == Cmd("textbf") && (solid[1] as? Group)?.items?.let(::flatText)?.trim() == "Given"
    }

    // ---- Taking the grouping marker out -------------------------------------------------------

    private fun clean(st: Statement): Statement = when (st) {
        is Define -> {
            val target = Printer.canonical(ungroup(st.target))
            if (!Define.isDefinitionTarget(target)) throw Error("${Printer.print(target)} can't be defined.")
            Define(target, Printer.canonical(ungroup(st.value)), st.global, st.result?.let { r -> ResultSlot(r.unit?.let { Printer.canonical(ungroup(it)) }) })
        }
        is Evaluate -> Evaluate(Printer.canonical(ungroup(st.expr)), ResultSlot(st.result.unit?.let { Printer.canonical(ungroup(it)) }))
        is ExprStatement -> ExprStatement(Printer.canonical(ungroup(st.expr)))
        is Symbolic -> Symbolic(Printer.canonical(ungroup(st.expr)), st.keywords.map { Printer.canonical(ungroup(it)) })
        Given -> Given
        is Unparsed -> throw Error(st.message)
    }

    private fun ungroup(e: Expr): Expr = when (e) {
        is Num, is Id, is Str, Placeholder, is Program -> e
        is Parens -> Parens(ungroup(e.inner))
        is Call -> if (e.fn == Id(GROUP) && e.args.size == 1) ungroup(e.args[0]) else Call(ungroup(e.fn), e.args.map(::ungroup))
        is Matrix -> Matrix(e.rows.map { r -> r.map(::ungroup) })
        is Range -> Range(ungroup(e.first), e.second?.let(::ungroup), ungroup(e.last))
        is Index -> Index(ungroup(e.target), e.indices.map(::ungroup))
        is Apply -> Apply(e.op, e.args.map(::ungroup), e.implicit, e.dot, e.division)
    }

    companion object {
        /** The grouping marker: a name no sheet uses. */
        private const val GROUP = "ʬʬʬ"

        private val NUMBER_TIMES_TEN = Regex("""([0-9]+(?:\.[0-9]*)?)\s*\\times\s*10\^\{?(-?[0-9]+)\}?([ij]?)""")

        private val GREEK: Map<String, Char> = Latex.GREEK.entries.filter { it.value.startsWith("\\") }.associate { it.value.drop(1) to it.key } +
            mapOf("varepsilon" to 'ε', "vartheta" to 'θ', "varphi" to 'φ', "varrho" to 'ρ', "varsigma" to 'ς', "varpi" to 'π')

        /** Commands that are letters in a name. */
        private val SYMBOL_LETTERS = mapOf("nabla" to "∇", "partial" to "∂")

        /** Commands for characters a name may hold. */
        private val SYMBOL_CHARS: Map<String, Char> = mapOf(
            "nabla" to '∇', "partial" to '∂', "%" to '%', "#" to '#', "&" to '&', "$" to '$', "_" to '_',
            "{" to '{', "}" to '}', "sim" to '~', "backslash" to '\\', "circ" to '°', "ast" to '*',
        )

        private val UPRIGHT = setOf("mathrm", "text", "textrm", "operatorname", "rm")

        private val MATRICES = setOf("bmatrix", "pmatrix", "matrix", "Bmatrix", "vmatrix", "smallmatrix")

        private val RELATIONS = setOf("le", "leq", "ge", "geq", "ne", "neq", "lt", "gt", "coloneqq", "equiv", "rightarrow", "to", "land", "lor")

        /** Functions LaTeX has a command for. */
        private val FUNCTION_COMMANDS = setOf(
            "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "coth",
            "exp", "ln", "log", "max", "min", "det", "arg", "lg",
        )

        /** Built-ins written with notation of their own, still names a run of letters can mean. */
        private val SPECIAL_FUNCTIONS = setOf("sum", "prod", "int", "diff", "vec", "sqrt", "nthroot")
    }
}
