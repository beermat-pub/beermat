package pub.beermat.engine.syntax

/**
 * A syntax error at [column] (0-based) of [line]: 0 for a statement's first line, k for the
 * k-th indented line of a program.
 */
class ParseException(message: String, val column: Int, val line: Int = 0) :
    RuntimeException(if (line == 0) "$message (column ${column + 1})" else "$message (program line $line, column ${column + 1})")

internal enum class TokenKind {
    NUMBER, IDENT, STRING, PLACEHOLDER,
    DEFINE, GLOBAL_DEFINE, EVALUATE,
    EQ, NE, LT, LE, GT, GE,
    PLUS, MINUS, STAR, DOT_TIMES, SLASH, CARET, BANG,
    LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, SEMICOLON, BAR, DOTDOT,
    /** `bt.f`: a module's member (docs/syntax.md#modules). */
    DOT,
    AND, OR, XOR, NOT, TRY, ON_ERROR,
    ASSIGN, ELEMENT_OF, ARROW,
    EOF,
}

/**
 * A token. For [TokenKind.IDENT], [text] has Greek escapes resolved (`\alpha` → `α`) and is the
 * whole name as drawn (`M_max`), split into [name] and [subscript]; for [TokenKind.STRING] it is
 * the unescaped value. A [marked] name had a style prefix (`\bf`, `\rm`, `\it`) or backticks, so it
 * is never a keyword.
 */
internal data class Token(
    val kind: TokenKind,
    val text: String,
    val column: Int,
    val style: Id.Style = Id.Style.PLAIN,
    val marked: Boolean = false,
    val name: String = text,
    val subscript: String? = null,
)

internal class Lexer(private val src: String) {
    private var i = 0

    fun tokens(): List<Token> {
        val out = mutableListOf<Token>()
        while (true) {
            while (i < src.length && (src[i] == ' ' || src[i] == '\t')) i++
            if (i >= src.length) {
                out += Token(TokenKind.EOF, "", i)
                return out
            }
            out += next()
        }
    }

    private fun next(): Token {
        val start = i
        val c = src[i]
        return when {
            c.isAsciiDigit() || (c == '.' && peek(1).isAsciiDigit()) -> number()
            Id.isNameStart(c) || c == '\\' || c == '`' -> identifier()
            // `∞` on its own is a name: the built-in infinity.
            c == Id.INFINITY[0] -> Token(TokenKind.IDENT, Id.INFINITY, start).also { i++ }
            c == '"' -> string()
            else -> symbol(start)
        }
    }

    private fun symbol(start: Int): Token {
        val two = src.substring(i, minOf(i + 2, src.length))
        val three = src.substring(i, minOf(i + 3, src.length))
        fun tok(kind: TokenKind, len: Int): Token {
            i += len
            return Token(kind, src.substring(start, i), start)
        }
        return when {
            three == "::=" -> tok(TokenKind.GLOBAL_DEFINE, 3)
            two == ":=" -> tok(TokenKind.DEFINE, 2)
            two == "==" -> tok(TokenKind.EQ, 2)
            two == "!=" -> tok(TokenKind.NE, 2)
            two == "<=" -> tok(TokenKind.LE, 2)
            two == "->" -> tok(TokenKind.ARROW, 2)
            two == ">=" -> tok(TokenKind.GE, 2)
            two == ".." -> tok(TokenKind.DOTDOT, 2)
            // `bt.f`, and `bt.?` while the name is still to be typed.
            two.length == 2 && two[0] == '.' && (Id.isNameStart(two[1]) || two[1] == '`' || two[1] == '?') -> tok(TokenKind.DOT, 1)
            // After `^`, `<` opens a column index, so `M^<-1>` is column -1, not an assignment.
            two == "<-" && src.getOrNull(start - 1) != '^' -> tok(TokenKind.ASSIGN, 2)
            else -> when (src[i]) {
                '=' -> tok(TokenKind.EVALUATE, 1)
                '<' -> tok(TokenKind.LT, 1)
                '>' -> tok(TokenKind.GT, 1)
                '+' -> tok(TokenKind.PLUS, 1)
                '-' -> tok(TokenKind.MINUS, 1)
                '*' -> tok(TokenKind.STAR, 1)
                // A multiplication always drawn with a dot; '⋅' (the dot operator) reads the same.
                '·', '⋅' -> tok(TokenKind.DOT_TIMES, 1)
                '/' -> tok(TokenKind.SLASH, 1)
                '^' -> tok(TokenKind.CARET, 1)
                '!' -> tok(TokenKind.BANG, 1)
                '(' -> tok(TokenKind.LPAREN, 1)
                ')' -> tok(TokenKind.RPAREN, 1)
                '[' -> tok(TokenKind.LBRACKET, 1)
                ']' -> tok(TokenKind.RBRACKET, 1)
                ';' -> tok(TokenKind.SEMICOLON, 1)
                ',' -> tok(TokenKind.COMMA, 1)
                '|' -> tok(TokenKind.BAR, 1)
                '?' -> tok(TokenKind.PLACEHOLDER, 1)
                '←' -> tok(TokenKind.ASSIGN, 1)
                '∈' -> tok(TokenKind.ELEMENT_OF, 1)
                '→' -> tok(TokenKind.ARROW, 1)
                else -> throw ParseException("unexpected character '${src[i]}'", start)
            }
        }
    }

    private fun number(): Token {
        val start = i
        // `0x1F`, `0o17`, `0b101.01`: a prefix, then the digits of that base.
        Num.radix(peek(1))?.takeIf { peek(0) == '0' && peek(2).digitToIntOrNull(it) != null }?.let { radix ->
            i += 2
            while (peek(0).digitToIntOrNull(radix) != null) i++
            if (peek(0) == '.' && peek(1).digitToIntOrNull(radix) != null) {
                i++
                while (peek(0).digitToIntOrNull(radix) != null) i++
            }
            return Token(TokenKind.NUMBER, src.substring(start, i), start)
        }
        digits()
        if (peek(0) == '.' && peek(1).isAsciiDigit()) {
            i++
            digits()
        }
        // An exponent only if digits follow; otherwise `2e` is 2·e.
        if (peek(0) == 'e' || peek(0) == 'E') {
            val signLen = if (peek(1) == '+' || peek(1) == '-') 1 else 0
            if (peek(1 + signLen).isAsciiDigit()) {
                i += 1 + signLen
                digits()
            }
        }
        // `2i` is imaginary, but `2in` is 2 inches.
        if ((peek(0) == 'i' || peek(0) == 'j') && !isIdentChar(peek(1))) i++
        return Token(TokenKind.NUMBER, src.substring(start, i), start)
    }

    private fun digits() {
        while (peek(0).isAsciiDigit()) i++
    }

    /** A whole identifier including any `_subscript`, with Greek escapes resolved. */
    private fun identifier(): Token {
        val start = i
        val prefix = PREFIXES.keys.firstOrNull { src.startsWith(it, i) && peek(it.length).let { c -> c == ' ' || c == '\t' } }
        if (prefix != null) {
            // `\bf F`, `\rm x`, `\it if`: a marked name, never a keyword, so `\it if` is a variable.
            i += prefix.length
            while (peek(0) == ' ' || peek(0) == '\t') i++
            if (!(Id.isNameStart(peek(0)) || peek(0) == '\\' || peek(0) == '`')) throw ParseException("expected a name after '$prefix'", i)
            val name = identifier()
            if (name.marked && name.style != Id.Style.PLAIN) throw ParseException("a name has one style", name.column)
            return name.copy(kind = TokenKind.IDENT, column = start, style = PREFIXES.getValue(prefix), marked = true)
        }
        val (name, quoted) = namePart(subscript = false)
        val subscript = if (peek(0) == '_') {
            i++
            // `x_` with nothing after it: an empty subscript, a different name from `x` that looks the same.
            namePart(subscript = true).first
        } else {
            null
        }
        val text = subscript?.let { "${name}_$it" } ?: name
        if (quoted || subscript != null) return Token(TokenKind.IDENT, text, start, marked = quoted, name = name, subscript = subscript)
        val kind = when (text) {
            "and" -> TokenKind.AND
            "or" -> TokenKind.OR
            "xor" -> TokenKind.XOR
            "not" -> TokenKind.NOT
            "try" -> TokenKind.TRY
            "on" -> {
                // `on error` is one operator; `on` alone is an ordinary name.
                val m = ON_ERROR.matchAt(src, i)
                if (m == null) TokenKind.IDENT else TokenKind.ON_ERROR.also { i = m.range.last + 1 }
            }
            else -> TokenKind.IDENT
        }
        return Token(kind, text, start)
    }

    /**
     * A name, or a subscript: letters, digits, `'`, `∇` and `∂` (a subscript also `_`, and `.` between
     * characters); or anything but a backtick between backticks (`` `θ*` ``). True when quoted.
     */
    private fun namePart(subscript: Boolean): Pair<String, Boolean> {
        if (peek(0) == '`') {
            val start = i
            val end = src.indexOf('`', i + 1)
            if (end < 0 || '\n' in src.substring(i, end)) throw ParseException("unterminated name: a closing ` is missing", start)
            if (end == i + 1) throw ParseException("empty name between backticks", start)
            i = end + 1
            return src.substring(start + 1, end) to true
        }
        val sb = StringBuilder()
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' -> sb.append(greekEscape())
                Id.isNameChar(c) || (subscript && c == '_') ||
                    (subscript && c == '.' && sb.isNotEmpty() && Id.isNameChar(peek(1))) -> {
                    sb.append(c)
                    i++
                }
                else -> break
            }
        }
        return sb.toString() to false
    }

    /** The escape ends with the name, so `\Deltax` is `Δx`; no Greek name is a prefix of another. */
    private fun greekEscape(): Char {
        val start = i
        val name = GREEK.keys.firstOrNull { src.startsWith(it, start + 1) }
        if (name == null) {
            var end = start + 1
            while (src.getOrElse(end) { ' ' }.isAsciiLetter()) end++
            throw ParseException("unknown escape '${src.substring(start, end)}'", start)
        }
        i = start + 1 + name.length
        return GREEK.getValue(name)
    }

    private fun string(): Token {
        val start = i
        i++
        val sb = StringBuilder()
        while (true) {
            if (i >= src.length) throw ParseException("unterminated string", start)
            val c = src[i++]
            when (c) {
                '"' -> return Token(TokenKind.STRING, sb.toString(), start)
                '\\' -> {
                    val e = if (i < src.length) src[i++] else throw ParseException("unterminated string", start)
                    sb.append(
                        when (e) {
                            '"' -> '"'
                            '\\' -> '\\'
                            'n' -> '\n'
                            else -> throw ParseException("unknown string escape '\\$e'", i - 2)
                        }
                    )
                }
                else -> sb.append(c)
            }
        }
    }

    private fun peek(offset: Int): Char = src.getOrElse(i + offset) { '\u0000' }

    private fun isIdentChar(c: Char) = Id.isNameChar(c) || c == '\\'

    private fun Char.isAsciiDigit() = this in '0'..'9'
    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    companion object {
        /** The style prefixes; `\it` marks a plain name. */
        private val PREFIXES = Id.Style.entries.associateBy { it.prefix ?: Id.PLAIN_PREFIX }

        /** The rest of `on error` after `on`: spaces, then `error` as a whole word. */
        private val ON_ERROR = Regex("""[ \t]+error(?![\p{L}\p{N}'_\\])""")

        /** LaTeX-style names for the Greek letters, e.g. `\alpha` → α, `\Delta` → Δ. */
        val GREEK: Map<String, Char> = run {
            val names = listOf(
                "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa",
                "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi",
                "chi", "psi", "omega",
            )
            // Lower case runs α (U+03B1) … ω (U+03C9), skipping final sigma ς (U+03C2);
            // upper case runs Α (U+0391) … Ω (U+03A9), skipping the unassigned U+03A2.
            names.flatMapIndexed { k, name ->
                val offset = if (k >= names.indexOf("sigma")) k + 1 else k
                listOf(name to ('α' + offset), name.replaceFirstChar(Char::uppercaseChar) to ('Α' + offset))
            }.toMap()
        }
    }
}
