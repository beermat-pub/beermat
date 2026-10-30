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
        fun of(rows: Int, cols: Int, f: (Int, Int) -> Value) = ArrayValue(rows, cols, List(rows * cols) { f(it / cols, it % cols) })

        fun column(values: List<Value>) = ArrayValue(values.size, 1, values)
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
