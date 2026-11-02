package pub.beermat.engine.units

/**
 * The seven SI base dimensions, in the order used by [Dimension], and the two a sheet can turn on
 * (docs/units.md#angle-and-currency-as-dimensions): angle and currency.
 */
enum class BaseDimension(val symbol: String) {
    LENGTH("L"),
    MASS("M"),
    TIME("T"),
    CURRENT("I"),
    TEMPERATURE("Θ"),
    AMOUNT("N"),
    LUMINOSITY("J"),
    ANGLE("∠"),
    CURRENCY("¤"),
}

/**
 * A physical dimension: one rational exponent per [BaseDimension].
 * Force is `M·L·T^-2`; a pure number is [NONE]. Immutable.
 */
class Dimension private constructor(private val exponents: List<Rational>) {

    operator fun get(base: BaseDimension): Rational = exponents[base.ordinal]

    val isNone: Boolean = exponents.all { it.isZero }

    // A pure number on either side keeps the other dimension as it is, so arrays of millions of
    // elements scaled by a number or a unit share one Dimension instead of one each.
    operator fun times(other: Dimension) = when {
        other.isNone -> this
        isNone -> other
        else -> Dimension(exponents.zip(other.exponents, Rational::plus))
    }

    operator fun div(other: Dimension) =
        if (other.isNone) this else Dimension(exponents.zip(other.exponents, Rational::minus))

    fun pow(n: Rational) = Dimension(exponents.map { it * n })

    fun pow(n: Int) = pow(n.toRational())

    override fun equals(other: Any?): Boolean = other is Dimension && exponents == other.exponents

    override fun hashCode(): Int = exponents.hashCode()

    /** For example `L·M·T^-2` or `L^(1/2)`; `1` when dimensionless. */
    override fun toString(): String {
        if (isNone) return "1"
        return BaseDimension.entries
            .filter { !this[it].isZero }
            .joinToString("·") { base ->
                val e = this[base]
                when {
                    e == Rational.ONE -> base.symbol
                    e.isInteger -> "${base.symbol}^$e"
                    else -> "${base.symbol}^($e)"
                }
            }
    }

    companion object {
        val NONE = Dimension(List(BaseDimension.entries.size) { Rational.ZERO })

        fun of(vararg exponents: Pair<BaseDimension, Int>): Dimension =
            Dimension(BaseDimension.entries.map { base ->
                exponents.filter { it.first == base }.sumOf { it.second }.toRational()
            })

        val LENGTH = of(BaseDimension.LENGTH to 1)
        val MASS = of(BaseDimension.MASS to 1)
        val TIME = of(BaseDimension.TIME to 1)
        val CURRENT = of(BaseDimension.CURRENT to 1)
        val TEMPERATURE = of(BaseDimension.TEMPERATURE to 1)
        val AMOUNT = of(BaseDimension.AMOUNT to 1)
        val LUMINOSITY = of(BaseDimension.LUMINOSITY to 1)
        val ANGLE = of(BaseDimension.ANGLE to 1)
        val CURRENCY = of(BaseDimension.CURRENCY to 1)
    }
}
