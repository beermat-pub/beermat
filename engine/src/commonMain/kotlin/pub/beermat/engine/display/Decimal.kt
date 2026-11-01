package pub.beermat.engine.display

/**
 * A decimal number for rounding results: the [digits] of a whole number (no leading zeros; "0"
 * for zero) times 10^-[scale], as `java.math.BigDecimal` keeps one, with the few operations
 * [NumberFormat] and the symbolic engine need, all rounding halves up. Plain Kotlin, so results
 * show the same on every platform.
 */
class Decimal private constructor(private val negative: Boolean, private val digits: String, val scale: Int) {

    val signum: Int get() = if (digits == "0") 0 else if (negative) -1 else 1

    /** How many digits: 1 for zero. */
    val precision: Int get() = digits.length

    /** With [places] decimals, rounding the dropped digits half up. */
    fun setScale(places: Int): Decimal {
        if (places >= scale) return Decimal(negative, if (digits == "0") "0" else digits + "0".repeat(places - scale), places)
        val drop = scale - places
        val kept = when {
            drop > digits.length -> "0"
            drop == digits.length -> if (digits[0] >= '5') "1" else "0"
            else -> digits.substring(0, digits.length - drop).let { if (digits[digits.length - drop] >= '5') increment(it) else it }
        }
        return of(negative, kept, places)
    }

    /** To [figures] significant digits, halves up (9.99 to two figures is 10). */
    fun round(figures: Int): Decimal {
        var x = this
        while (x.precision > figures) x = x.setScale(x.scale - (x.precision - figures))
        return x
    }

    /** Divided by 10^[n]; the scale stays 0 or more. */
    fun movePointLeft(n: Int): Decimal = Decimal(negative, digits, scale + n).let { if (it.scale < 0) it.setScale(0) else it }

    fun stripTrailingZeros(): Decimal {
        if (digits == "0") return ZERO
        val stripped = digits.trimEnd('0')
        return Decimal(negative, stripped, scale - (digits.length - stripped.length))
    }

    fun toPlainString(): String {
        val sign = if (signum < 0) "-" else ""
        if (scale <= 0) return sign + digits + if (digits == "0") "" else "0".repeat(-scale)
        val padded = digits.padStart(scale + 1, '0')
        return sign + padded.substring(0, padded.length - scale) + "." + padded.substring(padded.length - scale)
    }

    /** As `BigDecimal.toString` writes it: plain, or `1.5E+20` and `1.2E-7` when the exponent is large or the number tiny. */
    fun toScientificString(): String {
        val adjusted = precision - 1 - scale
        if (scale >= 0 && adjusted >= -6) return toPlainString()
        val sign = if (signum < 0) "-" else ""
        val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        return sign + mantissa + "E" + (if (adjusted > 0) "+" else "") + adjusted
    }

    fun toDouble(): Double = toPlainString().toDouble()

    override fun toString() = toPlainString()

    companion object {
        private val ZERO = Decimal(false, "0", 0)

        private fun of(negative: Boolean, digits: String, scale: Int): Decimal {
            val d = digits.trimStart('0').ifEmpty { "0" }
            return Decimal(negative && d != "0", d, scale)
        }

        /** Adds 1 to a whole number written in digits. */
        private fun increment(digits: String): String {
            val out = digits.toCharArray()
            for (k in out.indices.reversed()) {
                if (out[k] != '9') {
                    out[k]++
                    return out.concatToString()
                }
                out[k] = '0'
            }
            return "1" + out.concatToString()
        }

        /** The exact value of [x], every binary digit of it (as `BigDecimal(double)` gives it): 0.1 is 0.1000000000000000055511151231257827…. */
        fun exactOf(x: Double): Decimal {
            require(x.isFinite())
            if (x == 0.0) return ZERO
            val bits = x.toRawBits()
            val biased = ((bits ushr 52) and 0x7FF).toInt()
            val fraction = bits and 0xFFFFFFFFFFFFFL
            // x = m × 2^e, m a whole number.
            val m = if (biased == 0) fraction else fraction or (1L shl 52)
            val e = (if (biased == 0) 1 else biased) - 1075
            var digits = m.toString()
            return if (e >= 0) {
                repeat(e) { digits = timesSmall(digits, 2) }
                of(x < 0, digits, 0)
            } else {
                // m × 2^e = m × 5^-e / 10^-e.
                repeat(-e) { digits = timesSmall(digits, 5) }
                of(x < 0, digits, -e)
            }
        }

        /** A whole number written in digits, times a small factor. */
        private fun timesSmall(digits: String, factor: Int): String {
            val out = StringBuilder()
            var carry = 0
            for (k in digits.indices.reversed()) {
                val v = (digits[k] - '0') * factor + carry
                out.append('0' + v % 10)
                carry = v / 10
            }
            while (carry > 0) {
                out.append('0' + carry % 10)
                carry /= 10
            }
            return out.reverse().toString()
        }

        /**
         * The shortest decimal that reads back as [x] (as `BigDecimal.valueOf` takes it), so 2.675 is
         * 2.675 and rounds to 2.68 as a person expects. From Kotlin's text of the double, which every
         * platform writes as its shortest round-trip form ("1.0E-5" or "1e-5" alike are read here).
         */
        fun of(x: Double): Decimal {
            require(x.isFinite())
            val text = x.toString()
            val negative = text.startsWith("-")
            val body = text.removePrefix("-")
            val mantissa = body.substringBefore('e').substringBefore('E')
            val exponent = body.substringAfter('e', body.substringAfter('E', "0")).removePrefix("+").toInt()
            val whole = mantissa.substringBefore('.')
            val fraction = mantissa.substringAfter('.', "")
            return of(negative, whole + fraction, fraction.length - exponent)
        }
    }
}
