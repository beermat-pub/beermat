package pub.beermat.engine.eval

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Solve blocks' numeric core (docs/evaluation.md#solve-blocks): Levenberg–Marquardt on the
 * constraints' residuals, with a finite-difference Jacobian. Small systems only (a handful of
 * unknowns), which is what worksheets have.
 */
internal object Solver {

    /** The x that brings [residuals] closest to zero (least squares), starting from [x0]. */
    fun leastSquares(residuals: (DoubleArray) -> DoubleArray, x0: DoubleArray): DoubleArray {
        // Work in variables scaled by the guesses, so a step of 1 means "about the guess" whatever the units.
        val s = DoubleArray(x0.size) { if (x0[it] != 0.0) abs(x0[it]) else 1.0 }
        val scaled = { v: DoubleArray -> residuals(DoubleArray(v.size) { v[it] * s[it] }) }
        val u0 = DoubleArray(x0.size) { x0[it] / s[it] }
        fun cost(u: DoubleArray) = scaled(u).sumOf { it * it }
        // Steps within a trust region first; if that doesn't reach a solution (it can stop where the
        // residuals have gone flat), the unbounded steps from the guesses, and the closer of the two.
        val bounded = solveScaled(scaled, u0, bounded = true)
        val u = if (cost(bounded) <= 1e-12 * cost(u0).let { if (it.isFinite()) it else 1.0 }) bounded else solveScaled(scaled, u0, bounded = false).takeIf { cost(it) < cost(bounded) } ?: bounded
        return DoubleArray(u.size) { u[it] * s[it] }
    }

    private fun solveScaled(residuals: (DoubleArray) -> DoubleArray, x0: DoubleArray, bounded: Boolean): DoubleArray {
        val n = x0.size
        var x = x0.copyOf()
        var r = residuals(x)
        var cost = r.sumOf { it * it }
        var lambda = -1.0
        // A trust region in the scaled variables: a step goes at most this far, starting at about the
        // guesses' size and doubling while steps succeed, so a first linearised step can't fly off
        // across a pole to another family of solutions (a Weibull fit's k from 1 to -179).
        var radius = if (bounded) 1.0 else Double.POSITIVE_INFINITY
        repeat(500) {
            if (cost < 1e-30) return x
            val j = jacobian(residuals, x, r)
            // Normal equations: (JᵀJ + λ I) δ = −Jᵀr.
            val a = Array(n) { p -> DoubleArray(n) { q -> r.indices.sumOf { i -> j[i][p] * j[i][q] } } }
            val g = DoubleArray(n) { p -> r.indices.sumOf { i -> j[i][p] * r[i] } }
            val diagonal = max((0 until n).maxOf { a[it][it] }, 1e-30)
            if (lambda < 0) lambda = 1e-3 * diagonal
            var improved = false
            for (attempt in 0 until 40) {
                val m = Array(n) { p -> DoubleArray(n) { q -> a[p][q] + if (p == q) lambda else 0.0 } }
                val delta = solveLinear(m, DoubleArray(n) { -g[it] }) ?: break
                val length = norm(delta)
                if (length > radius) for (k in delta.indices) delta[k] *= radius / length
                val trial = DoubleArray(n) { x[it] + delta[it] }
                // A step onto a point where the constraints can't be evaluated (a pole of Γ) is a step too far: shorten it.
                val rt = try {
                    residuals(trial)
                } catch (_: EvalException) {
                    null
                }
                val ct = rt?.sumOf { it * it } ?: Double.NaN
                if (rt != null && ct.isFinite() && ct < cost) {
                    val small = delta.indices.all { abs(delta[it]) <= 1e-14 * max(abs(x[it]), 1e-300) }
                    x = trial
                    r = rt
                    cost = ct
                    lambda = max(lambda / 3, 1e-15 * diagonal)
                    radius *= 2
                    improved = true
                    if (small) return x
                    break
                }
                lambda *= 4
                if (bounded) radius = minOf(radius, length) / 2
            }
            if (!improved) return x
        }
        return x
    }

    private fun jacobian(f: (DoubleArray) -> DoubleArray, x: DoubleArray, fx: DoubleArray): Array<DoubleArray> {
        val j = Array(fx.size) { DoubleArray(x.size) }
        for (k in x.indices) {
            val h = 1e-7 * max(abs(x[k]), 1e-3)
            val xp = x.copyOf().also { it[k] += h }
            val fp = f(xp)
            for (i in fx.indices) j[i][k] = (fp[i] - fx[i]) / h
        }
        return j
    }

    /** Gaussian elimination with partial pivoting; null when singular. */
    internal fun solveLinear(m: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val a = Array(n) { m[it].copyOf() }
        val y = b.copyOf()
        for (c in 0 until n) {
            val p = (c until n).maxByOrNull { abs(a[it][c]) }!!
            if (abs(a[p][c]) < 1e-300) return null
            a[c] = a[p].also { a[p] = a[c] }
            y[c] = y[p].also { y[p] = y[c] }
            for (r in c + 1 until n) {
                val f = a[r][c] / a[c][c]
                for (k in c until n) a[r][k] -= f * a[c][k]
                y[r] -= f * y[c]
            }
        }
        val out = DoubleArray(n)
        for (r in n - 1 downTo 0) out[r] = (y[r] - (r + 1 until n).sumOf { a[r][it] * out[it] }) / a[r][r]
        return out
    }

    fun norm(v: DoubleArray) = sqrt(v.sumOf { it * it })

    /**
     * The x where [f] is least, from [x0]: Nelder–Mead on variables scaled by the guesses, restarted
     * from the best point until it stops improving (Minimize, Maximize). Derivative-free, so it
     * copes with the kinks a penalty for a broken constraint makes.
     */
    fun minimize(f: (DoubleArray) -> Double, x0: DoubleArray): DoubleArray {
        val s = DoubleArray(x0.size) { if (x0[it] != 0.0) abs(x0[it]) else 1.0 }
        fun g(u: DoubleArray) = f(DoubleArray(u.size) { u[it] * s[it] }).let { if (it.isNaN()) Double.POSITIVE_INFINITY else it }
        var u = DoubleArray(x0.size) { x0[it] / s[it] }
        var best = g(u)
        for (restart in 0 until 6) {
            val next = nelderMead(::g, u, if (restart == 0) 0.1 else 0.02)
            val value = g(next)
            val gain = best - value
            u = next
            if (value <= best) best = value
            if (gain <= 1e-14 * maxOf(abs(best), 1e-300) && restart > 0) break
        }
        return DoubleArray(u.size) { u[it] * s[it] }
    }

    private fun nelderMead(f: (DoubleArray) -> Double, start: DoubleArray, step: Double): DoubleArray {
        val n = start.size
        val points = Array(n + 1) { k -> start.copyOf().also { if (k > 0) it[k - 1] += if (it[k - 1] != 0.0) step * abs(it[k - 1]).coerceAtLeast(1.0) else step } }
        val values = DoubleArray(n + 1) { f(points[it]) }
        repeat(20_000) {
            val order = values.indices.sortedBy { values[it] }
            val best = order.first()
            val worst = order.last()
            val second = order[order.size - 2]
            val spread = abs(values[worst] - values[best])
            val size = (0 until n).maxOf { d -> points.maxOf { it[d] } - points.minOf { it[d] } }
            if (spread <= 1e-15 * maxOf(abs(values[best]), 1e-300) && size < 1e-12) return points[best]
            if (size < 1e-14) return points[best]
            val centroid = DoubleArray(n) { d -> points.indices.filter { it != worst }.sumOf { points[it][d] } / n }
            fun toward(t: Double) = DoubleArray(n) { centroid[it] + t * (points[worst][it] - centroid[it]) }
            val reflected = toward(-1.0)
            val fr = f(reflected)
            when {
                fr < values[best] -> {
                    val expanded = toward(-2.0)
                    val fe = f(expanded)
                    if (fe < fr) { points[worst] = expanded; values[worst] = fe } else { points[worst] = reflected; values[worst] = fr }
                }
                fr < values[second] -> { points[worst] = reflected; values[worst] = fr }
                else -> {
                    val contracted = if (fr < values[worst]) toward(-0.5) else toward(0.5)
                    val fc = f(contracted)
                    if (fc < minOf(fr, values[worst])) {
                        points[worst] = contracted
                        values[worst] = fc
                    } else {
                        // Shrink everything toward the best point.
                        for (k in points.indices) if (k != best) {
                            points[k] = DoubleArray(n) { d -> points[best][d] + 0.5 * (points[k][d] - points[best][d]) }
                            values[k] = f(points[k])
                        }
                    }
                }
            }
        }
        return points[values.indices.minBy { values[it] }]
    }
}
