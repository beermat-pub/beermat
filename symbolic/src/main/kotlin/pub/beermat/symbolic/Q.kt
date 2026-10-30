package pub.beermat.symbolic

import java.math.BigDecimal
import java.math.BigInteger

/** An exact rational number, in lowest terms with a positive denominator. */
class Q private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Q> {

    val isZero get() = num.signum() == 0
    val isOne get() = num == BigInteger.ONE && den == BigInteger.ONE
    val isInteger get() = den == BigInteger.ONE
    val sign get() = num.signum()

    operator fun plus(o: Q) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Q) = this + (-o)
    operator fun times(o: Q) = of(num * o.num, den * o.den)
    operator fun div(o: Q): Q {
        require(!o.isZero) { "division by zero" }
        return of(num * o.den, den * o.num)
    }
    operator fun unaryMinus() = Q(num.negate(), den)
    fun abs() = if (sign < 0) -this else this

    /** This to an integer power, or null when the result would be too large to be useful. */
    fun pow(n: Int): Q? {
        if (n == 0) return ONE
        if (isZero) return if (n > 0) ZERO else null
        if (Math.abs(n) > 64 || num.bitLength() * Math.abs(n) > 4096) return null
        val p = of(num.pow(Math.abs(n)), den.pow(Math.abs(n)))
        return if (n > 0) p else ONE / p
    }

    /** The exact [k]-th root, if this is a perfect power. */
    fun root(k: Int): Q? {
        if (sign < 0 && k % 2 == 0) return null
        fun r(b: BigInteger): BigInteger? {
            val a = b.abs()
            var x = BigInteger.valueOf(Math.round(Math.pow(a.toDouble(), 1.0 / k)))
            for (d in -1..1) {
                val c = x + BigInteger.valueOf(d.toLong())
                if (c.signum() >= 0 && c.pow(k) == a) return if (b.signum() < 0) c.negate() else c
            }
            return null
        }
        return of(r(num) ?: return null, r(den) ?: return null)
    }

    fun toDouble(): Double = BigDecimal(num).divide(BigDecimal(den), java.math.MathContext.DECIMAL64).toDouble()

    override fun compareTo(other: Q) = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Q && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    override fun toString() = if (isInteger) "$num" else "$num/$den"

    companion object {
        val ZERO = Q(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Q(BigInteger.ONE, BigInteger.ONE)

        fun of(n: Long, d: Long = 1) = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        fun of(n: BigInteger, d: BigInteger): Q {
            require(d.signum() != 0) { "division by zero" }
            val g = n.gcd(d).let { if (it.signum() == 0) BigInteger.ONE else it }
            val s = if (d.signum() < 0) BigInteger.ONE.negate() else BigInteger.ONE
            return Q(n / g * s, d / g * s)
        }

        /** A decimal literal exactly: `1.5e-3` is 3/2000. */
        fun parse(text: String): Q {
            val b = BigDecimal(text)
            return if (b.scale() <= 0) of(b.toBigIntegerExact(), BigInteger.ONE) else of(b.unscaledValue(), BigInteger.TEN.pow(b.scale()))
        }
    }
}
