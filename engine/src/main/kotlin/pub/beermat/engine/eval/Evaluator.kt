package pub.beermat.engine.eval

import pub.beermat.engine.eval.Scope.Binding
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Continue
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
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
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.ResultSlot
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity
import pub.beermat.engine.units.UnitsException
import kotlin.math.abs
import kotlin.math.floor

/** What happened to one statement. */
sealed interface Outcome {
    data class Defined(val id: Id, val value: Value, val shown: Shown? = null) : Outcome
    data class Evaluated(val shown: Shown) : Outcome
    /** A bare expression, which is displayed but not evaluated. */
    data object NotEvaluated : Outcome
    data class Failed(val error: EvalException) : Outcome
    /** A symbolic evaluation's result (`expr -> ?`). */
    data class Symbolic(val result: Expr) : Outcome
}

/**
 * A value to display. When the user filled the unit placeholder, [unit] is that unit's value,
 * already checked to have the same dimension, and [unitExpr] is what the user typed.
 * The number to show is `(value - zero) / unit`; only `°C` and `°F` have a [zero].
 */
data class Shown(val value: Value, val unit: Quantity? = null, val unitExpr: Expr? = null, val zero: Double = 0.0)

/**
 * A unit to show numbers in (a unit placeholder, a plot axis, a table column): [one] of it in
 * SI, and the SI value of its [zero], which only the temperature scales `°C` and `°F` move.
 */
data class DisplayUnit(val one: Quantity, val zero: Double = 0.0) {
    /** [q] as a number of this unit. */
    fun number(q: Quantity): Complex = (q.value - Complex(zero)) / one.value

    /** [x] of this unit, in SI. */
    fun quantity(x: Complex) = Quantity(x * one.value + Complex(zero), one.dimension)
}

/**
 * A tree-walking evaluator over the statements of a sheet, in order.
 *
 * [units] are the built-in units. They are also in [builtins], where a user definition can
 * shadow them; inside a unit placeholder (`= ? m`) they always mean the unit.
 *
 * Comparisons treat numbers within a relative `TOL` as equal; `TOL` is read
 * from the scope, so a sheet can redefine it.
 *
 * A statement that uses a range variable is evaluated once for each of its values
 * (docs/evaluation.md#ranges).
 */
class Evaluator(
    private val builtins: Scope = Builtins.scope,
    private val units: Map<Id, Value> = Builtins.units,
    /** What evaluates `expr -> ?`; without one, symbolic evaluation is an error. */
    private val symbolic: SymbolicBackend? = null,
    /** The folder data files (`READPRN("loads.txt")`) are read from: the sheet's; null for a sheet not saved yet. */
    private val folder: java.io.File? = null,
    /** What loads `use "file.py"`; without it (the web version), Python functions are an error. */
    private val modules: Modules? = null,
) {
    private var depth = 0

    /** Above zero inside `vec(…)`: operators and calls then apply element by element. */
    private var vectorizing = 0

    /**
     * Evaluates [statements] as a sheet: global definitions (`::=`) first, top to bottom,
     * then everything else in order. Returns one outcome per statement.
     */
    /** The random numbers of this evaluation: seeded again at every run (see `Builtin.Context.random`). */
    private var random = java.util.Random(1)

    fun run(statements: List<Statement>): List<Outcome> = runWithScopes(statements).first

    /**
     * Like [run], and also returns the scope before each statement, plus the final one at the end
     * (`statements.size + 1` scopes): what a plot or a query at that point of the sheet sees.
     *
     * [steps] are run between statements: those at k run before statement k (or at the end, for
     * `statements.size`), each taking the scope and giving the next. That is how regions other
     * than statements (a table, docs/tables.md) define values, without the engine knowing them.
     */
    fun runWithScopes(statements: List<Statement>, steps: Map<Int, List<(Scope) -> Scope>> = emptyMap()): Pair<List<Outcome>, List<Scope>> {
        random = java.util.Random(1)
        var globals = builtins
        val globalOutcomes = mutableMapOf<Int, Outcome>()
        statements.forEachIndexed { k, s ->
            if (s is Define && s.global) {
                val (outcome, next) = define(s, globals)
                globalOutcomes[k] = outcome
                globals = next
            }
        }
        var scope = globals
        val scopes = mutableListOf<Scope>()
        solveBlock = null
        val outcomes = statements.mapIndexed { k, s ->
            steps[k]?.forEach { scope = it(scope) }
            scopes += scope
            val outcome = globalOutcomes[k] ?: when (s) {
                is Define -> define(s, scope).let { (outcome, next) -> scope = next; outcome }
                is Evaluate -> attempt { Outcome.Evaluated(show(iterated(s.expr, scope), s.result, scope)) }
                is ExprStatement -> {
                    // In a solve block, a comparison is a constraint, kept with the definitions above it.
                    val block = solveBlock
                    if (block != null && !block.closed && (s.expr as? Apply)?.op in COMPARISONS) links(s.expr as Apply).forEach { block.constraints += it to scope }
                    Outcome.NotEvaluated
                }
                Given -> {
                    solveBlock = SolveBlock()
                    Outcome.NotEvaluated
                }
                is pub.beermat.engine.syntax.Symbolic -> attempt {
                    val backend = symbolic ?: throw EvalException("symbolic evaluation is not available here")
                    val here = scope
                    // Only what the sheet defined (a built-in unit is not a definition to substitute).
                    Outcome.Symbolic(backend.evaluate(s.expr, s.keywords) { id -> here[id].takeIf { it !== builtins[id] }.let { it as? Binding.Bound }?.value })
                }
                is Unparsed -> {
                    s.target?.let { scope = scope.with(it, Binding.Broken(s.message)) }
                    Outcome.Failed(EvalException(s.message))
                }
            }
            // A solve block ends with the statement that solves it.
            solveBlock?.let { if (it.used) it.closed = true }
            outcome
        }
        steps[statements.size]?.forEach { scope = it(scope) }
        scopes += scope
        return outcomes to scopes
    }

    /** The solve block being read: its constraints, each with the scope where it stands. */
    private class SolveBlock {
        val constraints = mutableListOf<Pair<Apply, Scope>>()
        var used = false
        var closed = false
    }

    private var solveBlock: SolveBlock? = null

    /** [x]'s value as a guess; null if it isn't defined. A definition that failed says why, as any use of it does. */
    private fun guess(x: Id, scope: Scope): Value? = when (val b = scope[x]) {
        is Binding.Bound -> b.value
        is Binding.Broken -> eval(x, scope)
        null -> null
    }

    /**
     * `Minimize(f, x, y, …)` and `Maximize(…)` (docs/evaluation.md#solve-blocks): the values of the
     * variables, starting from their current ones, where f(x, y, …) is least or greatest. Below a
     * `given`, its constraints hold too (as penalties that grow until they do).
     */
    private fun optimize(e: Call, scope: Scope, name: String): Value {
        val f = eval(e.args[0], scope) as? FunctionValue ?: throw EvalException("$name: the first argument must be the function to ${name.lowercase()}, as in $name(f, x)", e.args[0])
        val args = e.args.drop(1).singleOrNull()?.let { (it as? Matrix)?.rows?.flatten() } ?: e.args.drop(1)
        val vars = args.map { it as? Id ?: throw EvalException("$name: the variables must be names", it) }
        val guesses = vars.zip(args).map { (x, arg) ->
            (guess(x, scope) ?: throw EvalException("${display(x)} needs a guess: define it above", arg, listOf(x))).quantityOrThrow("a guess for $name", arg)
                .also { if (!it.isReal) throw EvalException("$name: complex guesses aren't supported", arg) }
        }
        val block = solveBlock?.takeIf { !it.closed || it.used }?.also { it.used = true }
        val sign = if (name == "Maximize") -1.0 else 1.0
        fun values(xs: DoubleArray) = xs.mapIndexed { k, v -> Scalar(Quantity(v, guesses[k].dimension)) }
        fun objective(xs: DoubleArray): Double = invoke(f, values(xs).map { v -> { v } }, e, scope).quantityOrThrow("$name: the function's value", e.args[0]).realValue(name)
        fun broken(xs: DoubleArray): Double = block?.constraints?.sumOf { (c, cs) ->
            var s = cs
            vars.forEachIndexed { k, x -> s = s.with(x, values(xs)[k]) }
            val d = eval(c.args[0], s).quantityOrThrow("a constraint", c.args[0]).value.re - eval(c.args[1], s).quantityOrThrow("a constraint", c.args[1]).value.re
            val miss = when (c.op) {
                Op.EQ -> d
                Op.LT, Op.LE -> maxOf(d, 0.0)
                Op.GT, Op.GE -> minOf(d, 0.0)
                else -> 0.0
            }
            miss * miss
        } ?: 0.0
        val x0 = DoubleArray(guesses.size) { guesses[it].value.re }
        val scale = maxOf(abs(objective(x0)), 1e-12)
        var weight = 1e3
        var x = x0
        repeat(8) {
            // A trial point where the function can't be evaluated (a complex value, a domain error) is a bad point, not the end.
            x = Solver.minimize({ xs -> try { sign * objective(xs) / scale + weight * broken(xs) } catch (_: EvalException) { Double.NaN } catch (_: pub.beermat.engine.units.UnitsException) { Double.NaN } }, x)
            if (broken(x) <= 1e-20) return@repeat
            weight *= 100
        }
        if (block != null && broken(x) > 1e-12) throw EvalException("$name: the constraints can't all be met from these guesses", e)
        val result = values(x)
        return if (result.size == 1) result[0] else ArrayValue.column(result)
    }

    /** `Odesolve(t, b[, steps])`, or with the unknowns first, `Odesolve([x; y], t, b[, steps])` (see [OdeSolve]). */
    private fun odesolve(e: Call, scope: Scope): Value {
        val block = solveBlock?.takeIf { !it.closed || it.used }
            ?: throw EvalException("Odesolve needs a solve block: a 'given' line above it, then the equations and initial conditions", e)
        val names = (e.args[0] as? Matrix)?.rows?.flatten()?.map { it as? Id ?: throw EvalException("Odesolve: the unknowns must be names", it) }
        val rest = if (names != null) e.args.drop(1) else e.args
        if (rest.size !in 2..3) throw EvalException("Odesolve takes the variable and the end of its range: Odesolve(t, 10 s), and the number of steps if wanted", e)
        val t = rest[0] as? Id ?: throw EvalException("Odesolve: the variable must be a name", rest[0])
        val end = eval(rest[1], scope).quantityOrThrow("the end of Odesolve's range", rest[1])
        val steps = rest.getOrNull(2)?.let { wholeNumber(it, scope, "Odesolve's number of steps") } ?: 1000
        if (steps < 1) throw EvalException("Odesolve needs at least one step", rest[2])
        block.used = true
        val functions = OdeSolve({ x, s -> eval(x, s) }, block.constraints).solve(names, t, end, steps)
        return if (names == null && functions.size == 1) functions[0] else ArrayValue.column(functions)
    }

    /**
     * `find(x, y, …)` and `minerr(x, y, …)`: the values of the unknowns that satisfy the solve
     * block's constraints, starting from their current values as guesses. `minerr` returns the
     * closest it gets even when the constraints can't all be met.
     */
    private fun solve(e: Call, scope: Scope, name: String): Value {
        val block = solveBlock?.takeIf { !it.closed || it.used }
            ?: throw EvalException("$name needs a solve block: a 'given' line above it, then the constraints", e)
        if (block.constraints.isEmpty()) throw EvalException("$name: the solve block has no constraints (comparisons such as x^2 == 2, below 'given')", e)
        if (e.args.isEmpty()) throw EvalException("$name needs the unknowns: $name(x, y)", e)
        // The unknowns one by one, `find(x, y)`, or as a vector of names, `find([x; y])`.
        val args = e.args.singleOrNull()?.let { (it as? Matrix)?.rows?.flatten() } ?: e.args
        val unknowns = args.map { it as? Id ?: throw EvalException("$name: the unknowns must be names", it) }
        val guesses = unknowns.zip(args).map { (x, arg) ->
            val q = (guess(x, scope) ?: throw EvalException("${display(x)} needs a guess: define it above 'given'", arg, listOf(x)))
                .quantityOrThrow("a guess for $name", arg)
            if (!q.isReal) throw EvalException("$name: complex guesses aren't supported yet", arg)
            q
        }
        block.used = true
        fun sides(c: Apply, cs: Scope, xs: DoubleArray): Pair<Quantity, Quantity> {
            var s = cs
            unknowns.forEachIndexed { k, x -> s = s.with(x, Scalar(Quantity(xs[k], guesses[k].dimension))) }
            val l = eval(c.args[0], s).quantityOrThrow("a constraint", c.args[0])
            val r = eval(c.args[1], s).quantityOrThrow("a constraint", c.args[1])
            if (l.dimension != r.dimension) throw EvalException("the two sides of a constraint have different units", c)
            return l to r
        }
        // Each constraint's residual in SI units (so minerr minimizes the same sum).
        val x0 = DoubleArray(guesses.size) { guesses[it].value.re }
        fun miss(c: Apply, l: Quantity, r: Quantity): Double {
            val d = l.value.re - r.value.re
            return when (c.op) {
                Op.EQ -> d
                Op.LT, Op.LE -> maxOf(d, 0.0)
                Op.GT, Op.GE -> minOf(d, 0.0)
                else -> 0.0
            }
        }
        // Inequalities are penalties; one still broken weighs more, and the solve goes on from there.
        val weights = DoubleArray(block.constraints.size) { 1.0 }
        val residuals = { xs: DoubleArray ->
            DoubleArray(block.constraints.size) { i ->
                val (c, cs) = block.constraints[i]
                val (l, r) = sides(c, cs, xs)
                weights[i] * miss(c, l, r)
            }
        }
        var x = Solver.leastSquares(residuals, x0)
        repeat(8) {
            val broken = block.constraints.indices.filter { i ->
                val (c, cs) = block.constraints[i]
                c.op != Op.EQ && sides(c, cs, x).let { (l, r) -> miss(c, l, r) != 0.0 }
            }
            if (broken.isEmpty()) return@repeat
            broken.forEach { weights[it] *= 30.0 }
            x = Solver.leastSquares(residuals, x)
        }
        if (name == "find") {
            // Each constraint must hold, relative to the size of its sides.
            val holds = block.constraints.all { (c, cs) ->
                val (l, r) = sides(c, cs, x)
                Math.abs(miss(c, l, r)) <= FIND_TOLERANCE * maxOf(Math.abs(l.value.re), Math.abs(r.value.re), 1e-12)
            }
            if (!holds) throw EvalException("find: no solution found from these guesses (try other guesses, or minerr for the closest values)", e)
        }
        val values = x.mapIndexed { k, v -> Scalar(Quantity(v, guesses[k].dimension)) }
        return if (values.size == 1) values[0] else ArrayValue.of(values.size, 1) { r, _ -> values[r] }
    }

    /** Evaluates a single expression in [scope]. */
    fun eval(e: Expr, scope: Scope = builtins): Value =
        try {
            // A thread that was interrupted (a stop, or a time limit) stops here, wherever it is: in a loop, a solver, an integral.
            if (Thread.currentThread().isInterrupted) throw EvalException("the evaluation was stopped")
            evalNode(e, scope)
        } catch (ex: EvalException) {
            throw if (ex.node == null) EvalException(ex.message ?: "error", e, ex.names) else ex
        } catch (ex: UnitsException) {
            throw EvalException(ex.message ?: "units error", e)
        } catch (ex: ArithmeticException) {
            throw EvalException(ex.message ?: "arithmetic error", e)
        }

    /** [e] evaluated once, or, if it uses range variables, once per value: a column of results. */
    private fun iterated(e: Expr, scope: Scope): Value {
        val vars = rangeVariables(listOf(e), scope)
        if (vars.isEmpty()) return eval(e, scope)
        return ArrayValue.column(combinations(vars, scope).map { eval(e, scope.withAll(it)) }.toList())
    }

    private fun define(s: Define, scope: Scope): Pair<Outcome, Scope> {
        when (val target = s.target) {
            is Id -> {
                val value = try {
                    definedValue(s.value, scope)
                } catch (ex: EvalException) {
                    return Outcome.Failed(ex) to scope.with(target, Binding.Broken(ex.message ?: "error", ex.names))
                }
                val next = scope.with(target, value)
                val outcome = s.result?.let { slot ->
                    val shownValue = if (value is RangeValue) ArrayValue.column(value.values) else value
                    attempt { Outcome.Defined(target, value, show(shownValue, slot, scope)) }
                }
                return (outcome ?: Outcome.Defined(target, value)) to next
            }
            is Call -> {
                val id = target.fn as Id
                val params = target.args.map { it as Id }
                if (params.toSet().size != params.size) {
                    val error = EvalException("${display(id)}: a parameter name is repeated", target, listOf(id))
                    return Outcome.Failed(error) to scope.with(id, Binding.Broken(error.message!!, error.names))
                }
                val fn = UserFunction(id, params, s.value)
                val next = scope.with(id, fn)
                fn.scope = next
                if (s.result != null) {
                    return Outcome.Failed(EvalException("a function definition has no value to show", target)) to next
                }
                return Outcome.Defined(id, fn) to next
            }
            is Index -> return defineElements(s, target, scope)
            is Matrix, is Apply -> {
                val names = assignedNames(target)
                return try {
                    val bindings = assign(target, eval(s.value, scope), scope)
                    val next = bindings.fold(scope) { sc, (id, v) -> sc.with(id, v) }
                    val (id, value) = bindings.first()
                    val outcome = s.result?.let { slot -> attempt { Outcome.Defined(id, value, show(eval(s.value, scope), slot, scope)) } }
                    (outcome ?: Outcome.Defined(id, value)) to next
                } catch (ex: EvalException) {
                    Outcome.Failed(ex) to names.fold(scope) { sc, id -> sc.with(id, Binding.Broken(ex.message ?: "error", ex.names)) }
                }
            }
            Placeholder -> return Outcome.Failed(EvalException("this definition needs a name", target)) to scope
            else -> error("parser guarantees the target shape")
        }
    }

    /** The value of `x := …`: a range, or an expression that must not use a range variable. */
    private fun definedValue(e: Expr, scope: Scope): Value {
        if (e is Range) return range(e, scope)
        rangeVariables(listOf(e), scope).firstOrNull()?.let {
            throw EvalException(
                "${display(it)} is a range variable: it can define the elements of an array (v[${display(it)}] := …), not a single value",
                e,
                listOf(it),
            )
        }
        return eval(e, scope)
    }

    /**
     * `v[i] := x` or `M[i, j] := x`: a copy of the array with that element set, grown as needed.
     * New elements are zeros in the unit of [Define.value].
     * With range variables (`v[i] := i^2`), one element per value, in order, so an element
     * can use the ones before it (`x[i + 1] := x[i] + h`).
     */
    /** The names a column or matrix target defines. */
    private fun assignedNames(target: Expr): List<Id> = when (target) {
        is Matrix -> target.rows.flatten().map { it as Id }
        is Apply -> listOf(target.args[0] as Id)
        else -> emptyList()
    }

    /**
     * [value] assigned to a column `M^<j>` (the array grows as needed, as with elements) or to
     * names in a matrix `[a; b]` (a multiple definition: each name gets the element in its
     * place). The names and their new values.
     */
    private fun assign(target: Expr, value: Value, scope: Scope, fresh: (Id) -> Boolean = { false }): List<Pair<Id, Value>> = when (target) {
        is Matrix -> {
            val v = value as? ArrayValue ?: throw EvalException("[a; b] := … needs a matrix on the right, of the same shape", target)
            val (rows, cols) = target.rows.size to target.rows[0].size
            if (v.rows != rows || v.cols != cols) throw EvalException("there are $rows×$cols names on the left but the value is ${v.size}", target)
            target.rows.flatMapIndexed { r, row -> row.mapIndexed { c, e -> e as Id to v[r, c] } }
        }
        is Apply -> {
            val id = target.args[0] as Id
            val column = (value as? ArrayValue)?.takeIf { it.cols == 1 } ?: ArrayValue.column(listOf(value))
            val j = wholeNumber(target.args[1], scope, "a column") - origin(scope)
            if (j < 0) throw EvalException("a column cannot be below ORIGIN = ${origin(scope)}", target.args[1])
            val old = if (fresh(id)) null else (scope[id] as? Binding.Bound)?.value as? ArrayValue
            val rows = maxOf(old?.rows ?: 0, column.rows)
            val cols = maxOf(old?.cols ?: 0, j + 1)
            if (rows.toLong() * cols > MAX_ELEMENTS) throw EvalException("the array would have more than $MAX_ELEMENTS elements", target)
            val zero = Scalar(Quantity(0.0, (column.elements.first() as? Scalar)?.q?.dimension ?: Dimension.NONE))
            listOf(id to ArrayValue.of(rows, cols) { r, c ->
                when {
                    c == j -> if (r < column.rows) column[r, 0] else zero
                    old != null && r < old.rows && c < old.cols -> old[r, c]
                    else -> zero
                }
            })
        }
        else -> error("not an assignable target: $target")
    }

    private fun defineElements(s: Define, target: Index, scope: Scope): Pair<Outcome, Scope> {
        val id = target.target as Id
        try {
            val vars = rangeVariables(target.indices + s.value, scope)
            var current = scope
            val values = mutableListOf<Value>()
            for (binding in combinations(vars, scope)) {
                val (array, value) = setElement(id, target, s.value, current.withAll(binding))
                current = current.with(id, array)
                values += value
            }
            val array = (current[id] as Binding.Bound).value
            val outcome = s.result?.let { slot ->
                attempt { Outcome.Defined(id, array, show(if (vars.isEmpty()) values[0] else ArrayValue.column(values), slot, scope)) }
            }
            return (outcome ?: Outcome.Defined(id, array)) to current
        } catch (ex: EvalException) {
            return Outcome.Failed(ex) to scope.with(id, Binding.Broken(ex.message ?: "error", ex.names))
        }
    }

    private fun setElement(id: Id, target: Index, valueExpr: Expr, scope: Scope, fresh: Boolean = false): Pair<ArrayValue, Value> {
        val value = eval(valueExpr, scope)
        val origin = origin(scope)
        val ks = target.indices.map { k ->
            (wholeNumber(k, scope, "an index") - origin).also { if (it < 0) throw EvalException("an index cannot be below ORIGIN = $origin", k) }
        }
        val old = if (fresh) null else (scope[id] as? Binding.Bound)?.value as? ArrayValue
        if (ks.size == 1 && old != null && !old.isVector) throw EvalException("${display(id)} is a ${old.size} matrix: use two indices, as in M[i, j]", target, listOf(id))
        val zero = Scalar(Quantity(0.0, (value as? Scalar)?.q?.dimension ?: Dimension.NONE))
        val (r, c) = when {
            ks.size == 2 -> ks[0] to ks[1]
            old != null && old.rows == 1 && old.cols > 1 -> 0 to ks[0]
            else -> ks[0] to 0
        }
        val rows = maxOf(old?.rows ?: 0, r + 1)
        val cols = maxOf(old?.cols ?: 0, c + 1)
        if (rows.toLong() * cols > MAX_ELEMENTS) throw EvalException("the array would have more than $MAX_ELEMENTS elements", target)
        // A bare 0 put where the array has a unit keeps it: in imported sheets an array's elements shared one unit,
        // so `v[1] <- 0` in an array of lengths is 0 m (docs/units.md#dimension-rules).
        val kept = (old?.takeIf { r < it.rows && c < it.cols }?.get(r, c) as? Scalar)?.q?.dimension
        val placed = if (kept != null && !kept.isNone && (value as? Scalar)?.q?.let { it.value == Complex.ZERO && it.dimension.isNone } == true) Scalar(Quantity(0.0, kept)) else value
        val array = ArrayValue.of(rows, cols) { i, j ->
            when {
                i == r && j == c -> placed
                old != null && i < old.rows && j < old.cols -> old[i, j]
                else -> zero
            }
        }
        return array to value
    }

    /**
     * [unitExpr] as a unit to show values in. Built-in unit names mean the unit here even where
     * the sheet redefined them; `°C` and `°F` on their own are their scales. The exception is a
     * sheet that made `°C` or `°F` its own (`°C ::= K`, how older imported sheets wrote temperatures):
     *, the placeholder then uses the sheet's definition too.
     */
    fun displayUnit(unitExpr: Expr, scope: Scope): DisplayUnit {
        val own = Builtins.temperatureZeros.keys.filter { id -> (scope[id] as? Binding.Bound)?.value.let { it != null && it !== units[id] } }
        val inUnits = scope.withAll(units - own.toSet())
        temperatureZero(unitExpr, inUnits)?.let { zero ->
            return DisplayUnit((units.getValue(unitExpr.unwrapped as Id) as Scalar).q, zero)
        }
        return DisplayUnit(eval(unitExpr, inUnits).quantityOrThrow("the unit", unitExpr))
    }

    /** `°C` or `°F` (or in parentheses), if [scope] has them as the built-in scales: the SI value of the zero. */
    private fun temperatureZero(e: Expr, scope: Scope): Double? {
        val id = e.unwrapped as? Id ?: return null
        val zero = Builtins.temperatureZeros[id] ?: return null
        return zero.takeIf { (scope[id] as? Binding.Bound)?.value === units[id] }
    }

    private val Expr.unwrapped: Expr get() = if (this is Parens) inner.unwrapped else this

    /**
     * `x °C`: a dimensionless [number] (or an array of them) on the scale [scale], as absolute
     * temperatures. After a quantity with units (`2 m °C`) it is one degree, as everywhere else.
     */
    private fun onScale(number: Expr, scale: Id, zero: Double, scope: Scope): Value {
        val unit = DisplayUnit((units.getValue(scale) as Scalar).q, zero)
        return MatrixMath.map(eval(number, scope), display(scale)) { x -> if (x.dimension.isNone) unit.quantity(x.value) else x * unit.one }
    }

    private fun show(value: Value, slot: ResultSlot, scope: Scope): Shown {
        val unitExpr = slot.unit ?: return Shown(value)
        val (unit, zero) = displayUnit(unitExpr, scope)
        fun check(v: Value) {
            if (v is ArrayValue) return v.elements.forEach(::check)
            val q = v.quantityOrThrow("a value shown in a unit", unitExpr)
            if (q.dimension != unit.dimension) {
                throw EvalException("units do not match: the result is ${q.dimension}, the unit is ${unit.dimension}", unitExpr)
            }
        }
        check(value)
        if (unit.value == Complex.ZERO) throw EvalException("the unit is zero", unitExpr)
        return Shown(value, unit, unitExpr, zero)
    }

    private inline fun attempt(block: () -> Outcome): Outcome =
        try {
            block()
        } catch (ex: EvalException) {
            Outcome.Failed(ex)
        }

    private fun evalNode(e: Expr, scope: Scope): Value = when (e) {
        is Num -> Scalar(Quantity(numberValue(e.text)))
        is Id -> when (val b = scope[e]) {
            // `°C` and `°F` on their own (`°C^-1`, `W/(m*°C)`) are one degree; only after a plain number are they on the scale.
            is Binding.Bound -> b.value
            is Binding.Broken -> throw EvalException("${display(e)} has an error: ${b.message}", e, listOf(e) + b.names)
            null -> throw EvalException("${display(e)} is not defined", e, listOf(e))
        }
        is Str -> Text(e.value)
        Placeholder -> throw EvalException("this expression is incomplete", e)
        is Parens -> eval(e.inner, scope)
        is Call -> call(e, scope)
        is Apply -> apply(e, scope)
        is Matrix -> {
            // A bare 0 beside elements that share one unit takes it (`[0; T - T_amb]`), as in an element definition.
            val values = e.rows.flatten().map { eval(it, scope) }
            val shared = values.takeIf { vs -> vs.all { it is Scalar } }?.let { vs -> sameUnits(vs.map { (it as Scalar).q }) }
            ArrayValue(e.rows.size, e.rows[0].size, shared?.map(::Scalar) ?: values)
        }
        is Index -> index(e, scope)
        is Range -> throw EvalException("a range can only be the whole value of a definition, as in i := 0..10", e)
        is Program -> program(e, scope)
    }

    // Ranges.

    private fun range(r: Range, scope: Scope): RangeValue {
        fun limit(e: Expr): Quantity {
            val q = eval(e, scope).quantityOrThrow("a range limit", e)
            if (!q.isReal) throw EvalException("a range needs real numbers", e)
            return q
        }
        // A bare 0 takes the other limits' unit: `0..L`.
        val given = listOfNotNull(limit(r.first), r.second?.let(::limit), limit(r.last))
        val limits = sameUnits(given)
            ?: throw EvalException("units do not match in the range: ${given.joinToString(", ") { it.dimension.toString() }}", r)
        val first = limits.first()
        val second = if (r.second != null) limits[1] else null
        val last = limits.last()
        val step = when {
            second != null -> second - first
            !first.dimension.isNone -> throw EvalException("a range with units needs a step, as in 0 m, 0.1 m..1 m", r)
            else -> Quantity(if (last.value.re >= first.value.re) 1.0 else -1.0)
        }
        if (step.value == Complex.ZERO) throw EvalException("the range's step is zero", r)
        val span = (last.value.re - first.value.re) / step.value.re
        if (span < -1e-9) throw EvalException("the step goes away from the last value, so the range is empty", r)
        val count = floor(span + 1e-9) + 1
        if (count > MAX_ELEMENTS) throw EvalException("the range has more than $MAX_ELEMENTS values", r)
        return RangeValue(first, step, count.toInt())
    }

    /**
     * The range variables that [exprs] use, in order of appearance. The variable of a range
     * sum or product (`sum(v[i], i)`) belongs to that sum, not to the statement.
     */
    private fun rangeVariables(exprs: List<Expr>, scope: Scope): List<Id> {
        val found = linkedSetOf<Id>()
        fun visit(e: Expr, bound: Set<Id>) {
            when (e) {
                is Id -> if (e !in bound && (scope[e] as? Binding.Bound)?.value is RangeValue) found += e
                is Parens -> visit(e.inner, bound)
                is Apply -> e.args.forEach { visit(it, bound) }
                is Matrix -> e.rows.flatten().forEach { visit(it, bound) }
                is Index -> (listOf(e.target) + e.indices).forEach { visit(it, bound) }
                is Range -> listOfNotNull(e.first, e.second, e.last).forEach { visit(it, bound) }
                is Call -> {
                    val variable = e.args.getOrNull(1) as? Id
                    if (isIteration(e, scope) && variable != null) {
                        visit(e.args[0], bound + variable)
                        e.args.drop(2).forEach { visit(it, bound) }
                    } else {
                        (listOf(e.fn) + e.args).forEach { visit(it, bound) }
                    }
                }
                // A program uses range variables one value at a time, like a function body.
                is Num, is Str, Placeholder, is Program -> {}
            }
        }
        exprs.forEach { visit(it, emptySet()) }
        return found.toList()
    }

    /** Every combination of the values of [vars]: the first varies slowest. */
    private fun combinations(vars: List<Id>, scope: Scope): Sequence<Map<Id, Value>> {
        val ranges = vars.map { (scope[it] as Binding.Bound).value as RangeValue }
        if (ranges.fold(1L) { n, r -> n * r.count } > MAX_ELEMENTS) {
            throw EvalException("the range variables ${vars.joinToString { display(it) }} have more than $MAX_ELEMENTS combinations", names = vars)
        }
        return sequence {
            val k = IntArray(vars.size)
            while (true) {
                yield(vars.indices.associate { vars[it] to Scalar(ranges[it][k[it]]) })
                var d = vars.size - 1
                while (d >= 0) {
                    if (++k[d] < ranges[d].count) break
                    k[d] = 0
                    d--
                }
                if (d < 0) break
            }
        }
    }

    /** `sum(expr, i)`, `sum(expr, i, a, b)`, the same for `prod`, and `int(f, x, a, b)` and `root(f, x, a, b)`: they bind their variable. (`diff` uses x's value, so a range variable x makes it a derivative at each x.) */
    private fun isIteration(e: Call, scope: Scope): Boolean {
        val fn = (e.fn as? Id)?.let { (scope[it] as? Binding.Bound)?.value } as? Builtin ?: return false
        return fn.special && (fn.name in ITERATIONS || fn.name == "int" || (fn.name == "root" && e.args.size == 4)) && e.args.size >= 2
    }

    // Programs.

    /**
     * Local variables of a running program: one scope for the whole program. [own] are
     * the names it has assigned (and its function's parameters, and the names it reads before it first
     * assigns them): setting an element of any other name starts a new array rather than changing a copy
     * of the sheet's, as the results saved in imported sheets show both ways: `C[n] <- x` first builds C afresh,
     * `S[k] <- S[k]*1000` changes the sheet's S.
     */
    private class Locals(var scope: Scope, val own: MutableSet<Id> = mutableSetOf())

    /** The parameters of the function being called, for a program that is its body. */
    private var parameters: Set<Id> = emptySet()

    private class ReturnJump(val value: Value) : RuntimeException(null, null, false, false)
    private class BreakJump : RuntimeException(null, null, false, false)
    private class ContinueJump : RuntimeException(null, null, false, false)

    /** The names [lines] read before they first assign them, in program order (a line's value before its target). */
    private fun readFirst(lines: List<ProgramLine>): Set<Id> {
        val assigned = mutableSetOf<Id>()
        val read = mutableSetOf<Id>()
        fun reads(e: Expr?) {
            when (e) {
                is Id -> if (e !in assigned) read += e
                is Apply -> e.args.forEach(::reads)
                is Call -> { reads(e.fn); e.args.forEach(::reads) }
                is Index -> { reads(e.target); e.indices.forEach(::reads) }
                is Parens -> reads(e.inner)
                is Matrix -> e.rows.flatten().forEach(::reads)
                is Range -> { reads(e.first); reads(e.second); reads(e.last) }
                else -> {}
            }
        }
        fun walk(ls: List<ProgramLine>) {
            for (line in ls) when (line) {
                is Line -> reads(line.expr)
                is Return -> reads(line.value)
                is LocalDefine -> {
                    reads(line.value)
                    when (val t = line.target) {
                        is Index -> { t.indices.forEach(::reads); assigned += t.target as Id }
                        is Id -> assigned += t
                        is Matrix -> t.rows.flatten().filterIsInstance<Id>().forEach { assigned += it }
                        is Apply -> { reads(t.args.getOrNull(1)); (t.args[0] as? Id)?.let { assigned += it } }
                        is Call -> (t.fn as? Id)?.let { assigned += it }
                        else -> {}
                    }
                }
                is IfThen -> { reads(line.condition); walk(line.body) }
                is Otherwise -> walk(line.body)
                is For -> { reads(line.over); (line.variable as? Id)?.let { assigned += it }; walk(line.body) }
                is While -> { reads(line.condition); walk(line.body) }
                else -> {}
            }
        }
        walk(lines)
        return read
    }

    /** Runs a program; its value is the last line executed, or what `return` gives. */
    private fun program(p: Program, scope: Scope): Value {
        val locals = Locals(scope, (parameters + readFirst(p.lines)).toMutableSet())
        parameters = emptySet()
        return try {
            block(p.lines, locals) ?: throw EvalException("the program ended without a value", p)
        } catch (r: ReturnJump) {
            r.value
        } catch (_: BreakJump) {
            throw EvalException("break is only allowed inside a loop", p)
        } catch (_: ContinueJump) {
            throw EvalException("continue is only allowed inside a loop", p)
        }
    }

    /** Runs [lines] in order; returns the value of the last one executed, or null if none gave one. */
    private fun block(lines: List<ProgramLine>, locals: Locals): Value? {
        var last: Value? = null
        // Whether an `if` in the run of `if` lines just above has run; null outside such a run.
        var ifRan: Boolean? = null
        for (line in lines) {
            when (line) {
                is IfThen -> {
                    val runs = truth(line.condition, locals.scope)
                    if (runs) last = block(line.body, locals) ?: last
                    ifRan = (ifRan ?: false) || runs
                    continue
                }
                is Otherwise -> {
                    val before = ifRan ?: throw EvalException("otherwise must come straight after an if line")
                    if (!before) last = block(line.body, locals) ?: last
                }
                is Line -> last = eval(line.expr, locals.scope)
                is LocalDefine -> last = when (val target = line.target) {
                    is Id -> eval(line.value, locals.scope).also { locals.scope = locals.scope.with(target, it); locals.own += target }
                    is Index -> {
                        val id = target.target as Id
                        val (array, _) = setElement(id, target, line.value, locals.scope, fresh = id !in locals.own)
                        locals.own += id
                        locals.scope = locals.scope.with(id, array)
                        // Its value is the whole array: a program ending `v[n] <- x` (or in an if not taken after it) gives v.
                        array
                    }
                    // `f(x, y) <- body`: a local function; its free names are the program's, as they stand here.
                    is Call -> {
                        val id = target.fn as Id
                        val fn = UserFunction(id, target.args.map { it as Id }, line.value)
                        locals.scope = locals.scope.with(id, fn)
                        fn.scope = locals.scope
                        locals.own += id
                        fn
                    }
                    is Matrix, is Apply -> eval(line.value, locals.scope).also { v ->
                        assign(target, v, locals.scope) { it !in locals.own }.forEach { (id, x) -> locals.scope = locals.scope.with(id, x); locals.own += id }
                    }
                    else -> error("parser guarantees the target shape")
                }
                is For -> {
                    for (v in loopValues(line, locals.scope)) {
                        locals.scope = locals.scope.with(line.variable as? Id ?: throw EvalException("a for loop needs its variable", line.over), v)
                        locals.own += line.variable as Id
                        try {
                            last = block(line.body, locals) ?: last
                        } catch (_: BreakJump) {
                            break
                        } catch (_: ContinueJump) {
                            continue
                        }
                    }
                }
                is While -> {
                    var count = 0
                    while (truth(line.condition, locals.scope)) {
                        if (++count > MAX_LOOPS) throw EvalException("the while loop ran more than $MAX_LOOPS times (does its condition ever become false?)", line.condition)
                        try {
                            last = block(line.body, locals) ?: last
                        } catch (_: BreakJump) {
                            break
                        } catch (_: ContinueJump) {
                            continue
                        }
                    }
                }
                is Return -> throw ReturnJump(eval(line.value, locals.scope))
                Break -> throw BreakJump()
                Continue -> throw ContinueJump()
            }
            ifRan = null
        }
        return last
    }

    /** What `for k in …` runs over: a range, or the elements of an array (or a single value). */
    private fun loopValues(line: For, scope: Scope): List<Value> = when (val over = line.over) {
        is Range -> range(over, scope).values
        else -> when (val v = eval(over, scope)) {
            is ArrayValue -> v.elements
            is RangeValue -> v.values
            else -> listOf(v)
        }
    }

    // Calls.

    private fun call(e: Call, scope: Scope): Value {
        // A function's arguments and body start afresh: `2*f(20 °C)` passes 293.15 K.
        val outer = compound
        compound = 0
        try {
            return callAfresh(e, scope)
        } finally {
            compound = outer
        }
    }

    /** `min(a, b)`: a built-in unit named like a function (the minute) gives way to the function when called, unless the sheet redefined it. */
    private fun unitNamedLikeFunction(fn: Expr, value: Value): FunctionValue? {
        val id = fn.unwrapped as? Id ?: return null
        return (Builtins.functions[id] as? FunctionValue)?.takeIf { value === Builtins.units[id] }
    }

    private fun callAfresh(e: Call, scope: Scope): Value {
        // `°C(20)`: temperature scales also work as functions, the same as `20 °C`.
        if (e.args.size == 1) temperatureZero(e.fn, scope)?.let { return onScale(e.args[0], e.fn.unwrapped as Id, it, scope) }
        val fn = eval(e.fn, scope).let { it as? FunctionValue ?: unitNamedLikeFunction(e.fn, it) }
            ?: throw EvalException("only a function can be called", e.fn)
        if (fn is Builtin && fn.special) {
            when (fn.name) {
                "vec" -> return vectorize(e, scope)
                in ITERATIONS -> if (e.args.size >= 2) return iterate(fn.name, e, scope)
                "diff" -> return derivative(e, scope)
                "int" -> return integral(e, scope)
                "root" -> return solveRoot(e, scope)
                "find", "minerr" -> return solve(e, scope, fn.name)
                "Minimize", "Maximize" -> return optimize(e, scope, fn.name)
                "Odesolve" -> return odesolve(e, scope)
                "use" -> return use(e, scope)
            }
        }
        if (vectorizing > 0) {
            val values = e.args.map { eval(it, scope) }
            if (values.any { it is ArrayValue }) return broadcast(values) { elements -> invoke(fn, elements.map { v -> { v } }, e, scope) }
            return invoke(fn, values.map { v -> { v } }, e, scope)
        }
        return invoke(fn, e.args.map { arg -> { eval(arg, scope) } }, e, scope)
    }

    private fun invoke(fn: FunctionValue, args: List<() -> Value>, e: Call, scope: Scope): Value = when (fn) {
        is Builtin -> {
            if (args.size !in fn.arity) throw EvalException("${fn.name} takes ${arityText(fn.arity)}, not ${args.size}", e)
            fn.impl(Builtin.Context({ origin(scope) }, random, folder) { f, values -> invoke(f, values.map { v -> { v } }, e, scope) }, args)
        }
        is UserFunction -> {
            if (args.size != fn.params.size) {
                throw EvalException("${fn.name} takes ${fn.params.size} argument(s), not ${args.size}", e, listOf(fn.id))
            }
            var body = fn.scope
            fn.params.zip(args).forEach { (p, arg) -> body = body.with(p, arg()) }
            parameters = fn.params.toSet()
            if (depth >= MAX_DEPTH) throw EvalException("too many nested calls (is the recursion infinite?)", e)
            depth++
            val outerVectorizing = vectorizing
            vectorizing = 0 // vec() applies to what is written inside it, not to function bodies
            try {
                eval(fn.body, body)
            } catch (_: StackOverflowError) {
                throw EvalException("too many nested calls (is the recursion infinite?)", e)
            } finally {
                depth--
                vectorizing = outerVectorizing
                parameters = emptySet()
            }
        }
    }

    /** `vec(expr)`: operators and function calls inside apply element by element. */
    private fun vectorize(e: Call, scope: Scope): Value {
        if (e.args.size != 1) throw EvalException("vec takes 1 argument(s), not ${e.args.size}", e)
        vectorizing++
        try {
            return eval(e.args[0], scope)
        } finally {
            vectorizing--
        }
    }

    /** Applies [f] to matching elements of the arrays in [values]; scalars go to every element. */
    private fun broadcast(values: List<Value>, f: (List<Value>) -> Value): Value {
        val arrays = values.filterIsInstance<ArrayValue>()
        val shape = arrays[0]
        if (arrays.any { it.rows != shape.rows || it.cols != shape.cols }) {
            throw EvalException("vec: array sizes do not match: ${arrays.joinToString(" and ") { it.size }}")
        }
        return ArrayValue.of(shape.rows, shape.cols) { r, c -> f(values.map { if (it is ArrayValue) it[r, c] else it }) }
    }

    /** `sum(expr, i)` over a range variable, or `sum(expr, i, a, b)` over whole numbers; also `prod`. */
    private fun iterate(name: String, e: Call, scope: Scope): Value {
        val variable = e.args[1] as? Id ?: throw EvalException("$name: the second argument must be the variable to iterate over", e.args[1])
        val values: List<Value> = when (e.args.size) {
            2 -> ((scope[variable] as? Binding.Bound)?.value as? RangeValue)?.values
                ?: throw EvalException("$name: ${display(variable)} is not a range variable (or give the limits: $name(expr, ${display(variable)}, a, b))", e.args[1], listOf(variable))
            4 -> {
                val a = wholeNumber(e.args[2], scope, "a limit of $name")
                val b = wholeNumber(e.args[3], scope, "a limit of $name")
                if (b.toLong() - a >= MAX_ELEMENTS) throw EvalException("$name: more than $MAX_ELEMENTS terms", e)
                (a..b).map { Scalar(Quantity(it.toDouble())) }
            }
            else -> throw EvalException("$name takes 1, 2 or 4 arguments: $name(v), $name(expr, i) or $name(expr, i, a, b)", e)
        }
        var acc: Value? = null
        for (v in values) {
            val term = eval(e.args[0], scope.with(variable, v))
            acc = when {
                acc == null -> term
                name == "sum" -> MatrixMath.elementwise(acc, term, "sum", Quantity::plus)
                else -> MatrixMath.multiply(acc, term)
            }
        }
        return acc ?: Scalar(Quantity(if (name == "sum") 0.0 else 1.0))
    }

    /** `diff(f, x)` or `diff(f, x, n)`: the derivative of f with respect to x, at x's current value. */
    private fun derivative(e: Call, scope: Scope): Value {
        if (e.args.size !in 2..3) throw EvalException("diff takes the expression and the variable: diff(f, x), or diff(f, x, n)", e)
        val x = e.args[1] as? Id ?: throw EvalException("diff: the second argument must be the variable", e.args[1])
        val n = e.args.getOrNull(2)?.let { wholeNumber(it, scope, "the order of diff") } ?: 1
        if (n !in 1..5) throw EvalException("diff: the order must be 1 to 5", e.args[2])
        val x0 = ((scope[x] as? Binding.Bound)?.value ?: throw EvalException("${display(x)} is not defined: diff needs its value", e.args[1], listOf(x)))
            .quantityOrThrow("diff's variable", e.args[1])
        if (!x0.isReal) throw EvalException("diff: complex values aren't supported yet", e.args[1])
        val (f, dimension) = Calculus.real({ t -> eval(e.args[0], scope.with(x, Scalar(t))).quantityOrThrow("diff's expression", e.args[0]) }, x0.dimension, "diff")
        val d = Calculus.derivative(f, x0.value.re, n)
        return Scalar(Quantity(d, dimension() / x0.dimension.pow(n)))
    }

    /** `int(f, x, a, b)`: the definite integral of f over x from a to b. */
    private fun integral(e: Call, scope: Scope): Value {
        if (e.args.size != 4) throw EvalException("int takes the expression, the variable and the limits: int(f, x, a, b)", e)
        val x = e.args[1] as? Id ?: throw EvalException("int: the second argument must be the variable", e.args[1])
        val (a, b) = sameUnits(listOf(eval(e.args[2], scope).quantityOrThrow("a limit of int", e.args[2]), eval(e.args[3], scope).quantityOrThrow("a limit of int", e.args[3])))
            ?: throw EvalException("int: the limits have different units", e.args[3])
        if (!a.isReal || !b.isReal || a.value.re.isNaN() || b.value.re.isNaN()) throw EvalException("int: the limits must be real numbers", e)
        val (f, dimension) = Calculus.real({ t -> eval(e.args[0], scope.with(x, Scalar(t))).quantityOrThrow("int's expression", e.args[0]) }, a.dimension, "int")
        val v = Calculus.integral(f, a.value.re, b.value.re, minOf(tolerance(scope), 1e-3) * 1e-6)
        return Scalar(Quantity(v, dimension() * a.dimension))
    }

    /**
     * `root(f, x)`: a value of x where f is 0, starting from x's current value as the guess;
     * `root(f, x, a, b)`: one between a and b, where f changes sign.
     */
    private fun solveRoot(e: Call, scope: Scope): Value {
        if (e.args.size != 2 && e.args.size != 4) throw EvalException("root takes the expression and the variable, and optionally limits: root(f, x) or root(f, x, a, b)", e)
        val x = e.args[1] as? Id ?: throw EvalException("root: the second argument must be the variable", e.args[1])
        val given: List<Quantity> = if (e.args.size == 4) {
            listOf(eval(e.args[2], scope).quantityOrThrow("a limit of root", e.args[2]), eval(e.args[3], scope).quantityOrThrow("a limit of root", e.args[3]))
        } else {
            listOf(
                (guess(x, scope) ?: throw EvalException("${display(x)} is not defined: root needs a guess, or limits", e.args[1], listOf(x)))
                    .quantityOrThrow("root's guess", e.args[1]),
            )
        }
        val start = sameUnits(given) ?: throw EvalException("root: the limits have different units", e)
        val dim = start[0].dimension
        if (start.any { !it.isReal }) throw EvalException("root: complex values aren't supported yet", e)
        val (f, _) = Calculus.real({ t -> eval(e.args[0], scope.with(x, Scalar(t))).quantityOrThrow("root's expression", e.args[0]) }, dim, "root")
        val r = if (start.size == 2) Calculus.brent(f, minOf(start[0].value.re, start[1].value.re), maxOf(start[0].value.re, start[1].value.re))
        else Calculus.root(f, start[0].value.re)
        return Scalar(Quantity(r, dim))
    }

    // Operators.

    private fun apply(e: Apply, scope: Scope): Value {
        val a = e.args
        if (e.op == Op.MEMBER) return member(e, scope)
        if (e.op == Op.ON_ERROR) {
            return try {
                eval(a[0], scope)
            } catch (_: EvalException) {
                eval(a[1], scope)
            }
        }
        // `20 °C` is on the scale; inside a product, quotient or power (`100*°C/(1.85*km)`, as imported sheets mean it) `°C` is one degree.
        if (e.op == Op.MUL && compound == 0) temperatureZero(a[1], scope)?.let { return onScale(a[0], a[1].unwrapped as Id, it, scope) }
        if (e.op == Op.MUL || e.op == Op.DIV || e.op == Op.POW) {
            compound++
            try {
                return operands(e, scope)
            } finally {
                compound--
            }
        }
        return operands(e, scope)
    }

    /** `use "file.py"`: the module of Python functions in that file, found next to the sheet. */
    private fun use(e: Call, scope: Scope): Value {
        val path = (eval(e.args[0], scope) as? Text)?.value ?: throw EvalException("use takes the file's name, in quotes: use \"tools.py\"", e.args[0])
        val loader = modules ?: throw EvalException("Python functions need the desktop version of Beermat, with its Python", e)
        val file = java.io.File(path).let { if (it.isAbsolute) it else java.io.File(folder ?: java.io.File("."), path) }
        if (!file.isFile) throw EvalException("there is no file $path${folder?.let { " next to the sheet" } ?: ""}", e.args[0])
        return loader.load(file)
    }

    /** `bt.f`: the function `f` of the module held in `bt`. */
    private fun member(e: Apply, scope: Scope): Value {
        val module = eval(e.args[0], scope) as? Module
            ?: throw EvalException("only a module (use \"file.py\") has functions to pick with '.'", e.args[0])
        val name = (e.args[1] as? Id)?.text ?: throw EvalException("this expression is incomplete", e.args[1])
        return module.functions[name] ?: throw EvalException(
            "${module.path} has no function $name" + if (module.functions.isEmpty()) "" else " (it has ${module.functions.keys.sorted().joinToString()})",
            e.args[1],
        )
    }

    /** Above zero while evaluating the operands of a product, quotient or power (see [apply]). */
    private var compound = 0

    private fun operands(e: Apply, scope: Scope): Value {
        val a = e.args
        val chain = e.op in COMPARISONS && (a[0] as? Apply)?.op in COMPARISONS
        if (vectorizing > 0 && !chain) {
            val values = a.map { eval(it, scope) }
            if (e.op !in WHOLE_ARRAY && values.any { it is ArrayValue }) return broadcast(values) { operate(e, it, scope) }
            return operate(e, values, scope)
        }
        return when (e.op) {
            Op.NOT -> bool(!truth(a[0], scope))
            Op.AND -> bool(truth(a[0], scope) && truth(a[1], scope))
            Op.OR -> bool(truth(a[0], scope) || truth(a[1], scope))
            Op.XOR -> bool(truth(a[0], scope) != truth(a[1], scope))
            Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE -> bool(chain(e, scope) != null)
            Op.COLUMN -> {
                val m = eval(a[0], scope) as? ArrayValue ?: throw EvalException("only a matrix has columns", a[0])
                val j = position(a[1], scope, m.cols, "column")
                ArrayValue.of(m.rows, 1) { r, _ -> m[r, j] }
            }
            else -> operate(e, a.map { eval(it, scope) }, scope)
        }
    }

    /** An operator on evaluated operands. Scalars take the plain path; arrays go to [MatrixMath]. */
    private fun operate(e: Apply, values: List<Value>, scope: Scope): Value {
        val a = e.args
        fun q(k: Int) = values[k].quantityOrThrow("an operand", a[k])
        val array = values.any { it is ArrayValue }
        return when (e.op) {
            Op.MEMBER -> error("a member is evaluated by apply")
            Op.ADD -> if (array) MatrixMath.elementwise(values[0], values[1], "addition", Quantity::plus) else Scalar(q(0) + q(1))
            Op.SUB -> if (array) MatrixMath.elementwise(values[0], values[1], "subtraction", Quantity::minus) else Scalar(q(0) - q(1))
            Op.MUL -> if (array) MatrixMath.multiply(values[0], values[1]) else Scalar(q(0) * q(1))
            Op.DIV -> {
                val den = values[1]
                if (den is Scalar && den.q.value == Complex.ZERO) throw EvalException("division by zero", a[1])
                if (array) MatrixMath.divide(values[0], den) else Scalar(q(0) / q(1))
            }
            Op.POW -> when (val base = values[0]) {
                is ArrayValue -> MatrixMath.power(base, values[1])
                else -> Scalar(q(0).pow(q(1)))
            }
            Op.NEG -> MatrixMath.map(values[0], "negation") { -it }
            Op.POS -> MatrixMath.map(values[0], "unary plus") { it }
            Op.ABS -> when (val x = values[0]) {
                is ArrayValue -> MatrixMath.abs(x)
                else -> Scalar(q(0).let { Quantity(it.value.abs(), it.dimension) })
            }
            Op.FACTORIAL -> Scalar(Quantity(factorial(q(0), a[0])))
            Op.TRANSPOSE -> MatrixMath.transpose(values[0])
            Op.COLUMN -> {
                val m = values[0] as? ArrayValue ?: throw EvalException("only a matrix has columns", a[0])
                ArrayValue.of(m.rows, 1) { r, _ -> m[r, positionOf(values[1], a[1], scope, m.cols, "column")] }
            }
            Op.NOT -> bool(!values[0].truth("a logical operand"))
            Op.AND -> bool(values[0].truth("a logical operand") && values[1].truth("a logical operand"))
            Op.OR -> bool(values[0].truth("a logical operand") || values[1].truth("a logical operand"))
            Op.XOR -> bool(values[0].truth("a logical operand") != values[1].truth("a logical operand"))
            Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE -> bool(compare(e.op, values[0], values[1], e, scope))
            Op.ON_ERROR -> error("on error is evaluated lazily in apply")
        }
    }

    /**
     * Evaluates a comparison, which may be a chain (`0 < x < 1`: a comparison whose left operand
     * is an unparenthesised comparison). Returns the rightmost operand's value if the whole
     * chain holds, or null as soon as one link fails.
     */
    /** A chain's links, each its own comparison: `0 < x < L` is `0 < x` and `x < L` (a constraint holds both). */
    private fun links(e: Apply): List<Apply> {
        val l = e.args[0]
        return if (l is Apply && l.op in COMPARISONS) links(l) + Apply(e.op, listOf(l.args[1], e.args[1])) else listOf(e)
    }

    private fun chain(e: Apply, scope: Scope): Value? {
        val l = e.args[0]
        val left = if (l is Apply && l.op in COMPARISONS) chain(l, scope) ?: return null else eval(l, scope)
        val right = eval(e.args[1], scope)
        return if (compare(e.op, left, right, e, scope)) right else null
    }

    private fun compare(op: Op, a: Value, b: Value, node: Expr, scope: Scope): Boolean {
        if (a is Text && b is Text) {
            val c = a.value.compareTo(b.value)
            return holds(op, c)
        }
        val x = a.quantityOrThrow("a comparison operand", node)
        val y = b.quantityOrThrow("a comparison operand", node)
        val tol = tolerance(scope)
        return when (op) {
            Op.EQ -> x.valueEquals(y, tol)
            Op.NE -> !x.valueEquals(y, tol)
            else -> holds(op, x.compareWithin(y, tol))
        }
    }

    private fun tolerance(scope: Scope): Double {
        val tol = (scope[TOL] as? Binding.Bound)?.value
            ?: throw EvalException("TOL is not defined or has an error")
        val q = tol.quantity("TOL")
        if (!q.dimension.isNone || !q.isReal || q.value.re < 0) throw EvalException("TOL must be a non-negative number")
        return q.value.re
    }

    private fun holds(op: Op, c: Int) = when (op) {
        Op.EQ -> c == 0
        Op.NE -> c != 0
        Op.LT -> c < 0
        Op.LE -> c <= 0
        Op.GT -> c > 0
        Op.GE -> c >= 0
        else -> error("not a comparison: $op")
    }

    private fun truth(e: Expr, scope: Scope): Boolean = eval(e, scope).truth("a logical operand")

    private fun factorial(q: Quantity, node: Expr): Double {
        val n = q.requireNone("factorial").realValue("factorial")
        if (n < 0 || n != Math.rint(n)) throw EvalException("factorial needs a non-negative integer", node)
        if (n > 170) throw EvalException("factorial overflows for n > 170", node)
        return (2..n.toInt()).fold(1.0) { acc, k -> acc * k }
    }

    // Indices.

    private fun index(e: Index, scope: Scope): Value {
        val m = eval(e.target, scope) as? ArrayValue ?: throw EvalException("only a matrix or vector can be indexed", e.target)
        if (e.indices.size == 2) {
            return m[position(e.indices[0], scope, m.rows, "row"), position(e.indices[1], scope, m.cols, "column")]
        }
        if (!m.isVector) throw EvalException("a ${m.size} matrix needs two indices, as in M[i, j]", e)
        return m.elements[position(e.indices[0], scope, m.elements.size, "element")]
    }

    /** Evaluates an index and turns it into a 0-based position below [size], counting from ORIGIN. */
    private fun position(e: Expr, scope: Scope, size: Int, what: String): Int =
        positionOf(eval(e, scope), e, scope, size, what)

    private fun positionOf(v: Value, e: Expr, scope: Scope, size: Int, what: String): Int {
        val origin = origin(scope)
        val k = wholeNumber(v, e, "an index")
        if (k < origin || k >= origin + size) {
            throw EvalException("$what $k is out of range: there are $size, numbered from ORIGIN = $origin", e)
        }
        return k - origin
    }

    private fun wholeNumber(e: Expr, scope: Scope, what: String): Int = wholeNumber(eval(e, scope), e, what)

    private fun wholeNumber(v: Value, e: Expr, what: String): Int {
        val q = v.quantityOrThrow(what, e)
        if (!q.dimension.isNone || !q.isReal || q.value.re != Math.rint(q.value.re)) {
            throw EvalException("$what must be a whole number", e)
        }
        return q.value.re.toInt()
    }

    private fun origin(scope: Scope): Int {
        val v = (scope[ORIGIN] as? Binding.Bound)?.value ?: throw EvalException("ORIGIN is not defined or has an error")
        val q = v.quantity("ORIGIN")
        if (!q.dimension.isNone || !q.isReal || q.value.re != Math.rint(q.value.re)) throw EvalException("ORIGIN must be a whole number")
        return q.value.re.toInt()
    }

    // Helpers.

    private fun Value.quantityOrThrow(what: String, node: Expr): Quantity = when (this) {
        is Scalar -> q
        is Text -> throw EvalException("$what must be a number, not a string", node)
        is Module -> throw EvalException("$what must be a number, not a module", node)
        is FunctionValue -> throw EvalException("$what must be a number, not the function $name", node)
        is ArrayValue -> throw EvalException("$what must be a number, not a $size matrix", node)
        is RangeValue -> throw EvalException(RangeValue.MISUSE, node)
    }

    private fun bool(b: Boolean) = Scalar(if (b) Quantity(1.0) else Quantity(0.0))

    private fun numberValue(text: String): Complex {
        val imaginary = text.endsWith('i') || text.endsWith('j')
        val x = (if (imaginary) text.dropLast(1) else Num(text).decimal).toDouble()
        return if (imaginary) Complex(0.0, x) else Complex(x)
    }

    private fun display(id: Id) = id.text

    private fun arityText(r: IntRange) = when {
        r.first == r.last -> "${r.first} argument(s)"
        r.last == Int.MAX_VALUE -> "at least ${r.first} argument(s)"
        else -> "${r.first} to ${r.last} arguments"
    }

    private companion object {
        const val MAX_DEPTH = 1000
        const val MAX_ELEMENTS = 1_000_000
        const val MAX_LOOPS = 10_000_000
        val TOL = Id("TOL")
        val ORIGIN = Id("ORIGIN")
        val COMPARISONS = setOf(Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE)

        /** How far (relative to the size of its sides) a constraint may miss for `find` to accept the solution. */
        const val FIND_TOLERANCE = 1e-6
        /** Operators that act on a whole array even inside `vec`. */
        val WHOLE_ARRAY = setOf(Op.TRANSPOSE, Op.COLUMN)
        val ITERATIONS = setOf("sum", "prod")
    }
}

/**
 * Values that must share a unit (the limits of a range, `int` or `root`), where a bare 0 takes the
 * others' unit, as 0 goes with any unit: `0..L`, `int(f, x, 0, L)`. With [data] (the
 * values of `linterp`, `max`, `min`), any 0 does, whatever its unit: in an array a program filled from
 * zeros, a zero element scaled with the rest (0/MN) still has the array's unit in imported sheets, where
 * elements shared one. Null when they still differ.
 */
internal fun sameUnits(qs: List<Quantity>, data: Boolean = false): List<Quantity>? {
    fun bare(q: Quantity) = q.value == Complex.ZERO && (data || q.dimension.isNone)
    val dim = qs.firstOrNull { !bare(it) }?.dimension ?: return qs
    return qs.map { if (bare(it)) Quantity(it.value, dim) else it }.takeIf { all -> all.all { it.dimension == dim } }
}
