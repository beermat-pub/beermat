package pub.beermat.engine.eval

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import pub.beermat.engine.Math
import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity

/**
 * Eigenvalues, Fourier transforms and ODE solvers (docs/evaluation.md#numerics), with the
 * traditional names and arguments.
 */
internal object Numerics {

    val functions: List<Builtin> = listOf(
        Builtin("eigenvals", 1..1) { a ->
            val (m, unit) = square(a[0](), "eigenvals")
            ArrayValue.column(eigenvalues(m).map { Scalar(Quantity(it, unit)) })
        },
        Builtin("eigenvecs", 1..1) { a ->
            val (m, _) = square(a[0](), "eigenvecs")
            val vectors = eigenvalues(m).map { eigenvector(m, it) }
            ArrayValue.of(m.size, m.size) { r, c -> Scalar(Quantity(vectors[c][r])) }
        },
        Builtin("eigenvec", 2..2) { a ->
            val (m, unit) = square(a[0](), "eigenvec")
            val z = a[1]().quantity("eigenvec")
            if (z.dimension != unit) throw EvalException("eigenvec: the eigenvalue needs the matrix's units")
            ArrayValue.column(eigenvector(m, z.value).map { Scalar(Quantity(it)) })
        },
        // Fourier transforms †: lowercase fft and ifft, cfft and icfft scale by 1/√n with +i in the
        // exponent forward; the capitalised FFT, IFFT, CFFT, ICFFT by 1/n with −i forward.
        Builtin("cfft", 1..1) { a -> transform(a[0](), "cfft", inverse = false, unitary =true) },
        Builtin("icfft", 1..1) { a -> transform(a[0](), "icfft", inverse = true, unitary =true) },
        Builtin("CFFT", 1..1) { a -> transform(a[0](), "CFFT", inverse = false, unitary =false) },
        Builtin("ICFFT", 1..1) { a -> transform(a[0](), "ICFFT", inverse = true, unitary =false) },
        Builtin("fft", 1..1) { a -> half(transform(a[0](), "fft", inverse = false, unitary =true, real = true)) },
        Builtin("FFT", 1..1) { a -> half(transform(a[0](), "FFT", inverse = false, unitary =false, real = true)) },
        Builtin("ifft", 1..1) { a -> realOf(transform(whole(a[0](), "ifft"), "ifft", inverse = true, unitary =true)) },
        Builtin("IFFT", 1..1) { a -> realOf(transform(whole(a[0](), "IFFT"), "IFFT", inverse = true, unitary =false)) },
        // rkfixed(y, x1, x2, npoints, D): fourth-order Runge–Kutta at fixed steps; Rkadapt the same
        // points with adaptive steps between them. D(x, y) gives the derivatives. The result's first
        // column is x, then one column per element of y.
        Builtin("rkfixed", 5..5) { a -> ode(a, "rkfixed", adaptive = false) },
        Builtin("Rkadapt", 5..5) { a -> ode(a, "Rkadapt", adaptive = true) },
        Builtin("rkadapt", 5..5) { a -> ode(a, "rkadapt", adaptive = true) },
    )

    // --- Eigenvalues ---------------------------------------------------------------------------

    /** A square matrix's numbers, all in one unit. */
    private fun square(v: Value, name: String): Pair<Array<Array<Complex>>, Dimension> {
        val m = MatrixMath.array(v, name)
        if (m.rows != m.cols) throw EvalException("$name needs a square matrix, not ${m.size}")
        val qs = m.elements.map { it.quantity(name) }
        val unit = qs[0].dimension
        if (qs.any { it.dimension != unit }) throw EvalException("$name: every element needs the same units")
        return Array(m.rows) { r -> Array(m.cols) { c -> qs[r * m.cols + c].value } } to unit
    }

    /**
     * The eigenvalues, by the shifted QR algorithm on a complex copy (Wilkinson shifts, deflating
     * from the bottom), sorted by real part then imaginary part. A real symmetric matrix gives
     * real eigenvalues exactly; elsewhere an imaginary part that is rounding noise is dropped.
     */
    fun eigenvalues(m0: Array<Array<Complex>>): List<Complex> {
        val n = m0.size
        val a = Array(n) { r -> Array(n) { c -> m0[r][c] } }
        val symmetric = (0 until n).all { r -> (0 until n).all { c -> a[r][c] == a[c][r] && a[r][c].isReal } }
        val out = mutableListOf<Complex>()
        var size = n
        var iterations = 0
        val scale = a.maxOf { row -> row.maxOf { it.abs() } }.takeIf { it > 0 } ?: return List(n) { Complex.ZERO }
        while (size > 0) {
            if (size == 1) {
                out += a[0][0]
                break
            }
            val k = size - 1
            if (a[k][k - 1].abs() <= 1e-14 * (a[k][k].abs() + a[k - 1][k - 1].abs() + 1e-300 * scale)) {
                out += a[k][k]
                size--
                continue
            }
            if (++iterations > 1000 * n) throw EvalException("eigenvals: the QR iteration didn't converge")
            // Wilkinson's shift: the eigenvalue of the trailing 2×2 nearer the corner; an exceptional one now and then.
            val (p, q, r, s) = listOf(a[k - 1][k - 1], a[k - 1][k], a[k][k - 1], a[k][k])
            val tr = p + s
            val det = p * s - q * r
            val disc = (tr * tr - Complex(4.0) * det).sqrt()
            val l1 = (tr + disc) / Complex(2.0)
            val l2 = (tr - disc) / Complex(2.0)
            var mu = if ((l1 - s).abs() < (l2 - s).abs()) l1 else l2
            if (iterations % 11 == 0) mu += Complex(a[k][k - 1].abs())
            qrStep(a, size, mu)
        }
        val cleaned = out.map { z -> if (symmetric || abs(z.im) <= 1e-12 * max(z.abs(), scale)) Complex(z.re) else z }
        return cleaned.sortedWith(compareBy({ it.re }, { it.im }))
    }

    /** One step A ← RQ + μI on the leading [size]×[size] block, with complex Givens rotations. */
    private fun qrStep(a: Array<Array<Complex>>, size: Int, mu: Complex) {
        for (i in 0 until size) a[i][i] -= mu
        val rotations = mutableListOf<Pair<Complex, Complex>>()
        // R = Qᴴ (A − μI): zero below the diagonal, column by column (Givens on rows i, j).
        for (col in 0 until size - 1) for (row in col + 1 until size) {
            val x = a[col][col]
            val y = a[row][col]
            if (y == Complex.ZERO) {
                rotations += Complex.ONE to Complex.ZERO
                continue
            }
            val norm = sqrt(x.abs().pow(2) + y.abs().pow(2))
            val c = x / Complex(norm)
            val s = y / Complex(norm)
            for (j in 0 until size) {
                val u = a[col][j]
                val v = a[row][j]
                a[col][j] = c.conj() * u + s.conj() * v
                a[row][j] = -s * u + c * v
            }
            rotations += c to s
        }
        // Then R Q: the same rotations, on the columns.
        var k = 0
        for (col in 0 until size - 1) for (row in col + 1 until size) {
            val (c, s) = rotations[k++]
            for (i in 0 until size) {
                val u = a[i][col]
                val v = a[i][row]
                a[i][col] = u * c + v * s
                a[i][row] = -(u * s.conj()) + v * c.conj()
            }
        }
        for (i in 0 until size) a[i][i] += mu
    }

    /** The unit eigenvector for the eigenvalue [z], by inverse iteration, with its largest element real and positive. */
    fun eigenvector(m: Array<Array<Complex>>, z: Complex): List<Complex> {
        val n = m.size
        val shift = z + Complex(1e-10 * max(z.abs(), m.maxOf { row -> row.maxOf { it.abs() } }))
        var x = Array(n) { Complex(1.0 / sqrt(n.toDouble()) * (1 + 0.1 * it)) }
        repeat(4) {
            val a = Array(n) { r -> Array(n) { c -> m[r][c] - if (r == c) shift else Complex.ZERO } }
            val y = solve(a, x)
            val norm = sqrt(y.sumOf { it.abs().pow(2) })
            x = Array(n) { y[it] / Complex(norm) }
        }
        val big = x.maxBy { it.abs() }
        val phase = Complex(big.abs()) / big
        return x.map { v -> (v * phase).let { if (abs(it.im) < 1e-12) Complex(it.re) else it } }
    }

    /** Gaussian elimination with partial pivoting; a zero pivot is nudged, as inverse iteration wants. */
    private fun solve(a: Array<Array<Complex>>, b: Array<Complex>): Array<Complex> {
        val n = b.size
        val x = b.copyOf()
        for (k in 0 until n) {
            val p = (k until n).maxBy { a[it][k].abs() }
            a[k] = a[p].also { a[p] = a[k] }
            x[k] = x[p].also { x[p] = x[k] }
            if (a[k][k].abs() < 1e-300) a[k][k] = Complex(1e-300)
            for (r in k + 1 until n) {
                val f = a[r][k] / a[k][k]
                for (c in k until n) a[r][c] -= f * a[k][c]
                x[r] -= f * x[k]
            }
        }
        for (r in n - 1 downTo 0) {
            var s = x[r]
            for (c in r + 1 until n) s -= a[r][c] * x[c]
            x[r] = s / a[r][r]
        }
        return x
    }

    // --- Fourier transforms --------------------------------------------------------------------

    /**
     * The discrete Fourier transform of a vector (any length: a radix-2 FFT when the length is a
     * power of 2, else the direct sum). [unitary] chooses lowercase fft's conventions (1/√n, +i
     * forward) over the capitalised ones (1/n forward, −i; the inverse unscaled).
     */
    private fun transform(v: Value, name: String, inverse: Boolean, unitary: Boolean, real: Boolean = false): ArrayValue {
        val m = vector(v, name)
        val qs = m.elements.map { it.quantity(name) }
        val unit = qs[0].dimension
        if (qs.any { it.dimension != unit }) throw EvalException("$name: every element needs the same units")
        val n = qs.size
        if (real) {
            if (qs.any { !it.isReal }) throw EvalException("$name needs real data; cfft takes complex data")
            if (n < 2 || n and (n - 1) != 0) throw EvalException("$name needs 2^m values (got $n); cfft takes any length")
        }
        val sign = if (unitary != inverse) 1.0 else -1.0
        val factor = when {
            unitary ->1 / sqrt(n.toDouble())
            inverse -> 1.0
            else -> 1.0 / n
        }
        val out = dft(qs.map { it.value }, sign).map { Scalar(Quantity(it * Complex(factor), unit)) }
        return ArrayValue(m.rows, m.cols, out)
    }

    private fun dft(x: List<Complex>, sign: Double): List<Complex> {
        val n = x.size
        if (n and (n - 1) == 0 && n > 1) return fft(x, sign)
        return List(n) { k -> (0 until n).fold(Complex.ZERO) { s, j -> s + x[j] * unit(sign * 2 * PI * j * k / n) } }
    }

    private fun fft(x: List<Complex>, sign: Double): List<Complex> {
        val n = x.size
        if (n == 1) return x
        val even = fft(x.filterIndexed { i, _ -> i % 2 == 0 }, sign)
        val odd = fft(x.filterIndexed { i, _ -> i % 2 == 1 }, sign)
        val out = Array(n) { Complex.ZERO }
        for (k in 0 until n / 2) {
            val t = unit(sign * 2 * PI * k / n) * odd[k]
            out[k] = even[k] + t
            out[k + n / 2] = even[k] - t
        }
        return out.toList()
    }

    private fun unit(t: Double) = Complex(cos(t), sin(t))

    /** fft of real data: the first n/2 + 1 coefficients (the rest mirror them). */
    private fun half(full: ArrayValue): ArrayValue = ArrayValue.column(full.elements.take(full.elements.size / 2 + 1))

    /** ifft's input, n/2 + 1 coefficients, made whole again by mirroring their conjugates. */
    private fun whole(v: Value, name: String): ArrayValue {
        val c = vector(v, name).elements.map { it.quantity(name) }
        if (c.size < 2) throw EvalException("$name needs at least 2 coefficients")
        val n = 2 * (c.size - 1)
        return ArrayValue.column(List(n) { k -> Scalar(if (k < c.size) c[k] else c[n - k].let { Quantity(it.value.conj(), it.dimension) }) })
    }

    private fun realOf(v: ArrayValue) = ArrayValue(v.rows, v.cols, v.elements.map { e -> (e as Scalar).q.let { Scalar(Quantity(it.value.re, it.dimension)) } })

    // --- ODE solvers ---------------------------------------------------------------------------

    private fun Builtin.Context.ode(a: List<() -> Value>, name: String, adaptive: Boolean): Value {
        val y0v = a[0]()
        val y0 = (y0v as? ArrayValue)?.elements?.map { it.quantity(name) } ?: listOf(y0v.quantity(name))
        val x1 = a[1]().quantity(name)
        val x2 = a[2]().quantity(name)
        if (x1.dimension != x2.dimension) throw EvalException("$name: the two ends of x need the same units")
        val points = a[3]().real(name, dimensionless = true)
        if (points != Math.rint(points) || points < 1) throw EvalException("$name: the number of steps must be a whole number of at least 1")
        val n = points.toInt()
        val d = a[4]() as? FunctionValue ?: throw EvalException("$name: the last argument must be the function D(x, y) of the derivatives")
        val xUnit = x1.dimension
        val units = y0.map { it.dimension }
        // Numbers in SI; D sees the quantities, and must give each derivative in y's unit per x's unit.
        fun derivative(x: Double, y: DoubleArray): DoubleArray {
            val yv: Value = if (y0v is ArrayValue) ArrayValue.column(y.indices.map { Scalar(Quantity(y[it], units[it])) }) else Scalar(Quantity(y[0], units[0]))
            val r = call(d, listOf(Scalar(Quantity(x, xUnit)), yv))
            val qs = (r as? ArrayValue)?.elements?.map { it.quantity("$name: D(x, y)") } ?: listOf(r.quantity("$name: D(x, y)"))
            if (qs.size != y.size) throw EvalException("$name: D(x, y) gives ${qs.size} derivatives for ${y.size} unknowns")
            return DoubleArray(y.size) { k ->
                if (qs[k].dimension != units[k] / xUnit) throw EvalException("$name: derivative ${k + origin} has the wrong units (${qs[k].dimension}, not ${units[k] / xUnit})")
                qs[k].realValue(name)
            }
        }
        val (a0, b0) = x1.realValue(name) to x2.realValue(name)
        val h = (b0 - a0) / n
        val rows = mutableListOf(a0 to y0.map { it.realValue(name) }.toDoubleArray())
        var y = rows[0].second
        for (k in 0 until n) {
            val x = a0 + k * h
            y = if (adaptive) dormandPrince(::derivative, x, x + h, y) else rk4(::derivative, x, h, y)
            rows += (a0 + (k + 1) * h) to y
        }
        return ArrayValue.of(rows.size, y0.size + 1) { r, c ->
            if (c == 0) Scalar(Quantity(rows[r].first, xUnit)) else Scalar(Quantity(rows[r].second[c - 1], units[c - 1]))
        }
    }

    private fun rk4(f: (Double, DoubleArray) -> DoubleArray, x: Double, h: Double, y: DoubleArray): DoubleArray {
        fun add(a: DoubleArray, b: DoubleArray, t: Double) = DoubleArray(a.size) { a[it] + t * b[it] }
        val k1 = f(x, y)
        val k2 = f(x + h / 2, add(y, k1, h / 2))
        val k3 = f(x + h / 2, add(y, k2, h / 2))
        val k4 = f(x + h, add(y, k3, h))
        return DoubleArray(y.size) { y[it] + h / 6 * (k1[it] + 2 * k2[it] + 2 * k3[it] + k4[it]) }
    }

    /** From [x0] to [x1] with Dormand–Prince 5(4) steps, each sized to a local error of about 10⁻¹⁰ of y. */
    internal fun dormandPrince(f: (Double, DoubleArray) -> DoubleArray, x0: Double, x1: Double, y0: DoubleArray): DoubleArray {
        val c = doubleArrayOf(0.0, 1.0 / 5, 3.0 / 10, 4.0 / 5, 8.0 / 9, 1.0, 1.0)
        val a = arrayOf(
            doubleArrayOf(),
            doubleArrayOf(1.0 / 5),
            doubleArrayOf(3.0 / 40, 9.0 / 40),
            doubleArrayOf(44.0 / 45, -56.0 / 15, 32.0 / 9),
            doubleArrayOf(19372.0 / 6561, -25360.0 / 2187, 64448.0 / 6561, -212.0 / 729),
            doubleArrayOf(9017.0 / 3168, -355.0 / 33, 46732.0 / 5247, 49.0 / 176, -5103.0 / 18656),
            doubleArrayOf(35.0 / 384, 0.0, 500.0 / 1113, 125.0 / 192, -2187.0 / 6784, 11.0 / 84),
        )
        val b5 = doubleArrayOf(35.0 / 384, 0.0, 500.0 / 1113, 125.0 / 192, -2187.0 / 6784, 11.0 / 84, 0.0)
        val b4 = doubleArrayOf(5179.0 / 57600, 0.0, 7571.0 / 16695, 393.0 / 640, -92097.0 / 339200, 187.0 / 2100, 1.0 / 40)
        var x = x0
        var y = y0
        var h = (x1 - x0)
        var steps = 0
        while ((x1 - x) * h > 0 && abs(x1 - x) > 1e-14 * abs(x1 - x0)) {
            if (++steps > 100_000) throw EvalException("Rkadapt: too many steps (is the system stiff?)")
            if ((x + h - x1) * h > 0) h = x1 - x
            val k = arrayOfNulls<DoubleArray>(7)
            for (s in 0 until 7) {
                val ys = DoubleArray(y.size) { i -> y[i] + h * (0 until s).sumOf { j -> a[s][j] * k[j]!![i] } }
                k[s] = f(x + c[s] * h, ys)
            }
            val y5 = DoubleArray(y.size) { i -> y[i] + h * (0 until 7).sumOf { j -> b5[j] * k[j]!![i] } }
            val y4 = DoubleArray(y.size) { i -> y[i] + h * (0 until 7).sumOf { j -> b4[j] * k[j]!![i] } }
            val err = y.indices.maxOfOrNull { i -> abs(y5[i] - y4[i]) / (1e-10 * max(abs(y[i]), abs(y5[i])) + 1e-14) } ?: 0.0
            if (err <= 1) {
                x += h
                y = y5
            }
            h *= min(5.0, max(0.2, 0.9 * (1 / max(err, 1e-10)).pow(0.2)))
        }
        return y
    }
}
