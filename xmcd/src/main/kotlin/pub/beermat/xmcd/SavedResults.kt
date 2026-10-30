package pub.beermat.xmcd

import org.w3c.dom.Element
import kotlin.math.PI
import kotlin.math.pow

/**
 * The results the original worksheet last showed, saved in an `.xmcd` (`<result>` under an `ml:eval`): read in SI,
 * so the import can be checked against them (docs/xmcd.md). The program that wrote the file
 * writes a value in the unit it showed, by the unit's full name.
 */
internal object SavedResults {

    /** The values in SI, element by element (a matrix column by column, as the file stores it); null when they can't be read. */
    /** The numbers as the original worksheet showed them; with [raw], without turning them into SI. */
    fun read(result: Element, raw: Boolean = false): DoubleArray? {
        val united = children(result).firstOrNull() ?: return null
        val (value, monomial) = if (united.localName == "unitedValue") children(united).let { it.firstOrNull() to it.getOrNull(1) } else united to null
        val numbers = when (value?.localName) {
            "real" -> listOf(value.textContent.trim().toDoubleOrNull() ?: return null)
            "matrix" -> children(value).map { if (it.localName == "real") it.textContent.trim().toDoubleOrNull() ?: return null else return null }
            else -> return null
        }
        val (factor, offset) = if (raw) 1.0 to 0.0 else monomial?.let(::unit) ?: (1.0 to 0.0)
        return numbers.map { it * factor + offset }.toDoubleArray()
    }

    /**
     * Whether the result is shown on a temperature scale (°C or °F alone). The file names the unit `degC`
     * even where the sheet redefined `°C` as a plain degree, so such a result is compared as shown, not in SI.
     */
    fun onScale(result: Element): Boolean {
        val united = children(result).firstOrNull()?.takeIf { it.localName == "unitedValue" } ?: return false
        val refs = children(children(united).getOrNull(1) ?: return false).filter { it.localName == "unitReference" }
        return refs.singleOrNull()?.getAttribute("unit") in setOf("degC", "degF")
    }

    /** A unit monomial's SI factor, and the offset of a temperature scale (only for °C on its own). */
    private fun unit(monomial: Element): Pair<Double, Double>? {
        val refs = children(monomial).filter { it.localName == "unitReference" }
        var factor = 1.0
        for (r in refs) {
            val (f, _) = UNITS[r.getAttribute("unit")] ?: return null
            val n = r.getAttribute("power-numerator").toDoubleOrNull() ?: 1.0
            val d = r.getAttribute("power-denominator").toDoubleOrNull() ?: 1.0
            factor *= f.pow(n / d)
        }
        val offset = refs.singleOrNull()?.takeIf { it.getAttribute("power-numerator").isEmpty() }?.let { UNITS[it.getAttribute("unit")]?.second } ?: 0.0
        return factor to offset
    }

    private fun children(e: Element) = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }

    private const val LBF = 4.4482216152605
    private const val PSI = 6894.757293168361

    /** The unit names, as `.xmcd` files write them: the SI factor, and a temperature scale's offset. */
    private val UNITS: Map<String, Pair<Double, Double>> = mapOf(
        "meter" to 1.0, "millimeter" to 1e-3, "centimeter" to 1e-2, "kilometer" to 1e3, "micron" to 1e-6,
        "inch" to 0.0254, "foot" to 0.3048, "mile" to 1609.344,
        "kilogram" to 1.0, "gram" to 1e-3, "metric_ton" to 1000.0, "pound" to 0.45359237, "ton" to 907.18474,
        "second" to 1.0, "minute" to 60.0, "hour" to 3600.0, "day" to 86400.0, "year" to 365.2422 * 86400,
        "newton" to 1.0, "kilonewton" to 1e3, "meganewton" to 1e6, "giganewton" to 1e9,
        "pound_force" to LBF, "kip" to 1000 * LBF, "kilogram_force" to 9.80665, "tonne_force" to 9806.65,
        "pascal" to 1.0, "kilopascal" to 1e3, "megapascal" to 1e6, "gigapascal" to 1e9, "bar" to 1e5,
        "pounds_per_sq_inch" to PSI, "kips_per_sq_inch" to 1000 * PSI, "pounds_per_cubic_inch" to 0.45359237 / 0.0254.pow(3),
        "joule" to 1.0, "liter" to 1e-3, "hertz" to 1.0, "radian" to 1.0, "degree" to PI / 180,
        "miles_per_hour" to 0.44704, "kilometers_per_hour" to 1 / 3.6,
        "ampere" to 1.0, "milliampere" to 1e-3, "coulomb" to 1.0, "volt" to 1.0, "ohm" to 1.0, "farad" to 1.0,
        "kelvin" to 1.0, "deltaC" to 1.0,
    ).mapValues { it.value to 0.0 } + ("degC" to (1.0 to 273.15))
}
