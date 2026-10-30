package pub.beermat.engine.units

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * A complex number. Real results stay exactly real: operations take the plain [Double]
 * path whenever both operands are real, so `2^0.5` is `Math.pow(2, 0.5)` and not
 * `exp(0.5·ln 2)`.
 *
 * `-0.0` is stored as `0.0`, so that `-8` (a negated real) is not `-8 − 0i`, which would put
 * principal values such as `(-8)^(1/3)` on the wrong branch.
 */
class Complex(re: Double, im: Double = 0.0) {
    val re: Double = re + 0.0
    val im: Double = im + 0.0

    val isReal: Boolean get() = im == 0.0

    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)

    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)

    operator fun times(o: Complex) =
        if (isReal && o.isReal) Complex(re * o.re) else Complex(re * o.re - im * o.im, re * o.im + im * o.re)

    operator fun div(o: Complex): Complex {
        if (isReal && o.isReal) return Complex(re / o.re)
        val d = o.re * o.re + o.im * o.im
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    operator fun unaryMinus() = Complex(-re, -im)

    fun abs(): Double = if (isReal) kotlin.math.abs(re) else hypot(re, im)

    fun arg(): Double = atan2(im, re)

    fun conj() = Complex(re, -im)

    /** Principal square root; `sqrt(-4)` is `2i`. */
    fun sqrt(): Complex {
        if (isReal) return if (re >= 0) Complex(sqrt(re)) else Complex(0.0, sqrt(-re))
        val m = abs()
        val r = sqrt((m + re) / 2)
        val i = sqrt((m - re) / 2)
        return Complex(r, if (im < 0) -i else i)
    }

    fun exp(): Complex = if (isReal) Complex(exp(re)) else exp(re).let { Complex(it * cos(im), it * sin(im)) }

    /** Principal natural logarithm; `ln(-1)` is `πi`. */
    fun ln(): Complex = if (isReal && re >= 0) Complex(ln(re)) else Complex(ln(abs()), arg())

    /** Principal power. A negative real base with a fractional exponent gives a complex result. */
    fun pow(w: Complex): Complex {
        if (isReal && w.isReal && (re >= 0 || w.re == kotlin.math.floor(w.re))) return Complex(re.pow(w.re))
        if (re == 0.0 && im == 0.0) return if (w.re > 0) ZERO else Complex(Double.NaN, Double.NaN)
        return (w * ln()).exp()
    }

    fun sin(): Complex = if (isReal) Complex(sin(re)) else Complex(sin(re) * cosh(im), cos(re) * sinh(im))

    fun cos(): Complex = if (isReal) Complex(cos(re)) else Complex(cos(re) * cosh(im), -sin(re) * sinh(im))

    override fun equals(other: Any?): Boolean = other is Complex && re == other.re && im == other.im

    override fun hashCode(): Int = 31 * re.hashCode() + im.hashCode()

    override fun toString(): String = when {
        isReal -> "$re"
        re == 0.0 -> "${im}i"
        im < 0 -> "$re-${-im}i"
        else -> "$re+${im}i"
    }

    companion object {
        val ZERO = Complex(0.0)
        val ONE = Complex(1.0)
        val I = Complex(0.0, 1.0)
    }
}
