package pub.beermat.symbolic

/**
 * A whole number of any size, for exact rationals ([Q]) on every platform: a sign and a magnitude
 * in 32-bit limbs, least significant first, with no leading zero limbs (none at all for zero).
 * It does what `java.math.BigInteger` did here, with the same results: division truncates
 * toward zero, and the remainder takes the dividend's sign.
 */
class BigInt private constructor(val signum: Int, private val mag: IntArray) : Comparable<BigInt> {

    operator fun plus(o: BigInt): BigInt = when {
        o.signum == 0 -> this
        signum == 0 -> o
        // Through `of`, which drops a zero top limb: a sum without a final carry has one.
        signum == o.signum -> of(signum, add(mag, o.mag))
        else -> when (val c = compareMag(mag, o.mag)) {
            0 -> ZERO
            else -> if (c > 0) of(signum, sub(mag, o.mag)) else of(o.signum, sub(o.mag, mag))
        }
    }

    operator fun minus(o: BigInt): BigInt = this + -o

    operator fun unaryMinus(): BigInt = if (signum == 0) this else BigInt(-signum, mag)

    operator fun times(o: BigInt): BigInt {
        if (signum == 0 || o.signum == 0) return ZERO
        val out = IntArray(mag.size + o.mag.size)
        for (i in mag.indices) {
            var carry = 0L
            val a = mag[i].toLong() and MASK
            for (j in o.mag.indices) {
                val t = a * (o.mag[j].toLong() and MASK) + (out[i + j].toLong() and MASK) + carry
                out[i + j] = t.toInt()
                carry = t ushr 32
            }
            out[i + o.mag.size] = carry.toInt()
        }
        return of(signum * o.signum, out)
    }

    operator fun div(o: BigInt): BigInt = divRem(o).first

    operator fun rem(o: BigInt): BigInt = divRem(o).second

    /** The quotient, truncated toward zero, and the remainder, with this number's sign. */
    fun divRem(o: BigInt): Pair<BigInt, BigInt> {
        if (o.signum == 0) throw ArithmeticException("division by zero")
        if (compareMag(mag, o.mag) < 0) return ZERO to this
        val (q, r) = divMag(mag, o.mag)
        return of(signum * o.signum, q) to of(signum, r)
    }

    fun abs(): BigInt = if (signum < 0) -this else this

    fun gcd(o: BigInt): BigInt {
        var a = abs()
        var b = o.abs()
        while (b.signum != 0) {
            val r = a % b
            a = b
            b = r
        }
        return a
    }

    fun pow(n: Int): BigInt {
        require(n >= 0) { "negative exponent" }
        var result = ONE
        var base = this
        var e = n
        while (e > 0) {
            if (e and 1 == 1) result *= base
            e = e shr 1
            if (e > 0) base *= base
        }
        return result
    }

    /** The bits of the smallest two's-complement form, without the sign bit (as `BigInteger.bitLength`). */
    fun bitLength(): Int {
        if (signum == 0) return 0
        val m = if (signum > 0) mag else sub(mag, intArrayOf(1))
        if (m.isEmpty()) return 0
        return (m.size - 1) * 32 + (32 - m.last().countLeadingZeroBits())
    }

    /** The lowest 64 bits, in two's complement (as `BigInteger.longValue`). */
    fun toLong(): Long {
        val low = (mag.getOrElse(0) { 0 }.toLong() and MASK) or (mag.getOrElse(1) { 0 }.toLong() shl 32)
        return if (signum < 0) -low else low
    }

    fun toInt(): Int = toLong().toInt()

    /** The nearest double, halves to even; infinite when too large (as `BigInteger.doubleValue`). */
    fun toDouble(): Double {
        val bits = abs().bitLength()
        if (bits <= 63) return toLong().toDouble()
        // The top 64 bits, with any lower bit kept as a sticky last bit, round exactly as the whole would.
        val shift = bits - 64
        val top = abs().shiftRight(shift)
        var u = top.toLong()
        if (abs() != top.shiftLeft(shift)) u = u or 1
        val d = ((u ushr 1) or (u and 1)).toDouble() * 2.0
        val scaled = d * 2.0.pow(shift)
        return if (signum < 0) -scaled else scaled
    }

    private fun shiftRight(n: Int): BigInt {
        val limbs = n / 32
        val bits = n % 32
        if (limbs >= mag.size) return ZERO
        val out = IntArray(mag.size - limbs) { i ->
            val lo = mag[i + limbs] ushr bits
            val hi = if (bits == 0 || i + limbs + 1 >= mag.size) 0 else mag[i + limbs + 1] shl (32 - bits)
            lo or hi
        }
        return of(signum, out)
    }

    private fun shiftLeft(n: Int): BigInt {
        if (signum == 0) return this
        val limbs = n / 32
        val bits = n % 32
        val out = IntArray(mag.size + limbs + 1)
        for (i in mag.indices) {
            out[i + limbs] = out[i + limbs] or (mag[i] shl bits)
            if (bits != 0) out[i + limbs + 1] = mag[i] ushr (32 - bits)
        }
        return of(signum, out)
    }

    override fun compareTo(other: BigInt): Int = when {
        signum != other.signum -> signum.compareTo(other.signum)
        else -> signum * compareMag(mag, other.mag)
    }

    override fun equals(other: Any?) = other is BigInt && signum == other.signum && mag.contentEquals(other.mag)

    override fun hashCode() = 31 * mag.contentHashCode() + signum

    override fun toString(): String {
        if (signum == 0) return "0"
        val chunks = mutableListOf<Int>()
        var m = mag
        while (m.isNotEmpty()) {
            val (q, r) = divSmall(m, BILLION)
            chunks += r
            m = q
        }
        val text = StringBuilder(if (signum < 0) "-" else "").append(chunks.last())
        for (k in chunks.size - 2 downTo 0) text.append(chunks[k].toString().padStart(9, '0'))
        return text.toString()
    }

    companion object {
        private const val MASK = 0xFFFFFFFFL
        private const val BASE = 1L shl 32
        private const val BILLION = 1_000_000_000

        val ZERO = BigInt(0, IntArray(0))
        val ONE = of(1)
        val TEN = of(10)

        fun of(n: Long): BigInt {
            if (n == 0L) return ZERO
            // Long.MIN_VALUE's magnitude doesn't fit a Long: its two limbs are taken as unsigned.
            val m = if (n < 0) -n else n
            return of(if (n < 0) -1 else 1, intArrayOf(m.toInt(), (m ushr 32).toInt()))
        }

        fun of(n: Int): BigInt = of(n.toLong())

        /** Decimal digits, with an optional `-` or `+`. */
        fun parse(text: String): BigInt {
            val negative = text.startsWith("-")
            val digits = text.removePrefix("-").removePrefix("+")
            require(digits.isNotEmpty() && digits.all { it in '0'..'9' }) { "not a whole number: '$text'" }
            var m = IntArray(0)
            var k = digits.length % 9
            if (k == 0) k = 9
            var start = 0
            while (start < digits.length) {
                m = mulAddSmall(m, if (start == 0) 1 else BILLION, digits.substring(start, start + k).toInt())
                start += k
                k = 9
            }
            return of(if (negative) -1 else 1, m)
        }

        private fun of(sign: Int, m: IntArray): BigInt {
            var n = m.size
            while (n > 0 && m[n - 1] == 0) n--
            return if (n == 0) ZERO else BigInt(sign, if (n == m.size) m else m.copyOf(n))
        }

        private fun compareMag(a: IntArray, b: IntArray): Int {
            if (a.size != b.size) return a.size.compareTo(b.size)
            for (i in a.indices.reversed()) {
                if (a[i] != b[i]) return (a[i].toLong() and MASK).compareTo(b[i].toLong() and MASK)
            }
            return 0
        }

        private fun add(a: IntArray, b: IntArray): IntArray {
            val out = IntArray(maxOf(a.size, b.size) + 1)
            var carry = 0L
            for (i in 0 until out.size - 1) {
                val t = (a.getOrElse(i) { 0 }.toLong() and MASK) + (b.getOrElse(i) { 0 }.toLong() and MASK) + carry
                out[i] = t.toInt()
                carry = t ushr 32
            }
            out[out.size - 1] = carry.toInt()
            return out
        }

        /** [a] - [b], for |a| ≥ |b|. */
        private fun sub(a: IntArray, b: IntArray): IntArray {
            val out = IntArray(a.size)
            var borrow = 0L
            for (i in a.indices) {
                var t = (a[i].toLong() and MASK) - (b.getOrElse(i) { 0 }.toLong() and MASK) - borrow
                borrow = if (t < 0) 1 else 0
                if (t < 0) t += 1L shl 32
                out[i] = t.toInt()
            }
            var n = out.size
            while (n > 0 && out[n - 1] == 0) n--
            return out.copyOf(n)
        }

        /** [m] times [factor] plus [add], all unsigned. */
        private fun mulAddSmall(m: IntArray, factor: Int, add: Int): IntArray {
            val out = IntArray(m.size + 1)
            var carry = add.toLong() and MASK
            for (i in m.indices) {
                val t = (m[i].toLong() and MASK) * (factor.toLong() and MASK) + carry
                out[i] = t.toInt()
                carry = t ushr 32
            }
            out[m.size] = carry.toInt()
            return out
        }

        /** [m] divided by one limb [d] (unsigned, not zero): the quotient (trimmed) and the remainder. */
        private fun divSmall(m: IntArray, d: Int): Pair<IntArray, Int> {
            val q = IntArray(m.size)
            val divisor = (d.toLong() and MASK).toULong()
            var r = 0UL
            for (i in m.indices.reversed()) {
                val cur = (r shl 32) or (m[i].toLong() and MASK).toULong()
                q[i] = (cur / divisor).toInt()
                r = cur % divisor
            }
            var n = q.size
            while (n > 0 && q[n - 1] == 0) n--
            return q.copyOf(n) to r.toInt()
        }

        /** Long division of magnitudes, |a| ≥ |b| > 0 (Knuth, The Art of Computer Programming, 4.3.1, algorithm D). */
        private fun divMag(a: IntArray, b: IntArray): Pair<IntArray, IntArray> {
            if (b.size == 1) {
                val (q, r) = divSmall(a, b[0])
                return q to intArrayOf(r)
            }
            // Normalize: shift so the divisor's top limb has its high bit set.
            val s = b.last().countLeadingZeroBits()
            val v = shl(b, s, b.size)
            val u = shl(a, s, a.size + 1)
            val n = v.size
            val m = a.size - n
            val q = IntArray(m + 1)
            val vTop = v[n - 1].toLong() and MASK
            val vNext = v[n - 2].toLong() and MASK
            for (j in m downTo 0) {
                val num = ((u[j + n].toLong() and MASK) shl 32) or (u[j + n - 1].toLong() and MASK)
                // Estimate the quotient digit from the top limbs; it is at most two too large.
                var qhat = (num.toULong() / vTop.toULong()).toLong()
                var rhat = (num.toULong() % vTop.toULong()).toLong()
                val below = u[j + n - 2].toLong() and MASK
                while (qhat >= BASE || unsignedGreater(qhat * vNext, (rhat shl 32) or below)) {
                    qhat--
                    rhat += vTop
                    if (rhat >= BASE) break
                }
                // Multiply and subtract.
                var borrow = 0L
                var carry = 0L
                for (i in 0 until n) {
                    val p = qhat * (v[i].toLong() and MASK) + carry
                    carry = p ushr 32
                    val t = (u[i + j].toLong() and MASK) - (p and MASK) - borrow
                    u[i + j] = t.toInt()
                    borrow = if (t < 0) 1 else 0
                }
                val t = (u[j + n].toLong() and MASK) - carry - borrow
                u[j + n] = t.toInt()
                if (t < 0) {
                    // qhat was one too large: add the divisor back.
                    qhat--
                    var c = 0L
                    for (i in 0 until n) {
                        val sum = (u[i + j].toLong() and MASK) + (v[i].toLong() and MASK) + c
                        u[i + j] = sum.toInt()
                        c = sum ushr 32
                    }
                    u[j + n] = (u[j + n] + c.toInt())
                }
                q[j] = qhat.toInt()
            }
            return q to shr(u, s, n)
        }

        private fun shl(m: IntArray, s: Int, size: Int): IntArray {
            val out = IntArray(size)
            for (i in m.indices) {
                out[i] = out[i] or (m[i] shl s)
                if (s != 0 && i + 1 < size) out[i + 1] = m[i] ushr (32 - s)
            }
            return out
        }

        private fun shr(m: IntArray, s: Int, size: Int): IntArray =
            IntArray(size) { i -> (m[i] ushr s) or (if (s == 0 || i + 1 >= m.size) 0 else m[i + 1] shl (32 - s)) }

        private fun unsignedGreater(a: Long, b: Long) = (a xor Long.MIN_VALUE) > (b xor Long.MIN_VALUE)

        private fun Double.pow(n: Int): Double {
            var r = 1.0
            repeat(n) { r *= this }
            return r
        }
    }
}
