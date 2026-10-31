package pub.beermat.engine.units

/** Thrown when an operation is not valid for the dimensions of its operands. */
class UnitsException(message: String) : RuntimeException(message)

/**
 * A value in SI base units, with its dimension: `3 mm` is `Quantity(0.003, LENGTH)`.
 * Implements the dimension rules of docs/units.md.
 */
data class Quantity(val value: Complex, val dimension: Dimension = Dimension.NONE) : Comparable<Quantity> {

    constructor(value: Double, dimension: Dimension = Dimension.NONE) : this(Complex(value), dimension)

    val isReal: Boolean get() = value.isReal

    /**
     * A sum. A zero adds to anything: `0 + 2 m` is 2 m, so an array started with zeros can collect forces, and
     * so does a zero that picked up a unit on the way (`0/M_p + 1`), as imported sheets' saved results show †.
     */
    operator fun plus(other: Quantity): Quantity {
        requireComparable(other)
        return Quantity(value + other.value, sumDimension(other))
    }

    operator fun minus(other: Quantity): Quantity {
        requireComparable(other)
        return Quantity(value - other.value, sumDimension(other))
    }

    private fun sumDimension(other: Quantity) = if (isPlainZero() || (value == Complex.ZERO && other.value != Complex.ZERO)) other.dimension else dimension

    operator fun times(other: Quantity) = Quantity(value * other.value, dimension * other.dimension)

    operator fun div(other: Quantity) = Quantity(value / other.value, dimension / other.dimension)

    operator fun unaryMinus() = Quantity(-value, dimension)

    /**
     * `this ^ exponent`. The exponent must be dimensionless. If this quantity has a dimension, the
     * exponent must be real; the dimension's power is the fraction it is (`2`, `-1`, `1/3`), or for
     * any other real number, as empirical formulas use (`D^1.852`), the closest fraction
     * with a denominator up to 10000. The value uses the exponent as it is.
     */
    fun pow(exponent: Quantity): Quantity {
        if (!exponent.dimension.isNone) {
            throw UnitsException("an exponent must be dimensionless, not ${exponent.dimension}")
        }
        if (dimension.isNone) return Quantity(value.pow(exponent.value))
        val x = exponent.value.takeIf { it.isReal }?.re
            ?: throw UnitsException("a value with units can only be raised to a real power, not ${exponent.value}")
        val n = Rational.approximate(x) ?: Rational.approximate(x, maxDen = 10_000) ?: Rational.approximate(x, maxDen = 10_000, tolerance = 1e-5)
            ?: throw UnitsException("a value with units can't be raised to the power $x")
        return Quantity(value.pow(Complex(x)), dimension.pow(n))
    }

    fun pow(n: Rational) = Quantity(value.pow(Complex(n.toDouble())), dimension.pow(n))

    fun sqrt() = Quantity(value.sqrt(), dimension.pow(Rational.of(1, 2)))

    fun root(n: Int): Quantity {
        if (n == 0) throw ArithmeticException("zeroth root")
        return pow(Rational.of(1, n))
    }

    /**
     * Compares two real quantities of the same dimension. Comparing with a
     * dimensionless zero is allowed whatever the dimension, so `x > 0` works for a length.
     */
    override fun compareTo(other: Quantity): Int {
        requireComparable(other)
        if (!isReal || !other.isReal) throw UnitsException("complex numbers cannot be ordered")
        return value.re.compareTo(other.value.re)
    }

    /**
     * Equality as `==` in a worksheet: the dimension rules of [compareTo], but complex values are
     * allowed. Values within a relative [tolerance] of the larger magnitude count as equal.
     */
    fun valueEquals(other: Quantity, tolerance: Double = 0.0): Boolean {
        requireComparable(other)
        return closeTo(other, tolerance)
    }

    /** [compareTo], except that values within a relative [tolerance] compare as equal. */
    fun compareWithin(other: Quantity, tolerance: Double): Int {
        val c = compareTo(other)
        return if (c != 0 && closeTo(other, tolerance)) 0 else c
    }

    private fun closeTo(other: Quantity, tolerance: Double) =
        // With an infinity, only the same infinity is equal (a tolerance relative to it would take in everything).
        if (!value.abs().isFinite() || !other.value.abs().isFinite()) value == other.value
        else (value - other.value).abs() <= tolerance * maxOf(value.abs(), other.value.abs())

    private fun requireComparable(other: Quantity) {
        if (dimension != other.dimension && value != Complex.ZERO && other.value != Complex.ZERO) {
            throw UnitsException("units do not match: $dimension vs ${other.dimension}")
        }
    }

    private fun isPlainZero() = value == Complex.ZERO && dimension.isNone

    override fun toString(): String = if (dimension.isNone) "$value" else "$value $dimension"
}
