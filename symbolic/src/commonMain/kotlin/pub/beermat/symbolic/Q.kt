package pub.beermat.symbolic

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** An exact rational number, in lowest terms with a positive denominator. */
class Q private constructor(val num: BigInt, val den: BigInt) : Comparable<Q> {

    val isZero get() = num.signum == 0
    val isOne get() = num == BigInt.ONE && den == BigInt.ONE
    val isInteger get() = den == BigInt.ONE
    val sign get() = num.signum

    operator fun plus(o: Q) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Q) = this + (-o)
    operator fun times(o: Q) = of(num * o.num, den * o.den)
    operator fun div(o: Q): Q {
        require(!o.isZero) { "division by zero" }
        return of(num * o.den, den * o.num)
    }
    operator fun unaryMinus() = Q(-num, den)
    fun abs() = if (sign < 0) -this else this

    /** This to an integer power, or null when the result would be too large to be useful. */
    fun pow(n: Int): Q? {
        if (n == 0) return ONE
        if (isZero) return if (n > 0) ZERO else null
        if (abs(n) > 64 || num.bitLength() * abs(n) > 4096) return null
        val p = of(num.pow(abs(n)), den.pow(abs(n)))
        return if (n > 0) p else ONE / p
    }

    /** The exact [k]-th root, if this is a perfect power. */
    fun root(k: Int): Q? {
        if (sign < 0 && k % 2 == 0) return null
        fun r(b: BigInt): BigInt? {
            val a = b.abs()
            val x = BigInt.of(a.toDouble().pow(1.0 / k).let { if (it.isNaN()) 0 else it.roundToLong() })
            for (d in -1..1) {
                val c = x + BigInt.of(d)
                if (c.signum >= 0 && c.pow(k) == a) return if (b.signum < 0) -c else c
            }
            return null
        }
        return of(r(num) ?: return null, r(den) ?: return null)
    }

    /**
     * The nearest double, by way of the quotient to 16 significant digits, halves to even (as
     * `BigDecimal` division in `MathContext.DECIMAL64` gave it before), then read as a double.
     */
    fun toDouble(): Double {
        if (isZero) return 0.0
        val n = num.abs()
        // Scaled so the quotient has at least 17 digits: one more than kept, to round from.
        val shift = maxOf(0, 18 - (n.toString().length - den.toString().length))
        val (q, r) = (n * BigInt.TEN.pow(shift)).divRem(den)
        val digits = q.toString()
        var kept = digits.take(16)
        val rest = digits.drop(16)
        val up = when {
            rest.isEmpty() -> false
            rest[0] > '5' -> true
            rest[0] < '5' -> false
            rest.drop(1).any { it != '0' } || r.signum != 0 -> true
            else -> (kept.last() - '0') % 2 == 1
        }
        if (up) kept = (BigInt.parse(kept) + BigInt.ONE).toString()
        // The quotient is at least 17 digits long, so the kept ones stand for kept × 10^(dropped − shift).
        return ((if (sign < 0) "-" else "") + kept + "E" + (digits.length - 16 - shift)).toDouble()
    }

    override fun compareTo(other: Q) = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Q && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    override fun toString() = if (isInteger) "$num" else "$num/$den"

    companion object {
        val ZERO = Q(BigInt.ZERO, BigInt.ONE)
        val ONE = Q(BigInt.ONE, BigInt.ONE)

        fun of(n: Long, d: Long = 1) = of(BigInt.of(n), BigInt.of(d))

        fun of(n: BigInt, d: BigInt): Q {
            require(d.signum != 0) { "division by zero" }
            val g = n.gcd(d).let { if (it.signum == 0) BigInt.ONE else it }
            val s = if (d.signum < 0) -BigInt.ONE else BigInt.ONE
            return Q(n / g * s, d / g * s)
        }

        /** A decimal literal exactly: `1.5e-3` is 3/2000. */
        fun parse(text: String): Q {
            val negative = text.startsWith("-")
            val body = text.removePrefix("-").removePrefix("+")
            val mantissa = body.substringBefore('e').substringBefore('E')
            val exponent = body.substringAfter('e', body.substringAfter('E', "0")).removePrefix("+").toInt()
            val fraction = mantissa.substringAfter('.', "")
            val digits = BigInt.parse((mantissa.substringBefore('.') + fraction).ifEmpty { "0" })
            val scale = fraction.length - exponent
            val n = if (negative) -digits else digits
            return if (scale <= 0) of(n * BigInt.TEN.pow(-scale), BigInt.ONE) else of(n, BigInt.TEN.pow(scale))
        }
    }
}
