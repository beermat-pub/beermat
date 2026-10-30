package pub.beermat.engine.eval

import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity

/**
 * Arithmetic on arrays (docs/evaluation.md#arrays). Elements keep their own units: sums and
 * products go through [Quantity], so mismatched units are caught element by element.
 * Inverses and determinants run Gaussian elimination on the numbers and work out the units
 * separately (see [Structure]).
 */
internal object MatrixMath {

    /** `+` and `-`: element by element; a scalar applies to every element. */
    fun elementwise(a: Value, b: Value, what: String, f: (Quantity, Quantity) -> Quantity): Value = when {
        a is ArrayValue && b is ArrayValue -> {
            if (a.rows != b.rows || a.cols != b.cols) throw EvalException("$what: matrix sizes do not match: ${a.size} and ${b.size}")
            ArrayValue.of(a.rows, a.cols) { r, c -> elementwise(a[r, c], b[r, c], what, f) }
        }
        a is ArrayValue -> ArrayValue.of(a.rows, a.cols) { r, c -> elementwise(a[r, c], b, what, f) }
        b is ArrayValue -> ArrayValue.of(b.rows, b.cols) { r, c -> elementwise(a, b[r, c], what, f) }
        else -> Scalar(f(a.quantity(what), b.quantity(what)))
    }

    fun map(a: Value, what: String, f: (Quantity) -> Quantity): Value =
        if (a is ArrayValue) ArrayValue.of(a.rows, a.cols) { r, c -> map(a[r, c], what, f) } else Scalar(f(a.quantity(what)))

    /**
     * `*`: a scalar scales every element; two column vectors of the same length give their dot
     * product; otherwise the matrix product. A product that comes out 1×1 (a row
     * times a column) is that number.
     */
    fun multiply(a: Value, b: Value): Value {
        if (a !is ArrayValue || b !is ArrayValue) return elementwise(a, b, "multiplication") { x, y -> x * y }
        if (a.cols == 1 && b.cols == 1 && a.rows == b.rows && a.rows > 1) {
            return Scalar((0 until a.rows).map { q(a[it, 0]) * q(b[it, 0]) }.reduce(Quantity::plus))
        }
        if (a.cols != b.rows) throw EvalException("cannot multiply a ${a.size} matrix by a ${b.size} matrix: the columns of the first must match the rows of the second")
        if (a.rows == 1 && b.cols == 1 && a.cols > 1) return Scalar((0 until a.cols).map { q(a[0, it]) * q(b[it, 0]) }.reduce(Quantity::plus))
        return ArrayValue.of(a.rows, b.cols) { r, c -> Scalar((0 until a.cols).map { q(a[r, it]) * q(b[it, c]) }.reduce(Quantity::plus)) }
    }

    fun divide(a: Value, b: Value): Value {
        // A number over a vector is element by element (the results saved in imported sheets show it).
        if (b is ArrayValue && b.isVector && a !is ArrayValue) return map(b, "division") { e ->
            if (e.value == Complex.ZERO) throw EvalException("division by zero")
            a.quantity("division") / e
        }
        // Two vectors of the same shape divide element by element, as the results saved in imported sheets show.
        if (a is ArrayValue && b is ArrayValue && b.isVector && a.rows == b.rows && a.cols == b.cols) return elementwise(a, b, "division") { x, y ->
            if (y.value == Complex.ZERO) throw EvalException("division by zero")
            x / y
        }
        if (b is ArrayValue) throw EvalException("cannot divide by a matrix: multiply by its inverse, M^-1")
        val d = b.quantity("division")
        if (d.value == Complex.ZERO) throw EvalException("division by zero")
        return map(a, "division") { it / d }
    }

    /**
     * `M^n` for a square matrix and a whole number n; `M^-1` is the inverse. A power of an array
     * that isn't square is taken element by element, what such a formula means (Dan, 2026-10-29;
     * some older imported sheets did otherwise for a row vector: docs/xmcd.md, known differences).
     */
    fun power(a: ArrayValue, exponent: Value): ArrayValue {
        if (a.rows != a.cols) return elementwise(a, exponent, "a power") { x, y -> x.pow(y) } as ArrayValue
        val n = exponent.real("a matrix power", dimensionless = true)
        if (n != Math.rint(n)) throw EvalException("a matrix can only be raised to a whole number power")
        if (n == 0.0) return identity(a.rows)
        val base = if (n < 0) inverse(a) else a
        var result = base
        repeat(kotlin.math.abs(n.toInt()) - 1) { result = multiply(result, base) as ArrayValue }
        return result
    }

    fun transpose(a: Value): ArrayValue {
        val m = array(a, "transpose")
        return ArrayValue.of(m.cols, m.rows) { r, c -> m[c, r] }
    }

    /** `|M|`: the determinant of a square matrix, or the length of a vector. */
    fun abs(a: ArrayValue): Value {
        if (a.rows == a.cols && a.rows > 1) return Scalar(determinant(a))
        if (!a.isVector) throw EvalException("|M| needs a square matrix (determinant) or a vector (length), not ${a.size}")
        val qs = a.elements.map { q(it) }
        val sum = qs.map { Quantity(Complex(it.value.abs() * it.value.abs()), it.dimension * it.dimension) }.reduce(Quantity::plus)
        return Scalar(sum.sqrt())
    }

    fun determinant(a: ArrayValue): Quantity {
        square(a, "the determinant")
        val s = Structure.of(a, "the determinant")
        val (lu, sign) = eliminate(values(a), null) ?: return Quantity(Complex.ZERO, s.determinant)
        var det = Complex(sign.toDouble())
        for (k in lu.indices) det *= lu[k][k]
        return Quantity(det, s.determinant)
    }

    fun inverse(a: ArrayValue): ArrayValue {
        square(a, "the inverse")
        val s = Structure.of(a, "the inverse")
        val n = a.rows
        val rhs = Array(n) { r -> Array(n) { c -> if (r == c) Complex.ONE else Complex.ZERO } }
        eliminate(values(a), rhs) ?: throw EvalException("the matrix is singular: it has no inverse")
        return ArrayValue.of(n, n) { r, c -> Scalar(Quantity(rhs[r][c], s.inverse(r, c))) }
    }

    fun identity(n: Int): ArrayValue = ArrayValue.of(n, n) { r, c -> Scalar(Quantity(if (r == c) 1.0 else 0.0)) }

    /**
     * Gaussian elimination with partial pivoting, in place. With [rhs], reduces to the identity
     * and leaves the solution in [rhs] (Gauss–Jordan); without, leaves the upper triangle.
     * Returns the reduced matrix and the sign of the row permutation, or null if singular.
     */
    private fun eliminate(m: Array<Array<Complex>>, rhs: Array<Array<Complex>>?): Pair<Array<Array<Complex>>, Int>? {
        val n = m.size
        var sign = 1
        for (k in 0 until n) {
            val p = (k until n).maxBy { m[it][k].abs() }
            if (m[p][k] == Complex.ZERO) return null
            if (p != k) {
                swap(m, p, k)
                rhs?.let { swap(it, p, k) }
                sign = -sign
            }
            val rows = if (rhs == null) (k + 1 until n) else (0 until n)
            for (r in rows) {
                if (r == k) continue
                val f = m[r][k] / m[k][k]
                if (f == Complex.ZERO) continue
                for (c in k until n) m[r][c] = m[r][c] - f * m[k][c]
                rhs?.let { for (c in 0 until n) it[r][c] = it[r][c] - f * it[k][c] }
            }
        }
        if (rhs != null) {
            for (k in 0 until n) {
                val d = m[k][k]
                for (c in 0 until n) rhs[k][c] = rhs[k][c] / d
            }
        }
        return m to sign
    }

    /**
     * The units of a matrix that can be inverted: element (i, j) must have the units of
     * `rows[i] * cols[j]`, as in a stiffness matrix. Then the inverse's element (i, j) has the
     * units of `1 / (cols[i] * rows[j])`, and the determinant the product of all of them.
     */
    private class Structure(val rows: List<Dimension>, val cols: List<Dimension>) {
        val determinant: Dimension get() = rows.zip(cols).fold(Dimension.NONE) { d, (r, c) -> d * r * c }

        fun inverse(r: Int, c: Int): Dimension = Dimension.NONE / (cols[r] * rows[c])

        companion object {
            fun of(a: ArrayValue, what: String): Structure {
                val dims = List(a.rows) { r -> List(a.cols) { c -> q(a[r, c]).dimension } }
                val cols = dims[0]
                val rows = dims.map { it[0] / cols[0] }
                for (r in 0 until a.rows) for (c in 0 until a.cols) {
                    if (dims[r][c] != rows[r] * cols[c]) {
                        throw EvalException("$what: the units of the matrix are not consistent (element [$r, $c] is ${dims[r][c]})")
                    }
                }
                return Structure(rows, cols)
            }
        }
    }

    private fun <T> swap(rows: Array<T>, i: Int, j: Int) {
        val t = rows[i]
        rows[i] = rows[j]
        rows[j] = t
    }

    private fun values(a: ArrayValue) = Array(a.rows) { r -> Array(a.cols) { c -> q(a[r, c]).value } }

    private fun square(a: ArrayValue, what: String) {
        if (a.rows != a.cols) throw EvalException("$what needs a square matrix, not ${a.size}")
    }

    fun array(v: Value, what: String): ArrayValue = v as? ArrayValue ?: throw EvalException("$what needs a matrix")

    private fun q(v: Value): Quantity = v.quantity("a matrix element")
}
