package pub.beermat.engine.eval

import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Quantity

/** The result of evaluating an expression. Booleans are the numbers 1 and 0. */
sealed interface Value

data class Scalar(val q: Quantity) : Value {
    override fun toString() = q.toString()
}

/**
 * A matrix, stored row by row. Each element carries its own unit, so `[F; M]` can hold a force
 * and a moment; an element can be any value, even another array.
 */
data class ArrayValue(val rows: Int, val cols: Int, val elements: List<Value>) : Value {
    init {
        require(rows >= 1 && cols >= 1 && elements.size == rows * cols) { "a ${rows}×$cols array needs ${rows * cols} elements" }
    }

    operator fun get(r: Int, c: Int): Value = elements[r * cols + c]

    val isVector: Boolean get() = rows == 1 || cols == 1

    val size: String get() = "${rows}×$cols"

    override fun toString() = (0 until rows).joinToString("; ", "[", "]") { r -> (0 until cols).joinToString(", ") { c -> this[r, c].toString() } }

    companion object {
        // Inline, so the indices aren't boxed: whole arrays are built this way, one element at a time.
        inline fun of(rows: Int, cols: Int, f: (Int, Int) -> Value): ArrayValue {
            val elements = arrayOfNulls<Value>(rows * cols)
            for (k in elements.indices) elements[k] = f(k / cols, k % cols)
            @Suppress("UNCHECKED_CAST")
            return ArrayValue(rows, cols, (elements as Array<Value>).asList())
        }

        fun column(values: List<Value>) = ArrayValue(values.size, 1, values)
    }
}

/**
 * Elements that one can be set in, or added after, without copying the others: a persistent vector,
 * a tree of 32-way nodes, so a change copies only the 3 or 4 nodes on its path. A program's loop
 * that fills an array element by element (`v[i] <- …`, `M[i, j] <- …`) then takes time in
 * proportion to the elements, not their square, and every earlier array stays as it was.
 */
internal class Elements private constructor(private val root: Array<Any?>, private val shift: Int, override val size: Int) : AbstractList<Value>(), RandomAccess {

    override fun get(index: Int): Value {
        java.util.Objects.checkIndex(index, size)
        var node = root
        var s = shift
        while (s > 0) {
            @Suppress("UNCHECKED_CAST")
            node = node[(index ushr s) and 31] as Array<Any?>
            s -= 5
        }
        return node[index and 31] as Value
    }

    /** These elements with the one at [index] (below [size], or [size] itself to add one) set to [value]. */
    fun with(index: Int, value: Value): Elements {
        java.util.Objects.checkIndex(index, size + 1)
        if (index < 1 shl (shift + 5)) return Elements(path(root, shift, index, value), shift, maxOf(size, index + 1))
        // Full: a new level on top.
        return Elements(path(arrayOfNulls<Any?>(32).also { it[0] = root }, shift + 5, index, value), shift + 5, size + 1)
    }

    private fun path(node: Array<Any?>?, s: Int, index: Int, value: Value): Array<Any?> {
        val copy = node?.copyOf() ?: arrayOfNulls(32)
        val k = (index ushr s) and 31
        @Suppress("UNCHECKED_CAST")
        copy[k] = if (s == 0) value else path(copy[k] as Array<Any?>?, s - 5, index, value)
        return copy
    }

    companion object {
        private val EMPTY = Elements(arrayOfNulls(32), 0, 0)

        fun of(values: List<Value>): Elements = values as? Elements ?: values.fold(EMPTY) { e, v -> e.with(e.size, v) }
    }
}

/**
 * The value of a range variable, `i := 0..10` or `x := 0, 0.1..1`: [count] values from [first]
 * in steps of [step]. A statement that uses it is evaluated once per value.
 */
data class RangeValue(val first: Quantity, val step: Quantity, val count: Int) : Value {
    operator fun get(k: Int): Quantity = Quantity(first.value + step.value * Complex(k.toDouble()), first.dimension)

    val values: List<Value> get() = List(count) { Scalar(this[it]) }

    override fun toString() = if (count == 1) "$first" else "$first, ${this[1]}..${this[count - 1]}"

    companion object {
        const val MISUSE = "a range variable stands for one value at a time: use it in an evaluation (x = ?), an indexed definition (v[i] := …) or sum(…, i)"
    }
}

data class Text(val value: String) : Value {
    override fun toString() = "\"$value\""
}

/** A function, built-in or user-defined. Functions are values: `h := f` works. */
sealed interface FunctionValue : Value {
    val name: String
}

/**
 * `f(x, y) := body`. Free names in [body] resolve in the scope where the function was defined
 *, which includes the function itself, so recursion works.
 */
class UserFunction(val id: Id, val params: List<Id>, val body: Expr) : FunctionValue {
    override val name: String get() = id.text

    internal lateinit var scope: Scope

    override fun toString() = "$name(${params.joinToString { it.name }})"
}

/**
 * A built-in function. [impl] receives its arguments unevaluated, as thunks, so that a
 * function like `if` evaluates only the branch it needs; most built-ins just force them all.
 * It runs with a [Context] for the few that depend on sheet settings.
 */
class Builtin(
    override val name: String,
    val arity: IntRange,
    /** Evaluated by the evaluator itself, because it binds a variable or changes how its argument is evaluated (`sum`, `prod`, `vec`). */
    val special: Boolean = false,
    val impl: Context.(args: List<() -> Value>) -> Value,
) : FunctionValue {
    override fun toString() = "$name (built-in)"

    /**
     * The sheet settings at the call, read only when a built-in asks, and [call] for built-ins
     * that take a function (`matrix(m, n, f)`).
     */
    class Context(
        origin: () -> Int,
        /** Random numbers (`rnd`, `rnorm`…): seeded with 1 at the start of every evaluation, so a sheet shows the same values each time. */
        val random: java.util.Random = java.util.Random(1),
        /** The sheet's folder, where data files are found; null for a sheet not saved yet. */
        val folder: java.io.File? = null,
        val call: (FunctionValue, List<Value>) -> Value = { _, _ -> error("no evaluator") },
    ) {
        val origin: Int by lazy(origin)
    }
}

/**
 * An evaluation error. [node] is the sub-expression to highlight, if known. [names] are the
 * variables the message quotes, each written as [Id.text], so a display can draw them as math.
 */
open class EvalException(message: String, val node: Expr? = null, val names: List<Id> = emptyList()) : RuntimeException(message)

/**
 * A module of Python functions, from `use "file.py"` (docs/symbolic.md#python-functions-in-a-sheet):
 * its public functions by name, each a [Builtin] that calls into Python.
 */
class Module(
    val path: String,
    val functions: Map<String, Builtin>,
    /** Each function's signature and docstring, for completion and hover. */
    val docs: Map<String, Doc> = emptyMap(),
) : Value {
    class Doc(val signature: String, val text: String)

    override fun toString() = "use \"$path\""
}

/** Loads Python modules for `use`; the engine itself has no Python (the symbolic module provides it). */
fun interface Modules {
    /** The module in [file]. Throws [EvalException] when it can't be loaded, or isn't allowed to run. */
    fun load(file: java.io.File): Module
}
