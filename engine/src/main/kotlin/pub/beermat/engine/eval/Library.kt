package pub.beermat.engine.eval

import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * More built-in functions (docs/evaluation.md#more-functions), chosen by how often
 * Dan's worksheets use them: rounding to a multiple, trigonometry, special functions, the normal
 * distribution, random numbers, histograms, lookup, sorting, strings and the cross product.
 * The traditional names and arguments.
 */
internal object Library {

    val functions: List<Builtin> = listOf(
        // Rounding to a multiple: Floor(x, y) is the largest multiple of y not above x; units allowed, matching.
        multiple("Floor") { kotlin.math.floor(it) },
        multiple("Ceil") { kotlin.math.ceil(it) },
        multiple("Round") { Math.rint(it) },
        multiple("Trunc") { kotlin.math.truncate(it) },
        Builtin("trunc", 1..1) { a -> Scalar(Quantity(kotlin.math.truncate(a[0]().real("trunc", dimensionless = true)))) },
        Builtin("conj", 1..1) { a -> MatrixMath.map(a[0](), "conj") { Quantity(it.value.conj(), it.dimension) } },
        Builtin("sign", 1..1) { a -> Scalar(Quantity(kotlin.math.sign(a[0]().quantity("sign").realValue("sign")))) },
        Builtin("signum", 1..1) { a -> a[0]().quantity("signum").let { q -> Scalar(Quantity(if (q.value == Complex.ZERO) Complex.ZERO else q.value / Complex(q.value.abs()))) } },
        // Trigonometry and hyperbolic functions.
        complex("sec") { Complex.ONE / it.cos() },
        complex("csc") { Complex.ONE / it.sin() },
        complex("cot") { it.cos() / it.sin() },
        real("sech") { 1 / kotlin.math.cosh(it) },
        real("csch") { 1 / kotlin.math.sinh(it) },
        real("coth") { 1 / kotlin.math.tanh(it) },
        real("asinh") { kotlin.math.asinh(it) },
        real("acosh") { kotlin.math.acosh(it) },
        real("atanh") { kotlin.math.atanh(it) },
        real("acot") { PI / 2 - kotlin.math.atan(it) },
        real("asec") { kotlin.math.acos(1 / it) },
        real("acsc") { kotlin.math.asin(1 / it) },
        real("acoth") { kotlin.math.atanh(1 / it) },
        // Special functions.
        real("erf") { erf(it) },
        real("erfc") { erfc(it) },
        real("Γ") { gamma(it) },
        real("gamma") { gamma(it) },
        real("lnΓ") { lnGamma(it) },
        // The normal distribution: density, cumulative, inverse.
        Builtin("dnorm", 3..3) { a -> normal(a, "dnorm") { x -> exp(-x * x / 2) / sqrt(2 * PI) }.let { (v, s) -> Scalar(v / s) } },
        Builtin("pnorm", 3..3) { a -> normal(a, "pnorm") { x -> 0.5 * erfc(-x / sqrt(2.0)) }.let { (v, _) -> Scalar(v) } },
        real("cnorm") { 0.5 * erfc(-it / sqrt(2.0)) },
        Builtin("qnorm", 3..3) { a ->
            val p = a[0]().real("qnorm", dimensionless = true)
            val mean = a[1]().quantity("qnorm")
            val sd = a[2]().quantity("qnorm")
            if (p <= 0 || p >= 1) throw EvalException("qnorm: the probability must be between 0 and 1")
            Scalar(mean + sd * Quantity(inverseNormal(p)))
        },
        // Random numbers, the same at every recalculation (the seed is reset each time).
        Builtin("rnd", 1..1) { a -> a[0]().quantity("rnd").let { x -> Scalar(x * Quantity(random.nextDouble())) } },
        Builtin("runif", 3..3) { a ->
            val m = count(a[0](), "runif")
            val lo = a[1]().quantity("runif")
            val hi = a[2]().quantity("runif")
            ArrayValue.column(List(m) { Scalar(lo + (hi - lo) * Quantity(random.nextDouble())) })
        },
        Builtin("rnorm", 3..3) { a ->
            val m = count(a[0](), "rnorm")
            val mean = a[1]().quantity("rnorm")
            val sd = a[2]().quantity("rnorm")
            ArrayValue.column(List(m) { Scalar(mean + sd * Quantity(random.nextGaussian())) })
        },
        // Histograms: hist gives the counts; histogram the bins' middles beside their counts.
        Builtin("hist", 2..2) { a -> histogram(a[0](), a[1](), "hist").let { (_, counts) -> ArrayValue.column(counts.map { Scalar(Quantity(it.toDouble())) }) } },
        Builtin("histogram", 2..2) { a ->
            val (middles, counts) = histogram(a[0](), a[1](), "histogram")
            ArrayValue.of(counts.size, 2) { r, c -> if (c == 0) Scalar(middles[r]) else Scalar(Quantity(counts[r].toDouble())) }
        },
        // Lookup.
        Builtin("match", 2..3) { a ->
            val z = a[0]().quantity("match")
            val m = MatrixMath.array(a[1](), "match")
            val mode = (a.getOrNull(2)?.invoke() as? Text)?.value ?: "eq"
            val qs = m.elements.map { it.quantity("match") }
            val chosen: List<Int> = when (mode) {
                "near" -> listOfNotNull(qs.indices.minByOrNull { (qs[it] - z).value.abs() })
                "eq" -> qs.indices.filter { close(qs[it], z) }
                "lt" -> qs.indices.filter { qs[it] < z }.let { l -> listOfNotNull(l.maxByOrNull { qs[it] }) }
                "gt" -> qs.indices.filter { qs[it] > z }.let { l -> listOfNotNull(l.minByOrNull { qs[it] }) }
                "leq" -> qs.indices.filter { qs[it] <= z }.let { l -> listOfNotNull(l.maxByOrNull { qs[it] }) }
                "geq" -> qs.indices.filter { qs[it] >= z }.let { l -> listOfNotNull(l.minByOrNull { qs[it] }) }
                else -> throw EvalException("match: the mode must be \"eq\", \"near\", \"lt\", \"gt\", \"leq\" or \"geq\"")
            }
            if (chosen.isEmpty()) throw EvalException("match: the value is not found")
            // A vector gives positions; a matrix, a [row; column] for each.
            ArrayValue.column(chosen.map { k ->
                if (m.isVector) count(k + origin) else ArrayValue.column(listOf(count(k / m.cols + origin), count(k % m.cols + origin)))
            })
        },
        Builtin("vlookup", 3..3) { a ->
            val z = a[0]().quantity("vlookup")
            val m = MatrixMath.array(a[1](), "vlookup")
            val c = index(a[2](), origin, m.cols, "vlookup")
            val rows = (0 until m.rows).filter { close(m[it, 0].quantity("vlookup"), z) }
            if (rows.isEmpty()) throw EvalException("vlookup: the value is not in the first column")
            ArrayValue.column(rows.map { m[it, c] })
        },
        Builtin("hlookup", 3..3) { a ->
            val z = a[0]().quantity("hlookup")
            val m = MatrixMath.array(a[1](), "hlookup")
            val r = index(a[2](), origin, m.rows, "hlookup")
            val cols = (0 until m.cols).filter { close(m[0, it].quantity("hlookup"), z) }
            if (cols.isEmpty()) throw EvalException("hlookup: the value is not in the first row")
            ArrayValue.column(cols.map { m[r, it] })
        },
        Builtin("lookup", 3..3) { a ->
            val z = a[0]().quantity("lookup")
            val keys = MatrixMath.array(a[1](), "lookup")
            val values = MatrixMath.array(a[2](), "lookup")
            val found = keys.elements.indices.filter { close(keys.elements[it].quantity("lookup"), z) }
            if (found.isEmpty()) throw EvalException("lookup: the value is not found")
            ArrayValue.column(found.map { values.elements.getOrNull(it) ?: throw EvalException("lookup: the two matrices differ in size") })
        },
        // Arrays.
        Builtin("reverse", 1..1) { a ->
            val m = MatrixMath.array(a[0](), "reverse")
            if (m.rows == 1) ArrayValue.of(1, m.cols) { _, c -> m[0, m.cols - 1 - c] } else ArrayValue.of(m.rows, m.cols) { r, c -> m[m.rows - 1 - r, c] }
        },
        Builtin("sort", 1..1) { a ->
            val v = vector(a[0](), "sort")
            val sorted = v.elements.sortedWith { x, y -> x.quantity("sort").compareTo(y.quantity("sort")) }
            ArrayValue(v.rows, v.cols, sorted)
        },
        Builtin("csort", 2..2) { a ->
            val m = MatrixMath.array(a[0](), "csort")
            val j = index(a[1](), origin, m.cols, "csort")
            val order = (0 until m.rows).sortedWith { x, y -> m[x, j].quantity("csort").compareTo(m[y, j].quantity("csort")) }
            ArrayValue.of(m.rows, m.cols) { r, c -> m[order[r], c] }
        },
        Builtin("rsort", 2..2) { a ->
            val m = MatrixMath.array(a[0](), "rsort")
            val i = index(a[1](), origin, m.rows, "rsort")
            val order = (0 until m.cols).sortedWith { x, y -> m[i, x].quantity("rsort").compareTo(m[i, y].quantity("rsort")) }
            ArrayValue.of(m.rows, m.cols) { r, c -> m[r, order[c]] }
        },
        Builtin("linspace", 3..3) { a -> spaced(a, "linspace") { lo, hi, k, n -> lo + (hi - lo) * k / (n - 1) } },
        Builtin("logspace", 3..3) { a ->
            spaced(a, "logspace") { lo, hi, k, n ->
                if (lo <= 0 || hi <= 0) throw EvalException("logspace: the ends must be above 0")
                lo * (hi / lo).pow(k.toDouble() / (n - 1))
            }
        },
        // cells(Table1, "B2:B21"): a range of a named table's grid, its header being row 1, as in Excel (docs/tables.md#cell-references).
        Builtin("cells", 2..2) { a ->
            val grid = a[0]() as? ArrayValue ?: throw EvalException("cells takes a table's name and a range: cells(Loads, \"B2:B9\")")
            CellRange.take(grid, (a[1]() as? Text)?.value ?: throw EvalException("cells: the range is a string, such as \"B2:B21\""))
        },
        Builtin("cross", 2..2) { a ->
            val u = vector(a[0](), "cross").elements.map { it.quantity("cross") }
            val v = vector(a[1](), "cross").elements.map { it.quantity("cross") }
            if (u.size != 3 || v.size != 3) throw EvalException("cross needs two vectors of 3 elements")
            ArrayValue.column(listOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]).map(::Scalar))
        },
        // Strings.
        Builtin("concat", 1..Int.MAX_VALUE) { a -> Text(a.joinToString("") { text(it(), "concat") }) },
        Builtin("strlen", 1..1) { a -> count(text(a[0](), "strlen").length) },
        Builtin("substr", 3..3) { a ->
            val s = text(a[0](), "substr")
            val start = a[1]().real("substr", dimensionless = true).toInt()
            val n = a[2]().real("substr", dimensionless = true).toInt()
            Text(s.drop(start.coerceIn(0, s.length)).take(maxOf(0, n)))
        },
        Builtin("search", 3..3) { a ->
            val s = text(a[0](), "search")
            val sub = text(a[1](), "search")
            val start = a[2]().real("search", dimensionless = true).toInt()
            count(s.indexOf(sub, start.coerceIn(0, s.length)))
        },
        Builtin("num2str", 1..1) { a -> Text(a[0]().quantity("num2str").let { q -> if (q.isReal) shortest(q.value.re) else q.value.toString() }) },
        Builtin("str2num", 1..1) { a ->
            val s = text(a[0](), "str2num").trim()
            Scalar(Quantity(s.toDoubleOrNull() ?: throw EvalException("str2num: '$s' is not a number")))
        },
        Builtin("error", 1..1) { a -> throw EvalException(text(a[0](), "error")) },
        // Debugging: trace(…) traditionally wrote to a trace window and pause(…) stopped there. Here both pass their first argument on.
        Builtin("trace", 0..Int.MAX_VALUE) { a -> a.firstOrNull()?.invoke() ?: Scalar(Quantity(0.0)) },
        Builtin("pause", 0..Int.MAX_VALUE) { a -> a.firstOrNull()?.invoke() ?: Scalar(Quantity(0.0)) },
        // time(z): the time now in seconds (z is ignored), for timing a long calculation; a dimensionless number.
        Builtin("time", 1..1) { _ -> Scalar(Quantity(System.currentTimeMillis() / 1000.0)) },
        // SIUnitsOf(x) and UnitsOf(x): 1 of x's unit in SI, so that x/SIUnitsOf(x) is the plain number.
        Builtin("SIUnitsOf", 1..1) { a -> Scalar(Quantity(1.0, a[0]().quantity("SIUnitsOf").dimension)) },
        Builtin("UnitsOf", 1..1) { a -> Scalar(Quantity(1.0, a[0]().quantity("UnitsOf").dimension)) },
        Builtin("IsString", 1..1) { a -> count(if (a[0]() is Text) 1 else 0) },
        Builtin("IsArray", 1..1) { a -> count(if (a[0]() is ArrayValue) 1 else 0) },
        Builtin("IsScalar", 1..1) { a -> count(if (a[0]() is Scalar) 1 else 0) },
    )

    private fun count(n: Int) = Scalar(Quantity(n.toDouble()))

    /** Equal for lookup: the same units, and the values within 10⁻¹² of each other's size. */
    private fun close(a: Quantity, b: Quantity) = a.dimension == b.dimension && (a.value - b.value).abs() <= 1e-12 * maxOf(a.value.abs(), b.value.abs())

    private fun count(v: Value, context: String): Int {
        val x = v.real(context, dimensionless = true)
        if (x != Math.rint(x) || x < 1) throw EvalException("$context: the number of values must be a whole number of at least 1")
        return x.toInt()
    }

    private fun index(v: Value, origin: Int, size: Int, context: String): Int {
        val k = v.real(context, dimensionless = true)
        if (k != Math.rint(k) || k - origin !in 0.0..(size - 1).toDouble()) throw EvalException("$context: $k is not a column or row of the matrix (ORIGIN = $origin)")
        return k.toInt() - origin
    }

    private fun text(v: Value, context: String) = (v as? Text)?.value ?: throw EvalException("$context: expected a string")

    private fun shortest(x: Double) = if (x == Math.rint(x) && abs(x) < 1e15) x.toLong().toString() else x.toString()

    private fun multiple(name: String, f: (Double) -> Double) = Builtin(name, 2..2) { a ->
        val x = a[0]().quantity(name)
        val y = a[1]().quantity(name)
        if (x.dimension != y.dimension) throw EvalException("$name: the two arguments need the same units")
        val step = y.realValue(name)
        if (step == 0.0) throw EvalException("$name: the multiple can't be 0")
        Scalar(Quantity(f(x.realValue(name) / step) * step, x.dimension))
    }

    private fun complex(name: String, f: (Complex) -> Complex) = Builtin(name, 1..1) { a -> Scalar(Quantity(f(a[0]().quantity(name).requireNone(name).value))) }

    private fun real(name: String, f: (Double) -> Double) = Builtin(name, 1..1) { a ->
        val y = f(a[0]().real(name, dimensionless = true))
        if (y.isNaN()) throw EvalException("$name: argument out of range")
        Scalar(Quantity(y))
    }

    /** dnorm and pnorm: [f] of the standardised x, and the standard deviation. */
    private fun normal(a: List<() -> Value>, name: String, f: (Double) -> Double): Pair<Quantity, Quantity> {
        val x = a[0]().quantity(name)
        val mean = a[1]().quantity(name)
        val sd = a[2]().quantity(name)
        if (x.dimension != mean.dimension || x.dimension != sd.dimension) throw EvalException("$name: x, the mean and the standard deviation need the same units")
        val s = sd.realValue(name)
        if (s <= 0) throw EvalException("$name: the standard deviation must be above 0")
        return Quantity(f((x.realValue(name) - mean.realValue(name)) / s)) to Quantity(s, sd.dimension)
    }

    private fun spaced(a: List<() -> Value>, name: String, f: (Double, Double, Int, Int) -> Double): Value {
        val lo = a[0]().quantity(name)
        val hi = a[1]().quantity(name)
        val n = count(a[2](), name)
        if (lo.dimension != hi.dimension) throw EvalException("$name: the ends need the same units")
        if (n < 2) return ArrayValue.column(listOf(Scalar(lo)))
        return ArrayValue.column(List(n) { k -> Scalar(Quantity(f(lo.realValue(name), hi.realValue(name), k, n), lo.dimension)) })
    }

    /**
     * The bins (n equal ones between the data's ends, or between the given edges) with their
     * middles, and how many values fall in each; a value on an inner edge goes to the bin above.
     */
    private fun histogram(intervals: Value, data: Value, name: String): Pair<List<Quantity>, List<Int>> {
        fun flat(v: Value): List<Quantity> = if (v is ArrayValue) v.elements.flatMap(::flat) else listOf(v.quantity(name))
        val values = flat(data).let { sameUnits(it, data = true) ?: throw EvalException("$name: every value needs the same units") }
        if (values.isEmpty()) throw EvalException("$name: there are no values")
        val dim = values[0].dimension
        if (values.any { it.dimension != dim }) throw EvalException("$name: every value needs the same units")
        val xs = values.map { it.realValue(name) }
        val edges = if (intervals is ArrayValue) {
            flat(intervals).map { if (it.dimension != dim) throw EvalException("$name: the edges need the values' units") else it.realValue(name) }
        } else {
            val n = count(intervals, name)
            val (lo, hi) = xs.min() to xs.max()
            List(n + 1) { k -> lo + (hi - lo) * k / n }
        }
        if (edges.size < 2 || edges.zipWithNext().any { (p, q) -> q <= p }) throw EvalException("$name: the edges must be in ascending order")
        val counts = IntArray(edges.size - 1)
        for (x in xs) {
            if (x < edges.first() || x > edges.last()) continue
            val k = edges.indexOfLast { it <= x }.coerceAtMost(counts.size - 1)
            counts[k]++
        }
        val middles = edges.zipWithNext().map { (p, q) -> Quantity((p + q) / 2, dim) }
        return middles to counts.toList()
    }

    // --- Special functions ---------------------------------------------------------------------

    /** The error function: its series near 0, else 1 − erfc. */
    fun erf(x: Double): Double = if (abs(x) < 2.5) {
        var term = x
        var sum = x
        var n = 0
        while (abs(term) > 1e-17 * abs(sum)) {
            n++
            term *= -x * x / n
            sum += term / (2 * n + 1)
        }
        2 / sqrt(PI) * sum
    } else {
        kotlin.math.sign(x) * (1 - erfc(abs(x)))
    }

    /** The complementary error function: a continued fraction for large x, which the series can't reach without cancelling. */
    fun erfc(x: Double): Double {
        if (x < 2.5) return 1 - erf(x)
        // Lentz's method on erfc(x) = exp(−x²)/√π · 1/(x + (1/2)/(x + 1/(x + (3/2)/(x + …)))).
        var f = x
        var c = x
        var d = 0.0
        for (k in 1..300) {
            val ak = k / 2.0
            d = x + ak * d
            d = if (d == 0.0) 1e-300 else 1 / d
            c = x + ak / c
            val delta = c * d
            f *= delta
            if (abs(delta - 1) < 1e-16) break
        }
        return exp(-x * x) / sqrt(PI) / f
    }

    private val LANCZOS = doubleArrayOf(
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
    )

    /** Γ by the Lanczos approximation (g = 7), and the reflection formula below ½. */
    fun gamma(x: Double): Double {
        if (x == floor(x) && x <= 0) return Double.NaN
        if (x < 0.5) return PI / (kotlin.math.sin(PI * x) * gamma(1 - x))
        val z = x - 1
        var a = LANCZOS[0]
        val t = z + 7.5
        for (k in 1 until LANCZOS.size) a += LANCZOS[k] / (z + k)
        return sqrt(2 * PI) * t.pow(z + 0.5) * exp(-t) * a
    }

    fun lnGamma(x: Double): Double {
        if (x <= 0) return Double.NaN
        val z = x - 1
        var a = LANCZOS[0]
        val t = z + 7.5
        for (k in 1 until LANCZOS.size) a += LANCZOS[k] / (z + k)
        return 0.5 * ln(2 * PI) + (z + 0.5) * ln(t) - t + ln(a)
    }

    /** The standard normal quantile: Acklam's approximation, then one Halley step on erfc to full precision. */
    fun inverseNormal(p: Double): Double {
        val a = doubleArrayOf(-3.969683028665376e1, 2.209460984245205e2, -2.759285104469687e2, 1.383577518672690e2, -3.066479806614716e1, 2.506628277459239)
        val b = doubleArrayOf(-5.447609879822406e1, 1.615858368580409e2, -1.556989798598866e2, 6.680131188771972e1, -1.328068155288572e1)
        val c = doubleArrayOf(-7.784894002430293e-3, -3.223964580411365e-1, -2.400758277161838, -2.549732539343734, 4.374664141464968, 2.938163982698783)
        val d = doubleArrayOf(7.784695709041462e-3, 3.224671290700398e-1, 2.445134137142996, 3.754408661907416)
        val low = 0.02425
        val x = when {
            p < low -> sqrt(-2 * ln(p)).let { q -> (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
            p > 1 - low -> sqrt(-2 * ln(1 - p)).let { q -> -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
            else -> (p - 0.5).let { q ->
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
            }
        }
        val e = 0.5 * erfc(-x / sqrt(2.0)) - p
        val u = e * sqrt(2 * PI) * exp(x * x / 2)
        return x - u / (1 + x * u / 2)
    }
}
