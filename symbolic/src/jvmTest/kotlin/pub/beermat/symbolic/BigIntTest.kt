package pub.beermat.symbolic

import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** [BigInt] does what `java.math.BigInteger` did before it, on numbers of every size and sign. */
class BigIntTest {

    private val random = Random(1)

    /** Random numbers of up to [bits] bits, with the awkward ones (limb edges, all ones) often. */
    private fun sample(bits: Int): BigInteger {
        val b = when (random.nextInt(6)) {
            0 -> BigInteger.ONE.shiftLeft(random.nextInt(bits + 1)).subtract(BigInteger.ONE)
            1 -> BigInteger.ONE.shiftLeft(32 * random.nextInt(1, bits / 32 + 2))
            else -> BigInteger(random.nextInt(1, bits + 1), java.util.Random(random.nextLong()))
        }
        return if (random.nextBoolean()) b.negate() else b
    }

    private fun ours(b: BigInteger) = BigInt.parse(b.toString())

    @Test
    fun `the same as BigInteger`() {
        repeat(5000) {
            val a = sample(300)
            val b = sample(if (it % 3 == 0) 40 else 300)
            val x = ours(a)
            val y = ours(b)
            assertEquals(a.toString(), x.toString())
            assertEquals(a.add(b).toString(), (x + y).toString(), "$a + $b")
            assertEquals(a.subtract(b).toString(), (x - y).toString(), "$a - $b")
            assertEquals(a.multiply(b).toString(), (x * y).toString(), "$a * $b")
            if (b.signum() != 0) {
                assertEquals(a.divide(b).toString(), (x / y).toString(), "$a / $b")
                assertEquals(a.rem(b).toString(), (x % y).toString(), "$a % $b")
            }
            assertEquals(a.gcd(b).toString(), x.gcd(y).toString(), "gcd($a, $b)")
            assertEquals(a.compareTo(b), x.compareTo(y), "compare $a, $b")
            assertEquals(a.signum(), x.signum)
            assertEquals(a.bitLength(), x.bitLength(), "bitLength $a")
            assertEquals(a.toLong(), x.toLong(), "toLong $a")
            assertEquals(a.toInt(), x.toInt(), "toInt $a")
            assertEquals(a.toDouble(), x.toDouble(), "toDouble $a")
            assertEquals(a.pow(it % 7).toString(), x.pow(it % 7).toString(), "$a ^ ${it % 7}")
        }
        for (n in listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 4294967295L, 4294967296L, -4294967296L)) {
            assertEquals(BigInteger.valueOf(n).toString(), BigInt.of(n).toString())
        }
        // Division where the quotient estimate needs correcting (top limbs close to each other).
        val big = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
        val d = BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE)
        assertEquals(big.divide(d).toString(), (ours(big) / ours(d)).toString())
        assertEquals(BigInteger.TEN.pow(400).toDouble(), ours(BigInteger.TEN.pow(400)).toDouble())
    }

    @Test
    fun `results used again as inputs`() {
        // Numbers made by arithmetic, not only parsed: a sum without a final carry once kept a zero top
        // limb, and dividing by it went wrong (gcd(-120000000, 400000000000000) never ended).
        var j = BigInteger.ONE
        var x = BigInt.ONE
        repeat(3000) {
            val b = sample(120)
            val y = ours(b)
            when (it % 5) {
                0 -> { j = j.add(b); x += y }
                1 -> { j = j.subtract(b); x -= y }
                2 -> { j = j.multiply(b).mod(BigInteger.TEN.pow(60)); x = (x * y).let { p -> p - p / BigInt.TEN.pow(60) * BigInt.TEN.pow(60) }.let { m -> if (m.signum < 0) m + BigInt.TEN.pow(60) else m } }
                3 -> if (b.signum() != 0) { j = j.rem(b); x %= y }
                else -> { j = j.gcd(b).add(b); x = x.gcd(y) + y }
            }
            assertEquals(j.toString(), x.toString(), "step $it")
            if (j.signum() != 0) assertEquals(b.rem(j).toString(), (y % x).toString(), "step $it, dividing by the result")
            assertEquals(j.bitLength(), x.bitLength(), "step $it, bits")
        }
        assertEquals("40000000", BigInt.of(-120000000).gcd(BigInt.of(160000000) + BigInt.of(399999840000000)).toString())
    }

    @Test
    fun `fractions read and turn into doubles as with BigDecimal`() {
        repeat(3000) {
            val n = sample(200)
            val d = sample(if (it % 2 == 0) 20 else 200).let { if (it.signum() == 0) BigInteger.ONE else it }
            val q = Q.of(ours(n), ours(d))
            val g = n.gcd(d)
            val expected = java.math.BigDecimal(n.divide(g)).divide(java.math.BigDecimal(d.divide(g)), java.math.MathContext.DECIMAL64).toDouble()
            assertEquals(expected, q.toDouble(), "$n/$d")
        }
        for (text in listOf("1.5e-3", "-0.25", "120", "3.0E10", ".5", "7.", "1e+2", "0.000")) {
            val b = java.math.BigDecimal(text)
            val exact = if (b.scale() <= 0) Q.of(ours(b.toBigIntegerExact()), BigInt.ONE) else Q.of(ours(b.unscaledValue()), ours(BigInteger.TEN.pow(b.scale())))
            assertEquals(exact, Q.parse(text), text)
        }
    }
}
