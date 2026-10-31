package pub.beermat.engine.syntax

/**
 * The expression tree: the parse result, the editor's model and the evaluator's input.
 * See docs/syntax.md. Shaped after the `.xmcd` math XML (`ml:apply`, `ml:id`, `ml:parens`, …).
 *
 * Nodes validate themselves on construction, so any tree can be printed as linear syntax.
 */
sealed interface Expr

/**
 * A number literal, kept as typed (`.5`, `1.2E+3`, `2i`, `0x1F`); the value is computed from [text].
 * Hexadecimal, octal and binary take the prefixes `0x`, `0o` and `0b`, and may have a fraction (`0b101.01`).
 */
data class Num(val text: String) : Expr {
    init {
        require(NUMBER.matches(text) || BASED.matches(text)) { "not a number literal: '$text'" }
    }

    /** The literal in decimal, exactly (`0x1F` is `31`, `0b0.1` is `0.5`); other literals as they are. */
    val decimal: String = based(text) ?: text

    companion object {
        private val NUMBER = Regex("""([0-9]+(\.[0-9]+)?|\.[0-9]+)([eE][+-]?[0-9]+)?[ij]?""")
        private val BASED = Regex("""0([xX][0-9a-fA-F]+(\.[0-9a-fA-F]+)?|[oO][0-7]+(\.[0-7]+)?|[bB][01]+(\.[01]+)?)""")

        /** The base a prefix names (`x` 16, `o` 8, `b` 2). */
        fun radix(prefix: Char): Int? = when (prefix.lowercaseChar()) {
            'x' -> 16
            'o' -> 8
            'b' -> 2
            else -> null
        }

        /** A based literal in decimal; exact, since a power of 2 has a finite decimal fraction. Null for any other literal. */
        private fun based(text: String): String? {
            if (!BASED.matches(text)) return null
            val radix = radix(text[1])!!
            val digits = text.drop(2)
            val whole = java.math.BigInteger(digits.substringBefore('.'), radix)
            var value = java.math.BigDecimal(whole)
            var unit = java.math.BigDecimal.ONE
            for (d in digits.substringAfter('.', "")) {
                unit = unit.divide(java.math.BigDecimal(radix))
                value += unit * java.math.BigDecimal(Character.digit(d, radix))
            }
            return value.stripTrailingZeros().toPlainString()
        }
    }
}

/**
 * An identifier with an optional literal subscript: `M_max` is `Id("M", "max")`.
 * The [style] is part of the name: plain `F` (drawn italic), upright `\rm F` and bold `\bf F` are
 * three different variables.
 */
data class Id(val name: String, val subscript: String? = null, val style: Style = Style.PLAIN) : Expr {
    init {
        require(name.isNotEmpty() && name.none { it == '`' || it == '\n' }) { "not an identifier name: '$name'" }
        require(subscript == null || (subscript.none { it == '`' || it == '\n' })) { "not a subscript: '$subscript'" }
    }

    override fun toString(): String = "Id($text)"

    /** The name without its style, as drawn: `M_max`, `θ*`. */
    val label: String get() = subscript?.let { "${name}_$it" } ?: name

    /**
     * The name in linear syntax, as printed and as messages show it: `M_max`, `\bf F`, `\it if`
     * for a plain name that is a keyword, and backticks around what a plain name can't hold (`` `θ*` ``).
     */
    val text: String get() {
        val plain = if (name == INFINITY) subscript == null && style == Style.PLAIN else isPlainName(name)
        val written = quoted(name, plain) + (subscript?.let { "_" + quoted(it, isPlainSubscript(it)) } ?: "")
        return when {
            style.prefix != null -> "${style.prefix} $written"
            subscript == null && name in KEYWORDS -> "$PLAIN_PREFIX $written"
            else -> written
        }
    }

    private fun quoted(part: String, plain: Boolean) = if (plain) part else "`$part`"

    /** A variable's style. Built-in names (units, functions, constants) are plain. */
    enum class Style(val prefix: String?) {
        /** Drawn italic, or upright when it names a unit or a built-in function. */
        PLAIN(null),
        UPRIGHT("\\rm"),
        BOLD("\\bf"),
    }

    companion object {
        /** Marks a plain name explicitly, so that `\it if` is a variable. */
        const val PLAIN_PREFIX = "\\it"

        /**
         * Words that are keywords wherever they appear, so a plain variable with one of these names
         * is always written `\it and`. Program words (`if`, `for`, `break`, …) are keywords only in
         * some places; there the printer marks them (see [Printer]).
         */
        val KEYWORDS = setOf("and", "or", "xor", "not", "try")

        /** `∇`, `∂` and `°` count as letters, so `∇H`, `∂u` and `°C` are plain names. */
        fun isNameStart(c: Char) = c.isLetter() || c == '∇' || c == '∂' || c == '°'

        fun isNameChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '∇' || c == '∂' || c == '°'

        /** Infinity: a name of its own, written without backticks when it stands alone (`∞`, not `∞_x`). */
        const val INFINITY = "∞"

        /** A name written without backticks. */
        fun isPlainName(s: String) = s.isNotEmpty() && isNameStart(s[0]) && s.all(::isNameChar)

        /** A subscript written without backticks: name characters, `_`, and `.` between them; or empty (`x_`). */
        fun isPlainSubscript(s: String) = s.indices.all { k ->
            val c = s[k]
            isNameChar(c) || c == '_' || (c == '.' && k > 0 && k < s.length - 1 && isNameChar(s[k + 1]))
        }
    }
}

/** A string literal; [value] is unescaped. */
data class Str(val value: String) : Expr

/** An empty slot: `?` in text, ■ in the editor. */
data object Placeholder : Expr

/** Parentheses the user typed. Kept so that redundant ones survive saving. */
data class Parens(val inner: Expr) : Expr

/**
 * An operator applied to its operands. Two flags, valid only on [Op.MUL], choose how a
 * multiplication is drawn (docs/syntax.md#multiplication-style): [implicit], written by juxtaposition
 * (`3 m`, `q L`), with a thin space; [dot], written `·`, with a dot. With neither (`*`), the sheet's
 * default decides.
 */
data class Apply(val op: Op, val args: List<Expr>, val implicit: Boolean = false, val dot: Boolean = false) : Expr {
    init {
        require(args.size == op.arity) { "$op takes ${op.arity} operand(s), not ${args.size}" }
        require(!implicit || op == Op.MUL) { "only multiplication can be implicit" }
        require(!dot || (op == Op.MUL && !implicit)) { "only a multiplication can have a dot, and not with a space" }
    }

    constructor(op: Op, vararg args: Expr, implicit: Boolean = false, dot: Boolean = false) : this(op, args.toList(), implicit, dot)
}

/** How a multiplication written `*` is drawn: the sheet's default (frontmatter `multiplication:`). */
enum class Multiplication(val key: String) {
    DOT("dot"),
    SPACE("space"),
}

/**
 * A matrix literal `[1, 2; 3, 4]`: a list of rows, all the same length. A column vector is
 * `[1; 2; 3]`, a row vector `[1, 2, 3]`.
 */
data class Matrix(val rows: List<List<Expr>>) : Expr {
    init {
        require(rows.isNotEmpty() && rows[0].isNotEmpty()) { "a matrix needs at least one element" }
        require(rows.all { it.size == rows[0].size }) { "every row of a matrix needs the same number of columns" }
    }
}

/**
 * A range `first..last` or `first, second..last` (the step is `second - first`). Only the whole
 * right-hand side of a definition (`i := 0..10`) or a `for` loop can be a range.
 */
data class Range(val first: Expr, val second: Expr?, val last: Expr) : Expr

/** An index `v[i]` or `M[i, j]` (`ml:indexer` in `.xmcd`); one or two [indices]. */
data class Index(val target: Expr, val indices: List<Expr>) : Expr {
    init {
        require(indices.size in 1..2) { "an index takes one or two subscripts, not ${indices.size}" }
    }

    constructor(target: Expr, vararg indices: Expr) : this(target, indices.toList())
}

/** A function call `f(x, y)`. Built-ins with special notation (`sqrt`, `sum`, `int`, …) are calls too. */
data class Call(val fn: Expr, val args: List<Expr>) : Expr {
    constructor(fn: Expr, vararg args: Expr) : this(fn, args.toList())
}

/**
 * A program (`ml:program` in `.xmcd`): the indented lines below `f(x) :=`. Its value is the value
 * of the last line executed, or of a `return`.
 */
data class Program(val lines: List<ProgramLine>) : Expr {
    init {
        require(lines.isNotEmpty()) { "a program needs at least one line" }
    }
}

/** One line of a [Program]. Blocks ([IfThen], [Otherwise], [For], [While]) have a body of lines. */
sealed interface ProgramLine

/** An expression line. */
data class Line(val expr: Expr) : ProgramLine

/**
 * `a <- expr` (`ml:localDefine` in `.xmcd`): [target] is an [Id], an element `v[i]` or column `M^<j>` of one,
 * names in a matrix, or a local function `f(x, y) <- x + y`.
 */
data class LocalDefine(val target: Expr, val value: Expr) : ProgramLine {
    init {
        require(Define.isAssignable(target) || Define.isFunctionTarget(target)) { "cannot assign to $target" }
    }
}

/** `if cond` with a body; with a one-line body it is written inline, `expr if cond`. */
data class IfThen(val condition: Expr, val body: List<ProgramLine>) : ProgramLine {
    init {
        require(body.isNotEmpty()) { "an if needs a body" }
    }
}

/** `otherwise` with a body, run when none of the `if` lines just above it ran; `expr otherwise` inline. */
data class Otherwise(val body: List<ProgramLine>) : ProgramLine {
    init {
        require(body.isNotEmpty()) { "otherwise needs a body" }
    }
}

/** `for k in over`, where [over] is a [Range] or evaluates to a vector. [variable] is an [Id] (or a [Placeholder] still to be filled, in the editor). */
data class For(val variable: Expr, val over: Expr, val body: List<ProgramLine>) : ProgramLine {
    init {
        require(variable is Id || variable == Placeholder) { "a for loop variable is a name" }
        require(body.isNotEmpty()) { "a for loop needs a body" }
    }
}

data class While(val condition: Expr, val body: List<ProgramLine>) : ProgramLine {
    init {
        require(body.isNotEmpty()) { "a while loop needs a body" }
    }
}

data class Return(val value: Expr) : ProgramLine

data object Break : ProgramLine

data object Continue : ProgramLine

/**
 * Operators, with their linear-syntax symbol and precedence (higher binds tighter),
 * as in the table in docs/syntax.md §4.
 */
enum class Op(val symbol: String, val arity: Int, val precedence: Int) {
    /** `try a on error b`: b if evaluating a fails. */
    ON_ERROR("on error", 2, 2),
    OR("or", 2, 3),
    XOR("xor", 2, 3),
    AND("and", 2, 4),
    NOT("not", 1, 5),
    EQ("==", 2, 6),
    NE("!=", 2, 6),
    LT("<", 2, 6),
    LE("<=", 2, 6),
    GT(">", 2, 6),
    GE(">=", 2, 6),
    ADD("+", 2, 8),
    SUB("-", 2, 8),
    MUL("*", 2, 9),
    DIV("/", 2, 9),
    NEG("-", 1, 10),
    POS("+", 1, 10),
    POW("^", 2, 11),
    FACTORIAL("!", 1, 12),
    /** `M^T`. */
    TRANSPOSE("^T", 1, 12),
    /** `M^<j>`, column j. */
    COLUMN("^<>", 2, 12),
    ABS("|", 1, ATOM_PRECEDENCE),
    /** `bt.f`: the function `f` of the module held in `bt` (docs/syntax.md#modules); the second argument is the [Id]. */
    MEMBER(".", 2, CALL_PRECEDENCE),
}

internal const val CALL_PRECEDENCE = 13
internal const val ATOM_PRECEDENCE = 14

/** One line of a math region. */
sealed interface Statement

/**
 * `x := 3 m`, `f(x) := x^2`, or with [global] `g ::= 9.81 m/s^2`.
 * [target] is an [Id], a [Call] of an [Id] on [Id] parameters, or an element `v[i]` (an [Index] of an [Id]).
 * A non-null [result] means the value is also shown: `M := q*L^2/8 = ? kN*m`.
 */
data class Define(
    val target: Expr,
    val value: Expr,
    val global: Boolean = false,
    val result: ResultSlot? = null,
) : Statement {
    init {
        require(isDefinitionTarget(target)) { "cannot define $target" }
    }

    companion object {
        /**
         * A name, a function head, an element, a column (`M^<j>`), names in a matrix (`[a; b]`, each
         * getting its element), or an empty slot still to be filled in (`? := 2`).
         */
        fun isDefinitionTarget(e: Expr): Boolean =
            e is Id || e == Placeholder || (e is Call && e.fn is Id && e.args.all { it is Id }) || isAssignable(e)

        /** `f(x, y)`: a function's name on its parameters' names. */
        fun isFunctionTarget(e: Expr): Boolean = e is Call && e.fn is Id && e.args.all { it is Id }

        /** What a program line can assign to with `<-`, as well: an element, a column, or names in a matrix. */
        fun isAssignable(e: Expr): Boolean = e is Id || (e is Index && e.target is Id) ||
            (e is Apply && e.op == Op.COLUMN && e.args[0] is Id) || (e is Matrix && e.rows.flatten().all { it is Id })
    }
}

/** `x = ?` or `x = ? mm`. */
data class Evaluate(val expr: Expr, val result: ResultSlot = ResultSlot()) : Statement

/** A bare expression, not yet a definition or evaluation. */
data class ExprStatement(val expr: Expr) : Statement

/**
 * `expr -> ?`, or with keywords `expr -> expand -> ?` (docs/syntax.md §9, docs/symbolic.md): a
 * symbolic evaluation. Each keyword transforms the result of the one before.
 */
data class Symbolic(val expr: Expr, val keywords: List<Expr> = emptyList()) : Statement

/**
 * `given`: the start of a solve block. The comparisons below it, up to the
 * statement that calls `find` or `minerr`, are its constraints (docs/evaluation.md#solve-blocks).
 */
data object Given : Statement

/**
 * A statement that failed to parse, kept so that evaluation can report it in place. [target] is
 * the name it would have defined, if that much can be told, so later uses of that name say why
 * it is missing. [line] is the line of the error, as in [ParseException.line].
 */
data class Unparsed(val target: Id?, val message: String, val line: Int = 0) : Statement

/** The `= ?` part of a statement; [unit] is the sticky unit placeholder, if the user filled it. */
data class ResultSlot(val unit: Expr? = null)
