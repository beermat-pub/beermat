package pub.beermat.engine.units

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * An exact fraction `num/den` in lowest terms, with `den > 0`.
 * Used for dimension exponents, so that `sqrt(m)` is L^(1/2) exactly.
 * Arithmetic throws [ArithmeticException] on overflow rather than wrapping.
 */
class Rational private constructor(val num: Int, val den: Int) : Comparable<Rational> {

    val isZero: Boolean get() = num == 0
    val isInteger: Boolean get() = den == 1

    operator fun plus(other: Rational): Rational =
        of(
            Math.addExact(Math.multiplyExact(num, other.den), Math.multiplyExact(other.num, den)),
            Math.multiplyExact(den, other.den),
        )

    operator fun minus(other: Rational): Rational = this + (-other)

    operator fun times(other: Rational): Rational =
        of(Math.multiplyExact(num, other.num), Math.multiplyExact(den, other.den))

    operator fun div(other: Rational): Rational {
        if (other.isZero) throw ArithmeticException("division by zero")
        return of(Math.multiplyExact(num, other.den), Math.multiplyExact(den, other.num))
    }

    operator fun unaryMinus(): Rational = Rational(Math.negateExact(num), den)

    fun toDouble(): Double = num.toDouble() / den

    override fun compareTo(other: Rational): Int =
        (num.toLong() * other.den).compareTo(other.num.toLong() * den)

    override fun equals(other: Any?): Boolean =
        other is Rational && num == other.num && den == other.den

    override fun hashCode(): Int = 31 * num + den

    override fun toString(): String = if (isInteger) "$num" else "$num/$den"

    companion object {
        val ZERO = Rational(0, 1)
        val ONE = Rational(1, 1)

        fun of(num: Int, den: Int = 1): Rational {
            if (den == 0) throw ArithmeticException("zero denominator")
            if (num == 0) return ZERO
            val g = gcd(abs(num.toLong()), abs(den.toLong()))
            val sign = if (den < 0) -1L else 1L
            return Rational(Math.toIntExact(sign * num / g), Math.toIntExact(sign * den / g))
        }

        /**
         * The simplest fraction with denominator at most [maxDen] that is within a relative
         * [tolerance] of [x], or null if there is none. Turns a computed exponent such as
         * `0.3333333333333333` back into `1/3`.
         */
        fun approximate(x: Double, maxDen: Int = 100, tolerance: Double = 1e-9): Rational? {
            if (!x.isFinite() || abs(x) > Int.MAX_VALUE) return null
            // Continued-fraction convergents h/k of x.
            var h0 = 0L
            var h1 = 1L
            var k0 = 1L
            var k1 = 0L
            var r = x
            while (true) {
                val a = floor(r)
                if (abs(a) > Int.MAX_VALUE) return null
                val h2 = a.toLong() * h1 + h0
                val k2 = a.toLong() * k1 + k0
                if (k2 > maxDen || abs(h2) > Int.MAX_VALUE) return null
                h0 = h1; h1 = h2; k0 = k1; k1 = k2
                if (abs(x - h1.toDouble() / k1) <= tolerance * max(1.0, abs(x))) {
                    return of(h1.toInt(), k1.toInt())
                }
                val frac = r - a
                if (frac == 0.0) return null
                r = 1 / frac
            }
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }
}

fun Int.toRational(): Rational = Rational.of(this)
