package pub.beermat.engine.display

import pub.beermat.engine.units.Complex
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.pow

/**
 * Rounds numbers for display (docs/results.md). Works on the shortest decimal form of the
 * double, so 2.675 rounds to 2.68 as a person expects.
 */
object NumberFormat {

    fun format(z: Complex, f: ResultFormat): String {
        val hide = 10.0.pow(-f.complexThreshold)
        val re = if (abs(z.re) < abs(z.im) * hide) 0.0 else z.re
        val im = if (abs(z.im) < abs(z.re) * hide) 0.0 else z.im
        val reText = format(re, f)
        val imText = format(abs(im), f)
        return when {
            imText == "0" -> reText
            reText == "0" -> (if (im < 0) "-" else "") + imText + "i"
            else -> "$reText ${if (im < 0) "-" else "+"} ${imText}i"
        }
    }

    fun format(x: Double, f: ResultFormat): String {
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "∞" else "-∞"
        if (abs(x) < 10.0.pow(-f.zeroThreshold)) return "0"
        val exact = BigDecimal.valueOf(x)
        val rounded = plain(exact, f.rounding)
        val t = f.exponentialThreshold
        val large = rounded.signum() != 0 && exponent(rounded) >= t
        // Also when plain rounding would show a non-zero value as 0 (a strain of 1e-6 at 3 decimals).
        val small = exponent(exact) < -t || rounded.signum() == 0
        val text = if (large || small) scientific(exact, f.rounding) else rounded.toPlainString()
        return if (isZero(text)) "0" else text
    }

    private fun plain(x: BigDecimal, r: Rounding): BigDecimal = when (r) {
        is Rounding.Decimals -> x.setScale(r.places, RoundingMode.HALF_UP).let { if (r.trailingZeros) it else it.stripTrailingZeros() }
        is Rounding.Significant -> {
            val s = x.round(MathContext(r.figures, RoundingMode.HALF_UP))
            if (!r.strict && s.scale() < 0) x.setScale(0, RoundingMode.HALF_UP) else pad(s, r.figures)
        }
    }

    /** `d.ddd` × 10^e, with the rounding applied to the mantissa. */
    private fun scientific(x: BigDecimal, r: Rounding): String {
        val digits = when (r) {
            is Rounding.Decimals -> r.places + 1
            is Rounding.Significant -> r.figures
        }
        val rounded = x.round(MathContext(digits, RoundingMode.HALF_UP))
        val e = exponent(rounded)
        var mantissa = rounded.movePointLeft(e).setScale(digits - 1, RoundingMode.HALF_UP)
        if (r is Rounding.Decimals && !r.trailingZeros) mantissa = mantissa.stripTrailingZeros()
        return "${mantissa.toPlainString()}e$e"
    }

    /** Pads with trailing zeros up to [figures] significant figures: 2.5 → 2.50. */
    private fun pad(x: BigDecimal, figures: Int): BigDecimal =
        if (x.precision() < figures) x.setScale(x.scale() + figures - x.precision()) else x

    /** The power of ten of the leading digit: 1234 → 3, 0.05 → -2. */
    private fun exponent(x: BigDecimal): Int = x.precision() - x.scale() - 1

    private fun isZero(text: String) = text.trimStart('-').all { it == '0' || it == '.' }
}
