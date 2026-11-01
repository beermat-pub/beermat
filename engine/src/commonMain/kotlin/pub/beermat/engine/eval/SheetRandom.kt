package pub.beermat.engine.eval

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Random numbers for `rnd`, `runif`, `rnorm`…: the same generator as `java.util.Random` (a 48-bit
 * linear congruential generator, and the polar method for normal numbers), so every platform gives
 * the same values from the same seed, and sheets keep the values they had on the desktop.
 */
class SheetRandom(seed: Long) {
    private var state = (seed xor MULTIPLIER) and MASK
    private var nextGaussian: Double? = null

    private fun next(bits: Int): Int {
        state = (state * MULTIPLIER + ADDEND) and MASK
        return (state ushr (48 - bits)).toInt()
    }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double = ((next(26).toLong() shl 27) + next(27)) * DOUBLE_UNIT

    /** Normal, mean 0 and standard deviation 1. */
    fun nextGaussian(): Double {
        nextGaussian?.let { nextGaussian = null; return it }
        var v1: Double
        var v2: Double
        var s: Double
        do {
            v1 = 2 * nextDouble() - 1
            v2 = 2 * nextDouble() - 1
            s = v1 * v1 + v2 * v2
        } while (s >= 1 || s == 0.0)
        val multiplier = sqrt(-2 * ln(s) / s)
        nextGaussian = v2 * multiplier
        return v1 * multiplier
    }

    private companion object {
        const val MULTIPLIER = 0x5DEECE66DL
        const val ADDEND = 0xBL
        const val MASK = (1L shl 48) - 1
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
    }
}
