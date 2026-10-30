package pub.beermat.app.editor

import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Continue
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Multiplication
import pub.beermat.engine.syntax.Otherwise
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.ProgramLine
import pub.beermat.engine.syntax.Return
import pub.beermat.engine.syntax.While
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.Given
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.ResultSlot
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.syntax.Symbolic

/** Which side of the selection the insertion cursor's vertical bar is on. */
enum class Side { LEFT, RIGHT }

/** A leaf being typed: its raw text, which may not be valid yet (`1.`, `2e-`, `M_`), and the caret. */
data class Draft(val kind: Kind, val text: String, val caret: Int, val style: Id.Style = Id.Style.PLAIN, val literal: Boolean = false) {
    enum class Kind { NAME, NUMBER, STRING }

    /** The node the draft stands for so far: the longest valid reading of its text, or a placeholder. */
    fun node(): Expr = when (kind) {
        Kind.STRING -> Str(text)
        Kind.NUMBER -> {
            var t = text
            while (t.isNotEmpty() && !NUMBER.matches(t)) t = t.dropLast(1)
            if (t.isEmpty()) Placeholder else Num(t)
        }
        Kind.NAME -> {
            val us = text.indexOf('_')
            val name = if (us < 0) text else text.take(us)
            val sub = if (us < 0) null else text.drop(us + 1).trimEnd('_')
            // Any characters, since special text mode (`Ctrl+Shift+K`) can type them: the file writes such a name in backticks.
            if (name.isEmpty()) Placeholder else Id(name, sub, style)
        }
    }

    /** What to draw while typing, when the node alone would show something else (`1.`, `M_`). */
    fun shown(): String? = when (kind) {
        Kind.NUMBER -> text.takeIf { (node() as? Num)?.text != text }
        Kind.NAME -> text.takeIf { it.endsWith("_") || it.isEmpty() }
        Kind.STRING -> null
    }

    companion object {
        val NUMBER = Regex("""([0-9]+(\.[0-9]+)?|\.[0-9]+)([eE][+-]?[0-9]+)?[ij]?|0([xX][0-9a-fA-F]+(\.[0-9a-fA-F]+)?|[oO][0-7]+(\.[0-7]+)?|[bB][01]+(\.[01]+)?)""")

        /** A number still being typed: `1.`, `2e`, `2e-`, `0x`, `0x1F.`. */
        val NUMBER_SO_FAR = Regex("""[0-9]*\.?[0-9]*([eE][+-]?[0-9]*)?[ij]?|0[xXoObB][0-9a-fA-F]*\.?[0-9a-fA-F]*""")

        /** The start of a hexadecimal, octal or binary literal. */
        val BASED_START = Regex("""0[xXoObB].*""")

        fun of(node: Expr, caretAtEnd: Boolean): Draft? = when (node) {
            is Id -> {
                val t = node.label
                Draft(Kind.NAME, t, if (caretAtEnd) t.length else 0, node.style)
            }
            is Num -> Draft(Kind.NUMBER, node.text, if (caretAtEnd) node.text.length else 0)
            is Str -> Draft(Kind.STRING, node.value, if (caretAtEnd) node.value.length else 0)
            else -> null
        }
    }
}

/**
 * The insertion cursor: the selected node ([path]) and the [side] of it the bar is on. While a
 * leaf is typed, [draft] holds its text and caret. [explicit] marks a selection widened with
 * Space, which operators then apply to as it is (instead of climbing by precedence).
 */
data class Cursor(val path: Path, val side: Side = Side.RIGHT, val draft: Draft? = null, val explicit: Boolean = false)

/** A statement being edited, and the cursor in it. The node at the cursor always mirrors its draft. */
data class EditState(val statement: Statement, val cursor: Cursor) {
    val node: Any get() = Nodes.at(statement, cursor.path)

    /** The linear text of the statement, as it will be saved. */
    val text: String get() = pub.beermat.engine.syntax.Printer.print(statement)

    companion object {
        /** A new, empty math region. */
        fun empty() = EditState(ExprStatement(Placeholder), Cursor(listOf(0)))

        /** Editing [st] with the cursor at the end of its last leaf. */
        fun of(st: Statement): EditState {
            val leaves = Nodes.leaves(st)
            return EditState(st, Cursor(leaves.lastOrNull() ?: if (Nodes.children(st).isEmpty()) emptyList() else listOf(0)))
        }
    }
}

/** A keystroke the editor understands (docs/editor.md). */
sealed interface Key {
    data class Type(val c: Char) : Key
    data class Ctrl(val c: Char, val shift: Boolean = false) : Key
    data object Backspace : Key
    data object Delete : Key
    data object Left : Key
    data object Right : Key
    data object Space : Key
    data object Insert : Key
    data object Tab : Key
    data object BackTab : Key
    data object Home : Key
    data object End : Key
}

/**
 * The editing operations: each takes a state and a key and returns the new state, or null when
 * the key does nothing there (the app beeps). Pure functions on the tree, so they are tested
 * without a window.
 */
object Editor {

    fun key(s: EditState, k: Key): EditState? = checked {
        // `given` is a whole statement: it can only be deleted.
        if (s.statement == Given) return@checked if (k == Key.Backspace || k == Key.Delete) EditState.empty() else null
        when (k) {
            is Key.Type -> type(s, k.c)
            is Key.Ctrl -> ctrl(s, k.c, k.shift)
            Key.Backspace -> backspace(s)
            Key.Delete -> delete(s)
            Key.Left -> left(s)
            Key.Right -> right(s)
            Key.Space -> s.cursor.draft?.takeIf { it.literal }?.let { type(s, ' ') } ?: keyword(s) ?: widen(s)
            Key.Insert -> s.copy(cursor = s.cursor.copy(side = if (s.cursor.side == Side.LEFT) Side.RIGHT else Side.LEFT))
            Key.Tab -> nextHole(s, forward = true)
            Key.BackTab -> nextHole(s, forward = false)
            Key.Home -> Nodes.leaves(s.statement).firstOrNull()?.let { enter(commit(s), it, atEnd = false) }
            Key.End -> Nodes.leaves(s.statement).lastOrNull()?.let { enter(commit(s), it, atEnd = true) }
        }
    }

    /**
     * Runs an edit, which does nothing (null) unless its result is a valid tree that can be written
     * (`x + 1 := 2` can't be built; a program can't stand inside an expression) with the cursor on
     * one of its nodes. Every edit goes through here, so a key can never leave a broken statement.
     */
    private fun checked(edit: () -> EditState?): EditState? = try {
        edit()?.takeIf { valid(it) }
    } catch (_: RuntimeException) {
        null // e.g. a node that can't go there (IllegalArgumentException), or a key on a line that isn't an expression
    }

    private fun valid(s: EditState): Boolean = try {
        pub.beermat.engine.syntax.Printer.print(s.statement)
        Nodes.at(s.statement, s.cursor.path)
        true
    } catch (_: RuntimeException) {
        false
    }

    /** Typing a sequence of characters, for tests and scripts. Unknown keys fail. */
    fun type(s: EditState, text: String): EditState = text.fold(s) { st, c -> key(st, Key.Type(c)) ?: error("'$c' does nothing at ${st.cursor} in ${st.text}") }

    // Typing.

    private fun type(s: EditState, c: Char): EditState? {
        val d = s.cursor.draft
        if (d != null) typeInDraft(s, d, c)?.let { return it }
        val st = commit(s)
        val node = st.node
        if (node == Placeholder) startLeaf(st, c)?.let { return it }
        // A leaf continues where the cursor is: `x` then `y` is `xy`.
        if (d == null && node is Expr && continues(node, c)) {
            Draft.of(node, st.cursor.side == Side.RIGHT)?.let { draft -> typeInDraft(withDraft(st, draft), draft, c)?.let { return it } }
        }
        // `bt.`: after a name that holds a module, `.` picks one of its functions.
        if (c == '.' && node is Id && st.cursor.side == Side.RIGHT && holdsModule(node)) return member(st)
        return when (c) {
            '+' -> binary(st, Op.ADD)
            '-' -> if (node == Placeholder || st.cursor.side == Side.LEFT) prefix(st, Op.NEG) else binary(st, Op.SUB)
            '*' -> binary(st, Op.MUL)
            '/' -> binary(st, Op.DIV)
            '^' -> binary(st, Op.POW)
            '<' -> binary(st, Op.LT)
            '>' -> binary(st, Op.GT)
            '!' -> postfix(st, Op.FACTORIAL)
            '=' -> equals(st)
            ':' -> define(st, global = false)
            '~' -> define(st, global = true)
            '(' -> open(st)
            '\'' -> wrap(st) { Parens(it) }
            ')' -> close(st)
            '[' -> index(st)
            ']' -> closeIndex(st) ?: programLine(st)
            '{' -> localDefine(st)
            '}' -> programIf(st)
            ',' -> comma(st)
            ';' -> range(st)
            '|' -> wrap(st, select = 0) { Apply(Op.ABS, it) }
            '\\' -> wrap(st, select = 1) { Call(Id("sqrt"), it) }
            '$' -> iteration(st, "sum", limits = false)
            '#' -> iteration(st, "prod", limits = false)
            '?' -> call(st, "diff", 2)
            '&' -> call(st, "int", 4)
            '"' -> if (node == Placeholder) withDraft(st, Draft(Draft.Kind.STRING, "", 0)) else null
            else -> if (Id.isNameStart(c) || c.isDigit() || c == '.') {
                // After a complete operand, a new leaf multiplies it: a number by a unit implicitly.
                val implicit = node is Num || (node is Apply && node.op == Op.MUL && node.implicit)
                binary(st, Op.MUL, implicit = implicit && Id.isNameStart(c))?.let { type(it, c) }
            } else {
                null
            }
        }
    }

    private fun continues(node: Expr, c: Char) = when (node) {
        is Id -> Id.isNameChar(c)
        is Num -> c.isDigit()
        else -> false
    }

    /** A first character in an empty slot. */
    private fun startLeaf(s: EditState, c: Char): EditState? = when {
        Id.isNameStart(c) -> withDraft(s, Draft(Draft.Kind.NAME, c.toString(), 1))
        c.isDigit() || c == '.' -> withDraft(s, Draft(Draft.Kind.NUMBER, c.toString(), 1))
        else -> null
    }

    /** [c] typed inside [d]; null when it doesn't belong to the leaf (it is then an operator). */
    private fun typeInDraft(s: EditState, d: Draft, c: Char): EditState? {
        fun insert(ch: Char) = withDraft(s, d.copy(text = d.text.substring(0, d.caret) + ch + d.text.substring(d.caret), caret = d.caret + 1))
        return when (d.kind) {
            Draft.Kind.STRING -> if (c == '"') commit(s).let { it.copy(cursor = it.cursor.copy(side = Side.RIGHT)) } else insert(c)
            Draft.Kind.NAME -> when {
                // Special text mode: every character goes into the name, but `_` (which starts the subscript) and the backtick.
                d.literal -> if (c == '_' || c == '`') null else insert(c)
                Id.isNameChar(c) -> insert(c)
                c == '.' && '_' !in d.text && d.caret == d.text.length && holdsModule(Id(d.text, style = d.style)) -> member(s)
                (c == '.' || c == '_') && '_' !in d.text && d.caret == d.text.length -> insert('_')
                // Inside a subscript, `.` is kept (`Ω.fo`).
                c == '.' && '_' in d.text && d.caret > d.text.indexOf('_') + 1 -> insert(c)
                else -> null
            }
            Draft.Kind.NUMBER -> {
                val atEnd = d.caret == d.text.length
                when {
                    c.isDigit() -> insert(c)
                    // In a hexadecimal literal `e` is a digit, not an exponent.
                    c == '.' && '.' !in d.text && (Draft.BASED_START.matches(d.text) || 'e' !in d.text.lowercase()) -> insert(c)
                    (c == '+' || c == '-') && atEnd && d.text.lastOrNull()?.lowercaseChar() == 'e' -> insert(c)
                    Id.isNameStart(c) && atEnd -> {
                        val t = d.text + c
                        if (Draft.NUMBER_SO_FAR.matches(t)) insert(c) else splitNumber(s, t)
                    }
                    else -> null
                }
            }
        }
    }

    /** `3m`: a number and then a name, which become an implicit product: 3 m. */
    private fun splitNumber(s: EditState, t: String): EditState? {
        val m = Regex("""([0-9]*\.?[0-9]*(?:[eE][+-]?[0-9]+)?)([\p{L}∇∂°].*)""").matchEntire(t) ?: return null
        val (number, name) = m.destructured
        if (!Draft.NUMBER.matches(number)) return null
        val draft = Draft(Draft.Kind.NAME, name, name.length)
        val product = Apply(Op.MUL, Num(number), draft.node(), implicit = true)
        val placed = place(s.statement, s.cursor.path, product)
        return EditState(placed.statement, Cursor(placed.path + 1, draft = draft))
    }

    /** [s] with the leaf at the cursor being typed as [d]. */
    private fun withDraft(s: EditState, d: Draft): EditState =
        EditState(Nodes.set(s.statement, s.cursor.path, d.node()) as Statement, s.cursor.copy(draft = d, side = Side.RIGHT, explicit = false))

    /** Ends typing the current leaf, if any. */
    fun commit(s: EditState): EditState {
        val d = s.cursor.draft ?: return s
        return s.copy(cursor = s.cursor.copy(draft = null, side = if (d.caret == 0 && d.text.isNotEmpty()) Side.LEFT else Side.RIGHT))
    }

    // Operators.

    /**
     * A binary operator applied to the selection (climbing by precedence unless it was widened).
     * In an empty slot it brings two empty operands, the cursor in the first (`*` → ■·■).
     */
    private fun binary(s: EditState, op: Op, implicit: Boolean = false): EditState? {
        if (s.node == Placeholder) {
            val placed = place(s.statement, s.cursor.path, Apply(op, Placeholder, Placeholder))
            return EditState(placed.statement, Cursor(placed.path + 0))
        }
        var path = s.cursor.path
        if (!s.cursor.explicit && s.cursor.side == Side.RIGHT) {
            while (path.size > 1) {
                val parent = Nodes.at(s.statement, path.dropLast(1))
                if (parent !is Apply || !climbs(parent, path.last(), op)) break
                path = path.dropLast(1)
            }
        }
        val sel = Nodes.at(s.statement, path) as? Expr ?: return null
        if (sel is Placeholder) return null
        val left = s.cursor.side == Side.LEFT
        val node = if (left) Apply(op, Placeholder, guard(op, 1, sel)) else Apply(op, guard(op, 0, sel), Placeholder, implicit = implicit)
        val placed = place(s.statement, path, node)
        return EditState(placed.statement, Cursor(placed.path + if (left) 0 else 1))
    }

    /** Whether typing [op] after the right operand of [parent] applies it to [parent] instead. */
    private fun climbs(parent: Apply, index: Int, op: Op): Boolean {
        val p = parent.op
        if (p == Op.ABS || p == Op.ON_ERROR || p == Op.COLUMN) return false
        val rightmost = index == parent.args.size - 1
        if (!rightmost) return false
        if (p.arity == 1) return p == Op.NEG || p == Op.POS || p == Op.NOT
        return p.precedence > op.precedence || (p.precedence == op.precedence && op != Op.POW)
    }

    private fun prefix(s: EditState, op: Op): EditState? {
        val sel = s.node as? Expr ?: return null
        val placed = place(s.statement, s.cursor.path, Apply(op, guard(op, 0, sel)))
        return EditState(placed.statement, Cursor(placed.path + 0, side = if (sel == Placeholder) Side.RIGHT else Side.LEFT))
    }

    /** `n!`, `Mᵀ`; in an empty slot, the operator with its operand to fill in. */
    private fun postfix(s: EditState, op: Op): EditState? {
        val sel = s.node as? Expr ?: return null
        val placed = place(s.statement, s.cursor.path, Apply(op, guard(op, 0, sel)))
        return EditState(placed.statement, Cursor(if (sel == Placeholder) placed.path + 0 else placed.path))
    }

    /** `=`: an evaluation; after `<`, `>` or `!`, the comparisons ≤, ≥ and ≠. */
    private fun equals(s: EditState): EditState? {
        val path = s.cursor.path
        val parent = if (path.size > 1) Nodes.at(s.statement, path.dropLast(1)) else null
        val node = s.node
        if (node == Placeholder && parent is Apply && (parent.op == Op.LT || parent.op == Op.GT)) {
            val op = if (parent.op == Op.LT) Op.LE else Op.GE
            return s.copy(statement = Nodes.set(s.statement, path.dropLast(1), parent.copy(op = op)) as Statement)
        }
        // `!=` is ≠, except that a whole statement `n!` followed by `=` is evaluated.
        if (node is Apply && node.op == Op.FACTORIAL && !(s.statement is ExprStatement && path.size == 1)) {
            val placed = place(s.statement, path, Apply(Op.NE, node.args[0], Placeholder))
            return EditState(placed.statement, Cursor(placed.path + 1))
        }
        return when (val st = s.statement) {
            is ExprStatement -> EditState(Evaluate(st.expr), Cursor(listOf(1)))
            is Define -> if (st.result == null) EditState(st.copy(result = ResultSlot()), Cursor(listOf(2))) else null
            else -> null
        }
    }

    /** `:` and `~`: the statement becomes a definition of what was typed. */
    private fun define(s: EditState, global: Boolean): EditState? {
        val st = s.statement as? ExprStatement ?: return null
        val target = st.expr.let { if (it is Parens) it.inner else it }
        if (!Define.isDefinitionTarget(target)) return null
        // On an empty spot, `■ := ■` with the cursor on the name.
        return EditState(Define(target, Placeholder, global), Cursor(listOf(if (target == Placeholder) 0 else 1)))
    }

    /** `(`: a call after a name, parentheses in an empty slot, else parentheses around the selection. */
    private fun open(s: EditState): EditState? {
        val node = s.node
        return when {
            node == Placeholder -> {
                val placed = place(s.statement, s.cursor.path, Parens(Placeholder))
                EditState(placed.statement, Cursor(placed.path + 0))
            }
            // `bt.f(`: the module's function is called, not the name inside it.
            node is Id && s.cursor.side == Side.RIGHT && memberAt(s) != null -> {
                val member = s.cursor.path.dropLast(1)
                val placed = place(s.statement, member, Call(Nodes.at(s.statement, member) as Expr, Placeholder))
                EditState(placed.statement, Cursor(placed.path + 1))
            }
            node is Id && s.cursor.side == Side.RIGHT -> {
                val placed = place(s.statement, s.cursor.path, Call(node, Placeholder))
                EditState(placed.statement, Cursor(placed.path + 1))
            }
            else -> wrap(s) { Parens(it) }
        }
    }

    /** `)`: the cursor moves out to the enclosing parentheses or call. */
    private fun close(s: EditState): EditState? {
        val path = s.cursor.path
        // What is drawn with parentheses first: in `(-b + √(b²-4ac))`, `)` closes the parentheses,
        // not the radical. A radical typed as `sqrt(` is closed when nothing else is open.
        var notationCall: Path? = null
        for (k in path.size - 1 downTo 1) {
            val p = path.take(k)
            val n = Nodes.at(s.statement, p)
            val notation = n is Call && (n.fn as? Id)?.name in SPECIAL
            if (n is Parens || (n is Call && !notation)) return EditState(s.statement, Cursor(p))
            if (notation && notationCall == null) notationCall = p
        }
        return notationCall?.let { EditState(s.statement, Cursor(it)) }
    }

    /** `]` inside an index: the cursor moves out to the indexed name. (Elsewhere `]` will add a program line.) */
    private fun closeIndex(s: EditState): EditState? {
        val path = s.cursor.path
        for (k in path.size - 1 downTo 1) {
            if (Nodes.at(s.statement, path.take(k)) is Index) return EditState(s.statement, Cursor(path.take(k)))
        }
        return null
    }

    private fun index(s: EditState): EditState? {
        val sel = s.node as? Expr ?: return null
        val target = if (Nodes.precedence(sel) < 12) Parens(sel) else sel
        val placed = place(s.statement, s.cursor.path, Index(target, Placeholder))
        return EditState(placed.statement, Cursor(placed.path + if (sel == Placeholder) 0 else 1))
    }

    /** `,`: the next argument of a call, a second index, or the second value of a range. */
    private fun comma(s: EditState): EditState? {
        val path = s.cursor.path
        for (k in path.size - 1 downTo 1) {
            val p = path.take(k)
            val i = path[k]
            when (val n = Nodes.at(s.statement, p)) {
                is Call -> if (i >= 1) {
                    val args = n.args.toMutableList().also { it.add(i, Placeholder) }
                    return EditState(Nodes.set(s.statement, p, n.copy(args = args)) as Statement, Cursor(p + (i + 1)))
                }
                is Index -> if (i >= 1 && n.indices.size == 1) {
                    return EditState(Nodes.set(s.statement, p, Index(n.target, n.indices[0], Placeholder)) as Statement, Cursor(p + 2))
                }
                is Range -> if (i == 0 && n.second == null) {
                    return EditState(Nodes.set(s.statement, p, n.copy(second = Placeholder)) as Statement, Cursor(p + 1))
                }
                is Parens, is Apply, is Matrix -> continue
                else -> return null
            }
        }
        // `i := 0,2;10`: in a definition's value, a stepped range.
        val st = s.statement
        if (st is Define && path == listOf(1) && st.value != Placeholder) {
            return EditState(st.copy(value = Range(st.value, Placeholder, Placeholder)), Cursor(listOf(1, 1)))
        }
        return null
    }

    /** `;`: a range from the selection to a new last value. */
    private fun range(s: EditState): EditState? {
        val path = s.cursor.path
        val parent = if (path.size > 1) Nodes.at(s.statement, path.dropLast(1)) else null
        if (parent is Range && parent.second != null && path.last() == 1) return EditState(s.statement, Cursor(path.dropLast(1) + 2))
        val sel = s.node as? Expr ?: return null
        val placed = place(s.statement, s.cursor.path, Range(sel, null, Placeholder))
        return EditState(placed.statement, Cursor(placed.path + 1))
    }

    /**
     * [build] around the selection. In an empty slot the cursor goes inside, to child [select]
     * of the new node; otherwise the new node is selected.
     */
    private fun wrap(s: EditState, select: Int = 0, build: (Expr) -> Expr): EditState? {
        val sel = s.node as? Expr ?: return null
        val placed = place(s.statement, s.cursor.path, build(sel))
        return EditState(placed.statement, Cursor(if (sel == Placeholder) placed.path + select else placed.path))
    }

    /**
     * Whether a name holds a module (`bt := use "file.py"`), so that `.` after it picks one of its
     * functions rather than starting a subscript. The session sets it from the last evaluation.
     */
    @Volatile
    var holdsModule: (Id) -> Boolean = { false }

    /** `bt.`: the name and an empty slot for the function's, the cursor in it. */
    private fun member(s: EditState): EditState? {
        val st = commit(s)
        val sel = st.node as? Expr ?: return null
        val placed = place(st.statement, st.cursor.path, Apply(Op.MEMBER, sel, Placeholder))
        return EditState(placed.statement, Cursor(placed.path + 1))
    }

    /** The module function slot the cursor is in (`bt.mo|`): the module's name and what is typed so far; null elsewhere. */
    fun memberAt(s: EditState): Pair<Id, String>? {
        val path = s.cursor.path
        if (path.isEmpty() || path.last() != 1) return null
        val parent = Nodes.at(s.statement, path.dropLast(1)) as? Apply ?: return null
        if (parent.op != Op.MEMBER) return null
        val module = parent.args[0] as? Id ?: return null
        val typed = s.cursor.draft?.takeIf { it.kind == Draft.Kind.NAME }?.text ?: (parent.args[1] as? Id)?.text ?: ""
        return module to typed
    }

    /** Completion: the function [name] put in the slot the cursor is in, the cursor after it (ready for `(`). */
    fun complete(s: EditState, name: String): EditState? = checked {
        val st = commit(s)
        memberAt(st) ?: return@checked null
        EditState(Nodes.set(st.statement, st.cursor.path, Id(name)) as Statement, Cursor(st.cursor.path, Side.RIGHT))
    }

    /** A call of [name] on the selection and [extra] - 1 empty slots; the cursor goes to the first empty one. */
    private fun call(s: EditState, name: String, extra: Int): EditState? {
        val sel = s.node as? Expr ?: return null
        val args = listOf(sel) + List(extra - 1) { Placeholder }
        val placed = place(s.statement, s.cursor.path, Call(Id(name), args))
        return EditState(placed.statement, Cursor(placed.path + if (sel == Placeholder) 1 else 2))
    }

    /** Σ or Π: over a range variable (`$`, `#`), or with limits (`Ctrl+Shift+4`, `Ctrl+Shift+3`). */
    private fun iteration(s: EditState, name: String, limits: Boolean): EditState? = call(s, name, if (limits) 4 else 2)

    // Control keys.

    private fun ctrl(s: EditState, c: Char, shift: Boolean): EditState? {
        val st = commit(s)
        return when (c.lowercaseChar()) {
            '=' -> binary(st, Op.EQ)
            '3' -> if (shift) iteration(st, "prod", limits = true) else binary(st, Op.NE)
            '4' -> if (shift) iteration(st, "sum", limits = true) else null
            '9' -> binary(st, Op.LE)
            '0' -> binary(st, Op.GE)
            '1' -> postfix(st, Op.TRANSPOSE)
            '6' -> binary(st, Op.COLUMN)
            '-' -> wrap(st, select = 1) { Call(Id("vec"), it) }
            '\\' -> call(st, "nthroot", 2)
            '.' -> symbolic(st, addKeyword = shift)
            'g' -> greek(s)
            'b' -> style(s, bold = true)
            'i' -> style(s, bold = false)
            'k' -> if (shift) specialText(s) else null
            else -> null
        }
    }

    /**
     * `Ctrl+Shift+K`: special text mode. What is typed then goes into the name, operators
     * and spaces included (`θ*`, `x%`); the file writes such a name in backticks. Again to end it.
     */
    private fun specialText(s: EditState): EditState? {
        s.cursor.draft?.let { d -> return if (d.kind == Draft.Kind.NAME) withDraft(s, d.copy(literal = !d.literal)) else null }
        return when (val node = s.node) {
            Placeholder -> withDraft(s, Draft(Draft.Kind.NAME, "", 0, literal = true))
            is Id -> Draft.of(node, atEnd(s))?.let { withDraft(s, it.copy(literal = true)) }
            else -> null
        }
    }

    /** `Ctrl+.`: a symbolic evaluation, `expr → ■`; `Ctrl+Shift+.` adds a keyword slot (`expr → ■ → …`). */
    private fun symbolic(s: EditState, addKeyword: Boolean): EditState? {
        val (expr, keywords) = when (val st = s.statement) {
            is ExprStatement -> st.expr to emptyList()
            is Evaluate -> st.expr to emptyList()
            is Symbolic -> if (addKeyword) st.expr to st.keywords else return null
            else -> return null
        }
        if (!addKeyword) return EditState(Symbolic(expr, keywords), Cursor(listOf(0)))
        return EditState(Symbolic(expr, keywords + Placeholder), Cursor(listOf(keywords.size + 1)))
    }

    /** `Ctrl+G`: the letter before the caret becomes Greek (a → α). */
    private fun greek(s: EditState): EditState? {
        val d = s.cursor.draft ?: Draft.of(s.node as? Expr ?: return null, atEnd(s))?.takeIf { it.kind == Draft.Kind.NAME } ?: return null
        if (d.kind != Draft.Kind.NAME || d.caret == 0) return null
        val k = d.caret - 1
        val g = GREEK[d.text[k]] ?: LATIN[d.text[k]] ?: return null
        return withDraft(s, d.copy(text = d.text.substring(0, k) + g + d.text.substring(k + 1)))
    }

    private fun atEnd(s: EditState) = s.cursor.side == Side.RIGHT

    /** `Ctrl+B` toggles bold, `Ctrl+I` italic (plain); a name with neither is upright. */
    private fun style(s: EditState, bold: Boolean): EditState? {
        val d = s.cursor.draft
        val id = (d?.node() ?: s.node) as? Id ?: return null
        val next = when {
            bold -> if (id.style == Id.Style.BOLD) Id.Style.UPRIGHT else Id.Style.BOLD
            else -> if (id.style == Id.Style.PLAIN) Id.Style.UPRIGHT else Id.Style.PLAIN
        }
        return if (d != null) withDraft(s, d.copy(style = next)) else s.copy(statement = Nodes.set(s.statement, s.cursor.path, id.copy(style = next)) as Statement)
    }

    // Deleting.

    private fun backspace(s: EditState): EditState? {
        val d = s.cursor.draft
        if (d != null) {
            if (d.caret == 0) return null
            val text = d.text.removeRange(d.caret - 1, d.caret)
            return if (text.isEmpty()) hole(s) else withDraft(s, d.copy(text = text, caret = d.caret - 1))
        }
        val node = s.node
        return when {
            node == Placeholder -> removeHole(s)
            node is Expr && Nodes.isLeaf(node) && s.cursor.side == Side.RIGHT -> Draft.of(node, true)?.let { backspace(withDraft(s, it)) }
            node is Expr -> hole(s)
            else -> null
        }
    }

    private fun delete(s: EditState): EditState? {
        val d = s.cursor.draft
        if (d != null) {
            if (d.caret == d.text.length) return null
            val text = d.text.removeRange(d.caret, d.caret + 1)
            return if (text.isEmpty()) hole(s) else withDraft(s, d.copy(text = text))
        }
        val node = s.node
        return when {
            node == Placeholder -> removeHole(s)
            node is Expr && Nodes.isLeaf(node) && s.cursor.side == Side.LEFT -> Draft.of(node, false)?.let { delete(withDraft(s, it)) }
            node is Expr -> hole(s)
            else -> null
        }
    }

    /** The selection replaced by an empty slot. */
    private fun hole(s: EditState) = EditState(Nodes.set(s.statement, s.cursor.path, Placeholder) as Statement, Cursor(s.cursor.path))

    /** Backspace in an empty slot removes what made the slot: the operator, the argument, the `=`. */
    private fun removeHole(s: EditState): EditState? {
        val path = s.cursor.path
        if (path.size == 1) {
            val st = s.statement
            return when {
                st is Define && path[0] == 2 -> EditState(st.copy(result = null), Cursor(listOf(1)))
                st is Define && path[0] == 1 -> EditState(ExprStatement(st.target), Cursor(listOf(0)))
                st is Evaluate && path[0] == 1 -> EditState(ExprStatement(st.expr), Cursor(listOf(0)))
                st is Symbolic && path[0] >= 1 -> EditState(st.copy(keywords = st.keywords.filterIndexed { k, _ -> k != path[0] - 1 }), Cursor(listOf(path[0] - 1)))
                else -> null
            }
        }
        val pp = path.dropLast(1)
        val i = path.last()
        val replacement: Pair<Any, Path>? = when (val p = Nodes.at(s.statement, pp)) {
            is Apply -> if (p.args.size == 2) unwrapParens(p.args[1 - i]) to pp else Placeholder to pp
            is Parens -> Placeholder to pp
            is Call -> when {
                i >= 1 && p.args.size > 1 -> p.copy(args = p.args.filterIndexed { k, _ -> k != i - 1 }) to (pp + maxOf(1, i - 1))
                i >= 1 && p.fn is Id && (p.fn as Id).name in SPECIAL -> Placeholder to pp
                i >= 1 -> p.fn to pp
                else -> null
            }
            is Index -> if (p.indices.size == 2) Index(p.target, p.indices[2 - i]) to (pp + 1) else p.target to pp
            is Range -> if (p.second != null && i == 1) p.copy(second = null) to (pp + 0) else p.first to pp
            // An empty program line goes; `x ← ■` goes back to `x`; `■ if c`'s empty condition drops the if.
            is Line -> return removeLine(s, pp)
            is LocalDefine -> if (i == 1) Line(p.target) to (pp + 0) else null
            is IfThen -> if (i == 0 && p.body.size == 1) p.body[0] to pp else null
            is Return -> Line(Placeholder) to (pp + 0)
            else -> null
        }
        val (node, select) = replacement ?: return null
        if (node is Expr && select == pp) {
            val placed = place(s.statement, pp, node)
            return EditState(placed.statement, Cursor(placed.path))
        }
        return EditState(Nodes.set(s.statement, pp, node) as Statement, Cursor(select))
    }

    private fun unwrapParens(e: Expr) = if (e is Parens) e.inner else e

    // Programs.

    /** The lines around [path]: the node holding them, the child index of its first line, and the child index of the line [path] is in. */
    private fun lines(root: Statement, path: Path): Triple<Path, Int, Int>? {
        for (k in path.size - 1 downTo 0) {
            val holder = path.take(k)
            val first = when (Nodes.at(root, holder)) {
                is Program, is Otherwise -> 0
                is IfThen, is While -> 1
                is For -> 2
                else -> continue
            }
            if (path[k] >= first) return Triple(holder, first, path[k])
        }
        return null
    }

    /** [holder] with its lines replaced. */
    private fun withLines(holder: Any, lines: List<ProgramLine>): Any = when (holder) {
        is Program -> Program(lines)
        is IfThen -> holder.copy(body = lines)
        is Otherwise -> Otherwise(lines)
        is For -> holder.copy(body = lines)
        is While -> holder.copy(body = lines)
        else -> error("not a holder of lines")
    }

    private fun linesOf(holder: Any): List<ProgramLine> = when (holder) {
        is Program -> holder.lines
        is IfThen -> holder.body
        is Otherwise -> holder.body
        is For -> holder.body
        is While -> holder.body
        else -> error("not a holder of lines")
    }

    /** `]`: a new program line below the current one; in a definition's value, the value becomes a program. */
    private fun programLine(s: EditState): EditState? {
        val ctx = lines(s.statement, s.cursor.path)
        if (ctx != null) {
            val (holder, first, i) = ctx
            val node = Nodes.at(s.statement, holder)
            val lines = linesOf(node).toMutableList().also { it.add(i - first + 1, Line(Placeholder)) }
            return EditState(Nodes.set(s.statement, holder, withLines(node, lines)) as Statement, Cursor(holder + (i + 1) + 0))
        }
        if (s.cursor.path.size > 1) return null
        val v = s.node as? Expr ?: return null
        val lines = listOf(Line(v), Line(Placeholder))
        return newProgram(s, lines, if (v == Placeholder) listOf(0, 0) else listOf(1, 0))
    }

    /**
     * Outside a program, a program made of [lines] in place of the whole value: of the definition,
     * or on an empty spot, of a new definition `■ := …` (a program is only ever a definition's
     * value). The cursor goes to [select], relative to the program.
     */
    private fun newProgram(s: EditState, lines: List<ProgramLine>, select: Path): EditState? {
        val program = Program(lines)
        return when (val st = s.statement) {
            is Define -> if (s.cursor.path == listOf(1)) EditState(st.copy(value = program), Cursor(listOf(1) + select)) else null
            is ExprStatement -> if (s.cursor.path == listOf(0)) EditState(Define(Placeholder, program), Cursor(listOf(1) + select)) else null
            else -> null
        }
    }

    /** The program line the cursor is in: its path. */
    private fun linePath(s: EditState): Path? {
        val (holder, _, i) = lines(s.statement, s.cursor.path) ?: return null
        return holder + i
    }

    /** `{`: the line `x` becomes `x ← ■`. */
    private fun localDefine(s: EditState): EditState? {
        val p = linePath(s) ?: return (s.node as? Id)?.let { newProgram(s, listOf(LocalDefine(it, Placeholder)), listOf(0, 1)) }
        val line = Nodes.at(s.statement, p) as? Line ?: return null
        val target = line.expr
        if (!(target is Id || (target is Index && target.target is Id))) return null
        return EditState(Nodes.set(s.statement, p, LocalDefine(target, Placeholder)) as Statement, Cursor(p + 1))
    }

    /**
     * `}`: the line becomes the body of an `if`, the cursor in its condition; on an empty line, in
     * the body, drawn first (`■ if ■`). Outside a program, the value does.
     */
    private fun programIf(s: EditState): EditState? {
        val p = linePath(s) ?: return (s.node as? Expr)?.let { v ->
            newProgram(s, listOf(IfThen(Placeholder, listOf(Line(v)))), if (v == Placeholder) listOf(0, 1, 0) else listOf(0, 0))
        }
        val line = Nodes.at(s.statement, p) as ProgramLine
        val empty = line is Line && line.expr == Placeholder
        return EditState(Nodes.set(s.statement, p, IfThen(Placeholder, listOf(line))) as Statement, Cursor(if (empty) p + 1 + 0 else p + 0))
    }

    /** A keyword typed as a name, then Space: its structure (docs/editor.md). */
    private fun keyword(s: EditState): EditState? {
        val d = s.cursor.draft ?: return null
        if (d.kind != Draft.Kind.NAME || d.style != Id.Style.PLAIN) return null
        return structure(s, d.text)
    }

    /**
     * A keyword's structure at the cursor, as its toolbar button: `if`, `for`, `given`… In an
     * empty slot, or replacing what is there when it is a program line's whole expression.
     */
    fun keyword(s: EditState, word: String): EditState? = checked {
        val st = commit(s)
        if (st.node != Placeholder) {
            // A program line that already says something: the keyword goes on a new line below.
            if (word in LINE_WORDS && lines(st.statement, st.cursor.path) != null) programLine(st)?.let { structure(it, word) } else null
        } else {
            structure(st, word)
        }
    }

    /** [word]'s structure in place of the node at the cursor (an empty slot, or the keyword typed as a name). */
    private fun structure(s: EditState, word: String): EditState? {
        val path = s.cursor.path
        if (word == "given") return if (s.statement is ExprStatement && path == listOf(0)) EditState(Given, Cursor(emptyList())) else null
        if (word == "try") {
            val placed = place(s.statement, path, Apply(Op.ON_ERROR, Placeholder, Placeholder))
            return EditState(placed.statement, Cursor(placed.path + 0))
        }
        // The other keywords make a whole program line: in the program, or as a new one.
        val (line, select) = when (word) {
            "if" -> IfThen(Placeholder, listOf(Line(Placeholder))) to listOf(1, 0)
            "for" -> For(Placeholder, Placeholder, listOf(Line(Placeholder))) to listOf(0)
            "while" -> While(Placeholder, listOf(Line(Placeholder))) to listOf(0)
            "otherwise" -> Otherwise(listOf(Line(Placeholder))) to listOf(0, 0)
            "return" -> Return(Placeholder) to listOf(0)
            "break" -> Break to emptyList()
            "continue" -> Continue to emptyList()
            else -> return null
        }
        val p = path.dropLast(1)
        if (path.lastOrNull() == 0 && Nodes.at(s.statement, p) is Line) {
            return EditState(Nodes.set(s.statement, p, line) as Statement, Cursor(p + select))
        }
        return newProgram(s.copy(statement = Nodes.set(s.statement, path, Placeholder) as Statement), listOf(line), listOf(0) + select)
    }

    private val LINE_WORDS = setOf("if", "for", "while", "otherwise", "return", "break", "continue")

    /** Removes the (empty) program line at [p]; the last line of a program leaves an empty value. */
    private fun removeLine(s: EditState, p: Path): EditState? {
        val (holder, first, i) = lines(s.statement, p + 0) ?: return null
        val node = Nodes.at(s.statement, holder)
        val lines = linesOf(node)
        if (lines.size == 1) {
            return if (node is Program) EditState(Nodes.set(s.statement, holder, Placeholder) as Statement, Cursor(holder)) else null
        }
        val rest = lines.filterIndexed { k, _ -> k != i - first }
        val statement = Nodes.set(s.statement, holder, withLines(node, rest)) as Statement
        // The cursor goes to the end of the line above (or the one that took this one's place).
        val target = holder + (if (i > first) i - 1 else i)
        val leaf = Nodes.leaves(Nodes.at(statement, target)).lastOrNull()?.let { target + it } ?: target
        return enter(EditState(statement, Cursor(target)), leaf, atEnd = true)
    }

    /** `Ctrl+M`: a matrix of empty slots in place of the selection, the cursor in its first cell. */
    /** The multiplication the cursor is in (the innermost), for the right-click menu; null when there's none. */
    fun multiplicationAt(s: EditState): Pair<Path, Apply>? {
        val st = commit(s)
        return generateSequence(st.cursor.path) { if (it.isEmpty()) null else it.dropLast(1) }
            .firstNotNullOfOrNull { p -> (Nodes.at(st.statement, p) as? Apply)?.takeIf { it.op == Op.MUL }?.let { p to it } }
    }

    /**
     * Right-click on a multiplication: draw it with a dot, a thin space, or (null) as the sheet's
     * default says (docs/syntax.md#multiplication-style). A thin space needs a name on the right (`q L`).
     */
    fun multiplication(s: EditState, style: Multiplication?): EditState? = checked {
        val st = commit(s)
        val (path, a) = multiplicationAt(st) ?: return@checked null
        if (style == Multiplication.SPACE && !Printer.canJuxtapose(a.args[1])) return@checked null
        EditState(Nodes.set(st.statement, path, a.copy(implicit = style == Multiplication.SPACE, dot = style == Multiplication.DOT)) as Statement, st.cursor)
    }

    fun matrix(s: EditState, rows: Int, cols: Int): EditState? = checked {
        val st = commit(s)
        if (rows !in 1..100 || cols !in 1..100 || st.node != Placeholder) {
            null
        } else {
            val placed = place(st.statement, st.cursor.path, Matrix(List(rows) { List(cols) { Placeholder } }))
            EditState(placed.statement, Cursor(placed.path + 0))
        }
    }

    /** The innermost matrix the cursor is in, and the row and column of the cell it's in; null outside a matrix. */
    fun matrixAt(s: EditState): Triple<Path, Int, Int>? {
        val path = commit(s).cursor.path
        for (k in path.indices.reversed()) {
            val m = Nodes.at(s.statement, path.take(k)) as? Matrix ?: continue
            val cols = m.rows[0].size
            return Triple(path.take(k), path[k] / cols, path[k] % cols)
        }
        return null
    }

    /**
     * `Ctrl+M` in a matrix, Insert and Delete: [insert] adds [rows] empty rows below the
     * cursor's and [cols] empty columns to its right; otherwise that many are removed, starting at the
     * cursor's. Removing them all leaves an empty slot.
     */
    fun resizeMatrix(s: EditState, rows: Int, cols: Int, insert: Boolean): EditState? = checked {
        val st = commit(s)
        val (path, r, c) = matrixAt(st) ?: return@checked null
        if (rows !in 0..100 || cols !in 0..100 || rows + cols == 0) return@checked null
        val m = Nodes.at(st.statement, path) as Matrix
        val grid = if (insert) {
            val wide = m.rows.map { row -> row.take(c + 1) + List(cols) { Placeholder } + row.drop(c + 1) }
            wide.take(r + 1) + List(rows) { List(wide[0].size) { Placeholder } } + wide.drop(r + 1)
        } else {
            m.rows.filterIndexed { k, _ -> k !in r until r + rows }.map { row -> row.filterIndexed { k, _ -> k !in c until c + cols } }
        }
        if (grid.isEmpty() || grid[0].isEmpty()) {
            EditState(Nodes.set(st.statement, path, Placeholder) as Statement, Cursor(path))
        } else {
            // The cursor stays in its cell, or the nearest one left.
            val (r2, c2) = minOf(r, grid.size - 1) to minOf(c, grid[0].size - 1)
            val statement = Nodes.set(st.statement, path, Matrix(grid)) as Statement
            val cell = path + (r2 * grid[0].size + c2)
            val leaf = Nodes.leaves(Nodes.at(statement, cell)).lastOrNull()?.let { cell + it } ?: cell
            enter(EditState(statement, Cursor(cell)), leaf, atEnd = true)
        }
    }

    // Moving.

    private fun widen(s: EditState): EditState? {
        if (s.cursor.draft != null) return commit(s).let { it.copy(cursor = it.cursor.copy(explicit = true)) }
        val path = s.cursor.path
        if (path.size <= 1) return s.copy(cursor = s.cursor.copy(explicit = true))
        return EditState(s.statement, Cursor(path.dropLast(1), s.cursor.side, explicit = true))
    }

    private fun right(s: EditState): EditState? {
        val d = s.cursor.draft
        if (d != null && d.caret < d.text.length) return moveCaret(s, d, +1)
        val st = commit(s)
        if (d == null && st.cursor.side == Side.LEFT && Nodes.isLeaf(st.node)) {
            // From the left side of a leaf into it: past its first character.
            val inside = enter(st, st.cursor.path, atEnd = false)
            val dd = inside.cursor.draft ?: return st.copy(cursor = st.cursor.copy(side = Side.RIGHT))
            return moveCaret(inside, dd, +1)
        }
        val leaves = Nodes.leaves(st.statement)
        return leaves.getOrNull(leaves.indexOf(leafOf(st)) + 1)?.let { enter(st, it, atEnd = false) }
    }

    private fun left(s: EditState): EditState? {
        val d = s.cursor.draft
        if (d != null && d.caret > 0) return moveCaret(s, d, -1)
        val st = commit(s)
        if (d == null && st.cursor.side == Side.RIGHT && Nodes.isLeaf(st.node)) {
            val inside = enter(st, st.cursor.path, atEnd = true)
            val dd = inside.cursor.draft ?: return st.copy(cursor = st.cursor.copy(side = Side.LEFT))
            return moveCaret(inside, dd, -1)
        }
        val leaves = Nodes.leaves(st.statement)
        return leaves.getOrNull(leaves.indexOf(leafOf(st)) - 1)?.let { enter(st, it, atEnd = true) }
    }

    private fun moveCaret(s: EditState, d: Draft, by: Int) =
        s.copy(cursor = s.cursor.copy(draft = d.copy(caret = (d.caret + by).coerceIn(0, d.text.length))))

    /** The leaf the cursor is at: the selection itself, or its first or last leaf. */
    private fun leafOf(s: EditState): Path {
        val leaves = Nodes.leaves(s.statement)
        val path = s.cursor.path
        if (path in leaves) return path
        val inside = leaves.filter { it.size > path.size && it.take(path.size) == path }
        return (if (s.cursor.side == Side.LEFT) inside.firstOrNull() else inside.lastOrNull()) ?: path
    }

    /** The cursor on the leaf at [path]: a caret at its start or end, or the slot selected. */
    fun enter(s: EditState, path: Path, atEnd: Boolean): EditState {
        val st = commit(s)
        val node = Nodes.at(st.statement, path)
        val draft = (node as? Expr)?.let { Draft.of(it, atEnd) }
        if (draft == null) return EditState(st.statement, Cursor(path, if (atEnd) Side.RIGHT else Side.LEFT))
        // A fresh node instance, so the view can tell the leaf being typed from equal ones.
        return EditState(Nodes.set(st.statement, path, draft.node()) as Statement, Cursor(path, Side.RIGHT, draft))
    }

    /** Selecting [path] (a click), with the caret at [caret] when it is a leaf with text. */
    fun select(s: EditState, path: Path, caret: Int? = null): EditState {
        val st = commit(s)
        val node = Nodes.at(st.statement, path)
        val draft = (node as? Expr)?.let { Draft.of(it, true) }
        if (draft == null || caret == null) return EditState(st.statement, Cursor(path))
        val d = draft.copy(caret = caret.coerceIn(0, draft.text.length))
        return EditState(Nodes.set(st.statement, path, d.node()) as Statement, Cursor(path, Side.RIGHT, d))
    }

    private fun nextHole(s: EditState, forward: Boolean): EditState? {
        val st = commit(s)
        val holes = Nodes.leaves(st.statement).filter { Nodes.at(st.statement, it) == Placeholder }
        if (holes.isEmpty()) return null
        val all = Nodes.leaves(st.statement)
        val here = all.indexOf(leafOf(st))
        val next = if (forward) holes.firstOrNull { all.indexOf(it) > here } ?: holes.first()
        else holes.lastOrNull { all.indexOf(it) < here } ?: holes.last()
        return EditState(st.statement, Cursor(next))
    }

    // Placing nodes.

    private class Placed(val statement: Statement, val path: Path)

    /** [node] at [path], in parentheses if its parent needs them there (as the printer would add). */
    private fun place(root: Statement, path: Path, node: Expr): Placed {
        val parent = if (path.size > 1) Nodes.at(root, path.dropLast(1)) else null
        val wrapped = parent != null && needsParens(parent, path.last(), node)
        val statement = Nodes.set(root, path, if (wrapped) Parens(node) else node) as Statement
        return Placed(statement, if (wrapped) path + 0 else path)
    }

    /** [e] as operand [i] of [op], in parentheses when it binds too loosely to stand there. */
    private fun guard(op: Op, i: Int, e: Expr): Expr = if (needsParens(Apply(op, List(op.arity) { Placeholder }), i, e)) Parens(e) else e

    /** The printer's rules (Printer.canonical): whether [child] needs parentheses as child [i] of [parent]. */
    fun needsParens(parent: Any, i: Int, child: Expr): Boolean {
        val p = Nodes.precedence(child)
        return when (parent) {
            is Apply -> when (val op = parent.op) {
                Op.ABS, Op.ON_ERROR -> false
                Op.NEG, Op.POS -> p < Op.NEG.precedence
                Op.NOT -> p < Op.NOT.precedence
                Op.FACTORIAL, Op.TRANSPOSE -> p < Op.FACTORIAL.precedence
                Op.COLUMN -> p < if (i == 0) Op.FACTORIAL.precedence else Op.ADD.precedence
                Op.POW -> p < if (i == 0) Op.FACTORIAL.precedence else Op.NEG.precedence
                Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE -> p < if (i == 0) op.precedence else Op.ADD.precedence
                else -> p < if (i == 0) op.precedence else op.precedence + 1
            }
            is Call, is Index -> i == 0 && p < Op.FACTORIAL.precedence
            else -> false
        }
    }

    /** Calls drawn as notation, whose last argument removed leaves nothing (`sqrt(■)` → ■). */
    private val SPECIAL = setOf("sqrt", "nthroot", "sum", "prod", "vec", "diff", "int")

    private val GREEK = mapOf(
        'a' to 'α', 'b' to 'β', 'g' to 'γ', 'd' to 'δ', 'e' to 'ε', 'z' to 'ζ', 'h' to 'η', 'q' to 'θ', 'i' to 'ι',
        'k' to 'κ', 'l' to 'λ', 'm' to 'μ', 'n' to 'ν', 'x' to 'ξ', 'o' to 'ο', 'p' to 'π', 'r' to 'ρ', 's' to 'σ',
        't' to 'τ', 'u' to 'υ', 'f' to 'φ', 'c' to 'χ', 'y' to 'ψ', 'w' to 'ω', 'j' to 'ϕ', 'v' to 'ϖ',
        'A' to 'Α', 'B' to 'Β', 'G' to 'Γ', 'D' to 'Δ', 'E' to 'Ε', 'Z' to 'Ζ', 'H' to 'Η', 'Q' to 'Θ', 'I' to 'Ι',
        'K' to 'Κ', 'L' to 'Λ', 'M' to 'Μ', 'N' to 'Ν', 'X' to 'Ξ', 'O' to 'Ο', 'P' to 'Π', 'R' to 'Ρ', 'S' to 'Σ',
        'T' to 'Τ', 'U' to 'Υ', 'F' to 'Φ', 'C' to 'Χ', 'Y' to 'Ψ', 'W' to 'Ω',
    )

    /** Ctrl+G on a Greek letter turns it back. */
    private val LATIN = GREEK.entries.associate { (k, v) -> v to k }
}
