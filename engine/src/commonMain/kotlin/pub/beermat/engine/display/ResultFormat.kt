package pub.beermat.engine.display

/** How results are rounded and shown: the `result.*` settings of docs/results.md. */
data class ResultFormat(
    val rounding: Rounding = Rounding.Decimals(3),
    /** Numbers of 10^n or more, or below 10^-n, are shown with an exponent. The usual default is 3. */
    val exponentialThreshold: Int = 6,
    /** Values below 10^-n show as 0. */
    val zeroThreshold: Int = 15,
    /** In a complex number, a part smaller than 10^-n of the other part is hidden. */
    val complexThreshold: Int = 10,
    /** Engineering prefixes on a unit no one asked for: `54 kN*m` rather than `54000 N*m` (docs/units.md#display). */
    val prefixes: Boolean = true,
)

sealed interface Rounding {
    /** A fixed number of decimal places, trailing zeros dropped unless asked for. */
    data class Decimals(val places: Int, val trailingZeros: Boolean = false) : Rounding {
        init {
            require(places >= 0) { "decimal places must not be negative" }
        }
    }

    /**
     * Significant figures, trailing zeros kept. Unless [strict], digits left of the decimal
     * point are never rounded away (1234.567 to 3 figures is 1235, strictly 1230).
     */
    data class Significant(val figures: Int, val strict: Boolean = false) : Rounding {
        init {
            require(figures >= 1) { "at least one significant figure" }
        }
    }
}
