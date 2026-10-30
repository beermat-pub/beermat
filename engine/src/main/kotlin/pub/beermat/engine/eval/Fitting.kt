package pub.beermat.engine.eval

import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Data functions (docs/evaluation.md#data): polynomial roots, interpolation, regression and the
 * statistics beyond `mean`. Data vectors are real, and each holds one unit throughout.
 */
internal object Fitting {

    val functions: List<Builtin> = listOf(
        Builtin("polyroots", 1..1) { a -> polyroots(vector(a[0](), "polyroots").elements.map { it.quantity("polyroots") }) },
        Builtin("linterp", 3..3) { a ->
            val (x, y) = data(a[0](), a[1](), "linterp", 2)
            val t = at(a[2](), x, "linterp")
            val i = segment(x.values, t)
            val u = (t - x.values[i]) / (x.values[i + 1] - x.values[i])
            Scalar(Quantity(y.values[i] + u * (y.values[i + 1] - y.values[i]), y.unit))
        },
        spline("lspline", Ends.LINEAR),
        spline("pspline", Ends.PARABOLIC),
        spline("cspline", Ends.CUBIC),
        Builtin("regress", 3..3) { a ->
            val (x, y) = data(a[0](), a[1](), "regress", 2, ordered = false)
            val k = a[2]().real("regress", dimensionless = true)
            if (k != Math.rint(k) || k < 1) throw EvalException("regress: the degree must be a whole number of at least 1")
            if (x.values.size <= k) throw EvalException("regress: a degree ${k.toInt()} fit needs at least ${k.toInt() + 1} points")
            val coefficients = leastSquares(x.values.map { xi -> DoubleArray(k.toInt() + 1) { xi.pow(it) } }, y.values, "regress")
            tagged(REGRESSION, k, coefficients.mapIndexed { j, c -> Scalar(Quantity(c, y.unit / x.unit.pow(j))) })
        },
        Builtin("interp", 4..4) { a ->
            val vs = vector(a[0](), "interp").elements
            val (x, y) = data(a[1](), a[2](), "interp", 2)
            val t = at(a[3](), x, "interp")
            when (vs.firstOrNull()?.let { if (it is Scalar) it.q.value.re else null }) {
                SPLINE -> {
                    if (vs.size != x.values.size + 3) throw EvalException("interp: the spline was made from ${vs.size - 3} points, not ${x.values.size}")
                    Scalar(Quantity(cubic(x.values, y.values, vs.drop(3).map { it.quantity("interp").value.re }, t), y.unit))
                }
                REGRESSION -> Scalar(vs.drop(3).map { it.quantity("interp") }.foldIndexed(Quantity(0.0, y.unit)) { j, sum, c ->
                    sum + c * Quantity(t.pow(j), x.unit.pow(j))
                })
                else -> throw EvalException("interp: the first argument must come from cspline, pspline, lspline or regress")
            }
        },
        Builtin("linfit", 3..3) { a ->
            val (x, y) = data(a[0](), a[1](), "linfit", 1, ordered = false)
            val f = a[2]() as? FunctionValue ?: throw EvalException("linfit: the third argument must be a function F(x) returning a vector")
            var units: List<Dimension>? = null
            val rows = x.values.map { xi ->
                val fx = vector(call(f, listOf(Scalar(Quantity(xi, x.unit)))), "linfit: F(x)").elements.map { it.quantity("linfit: F(x)") }
                val us = fx.map { it.dimension }
                if (units == null) units = us
                else if (us != units) throw EvalException("linfit: F(x) must give the same number of terms, in the same units, at every x")
                DoubleArray(fx.size) { fx[it].realValue("linfit: F(x)") }
            }
            if (rows.size < rows[0].size) throw EvalException("linfit: ${rows[0].size} terms need at least ${rows[0].size} points")
            val coefficients = leastSquares(rows, y.values, "linfit")
            ArrayValue.column(coefficients.mapIndexed { j, c -> Scalar(Quantity(c, y.unit / units!![j])) })
        },
        Builtin("slope", 2..2) { a ->
            val (x, y) = data(a[0](), a[1](), "slope", 2, ordered = false)
            Scalar(Quantity(line(x.values, y.values).first, y.unit / x.unit))
        },
        Builtin("intercept", 2..2) { a ->
            val (x, y) = data(a[0](), a[1](), "intercept", 2, ordered = false)
            Scalar(Quantity(line(x.values, y.values).second, y.unit))
        },
        Builtin("corr", 2..2) { a ->
            val (x, y) = data(a[0](), a[1](), "corr", 2, ordered = false)
            val mx = x.values.average()
            val my = y.values.average()
            val sxy = x.values.indices.sumOf { (x.values[it] - mx) * (y.values[it] - my) }
            val sxx = x.values.sumOf { (it - mx) * (it - mx) }
            val syy = y.values.sumOf { (it - my) * (it - my) }
            if (sxx == 0.0 || syy == 0.0) throw EvalException("corr: the data doesn't vary")
            Scalar(Quantity(sxy / sqrt(sxx * syy)))
        },
        // The pairs: var and stdev divide by n (the population), Var and Stdev by n − 1 (a sample).
        variance("var", sample = false, root = false),
        variance("Var", sample = true, root = false),
        variance("stdev", sample = false, root = true),
        variance("Stdev", sample = true, root = true),
        Builtin("median", 1..Int.MAX_VALUE) { a ->
            val (xs, unit) = reals(numbers(a, "median"), "median")
            val s = xs.sorted()
            val n = s.size
            Scalar(Quantity(if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2, unit))
        },
    )

    // The first three elements of what cspline and regress return say what follows, so that interp
    // can take either; the numbers are ours (the original header values †).
    private const val SPLINE = 0.0
    private const val REGRESSION = 3.0

    private fun tagged(kind: Double, detail: Double, rest: List<Value>) =
        ArrayValue.column(listOf(kind, 3.0, detail).map { Scalar(Quantity(it)) } + rest)

    /** One data vector's numbers, all in one [unit]. */
    private class Data(val values: List<Double>, val unit: Dimension)

    private fun reals(given: List<Quantity>, context: String): Pair<List<Double>, Dimension> {
        // Bare zeros take the others' unit (docs/units.md).
        val qs = sameUnits(given, data = true) ?: throw EvalException("$context: every value needs the same units")
        return qs.map { it.realValue(context) } to qs.first().dimension
    }

    /** vx and vy: vectors of the same length, at least [min] long, vx ascending unless [ordered] is false. */
    private fun data(vx: Value, vy: Value, context: String, min: Int, ordered: Boolean = true): Pair<Data, Data> {
        val (xs, xu) = reals(vector(vx, context).elements.map { it.quantity(context) }, context)
        val (ys, yu) = reals(vector(vy, context).elements.map { it.quantity(context) }, context)
        if (xs.size != ys.size) throw EvalException("$context: vx has ${xs.size} values and vy ${ys.size}")
        if (xs.size < min) throw EvalException("$context needs at least $min points")
        if (ordered && xs.zipWithNext().any { (p, q) -> q <= p }) throw EvalException("$context: the values in vx must be in ascending order")
        return Data(xs, xu) to Data(ys, yu)
    }

    /** The point to interpolate at, as a number in vx's units. */
    private fun at(v: Value, x: Data, context: String): Double {
        val q = v.quantity(context)
        if (q.dimension != x.unit) throw EvalException("$context: x must have the units of vx")
        return q.realValue(context)
    }

    /** The interval [x_i, x_i+1] holding t; the first or last beyond the ends, to extrapolate. */
    private fun segment(xs: List<Double>, t: Double): Int {
        var lo = 0
        var hi = xs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (t < xs[mid]) hi = mid else lo = mid
        }
        return lo
    }

    // --- Splines -------------------------------------------------------------------------------

    /** How a spline ends: straight (the second derivative 0), parabolic, or cubic over the last two intervals. */
    private enum class Ends { LINEAR, PARABOLIC, CUBIC }

    private fun spline(name: String, ends: Ends) = Builtin(name, 2..2) { a ->
        val (x, y) = data(a[0](), a[1](), name, 2)
        val m = secondDerivatives(x.values, y.values, ends)
        tagged(SPLINE, ends.ordinal.toDouble(), m.map { Scalar(Quantity(it, y.unit / x.unit.pow(2))) })
    }

    /**
     * The spline's second derivatives at the points. Interior points join smoothly; the ends
     * fix M0 and Mn: 0 (linear), equal to their neighbour's (parabolic), or extrapolated in a
     * straight line from the next two, which makes the first and last two intervals one cubic
     * ("not-a-knot"). The ends are put into the first and last rows, leaving a tridiagonal system.
     */
    private fun secondDerivatives(x: List<Double>, y: List<Double>, ends: Ends): List<Double> {
        val n = x.size
        if (n == 2) return listOf(0.0, 0.0)
        // Through three points, a cubic over both intervals is the parabola.
        val e = if (ends == Ends.CUBIC && n == 3) Ends.PARABOLIC else ends
        val h = DoubleArray(n - 1) { x[it + 1] - x[it] }
        val k = n - 2  // unknowns M1 … M(n-2)
        val sub = DoubleArray(k) { if (it > 0) h[it] else 0.0 }
        val diag = DoubleArray(k) { 2 * (h[it] + h[it + 1]) }
        val sup = DoubleArray(k) { if (it < k - 1) h[it + 1] else 0.0 }
        val rhs = DoubleArray(k) { 6 * ((y[it + 2] - y[it + 1]) / h[it + 1] - (y[it + 1] - y[it]) / h[it]) }
        when (e) {
            Ends.LINEAR -> {}
            Ends.PARABOLIC -> {
                diag[0] += h[0]
                diag[k - 1] += h[n - 2]
            }
            Ends.CUBIC -> {
                // M0 = (1 + r) M1 − r M2, with r = h0/h1; the same at the far end.
                val r = h[0] / h[1]
                diag[0] += h[0] * (1 + r)
                sup[0] -= h[0] * r
                val s = h[n - 2] / h[n - 3]
                diag[k - 1] += h[n - 2] * (1 + s)
                sub[k - 1] -= h[n - 2] * s
            }
        }
        // Thomas: the system is diagonally dominant, so it needs no pivoting.
        for (i in 1 until k) {
            val w = sub[i] / diag[i - 1]
            diag[i] -= w * sup[i - 1]
            rhs[i] -= w * rhs[i - 1]
        }
        val inner = DoubleArray(k)
        for (i in k - 1 downTo 0) inner[i] = (rhs[i] - (if (i < k - 1) sup[i] * inner[i + 1] else 0.0)) / diag[i]
        val (first, last) = when (e) {
            Ends.LINEAR -> 0.0 to 0.0
            Ends.PARABOLIC -> inner[0] to inner[k - 1]
            Ends.CUBIC -> {
                val r = h[0] / h[1]
                val s = h[n - 2] / h[n - 3]
                ((1 + r) * inner[0] - r * inner[1]) to ((1 + s) * inner[k - 1] - s * inner[k - 2])
            }
        }
        return listOf(first) + inner.toList() + last
    }

    /** The spline's value at t; beyond the ends, the end interval's cubic carries on. */
    private fun cubic(x: List<Double>, y: List<Double>, m: List<Double>, t: Double): Double {
        val i = segment(x, t)
        val h = x[i + 1] - x[i]
        val a = (x[i + 1] - t) / h
        val b = 1 - a
        return a * y[i] + b * y[i + 1] + ((a * a * a - a) * m[i] + (b * b * b - b) * m[i + 1]) * h * h / 6
    }

    // --- Least squares ------------------------------------------------------------------------

    /**
     * The coefficients c minimising |A c − y|, by Householder QR on A with its columns scaled to
     * length 1 (so x, x², x³ … of very different sizes stay well conditioned).
     */
    private fun leastSquares(rows: List<DoubleArray>, y: List<Double>, context: String): List<Double> {
        val m = rows.size
        val n = rows[0].size
        val a = Array(m) { rows[it].copyOf() }
        val scale = DoubleArray(n) { j -> sqrt((0 until m).sumOf { a[it][j] * a[it][j] }).takeIf { it > 0 } ?: 1.0 }
        for (row in a) for (j in 0 until n) row[j] /= scale[j]
        val b = y.toDoubleArray()
        for (j in 0 until n) {
            val norm = sqrt((j until m).sumOf { a[it][j] * a[it][j] })
            if (norm < 1e-12) throw EvalException("$context: the fit's terms are not independent at these points")
            val alpha = if (a[j][j] > 0) -norm else norm
            val v = DoubleArray(m) { if (it < j) 0.0 else a[it][j] }
            v[j] -= alpha
            val vv = (j until m).sumOf { v[it] * v[it] }
            if (vv == 0.0) continue
            for (c in j until n) {
                val f = 2 * (j until m).sumOf { v[it] * a[it][c] } / vv
                for (r in j until m) a[r][c] -= f * v[r]
            }
            val f = 2 * (j until m).sumOf { v[it] * b[it] } / vv
            for (r in j until m) b[r] -= f * v[r]
        }
        val c = DoubleArray(n)
        for (j in n - 1 downTo 0) c[j] = (b[j] - (j + 1 until n).sumOf { a[j][it] * c[it] }) / a[j][j]
        return List(n) { c[it] / scale[it] }
    }

    /** The least-squares line through the points: slope and intercept. */
    private fun line(x: List<Double>, y: List<Double>): Pair<Double, Double> {
        val mx = x.average()
        val my = y.average()
        val sxx = x.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) throw EvalException("slope: the values in vx are all the same")
        val b = x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sxx
        return b to my - b * mx
    }

    private fun variance(name: String, sample: Boolean, root: Boolean) = Builtin(name, 1..Int.MAX_VALUE) { a ->
        val qs = numbers(a, name)
        val n = qs.size
        if (sample && n < 2) throw EvalException("$name needs at least 2 values")
        val mean = qs.reduce(Quantity::plus) / Quantity(n.toDouble())
        // |x − mean|², so complex data gives a real variance.
        val sum = qs.map { (it - mean).value.abs().let { d -> d * d } }.sum()
        val v = Quantity(Complex(sum / (if (sample) n - 1 else n)), mean.dimension.pow(2))
        Scalar(if (root) v.sqrt() else v)
    }

    // --- Polynomial roots ---------------------------------------------------------------------

    /**
     * The roots of v0 + v1 x + v2 x² + …, as a column, by the Aberth–Ehrlich iteration (all roots
     * at once, cubically convergent), sorted by real part. The coefficients may carry units, as
     * long as every term has the same: then the roots have the units of v0/v1.
     */
    private fun polyroots(v: List<Quantity>): Value {
        val coefficients = v.dropLastWhile { it.value == Complex.ZERO }
        val n = coefficients.size - 1
        if (n < 1) throw EvalException("polyroots needs a polynomial of degree 1 or more: at least two coefficients, the last not 0")
        val unit = coefficients[0].dimension / coefficients[1].dimension
        coefficients.forEachIndexed { k, c ->
            if (c.dimension != coefficients[0].dimension / unit.pow(k)) {
                throw EvalException("polyroots: the coefficients' units don't make every term the same unit")
            }
        }
        // v0 = 0 (and v1 …) are roots at 0 exactly; the iteration finds the rest.
        val zeros = coefficients.indexOfFirst { it.value != Complex.ZERO }
        if (zeros == n) return roots(List(n) { Complex.ZERO }, unit)
        val monic = coefficients.map { it.value / coefficients[n].value }
        fun p(z: Complex) = monic.foldRight(Complex.ZERO) { c, acc -> acc * z + c }
        // How large p's rounding errors are near z.
        fun noise(z: Complex) = 1e-14 * monic.foldRight(0.0) { c, acc -> acc * z.abs() + c.abs() }
        val found = aberth(monic.drop(zeros))
        // Real coefficients: a root whose imaginary part is rounding noise is real. A multiple root
        // comes out only to about ε^(1/m), so "noise" is when the real part fits as well, within p's rounding.
        val real = coefficients.all { it.value.isReal }
        val cleaned = found.map { z ->
            val snap = real && z.im != 0.0 && abs(z.im) <= 1e-4 * z.abs() && p(Complex(z.re)).abs() <= p(z).abs() + noise(z)
            if (snap) Complex(z.re) else z
        }
        return roots((List(zeros) { Complex.ZERO } + cleaned).sortedWith(compareBy({ it.re }, { it.im })), unit)
    }

    /** The roots of the monic polynomial a0 + a1 x + … + x^n, none of them 0. */
    private fun aberth(a: List<Complex>): List<Complex> {
        val n = a.size - 1
        fun p(z: Complex) = a.foldRight(Complex.ZERO) { c, acc -> acc * z + c }
        fun dp(z: Complex) = (n downTo 1).fold(Complex.ZERO) { acc, k -> acc * z + a[k] * Complex(k.toDouble()) }
        // Start on a circle of the roots' typical size, turned off the real axis.
        val radius = (0 until n).filter { a[it] != Complex.ZERO }.maxOf { a[it].abs().pow(1.0 / (n - it)) }
        val z = Array(n) { k -> (2 * Math.PI * k / n + 0.4).let { t -> Complex(radius * cos(t), radius * sin(t)) } }
        for (iteration in 0 until 500) {
            var moved = 0.0
            for (i in 0 until n) {
                val ratio = p(z[i]) / dp(z[i])
                var sum = Complex.ZERO
                for (j in 0 until n) if (j != i) sum += Complex.ONE / (z[i] - z[j])
                val w = ratio / (Complex.ONE - ratio * sum)
                z[i] -= w
                moved = max(moved, w.abs() / z[i].abs())
            }
            if (moved < 1e-15 || moved.isNaN()) break
        }
        if (z.any { it.re.isNaN() || it.im.isNaN() }) throw EvalException("polyroots: the roots could not be found")
        return z.toList()
    }

    private fun roots(zs: List<Complex>, unit: Dimension) = ArrayValue.column(zs.map { Scalar(Quantity(it, unit)) })
}
