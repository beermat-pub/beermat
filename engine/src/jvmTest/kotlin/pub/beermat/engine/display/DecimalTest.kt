package pub.beermat.engine.display

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** [Decimal] does what `java.math.BigDecimal` did before it, on numbers of every size. */
class DecimalTest {

    @Test
    fun `the same as BigDecimal`() {
        val random = Random(1)
        val samples = List(20000) {
            when (it % 4) {
                0 -> random.nextDouble(-1e6, 1e6)
                1 -> random.nextDouble() * Math.pow(10.0, random.nextInt(-30, 30).toDouble())
                2 -> random.nextInt(-100000, 100000) / 1000.0
                else -> Math.pow(10.0, random.nextInt(-20, 20).toDouble()) * (if (random.nextBoolean()) 9.995 else 1.0)
            }
        } + listOf(0.0, -0.0, 2.675, 0.5, 1.5, 9.99, 999.5, 1e-7, 123456789.0, 1e21, 5e-324, Double.MAX_VALUE)
        for (x in samples) {
            val d = Decimal.of(x)
            val b = BigDecimal.valueOf(x)
            assertEquals(b.toPlainString().trimZero(), d.toPlainString().trimZero(), "of($x)")
            assertEquals(b.precision() - b.scale(), d.precision - d.scale, "magnitude of $x")
            for (places in listOf(0, 1, 3, 6)) {
                assertEquals(b.setScale(places, RoundingMode.HALF_UP).toPlainString(), d.setScale(places).toPlainString(), "$x to $places places")
                assertEquals(b.setScale(places, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(), d.setScale(places).stripTrailingZeros().toPlainString(), "$x to $places places, stripped")
            }
            for (figures in listOf(1, 3, 6)) {
                val br = b.round(MathContext(figures, RoundingMode.HALF_UP))
                val dr = d.round(figures)
                assertEquals(br.toPlainString(), dr.toPlainString(), "$x to $figures figures")
                assertEquals(br.precision() to br.scale(), dr.precision to dr.scale, "$x to $figures figures: digits and scale")
                val e = br.precision() - br.scale() - 1
                assertEquals(br.movePointLeft(e).setScale(figures - 1, RoundingMode.HALF_UP).toPlainString(), dr.movePointLeft(e).setScale(figures - 1).toPlainString(), "$x's mantissa")
            }
        }
    }

    // BigDecimal.valueOf keeps a trailing ".0" (1.0E10 is 10 digits and a scale); only the value need agree.
    private fun String.trimZero() = if ('.' in this) trimEnd('0').trimEnd('.') else this
}
