package pub.beermat.engine.eval

import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity
import kotlin.math.abs
import kotlin.math.max

/**
 * Numeric derivatives and integrals (the d/dx and ∫ operators, evaluated with `=`;
 * docs/evaluation.md#calculus). Values are real; units follow: d/dx divides by x's unit,
 * ∫ … dx multiplies by it.
 */
internal object Calculus {

    /**
     * The [n]-th derivative of [f] at [x0]: central differences, refined by Richardson
     * extrapolation (Ridders' method) until the estimate stops improving.
     */
    fun derivative(f: (Double) -> Double, x0: Double, n: Int): Double {
        if (n == 0) return f(x0)
        // Each order is the derivative of the one below, each refined the same way.
        val g: (Double) -> Double = if (n == 1) f else { t -> derivative(f, t, n - 1) }
        return ridders(g, x0)
    }

    private fun ridders(f: (Double) -> Double, x: Double): Double {
        val ntab = 10
        val con = 1.4
        val con2 = con * con
        var h = 0.05 * max(abs(x), 1.0)
        val a = Array(ntab) { DoubleArray(ntab) }
        a[0][0] = (f(x + h) - f(x - h)) / (2 * h)
        var best = a[0][0]
        var err = Double.MAX_VALUE
        for (i in 1 until ntab) {
            h /= con
            a[0][i] = (f(x + h) - f(x - h)) / (2 * h)
            var fac = con2
            for (j in 1..i) {
                a[j][i] = (a[j - 1][i] * fac - a[j - 1][i - 1]) / (fac - 1)
                fac *= con2
                val e = max(abs(a[j][i] - a[j - 1][i]), abs(a[j][i] - a[j - 1][i - 1]))
                if (e <= err) {
                    err = e
                    best = a[j][i]
                }
            }
            if (abs(a[i][i] - a[i - 1][i - 1]) >= 2 * err) break
        }
        return best
    }

    /** ∫ [f] from [a] to [b], adaptive Gauss–Kronrod (7 and 15 points), to a relative [tolerance]. */
    fun integral(f: (Double) -> Double, a: Double, b: Double, tolerance: Double): Double {
        if (a == b) return 0.0
        if (b < a) return -integral(f, b, a, tolerance)
        // An infinite limit: onto [0, 1) or (-1, 1) by the usual substitutions (Gauss–Kronrod never touches the ends).
        if (b.isInfinite() && a.isInfinite()) return integral({ t -> f(t / (1 - t * t)) * (1 + t * t) / ((1 - t * t) * (1 - t * t)) }, -1.0, 1.0, tolerance)
        if (b.isInfinite()) return integral({ t -> f(a + t / (1 - t)) / ((1 - t) * (1 - t)) }, 0.0, 1.0, tolerance)
        if (a.isInfinite()) return integral({ t -> f(b - (1 - t) / t) / (t * t) }, 0.0, 1.0, tolerance)
        val whole = gk15(f, a, b)
        val target = max(tolerance * abs(whole.first), 1e-300)
        var evaluations = 0
        fun adapt(lo: Double, hi: Double, estimate: Pair<Double, Double>, depth: Int): Double {
            if (estimate.second <= target * (hi - lo) / (b - a) || depth > 40 || ++evaluations > 20_000) return estimate.first
            val mid = (lo + hi) / 2
            return adapt(lo, mid, gk15(f, lo, mid), depth + 1) + adapt(mid, hi, gk15(f, mid, hi), depth + 1)
        }
        return adapt(a, b, whole, 0)
    }

    /** The 15-point Kronrod estimate over [a, b] and its difference from the 7-point Gauss one. */
    private fun gk15(f: (Double) -> Double, a: Double, b: Double): Pair<Double, Double> {
        val c = (a + b) / 2
        val h = (b - a) / 2
        var k = WK[7] * f(c)
        var g = WG[3] * f(c)
        for (i in 0 until 7) {
            val dx = h * XK[i]
            val s = f(c - dx) + f(c + dx)
            k += WK[i] * s
            if (i % 2 == 1) g += WG[i / 2] * s
        }
        return k * h to abs((k - g) * h)
    }

    private val XK = doubleArrayOf(
        0.991455371120812639, 0.949107912342758525, 0.864864423359769073, 0.741531185599394440,
        0.586087235467691130, 0.405845151377397167, 0.207784955007898468,
    )
    private val WK = doubleArrayOf(
        0.022935322010529225, 0.063092092629978553, 0.104790010322250184, 0.140653259715525919,
        0.169004726639267903, 0.190350578064785410, 0.204432940075298892, 0.209482141084727828,
    )
    private val WG = doubleArrayOf(0.129484966168869693, 0.279705391489276668, 0.381830050505118945, 0.417959183673469388)

    /** A root of [f] between [a] and [b], where f changes sign: Brent's method. */
    fun brent(f: (Double) -> Double, a0: Double, b0: Double): Double {
        var a = a0
        var b = b0
        var fa = f(a)
        var fb = f(b)
        if (fa == 0.0) return a
        if (fb == 0.0) return b
        if (fa * fb > 0) throw EvalException("root: the expression must change sign between the limits")
        var c = a
        var fc = fa
        var d = b - a
        var e = d
        repeat(200) {
            if (fb * fc > 0) {
                c = a; fc = fa; d = b - a; e = d
            }
            if (abs(fc) < abs(fb)) {
                a = b; b = c; c = a
                fa = fb; fb = fc; fc = fa
            }
            val tol = 2 * Math.ulp(1.0) * abs(b) + 1e-300
            val m = (c - b) / 2
            if (abs(m) <= tol || fb == 0.0) return b
            if (abs(e) >= tol && abs(fa) > abs(fb)) {
                // Inverse quadratic interpolation, or the secant step.
                val s = fb / fa
                var p: Double
                var q: Double
                if (a == c) {
                    p = 2 * m * s
                    q = 1 - s
                } else {
                    val qq = fa / fc
                    val r = fb / fc
                    p = s * (2 * m * qq * (qq - r) - (b - a) * (r - 1))
                    q = (qq - 1) * (r - 1) * (s - 1)
                }
                if (p > 0) q = -q else p = -p
                if (2 * p < minOf(3 * m * q - abs(tol * q), abs(e * q))) {
                    e = d
                    d = p / q
                } else {
                    d = m
                    e = m
                }
            } else {
                d = m
                e = m
            }
            a = b
            fa = fb
            b += if (abs(d) > tol) d else if (m > 0) tol else -tol
            fb = f(b)
        }
        return b
    }

    /** A root of [f] near the guess [x0]: secant steps from it; else widening outwards until f changes sign, then Brent's method. */
    fun root(f: (Double) -> Double, x0: Double): Double {
        val f0 = f(x0)
        if (f0 == 0.0) return x0
        secant(f, x0, f0)?.let { return it }
        var step = 0.01 * max(abs(x0), 1.0)
        // Outward from the guess on both sides, a few points inside each widening, so that the
        // nearest sign change is found first: a wide jump could pass two roots without one.
        val last = doubleArrayOf(x0, x0)
        val lastF = doubleArrayOf(f0, f0)
        repeat(80) {
            val start = doubleArrayOf(last[0], last[1])
            // Point by point on both sides in turn, so that of two roots in one widening the nearer comes first.
            for (j in 1..4) for (side in 0..1) {
                val end = if (side == 0) x0 - step else x0 + step
                val x = start[side] + (end - start[side]) * j / 4
                // A point outside the expression's domain (a complex value) is skipped.
                val fx = try { f(x) } catch (_: EvalException) { Double.NaN }
                if (!fx.isFinite()) continue
                if (fx * lastF[side] <= 0) return brent(f, minOf(last[side], x), max(last[side], x))
                last[side] = x
                lastF[side] = fx
            }
            step *= 1.6
        }
        throw EvalException("root: no root found near the guess (give limits: root(f, x, a, b))")
    }

    /**
     * Secant steps from the guess, as `root` traditionally takes: quick (a root solved at every point of an
     * integral), and they reach a root the widening search would step over, such as k in ω²/g = k·tanh(k·d)
     * at low ω, close to 0 where the sign differs only in a narrow band. Null if they don't converge.
     */
    private fun secant(f: (Double) -> Double, x0: Double, f0: Double): Double? {
        var (a, fa) = x0 to f0
        var b = if (x0 != 0.0) x0 * (1 + 1e-4) else 1e-4
        var fb = try { f(b) } catch (_: EvalException) { return null }
        repeat(200) {
            if (fb == 0.0) return b
            if (fb == fa || !fb.isFinite()) return null
            val c = b - fb * (b - a) / (fb - fa)
            val fc = try { f(c) } catch (_: EvalException) { return null }
            if (!c.isFinite()) return null
            a = b; fa = fb; b = c; fb = fc
            if (abs(b - a) <= 1e-12 * max(abs(b), 1e-300)) return b.takeIf { abs(fb) <= 1e-9 * max(abs(f0), 1e-300) || fb == 0.0 }
        }
        return null
    }

    /** A function of a real number (in [x]'s unit) giving a real number, with the unit of what [f] gives. */
    fun real(f: (Quantity) -> Quantity, xDimension: Dimension, what: String): Pair<(Double) -> Double, () -> Dimension> {
        var dimension: Dimension? = null
        val g = { t: Double ->
            val q = f(Quantity(t, xDimension))
            if (!q.isReal) throw EvalException("$what: complex values aren't supported yet")
            if (dimension == null) dimension = q.dimension
            else if (q.dimension != dimension) throw EvalException("$what: the expression's units change with the variable")
            q.value.re
        }
        return g to { dimension ?: Dimension.NONE }
    }
}
