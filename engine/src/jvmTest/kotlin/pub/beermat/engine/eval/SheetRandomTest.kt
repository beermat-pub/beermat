package pub.beermat.engine.eval

import kotlin.test.Test
import kotlin.test.assertEquals

/** [SheetRandom] gives what `java.util.Random` gave before it, so sheets keep their random values. */
class SheetRandomTest {

    @Test
    fun `the same numbers as java util Random`() {
        for (seed in listOf(1L, 0L, -7L, 123456789L)) {
            val ours = SheetRandom(seed)
            val java = java.util.Random(seed)
            repeat(1000) { k ->
                if (k % 3 == 2) assertEquals(java.nextGaussian(), ours.nextGaussian(), 1e-15, "seed $seed, number $k")
                else assertEquals(java.nextDouble(), ours.nextDouble(), "seed $seed, number $k")
            }
        }
    }
}
