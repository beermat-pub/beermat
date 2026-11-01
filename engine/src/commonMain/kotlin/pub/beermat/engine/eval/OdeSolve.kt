package pub.beermat.engine.eval

import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity
import kotlin.math.abs
import kotlin.math.max

/**
 * `Odesolve` (docs/evaluation.md#differential-equations): a solve block of differential equations
 * and initial conditions, solved numerically, giving each unknown as a function of the variable.
 *
 * Below `given`, an equation holds at the variable t (`y''(t) + y(t) == 0`), and an initial
 * condition at a number (`y(0) == 1`, `y'(0) == 0`). A derivative is written with primes,
 * `y'(t)`, or as `diff(y(t), t[, n])`, as imported sheets store it. The equations are turned into a
 * first-order system, the highest derivatives found from them at each step (by Newton's method,
 * so they needn't be solved by hand), and the system integrated with adaptive Dormand–Prince
 * steps. The functions given back interpolate the solution (cubic Hermite) between its points.
 */
internal class OdeSolve(
    private val eval: (Expr, Scope) -> Value,
    private val constraints: List<Pair<Apply, Scope>>,
) {
    /** An unknown function: its name, the order of its highest derivative, and its unit. */
    private class Unknown(val name: Id, var order: Int = 0, var unit: Dimension = Dimension.NONE)

    /** [names] (null: those the equations differentiate), as functions of [t] from the initial point to [end], [steps] intervals. */
    fun solve(names: List<Id>?, t: Id, end: Quantity, steps: Int): List<FunctionValue> {
        // Equations mention t; conditions don't. Derivatives as `diff(y(t), t, n)` are rewritten as `y''(t)`.
        val (equations, conditions) = constraints.partition { (c, _) -> mentions(c, t) }
        if (equations.isEmpty()) throw EvalException("Odesolve: the solve block has no differential equation (one that uses $t)")
        val odes = equations.map { (c, s) -> rewrite(c, t) to s }
        val found = linkedMapOf<String, Unknown>()
        for ((c, _) in odes) derivatives(c, t) { base, k -> found.getOrPut(base.name + "#" + base.subscript) { Unknown(base) }.let { if (k > it.order) it.order = k } }
        val unknowns = names?.map { n -> found[n.name + "#" + n.subscript] ?: throw EvalException("Odesolve: no equation differentiates ${n.text}") } ?: found.values.toList()
        if (unknowns.isEmpty()) throw EvalException("Odesolve: no unknown function: write its derivatives as y'(t) or diff(y(t), t)")
        if (odes.size != unknowns.size) throw EvalException("Odesolve: ${odes.size} equations for ${unknowns.size} unknown functions")
        for (u in unknowns) if (u.order < 1) throw EvalException("Odesolve: ${u.name.text} has no derivative in the equations")

        // Initial conditions: y(a) == v, y'(a) == v, …, all at one point a.
        var start: Quantity? = null
        val initial = unknowns.associateWith { arrayOfNulls<Quantity>(it.order) }
        for ((c, s) in conditions) {
            if (c.op != Op.EQ) throw EvalException("Odesolve: a condition must be an equality, such as y(0) == 1", c)
            val (call, other) = listOf(c.args[0], c.args[1]).let { (l, r) -> if (asCall(l) != null) l to r else r to l }
            val (base, k, at) = asCall(call)?.let { (b, k, at) -> Triple(b, k, at) } ?: throw EvalException("Odesolve: a condition reads y(a) == value, or y'(a) == value", c)
            val u = unknowns.firstOrNull { it.name == base } ?: throw EvalException("Odesolve: ${base.text} is not one of the unknowns", c)
            if (k >= u.order) throw EvalException("Odesolve: ${base.text} needs conditions up to its derivative of order ${u.order - 1}", c)
            val a = eval(at, s).quantity("Odesolve")
            val v = eval(other, s).quantity("Odesolve")
            if (start == null) start = a
            else if (!a.valueEquals(start, 1e-12) || a.dimension != start.dimension) throw EvalException("Odesolve: the initial conditions must all be at one point (boundary values at two points aren't supported yet)", c)
            initial.getValue(u)[k] = v
        }
        val a = start ?: throw EvalException("Odesolve: the solve block needs initial conditions, such as y(0) == 1")
        val (ta, tb) = sameUnits(listOf(a, end)) ?: throw EvalException("Odesolve: the end of the range needs the units of the initial point")
        val tUnit = ta.dimension
        for (u in unknowns) {
            val values = initial.getValue(u)
            values.forEachIndexed { k, v -> if (v == null) throw EvalException("Odesolve: ${u.name.text} needs a condition for ${prime(u.name, k)}(${ta.value.re})") }
            u.unit = values[0]!!.dimension
            values.forEachIndexed { k, v -> if (v!!.dimension != u.unit / tUnit.pow(k)) throw EvalException("Odesolve: the condition on ${prime(u.name, k)} has the wrong units") }
        }

        // The state: each unknown and its derivatives below the highest, in SI.
        val offsets = unknowns.runningFold(0) { o, u -> o + u.order }
        val y0 = DoubleArray(offsets.last()).also { y -> unknowns.forEachIndexed { i, u -> initial.getValue(u).forEachIndexed { k, v -> y[offsets[i] + k] = v!!.value.re } } }
        var guess = DoubleArray(unknowns.size)

        /** The equations' residuals at x with the state y and highest derivatives h. */
        fun residuals(x: Double, y: DoubleArray, h: DoubleArray): DoubleArray = DoubleArray(odes.size) { i ->
            val (c, s) = odes[i]
            var scope = s.with(t, Scalar(Quantity(x, tUnit)))
            unknowns.forEachIndexed { j, u ->
                for (k in 0..u.order) {
                    val value = Quantity(if (k == u.order) h[j] else y[offsets[j] + k], u.unit / tUnit.pow(k))
                    scope = scope.with(prime(u.name, k), Builtin(prime(u.name, k).text, 1..1) { _ -> Scalar(value) })
                }
            }
            val l = eval(c.args[0], scope).quantity("Odesolve")
            val r = eval(c.args[1], scope).quantity("Odesolve")
            if (l.dimension != r.dimension) throw EvalException("Odesolve: the two sides of an equation have different units", c)
            (l - r).realValue("Odesolve")
        }

        /** The highest derivatives that satisfy the equations, by Newton's method from the last ones. */
        fun highest(x: Double, y: DoubleArray): DoubleArray {
            var h = guess.copyOf()
            repeat(30) {
                val r = residuals(x, y, h)
                val jac = Array(h.size) { j ->
                    val step = 1e-7 * max(1.0, abs(h[j]))
                    val hp = h.copyOf().also { it[j] += step }
                    val rp = residuals(x, y, hp)
                    DoubleArray(r.size) { i -> (rp[i] - r[i]) / step }
                }
                val delta = Solver.solveLinear(Array(r.size) { i -> DoubleArray(h.size) { j -> jac[j][i] } }, DoubleArray(r.size) { -r[it] })
                    ?: throw EvalException("Odesolve: the equations don't determine the highest derivatives")
                h = DoubleArray(h.size) { h[it] + delta[it] }
                if (delta.indices.all { abs(delta[it]) <= 1e-12 * max(1.0, abs(h[it])) }) return h.also { guess = it }
            }
            return h.also { guess = it }
        }

        fun derivative(x: Double, y: DoubleArray): DoubleArray {
            val h = highest(x, y)
            return DoubleArray(y.size) { i ->
                val j = offsets.indexOfLast { it <= i }
                val k = i - offsets[j]
                if (k + 1 < unknowns[j].order) y[i + 1] else h[j]
            }
        }

        val xs = DoubleArray(steps + 1) { ta.value.re + (tb.value.re - ta.value.re) * it / steps }
        val ys = Array(steps + 1) { DoubleArray(0) }
        val ds = Array(steps + 1) { DoubleArray(0) }
        ys[0] = y0
        ds[0] = derivative(xs[0], y0)
        for (k in 1..steps) {
            ys[k] = Numerics.dormandPrince(::derivative, xs[k - 1], xs[k], ys[k - 1])
            ds[k] = derivative(xs[k], ys[k])
        }
        return unknowns.mapIndexed { j, u -> interpolant(u, offsets[j], xs, ys, ds, tUnit) }
    }

    /** The unknown as a function of t: cubic Hermite between the solution's points, from its value and slope there. */
    private fun interpolant(u: Unknown, offset: Int, xs: DoubleArray, ys: Array<DoubleArray>, ds: Array<DoubleArray>, tUnit: Dimension): FunctionValue =
        Builtin(u.name.text, 1..1) { a ->
            val q = a[0]().quantity(u.name.text)
            if (q.dimension != tUnit) throw EvalException("${u.name.text}: the argument needs the units of the variable")
            val x = q.realValue(u.name.text)
            val (lo, hi) = minOf(xs.first(), xs.last()) to maxOf(xs.first(), xs.last())
            if (x < lo - 1e-9 * (hi - lo) || x > hi + 1e-9 * (hi - lo)) throw EvalException("${u.name.text}: $x is outside the range Odesolve solved over, ${xs.first()} to ${xs.last()}")
            val n = xs.size - 1
            val k = ((x - xs[0]) / (xs[n] - xs[0]) * n).toInt().coerceIn(0, n - 1)
            val h = xs[k + 1] - xs[k]
            val s = (x - xs[k]) / h
            val (y0, y1) = ys[k][offset] to ys[k + 1][offset]
            val (d0, d1) = ds[k][offset] * h to ds[k + 1][offset] * h
            val v = (2 * s * s * s - 3 * s * s + 1) * y0 + (s * s * s - 2 * s * s + s) * d0 + (-2 * s * s * s + 3 * s * s) * y1 + (s * s * s - s * s) * d1
            Scalar(Quantity(v, u.unit))
        }

    private fun prime(id: Id, k: Int) = id.copy(name = id.name + "'".repeat(k))

    /** `y'''(a)` as the base name, the order and the argument; null for anything else. */
    private fun asCall(e: Expr): Triple<Id, Int, Expr>? {
        val c = unwrap(e) as? Call ?: return null
        val id = c.fn as? Id ?: return null
        if (c.args.size != 1) return null
        val k = id.name.length - id.name.trimEnd('\'').length
        return Triple(id.copy(name = id.name.trimEnd('\'')), k, c.args[0])
    }

    /** Each derivative called at t in [e]: `y''(t)` gives (y, 2), and `y(t)` (y, 0). */
    private fun derivatives(e: Expr, t: Id, found: (Id, Int) -> Unit) {
        val c = unwrap(e)
        if (c is Call && c.args.size == 1 && unwrap(c.args[0]) == t && c.fn is Id) asCall(c)?.let { (b, k, _) -> found(b, k) }
        children(c).forEach { derivatives(it, t, found) }
    }

    /** `diff(y(t), t)` and `diff(y(t), t, n)` as `y'(t)` and `y''…'(t)`. */
    private fun rewrite(e: Expr, t: Id): Apply = map(e, t) as Apply

    private fun map(e: Expr, t: Id): Expr {
        if (e is Call && (e.fn as? Id)?.name == "diff" && e.args.size in 2..3 && unwrap(e.args[1]) == t) {
            val inner = asCall(e.args[0])
            val n = e.args.getOrNull(2)?.let { (unwrap(it) as? Num)?.text?.toIntOrNull() } ?: 1
            if (inner != null && unwrap(inner.third) == t) return Call(prime(inner.first, inner.second + n), listOf(inner.third))
        }
        return when (e) {
            is Apply -> e.copy(args = e.args.map { map(it, t) })
            is Call -> Call(map(e.fn, t), e.args.map { map(it, t) })
            is Parens -> Parens(map(e.inner, t))
            is Index -> Index(map(e.target, t), e.indices.map { map(it, t) })
            is Matrix -> Matrix(e.rows.map { r -> r.map { map(it, t) } })
            else -> e
        }
    }

    private fun mentions(e: Expr, t: Id): Boolean = e == t || children(e).any { mentions(it, t) }

    private fun unwrap(e: Expr): Expr = if (e is Parens) unwrap(e.inner) else e

    private fun children(e: Expr): List<Expr> = when (e) {
        is Apply -> e.args
        is Call -> listOf(e.fn) + e.args
        is Parens -> listOf(e.inner)
        is Index -> listOf(e.target) + e.indices
        is Matrix -> e.rows.flatten()
        else -> emptyList()
    }
}
