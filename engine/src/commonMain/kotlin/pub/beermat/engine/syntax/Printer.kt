package pub.beermat.engine.syntax

/**
 * Prints the tree as linear syntax, with one canonical spacing.
 *
 * A tree built by hand (or by the editor) may lack parentheses that its structure needs, or
 * mark a multiplication implicit where juxtaposition can't express it. Printing goes through
 * [canonical] first, which fixes both, so the output always parses back to `canonical(tree)`.
 * For trees that are already canonical (every parse result is), `parse(print(t)) == t`.
 */
object Printer {

    fun print(s: Statement): String = ProgramSlot.extract(s)?.let { (head, program) ->
        // A program inside the expression: `program` in its place, its lines below.
        val out = mutableListOf(print(head))
        programLines(program.lines, INDENT, out)
        out.joinToString("\n")
    } ?: when (s) {
        is Define -> if (s.value is Program) {
            val out = mutableListOf(print(s.target) + if (s.global) " ::=" else " :=")
            programLines(s.value.lines, INDENT, out)
            out.joinToString("\n")
        } else buildString {
            append(print(s.target))
            append(if (s.global) " ::= " else " := ")
            append(print(s.value))
            s.result?.let { append(slot(it)) }
        }
        is Evaluate -> print(s.expr) + slot(s.result)
        // A variable named `given` on its own would read as the keyword.
        is ExprStatement -> if (s.expr == Id("given")) "${Id.PLAIN_PREFIX} given" else print(s.expr)
        Given -> "given"
        is Symbolic -> print(s.expr) + s.keywords.joinToString("") { " -> " + print(it) } + " -> ?"
        is Unparsed -> throw IllegalArgumentException("a statement that failed to parse can't be printed")
    }

    fun print(e: Expr): String = StringBuilder().also { write(canonical(e), it) }.toString()

    /** Whether a multiplication by [right] can be written with a space (`q L`): only when [right] starts with a name. */
    fun canJuxtapose(right: Expr): Boolean = ImplicitMultiplication.isValid(wrap(canonical(right), Op.MUL.precedence + 1))

    /** A program's lines, indented by two spaces per level. */
    private fun programLines(lines: List<ProgramLine>, indent: String, out: MutableList<String>) {
        for (line in lines) {
            // A line with a program in it: `program` in its place, and its lines indented below.
            val nested = if (line is IfThen || line is Otherwise) line.takeIf { isInline((it as? IfThen)?.body ?: (it as Otherwise).body) } else line
            nested?.let(ProgramSlot::extract)?.let { (marked, program) ->
                programLines(listOf(marked), indent, out)
                programLines(program.lines, indent + INDENT, out)
                continue
            }
            when (line) {
                is IfThen -> if (isInline(line.body)) {
                    out += indent + simple(line.body[0]) + " if " + print(line.condition)
                } else {
                    out += indent + "if " + print(line.condition)
                    programLines(line.body, indent + INDENT, out)
                }
                is Otherwise -> if (isInline(line.body)) {
                    out += indent + simple(line.body[0]) + " otherwise"
                } else {
                    out += indent + "otherwise"
                    programLines(line.body, indent + INDENT, out)
                }
                is For -> {
                    out += indent + "for " + print(line.variable) + " in " + print(line.over)
                    programLines(line.body, indent + INDENT, out)
                }
                is While -> {
                    out += indent + "while " + print(line.condition)
                    programLines(line.body, indent + INDENT, out)
                }
                else -> out += indent + simple(line)
            }
        }
    }

    private fun isInline(body: List<ProgramLine>) =
        body.size == 1 && body[0].let { it is Line || it is LocalDefine || it is Return || it == Break || it == Continue }

    private fun simple(line: ProgramLine): String = when (line) {
        // A line starting with a variable named like a program word is marked, `\it break`, so
        // it isn't read as a special line. The printed text starts with that very name.
        is Line -> canonical(line.expr).let { if (leadingWord(it) in LINE_WORDS) "${Id.PLAIN_PREFIX} ${print(it)}" else print(it) }
        is LocalDefine -> print(line.target) + " <- " + print(line.value)
        is Return -> "return " + print(line.value)
        Break -> "break"
        Continue -> "continue"
        else -> error("not a simple line: $line")
    }

    /** The plain, unsubscripted name a printed expression starts with, if it starts with one. */
    private fun leadingWord(e: Expr): String? = when (e) {
        is Id -> if (e.subscript == null && e.style == Id.Style.PLAIN) e.name else null
        is Call -> leadingWord(e.fn)
        is Index -> leadingWord(e.target)
        is Apply -> if ((e.args.size == 2 && e.op != Op.ON_ERROR) || e.op in POSTFIX) leadingWord(e.args[0]) else null
        else -> null
    }

    private const val INDENT = "  "

    /** Words that start a special program line. */
    private val LINE_WORDS = setOf("return", "break", "continue", "otherwise", "for", "while")

    private val POSTFIX = setOf(Op.FACTORIAL, Op.TRANSPOSE)

    private fun slot(r: ResultSlot): String = " = ?" + (r.unit?.let { " " + print(it) } ?: "")

    private fun write(e: Expr, out: StringBuilder) {
        when (e) {
            is Num -> out.append(e.text)
            is Id -> out.append(e.text)
            is Str -> {
                out.append('"')
                e.value.forEach {
                    when (it) {
                        '"' -> out.append("\\\"")
                        '\\' -> out.append("\\\\")
                        '\n' -> out.append("\\n")
                        else -> out.append(it)
                    }
                }
                out.append('"')
            }
            Placeholder -> out.append('?')
            is Parens -> {
                out.append('(')
                write(e.inner, out)
                out.append(')')
            }
            is Call -> {
                // `use "file.py"` reads without parentheses.
                if (isUse(e)) {
                    out.append("use ")
                    write(e.args[0], out)
                    return
                }
                write(e.fn, out)
                out.append('(')
                e.args.forEachIndexed { k, arg ->
                    if (k > 0) out.append(", ")
                    write(arg, out)
                }
                out.append(')')
            }
            is Matrix -> {
                out.append('[')
                e.rows.forEachIndexed { r, row ->
                    if (r > 0) out.append("; ")
                    row.forEachIndexed { c, x ->
                        if (c > 0) out.append(", ")
                        write(x, out)
                    }
                }
                out.append(']')
            }
            is Program -> throw IllegalArgumentException("a program can only be printed as the value of a definition")
            is Range -> {
                write(e.first, out)
                e.second?.let { out.append(", "); write(it, out) }
                out.append("..")
                write(e.last, out)
            }
            is Index -> {
                write(e.target, out)
                out.append('[')
                e.indices.forEachIndexed { k, x ->
                    if (k > 0) out.append(", ")
                    write(x, out)
                }
                out.append(']')
            }
            is Apply -> writeApply(e, out)
        }
    }

    private fun writeApply(e: Apply, out: StringBuilder) {
        val a = e.args
        when (e.op) {
            Op.NEG, Op.POS -> {
                out.append(e.op.symbol)
                write(a[0], out)
            }
            Op.NOT -> {
                out.append("not ")
                write(a[0], out)
            }
            Op.FACTORIAL -> {
                write(a[0], out)
                out.append('!')
            }
            Op.TRANSPOSE -> {
                write(a[0], out)
                out.append("^T")
            }
            Op.COLUMN -> {
                write(a[0], out)
                out.append("^<")
                write(a[1], out)
                out.append('>')
            }
            Op.ABS -> {
                out.append('|')
                write(a[0], out)
                out.append('|')
            }
            Op.MEMBER -> {
                write(a[0], out)
                out.append('.')
                write(a[1], out)
            }
            Op.ON_ERROR -> {
                out.append("try ")
                write(a[0], out)
                out.append(" on error ")
                write(a[1], out)
            }
            else -> {
                write(a[0], out)
                out.append(
                    when {
                        e.implicit -> " "
                        e.dot -> "·"
                        e.op == Op.DIV -> e.division.symbol
                        e.op == Op.MUL || e.op == Op.DIV || e.op == Op.POW -> e.op.symbol
                        else -> " ${e.op.symbol} "
                    }
                )
                write(a[1], out)
            }
        }
    }

    /**
     * The tree with [Parens] added wherever the structure needs them, and implicit
     * multiplications that juxtaposition can't express made explicit.
     */
    fun canonical(e: Expr): Expr = when (e) {
        is Num, is Id, is Str, Placeholder -> e
        is Parens -> Parens(canonical(e.inner))
        is Call -> Call(wrap(canonical(e.fn), Op.FACTORIAL.precedence), e.args.map(::canonical))
        is Matrix -> Matrix(e.rows.map { it.map(::canonical) })
        is Range -> Range(canonical(e.first), e.second?.let(::canonical), canonical(e.last))
        is Program -> e
        is Index -> Index(wrap(canonical(e.target), Op.FACTORIAL.precedence), e.indices.map(::canonical))
        is Apply -> canonicalApply(e)
    }

    private fun canonicalApply(e: Apply): Expr {
        val args = e.args.map(::canonical)
        val op = e.op
        val wrapped = when (op) {
            // `try` and `on error` delimit both operands.
            Op.ABS, Op.ON_ERROR -> args
            // The module before the dot binds as tightly as a call's function; the name after it is a name.
            // After a subscript a dot is part of it (`x_a.b`), so a subscripted module is written `(x_a).f`.
            // A number too, since `0x1F.f` would read as hexadecimal digits.
            Op.MEMBER -> listOf(wrap(args[0], CALL_PRECEDENCE).let { if (endsWithSubscript(it) || it is Num) Parens(it) else it }, args[1])
            Op.NEG, Op.POS -> listOf(wrap(args[0], Op.NEG.precedence))
            Op.NOT -> listOf(wrap(args[0], Op.NOT.precedence))
            Op.FACTORIAL, Op.TRANSPOSE -> listOf(wrap(args[0], Op.FACTORIAL.precedence))
            // Inside `^< >` the index is parsed at the additive level, so a comparison needs parentheses.
            Op.COLUMN -> listOf(wrap(args[0], Op.FACTORIAL.precedence), wrap(args[1], Op.ADD.precedence))
            // Right-associative: the base must bind tighter, the exponent may be signed. An exponent
            // starting with a bare `T` would read as a transpose: `x^(T)`.
            Op.POW -> listOf(
                wrap(args[0], Op.FACTORIAL.precedence),
                wrap(args[1], Op.NEG.precedence).let { if (startsWithT(it)) Parens(it) else it },
            )
            // Comparisons: the right operand is parsed at the additive level, so that
            // `a < b < c` is a chain and `a < (b < c)` keeps its parentheses.
            Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE ->
                listOf(wrap(args[0], op.precedence), wrap(args[1], Op.ADD.precedence))
            // Left-associative binary operators.
            else -> listOf(wrap(args[0], op.precedence), wrap(args[1], op.precedence + 1))
        }
        val implicit = e.implicit && ImplicitMultiplication.isValid(wrapped[1])
        return Apply(op, wrapped, implicit, e.dot, e.division)
    }

    private fun startsWithT(e: Expr): Boolean = when (e) {
        is Id -> e == Id("T")
        is Call -> startsWithT(e.fn)
        is Index -> startsWithT(e.target)
        is Apply -> e.op in setOf(Op.POW, Op.FACTORIAL, Op.TRANSPOSE, Op.COLUMN) && startsWithT(e.args[0])
        else -> false
    }

    private fun wrap(e: Expr, minPrecedence: Int): Expr = if (precedence(e) < minPrecedence) Parens(e) else e
}

/** `use "file.py"`: a call of `use` on a string, printed without parentheses. */
internal fun isUse(e: Call): Boolean =
    e.fn == Id("use") && e.args.size == 1 && e.args[0] is Str

/** Whether [e] is written ending in a subscript, so that a `.` after it would read as part of that subscript. */
private fun endsWithSubscript(e: Expr): Boolean = when (e) {
    is Id -> e.subscript != null
    is Apply -> e.op == Op.MEMBER && endsWithSubscript(e.args[1])
    else -> false
}
