package pub.beermat.app.editor

import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Continue
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Otherwise
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.ProgramLine
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.ResultSlot
import pub.beermat.engine.syntax.Return
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.syntax.Symbolic
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.engine.syntax.While

/** A position in a statement: child indices from the statement down (see [Nodes.children]). */
typealias Path = List<Int>

/**
 * The tree the editor walks: a [Statement], its expressions and program lines, seen uniformly
 * as nodes with numbered children. The order of children is the reading order (left to right,
 * top to bottom), which is what arrow keys and Tab follow.
 *
 * A statement's result unit is a child too, as a [Placeholder] while it is empty, so the cursor
 * can go there: `x = ?` has children `[x, ?]`.
 */
object Nodes {

    fun children(n: Any): List<Any> = when (n) {
        is Define -> listOfNotNull(n.target, n.value, n.result?.let { it.unit ?: Placeholder })
        is Evaluate -> listOf(n.expr, n.result.unit ?: Placeholder)
        is ExprStatement -> listOf(n.expr)
        is Symbolic -> listOf(n.expr) + n.keywords
        is Unparsed -> emptyList()
        is Apply -> n.args
        is Call -> listOf(n.fn) + n.args
        is Index -> listOf(n.target) + n.indices
        is Parens -> listOf(n.inner)
        is Matrix -> n.rows.flatten()
        is Range -> listOfNotNull(n.first, n.second, n.last)
        is Program -> n.lines
        is Line -> listOf(n.expr)
        is LocalDefine -> listOf(n.target, n.value)
        is IfThen -> listOf(n.condition) + n.body
        is Otherwise -> n.body
        is For -> listOf(n.variable, n.over) + n.body
        is While -> listOf(n.condition) + n.body
        is Return -> listOf(n.value)
        else -> emptyList() // Num, Id, Str, Placeholder, Break, Continue
    }

    /** [n] with child [i] replaced by [c]. The caller keeps the node valid (a definition's target stays definable…). */
    fun replace(n: Any, i: Int, c: Any): Any {
        fun e() = c as Expr
        fun <T> List<T>.with(k: Int, v: T) = toMutableList().also { it[k] = v }
        return when (n) {
            is Define -> when (i) {
                0 -> n.copy(target = e())
                1 -> n.copy(value = e())
                else -> n.copy(result = ResultSlot(unit(e())))
            }
            is Evaluate -> if (i == 0) n.copy(expr = e()) else n.copy(result = ResultSlot(unit(e())))
            is ExprStatement -> n.copy(expr = e())
            is Symbolic -> if (i == 0) n.copy(expr = e()) else n.copy(keywords = n.keywords.toMutableList().also { it[i - 1] = e() })
            is Apply -> n.copy(args = n.args.with(i, e()))
            is Call -> if (i == 0) n.copy(fn = e()) else n.copy(args = n.args.with(i - 1, e()))
            is Index -> if (i == 0) n.copy(target = e()) else n.copy(indices = n.indices.with(i - 1, e()))
            is Parens -> Parens(e())
            is Matrix -> {
                val cols = n.rows[0].size
                n.copy(rows = n.rows.mapIndexed { r, row -> if (r == i / cols) row.with(i % cols, e()) else row })
            }
            is Range -> when {
                i == 0 -> n.copy(first = e())
                n.second != null && i == 1 -> n.copy(second = e())
                else -> n.copy(last = e())
            }
            is Program -> n.copy(lines = n.lines.with(i, c as ProgramLine))
            is Line -> Line(e())
            is LocalDefine -> if (i == 0) n.copy(target = e()) else n.copy(value = e())
            is IfThen -> if (i == 0) n.copy(condition = e()) else n.copy(body = n.body.with(i - 1, c as ProgramLine))
            is Otherwise -> n.copy(body = n.body.with(i, c as ProgramLine))
            is For -> when (i) {
                0 -> n.copy(variable = e())
                1 -> n.copy(over = e())
                else -> n.copy(body = n.body.with(i - 2, c as ProgramLine))
            }
            is While -> if (i == 0) n.copy(condition = e()) else n.copy(body = n.body.with(i - 1, c as ProgramLine))
            is Return -> Return(e())
            else -> error("${n::class.simpleName} has no children")
        }
    }

    private fun unit(e: Expr): Expr? = if (e == Placeholder) null else e

    fun at(root: Any, path: Path): Any = path.fold(root) { n, i -> children(n)[i] }

    fun set(root: Any, path: Path, c: Any): Any =
        if (path.isEmpty()) c else replace(root, path[0], set(children(root)[path[0]], path.drop(1), c))

    /** Whether [n] is a leaf the cursor can type into (or replace): a name, number, string or placeholder. */
    fun isLeaf(n: Any) = n is Id || n is Num || n is Str || n == Placeholder

    /** Every path below [root], in reading order (a parent before its children). */
    fun paths(root: Any, prefix: Path = emptyList()): List<Path> {
        val kids = children(root).withIndex().toList()
        // In reading order: a one-line `if` is drawn `value if condition`, its body first.
        val order = if (root is IfThen && root.body.size == 1) kids.drop(1) + kids.take(1) else kids
        return order.flatMap { (i, c) -> listOf(prefix + i) + paths(c, prefix + i) }
    }

    /** The leaves below [root], in reading order. */
    fun leaves(root: Any): List<Path> = paths(root).filter { isLeaf(at(root, it)) || children(at(root, it)).isEmpty() }

    /** How tightly a node binds, for deciding parentheses (as the printer does). */
    fun precedence(e: Any): Int = when (e) {
        is Apply -> e.op.precedence
        is Call, is Index -> 13
        else -> 14
    }

    /** Break and Continue, the program lines without children. */
    fun isBareLine(n: Any) = n == Break || n == Continue
}
