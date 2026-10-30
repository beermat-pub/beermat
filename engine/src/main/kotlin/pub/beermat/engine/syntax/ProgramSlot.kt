package pub.beermat.engine.syntax

/**
 * A program inside an expression (docs/syntax.md#programs): in linear syntax the word `program`
 * stands where it goes, and its lines are indented below the statement, as in
 * `M := program*kN*m`. One per statement. A definition whose whole value is a program keeps
 * the plain form, `f(x) :=` with the lines below.
 */
internal object ProgramSlot {

    /** The word that marks the place. */
    val MARK = Id("program")

    /** [s] with its one nested program taken out (replaced by [MARK]), and that program; null if it has none. */
    fun extract(s: Statement): Pair<Statement, Program>? {
        if (s is Define && s.value is Program) return null
        var found: Program? = null
        val out = map(s) { e ->
            if (e !is Program) null
            else {
                if (found != null) throw IllegalArgumentException("only one program can sit inside a statement's expression")
                found = e
                MARK
            }
        }
        return found?.let { out to it }
    }

    /** [s] with [MARK] replaced by [program]; null if [s] has no mark. */
    fun insert(s: Statement, program: Program): Statement? {
        var done = false
        val out = map(s) { e -> if (e == MARK && !done) program.also { done = true } else null }
        return out.takeIf { done }
    }

    /**
     * The same inside a program: a line written on one line (an expression, `a <- …`, `return …`,
     * or `… if c` / `… otherwise`) with a program in it, taken out; its lines go indented below it.
     */
    fun extract(line: ProgramLine): Pair<ProgramLine, Program>? {
        var found: Program? = null
        val out = mapLine(line) { e ->
            if (e !is Program) null
            else {
                if (found != null) throw IllegalArgumentException("only one program can sit inside a program line")
                found = e
                MARK
            }
        }
        return found?.let { out to it }
    }

    /** [line] with [MARK] replaced by [program]; null if it has no mark. */
    fun insert(line: ProgramLine, program: Program): ProgramLine? {
        var done = false
        val out = mapLine(line) { e -> if (e == MARK && !done) program.also { done = true } else null }
        return out.takeIf { done }
    }

    private fun mapLine(line: ProgramLine, f: (Expr) -> Expr?): ProgramLine = when (line) {
        is Line -> Line(map(line.expr, f))
        is LocalDefine -> line.copy(value = map(line.value, f))
        is Return -> Return(map(line.value, f))
        // Only the inline forms: a block's lines are printed, and parsed, on their own.
        is IfThen -> if (line.body.size == 1 && line.body[0].isSimple()) IfThen(map(line.condition, f), listOf(mapLine(line.body[0], f))) else line
        is Otherwise -> if (line.body.size == 1 && line.body[0].isSimple()) Otherwise(listOf(mapLine(line.body[0], f))) else line
        else -> line
    }

    private fun ProgramLine.isSimple() = this is Line || this is LocalDefine || this is Return

    private fun map(s: Statement, f: (Expr) -> Expr?): Statement = when (s) {
        is Define -> s.copy(value = map(s.value, f))
        is Evaluate -> s.copy(expr = map(s.expr, f))
        is ExprStatement -> s.copy(expr = map(s.expr, f))
        is Symbolic -> s.copy(expr = map(s.expr, f))
        else -> s
    }

    /** [e] with [f] applied top-down: where f gives a replacement it is used, otherwise the children are visited. */
    private fun map(e: Expr, f: (Expr) -> Expr?): Expr = f(e) ?: when (e) {
        is Apply -> e.copy(args = e.args.map { map(it, f) })
        is Call -> Call(map(e.fn, f), e.args.map { map(it, f) })
        is Index -> Index(map(e.target, f), e.indices.map { map(it, f) })
        is Parens -> Parens(map(e.inner, f))
        is Matrix -> Matrix(e.rows.map { r -> r.map { map(it, f) } })
        is Range -> Range(map(e.first, f), e.second?.let { map(it, f) }, map(e.last, f))
        else -> e
    }
}
