package pub.beermat.engine.syntax

import pub.beermat.engine.syntax.TokenKind as K

/**
 * Parses linear syntax (docs/syntax.md). Recursive descent, one function per precedence level;
 * [Printer] mirrors these levels. A statement is one line, except a program: a definition
 * ending in `:=` followed by indented lines (docs/syntax.md#8-program-blocks).
 */
class Parser private constructor(text: String, private val line: Int = 0) {
    private val tokens = try {
        Lexer(text).tokens()
    } catch (e: ParseException) {
        throw ParseException(e.message!!.substringBeforeLast(" (column"), e.column, line)
    }
    private var pos = 0

    companion object {
        internal fun programLine(node: ProgramParser.Node): ProgramLine = Parser(node.text, node.line).programLine(node)

        fun parseStatement(text: String): Statement {
            val lines = text.split('\n')
            if (lines.size == 1) return Parser(text).run { finish(statement()) }
            // `M := program*kN*m` with the lines below: the program sits where the word `program` is.
            val head = runCatching { Parser(lines[0]).run { finish(statement()) } }.getOrNull()
            if (head != null && !(head is Define && head.value == Placeholder)) {
                return ProgramSlot.insert(head, ProgramParser(lines).program())
                    ?: throw ParseException("a statement with indented lines below it must end with ':=', or hold the word 'program' where they go", lines[0].length, 0)
            }
            val (target, global) = Parser(lines[0]).run { finish(programHead()) }
            return Define(target, ProgramParser(lines).program(), global)
        }

        fun parseExpression(text: String): Expr = Parser(text).run { finish(expression()) }

        /**
         * Parses [text], or, if it has a syntax error, returns it as [Unparsed] with the name it
         * would have defined: the part before `:=`, if that parses as `x` or `f(x)`.
         */
        fun parseOrKeep(text: String): Statement = try {
            parseStatement(text)
        } catch (e: ParseException) {
            val head = text.lineSequence().first()
            val target = if (":=" in head) {
                runCatching { parseExpression(head.substringBefore(":=").trimEnd(':')) }.getOrNull()
            } else null
            val id = when {
                target is Id -> target
                target is Call && target.fn is Id -> target.fn
                target is Index && target.target is Id -> target.target
                else -> null
            }
            Unparsed(id as Id?, e.message!!, e.line)
        }
    }

    private fun <T> finish(result: T): T {
        if (peek.kind == K.ASSIGN) fail("'<-' defines a local variable inside a program; elsewhere use ':='")
        if (peek.kind != K.EOF) fail("unexpected '${peek.text}'")
        return result
    }

    private fun statement(): Statement {
        if (word("given") && next.kind == K.EOF) return Given.also { advance() }
        val first = expression()
        return when (peek.kind) {
            K.DEFINE, K.GLOBAL_DEFINE -> {
                val global = advance().kind == K.GLOBAL_DEFINE
                if (!Define.isDefinitionTarget(first)) {
                    fail("can only define a name or a function such as f(x, y)", column = 0)
                }
                if (peek.kind == K.EOF) fail("missing value after ':=' (a program's lines go below, indented)")
                val value = rangeOrExpression()
                val result = if (peek.kind == K.EVALUATE) resultSlot() else null
                Define(first, value, global, result)
            }
            K.EVALUATE -> Evaluate(first, resultSlot())
            K.ARROW -> Symbolic(first, keywords())
            else -> ExprStatement(first)
        }
    }

    /** After `expr`: `-> kw -> … -> ?`, the keywords of a symbolic evaluation. */
    private fun keywords(): List<Expr> {
        val out = mutableListOf<Expr>()
        while (accept(K.ARROW)) {
            if (accept(K.PLACEHOLDER)) {
                if (peek.kind != K.EOF) fail("nothing may follow the result placeholder '-> ?'")
                return out
            }
            out += expression()
        }
        fail("a symbolic evaluation ends with '-> ?'")
    }

    /** `f(x) :=` on the first line of a program. */
    private fun programHead(): Pair<Expr, Boolean> {
        val target = expression()
        if (peek.kind != K.DEFINE && peek.kind != K.GLOBAL_DEFINE) fail("a statement with indented lines below it must be a definition ending in ':='")
        val global = advance().kind == K.GLOBAL_DEFINE
        if (!Define.isDefinitionTarget(target)) fail("can only define a name or a function such as f(x, y)", column = 0)
        if (peek.kind != K.EOF) fail("a definition with indented lines below it must end with ':='")
        return target to global
    }

    /**
     * One line of a program, given its [node] in the indentation tree. Blocks (`if`, `otherwise`,
     * `for`, `while`) take the node's children as their body; any simple line can end in an
     * inline `if cond` or `otherwise`.
     */
    private fun programLine(node: ProgramParser.Node): ProgramLine {
        val hasBody = node.children.isNotEmpty()
        fun body() = node.children.map { Parser.programLine(it) }
        fun needsBody(what: String) {
            if (!hasBody) fail("$what needs lines indented below it")
        }
        val result = when {
            word("if") && hasBody -> {
                advance()
                IfThen(expression(), body())
            }
            word("otherwise") && next.kind == K.EOF -> {
                advance()
                needsBody("otherwise")
                Otherwise(body())
            }
            word("for") -> {
                advance()
                if (peek.kind != K.IDENT && peek.kind != K.PLACEHOLDER) fail("expected the loop variable: for k in 1..n")
                val variable = if (peek.kind == K.PLACEHOLDER) Placeholder.also { advance() } else identifier(advance())
                if (!word("in") && peek.kind != K.ELEMENT_OF) fail("expected 'in' after the loop variable: for k in 1..n")
                advance()
                val over = rangeOrExpression()
                needsBody("a for loop")
                For(variable, over, body())
            }
            word("while") -> {
                advance()
                val condition = expression()
                needsBody("a while loop")
                While(condition, body())
            }
            else -> {
                val simple = simpleLine()
                val line = when {
                    word("if") -> {
                        advance()
                        IfThen(expression(), listOf(simple))
                    }
                    word("otherwise") -> {
                        advance()
                        Otherwise(listOf(simple))
                    }
                    else -> simple
                }
                // A line holding the word `program` (`a <- program*2`) takes the lines below it as that program.
                if (hasBody) {
                    ProgramSlot.insert(line, Program(body()))
                        ?: throw ParseException("only if, otherwise, for and while lines, or a line holding the word 'program', can have lines indented below them", node.children[0].indent, node.children[0].line)
                } else {
                    line
                }
            }
        }
        return finish(result)
    }

    private fun simpleLine(): ProgramLine = when {
        word("return") -> {
            advance()
            if (peek.kind == K.EOF || word("if") || word("otherwise")) fail("return needs a value")
            Return(expression())
        }
        word("break") -> Break.also { advance() }
        word("continue") -> Continue.also { advance() }
        else -> {
            val e = expression()
            if (accept(K.ASSIGN)) {
                if (!Define.isAssignable(e) && !Define.isFunctionTarget(e)) fail("can only assign to a name, an element, a column, names in a matrix or a local function, as in a <- 1, v[i] <- 1, M^<j> <- v, [a; b] <- v or f(x) <- x^2", column = 0)
                LocalDefine(e, expression())
            } else {
                Line(e)
            }
        }
    }
    /** An unmarked name used as a keyword here; `\it if` and `\bf if` are variables. */
    /** A plain name used as a keyword here; `bf if` is a variable. */
    private fun word(w: String) = unmarked(peek) && peek.text == w

    private fun unmarked(t: Token) = t.kind == K.IDENT && !t.marked

    /** `= ?` with an optional unit. */
    private fun resultSlot(): ResultSlot {
        expect(K.EVALUATE)
        if (peek.kind != K.PLACEHOLDER) fail("expected '?' after '=' (write 'x = ?' to evaluate, or '==' to compare)")
        advance()
        return ResultSlot(if (peek.kind == K.EOF) null else expression())
    }

    /** `a..b`, `a, b..c`, or a plain expression. */
    private fun rangeOrExpression(): Expr {
        val first = expression()
        val second = if (accept(K.COMMA)) expression() else null
        if (second == null && peek.kind != K.DOTDOT) return first
        if (!accept(K.DOTDOT)) fail("expected '..': a stepped range is written first, second..last")
        return Range(first, second, expression())
    }

    /** `try a on error b`, or a plain expression. [attempt]: the `a` of a `try`, which `on error` ends. */
    private fun expression(attempt: Boolean = false): Expr {
        if (accept(K.TRY)) {
            val a = expression(attempt = true)
            if (!accept(K.ON_ERROR)) fail("expected 'on error' after 'try' and the expression to try")
            // Inside another attempt, the next `on error` belongs to the outer `try`.
            return Apply(Op.ON_ERROR, a, expression(attempt))
        }
        val e = or()
        if (peek.kind == K.ON_ERROR && !attempt) fail("'on error' needs 'try' before the expression it guards: try a on error b")
        return e
    }

    private fun or(): Expr {
        var left = and()
        while (peek.kind == K.OR || peek.kind == K.XOR) {
            val op = if (advance().kind == K.OR) Op.OR else Op.XOR
            left = Apply(op, left, and())
        }
        return left
    }

    private fun and(): Expr {
        var left = not()
        while (accept(K.AND)) left = Apply(Op.AND, left, not())
        return left
    }

    private fun not(): Expr = if (accept(K.NOT)) Apply(Op.NOT, not()) else comparison()

    private fun comparison(): Expr {
        var left = additive()
        while (true) {
            val op = COMPARISONS[peek.kind] ?: return left
            advance()
            left = Apply(op, left, additive())
        }
    }

    private fun additive(): Expr {
        var left = multiplicative()
        while (true) {
            val op = when (peek.kind) {
                K.PLUS -> Op.ADD
                K.MINUS -> Op.SUB
                else -> return left
            }
            advance()
            left = Apply(op, left, multiplicative())
        }
    }

    /** `*`, `/`, and implicit multiplication of a number literal by what follows (`3 m`). */
    private fun multiplicative(): Expr {
        var last = unary()
        var left = last
        while (true) {
            when {
                // `x if c` and `x otherwise` end the expression on a program line.
                unmarked(peek) && peek.text in PROGRAM_STOPS -> return left
                peek.kind == K.STAR || peek.kind == K.SLASH || peek.kind == K.DOT_TIMES -> {
                    val kind = advance().kind
                    last = unary()
                    left = if (kind == K.SLASH) Apply(Op.DIV, left, last) else Apply(Op.MUL, left, last, dot = kind == K.DOT_TIMES)
                }
                // A name after an operand, `3 m` or `q L`: a multiplication drawn with a thin space.
                peek.kind == K.IDENT -> {
                    last = power()
                    left = Apply(Op.MUL, left, last, implicit = true)
                }
                peek.kind == K.NUMBER ->
                    fail("missing operator before '${peek.text}': write '*'")
                else -> return left
            }
        }
    }

    private fun unary(): Expr = when {
        accept(K.MINUS) -> Apply(Op.NEG, unary())
        accept(K.PLUS) -> Apply(Op.POS, unary())
        else -> power()
    }

    /** Right-associative; the exponent may be negated (`a^-1`). */
    private fun power(): Expr {
        val base = postfix()
        return if (accept(K.CARET)) Apply(Op.POW, base, unary()) else base
    }

    /** Factorial, calls, indices, and the superscript operators `M^T` and `M^<j>`. */
    private fun postfix(): Expr {
        var e = atom()
        while (true) {
            e = when {
                accept(K.BANG) -> Apply(Op.FACTORIAL, e)
                // `bt.f`: a module's function.
                peek.kind == K.DOT && next.kind == K.IDENT -> {
                    pos++
                    Apply(Op.MEMBER, e, identifier(advance()))
                }
                peek.kind == K.DOT && next.kind == K.PLACEHOLDER -> {
                    pos += 2
                    Apply(Op.MEMBER, e, Placeholder)
                }
                accept(K.LPAREN) -> Call(e, arguments(K.RPAREN))
                peek.kind == K.LBRACKET -> {
                    val column = advance().column
                    val indices = arguments(K.RBRACKET)
                    if (indices.size !in 1..2) fail("an index takes one or two subscripts: v[i] or M[i, j]", column)
                    Index(e, indices)
                }
                // `^T` is always a transpose; a power of a variable named T is written `x^(T)`.
                peek.kind == K.CARET && unmarked(next) && next.text == "T" -> {
                    pos += 2
                    Apply(Op.TRANSPOSE, e)
                }
                peek.kind == K.CARET && next.kind == K.LT -> {
                    pos += 2
                    Apply(Op.COLUMN, e, additive()).also { expect(K.GT) }
                }
                else -> return e
            }
        }
    }

    private fun arguments(close: K): List<Expr> {
        val args = mutableListOf<Expr>()
        if (!accept(close)) {
            do args += expression() while (accept(K.COMMA))
            expect(close)
        }
        return args
    }

    /** After `[`: rows separated by `;`, elements by `,`. */
    private fun matrix(open: Int): Matrix {
        val rows = mutableListOf<List<Expr>>()
        do {
            val row = mutableListOf<Expr>()
            do row += expression() while (accept(K.COMMA))
            rows += row
        } while (accept(K.SEMICOLON))
        expect(K.RBRACKET)
        if (rows.any { it.size != rows[0].size }) fail("every row of a matrix needs the same number of columns", open)
        return Matrix(rows)
    }

    private fun atom(): Expr {
        val t = peek
        return when (t.kind) {
            K.NUMBER -> {
                advance()
                Num(t.text)
            }
            K.IDENT -> {
                advance()
                // `use "file.py"`: a module of Python functions, written without parentheses.
                if (t.name == "use" && t.subscript == null && t.style == Id.Style.PLAIN && peek.kind == K.STRING) Call(identifier(t), listOf(Str(advance().text)))
                else identifier(t)
            }
            K.STRING -> {
                advance()
                Str(t.text)
            }
            K.PLACEHOLDER -> {
                advance()
                Placeholder
            }
            K.LPAREN -> {
                advance()
                Parens(expression()).also { expect(K.RPAREN) }
            }
            K.BAR -> {
                advance()
                Apply(Op.ABS, expression()).also { expect(K.BAR) }
            }
            K.LBRACKET -> matrix(advance().column)
            K.EOF -> fail("incomplete expression: use '?' for an empty slot")
            else -> fail("unexpected '${t.text}'")
        }
    }

    private fun identifier(t: Token): Id = Id(t.name, t.subscript, t.style)

    private val peek: Token get() = tokens[pos]

    private val next: Token get() = tokens[minOf(pos + 1, tokens.size - 1)]

    private fun advance(): Token = tokens[pos++]

    private fun accept(kind: K): Boolean = (peek.kind == kind).also { if (it) pos++ }

    private fun expect(kind: K) {
        if (!accept(kind)) {
            val what = when (kind) {
                K.RPAREN -> "')'"
                K.RBRACKET -> "']'"
                K.GT -> "'>'"
                K.DOTDOT -> "'..'"
                K.BAR -> "closing '|'"
                K.EVALUATE -> "'='"
                else -> kind.name
            }
            fail(if (peek.kind == K.EOF) "missing $what" else "expected $what, found '${peek.text}'")
        }
    }

    private fun fail(message: String, column: Int = peek.column): Nothing = throw ParseException(message, column, line)
}

private val COMPARISONS = mapOf(
    K.EQ to Op.EQ, K.NE to Op.NE, K.LT to Op.LT, K.LE to Op.LE, K.GT to Op.GT, K.GE to Op.GE,
)

/** Words that end an expression, because a program line uses them after one. */
internal val PROGRAM_STOPS = setOf("if", "otherwise")

private val POSTFIX_OR_POWER = setOf(Op.POW, Op.FACTORIAL, Op.TRANSPOSE, Op.COLUMN)

/** The §5 rule, shared by the parser and the printer so they agree on when juxtaposition is valid. */
internal object ImplicitMultiplication {
    /** Whether `left right` with a space parses back as this implicit multiplication: `3 m`, `q L`, `q L^2`. */
    fun isValid(right: Expr): Boolean = precedence(right) >= Op.POW.precedence && startsWithName(right)

    private fun startsWithName(e: Expr): Boolean = when (e) {
        is Id -> e.subscript != null || e.style != Id.Style.PLAIN || e.name !in PROGRAM_STOPS
        is Call -> startsWithName(e.fn)
        is Index -> startsWithName(e.target)
        is Apply -> e.op in POSTFIX_OR_POWER && startsWithName(e.args[0])
        else -> false
    }
}

internal fun precedence(e: Expr): Int = when (e) {
    is Apply -> e.op.precedence
    is Call -> CALL_PRECEDENCE
    is Index -> CALL_PRECEDENCE
    else -> ATOM_PRECEDENCE
}
