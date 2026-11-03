package pub.beermat.format

import it.krzeminski.snakeyaml.engine.kmp.api.Load
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.exceptions.YamlEngineException
import kotlin.math.floor
import pub.beermat.engine.display.ResultFormat
import pub.beermat.engine.display.Rounding
import pub.beermat.engine.eval.UnitOptions
import pub.beermat.engine.syntax.Multiplication

/** A frontmatter setting in the manual: [key] (`result.decimals`), its default, and what it does. */
data class SettingDoc(val key: String, val default: String, val meaning: String)

/**
 * The frontmatter settings that evaluation and display use (docs/file-format.md#frontmatter).
 * Settings that are invalid or not supported yet are listed in [problems] and left at their defaults.
 */
data class Settings(
    val format: ResultFormat = ResultFormat(),
    val tolerance: Double = 1e-3,
    /** The initial `ORIGIN`, the first index of arrays. */
    val origin: Int = 0,
    /** How `*` is drawn: a dot, or a thin space (docs/syntax.md#multiplication-style). */
    val multiplication: Multiplication = Multiplication.DOT,
    val problems: List<String> = emptyList(),
    /** The sheet's title, and the text printed in the top and bottom margins of each page (docs/file-format.md#frontmatter). */
    val title: String? = null,
    val header: PageText? = null,
    val footer: PageText? = null,
    /** Angle and currency as dimensions (`angle: dimension`, `currency: AUD`; docs/units.md#angle-and-currency-as-dimensions). */
    val units: UnitOptions = UnitOptions(),
    /** Printing leaves out what is past the right margin (`single_page_width: true`; docs/editor.md#printing). */
    val singlePageWidth: Boolean = false,
) {
    /** The result format for a block, after its attribute overrides (`decimals=2`, `significant=3`, `strict`, …). */
    fun formatFor(attributes: Attributes, problems: MutableList<String>): ResultFormat {
        val values: Map<String, Any?> = attributes.values.filterKeys { it in resultKeys }
            .mapValues { (_, v) -> v ?: true }
        return resultFormat(format, values, "", problems)
    }

    companion object {
        private val resultKeys = setOf(
            "decimals", "significant", "strict", "trailing_zeros",
            "exponential_threshold", "zero_threshold", "complex_threshold", "prefixes",
        )

        /**
         * Every setting, for the manual's reference (docs/manual.md#what-generates-itself): its key,
         * its default and what it does. A test checks that each is read without a problem.
         */
        val reference: List<SettingDoc> = listOf(
            SettingDoc("title", "the file's name", "The worksheet's title, `{title}` in a header or footer."),
            SettingDoc("page", "`A4`", "The page size, `A4` or `letter`, with ¾ inch margins."),
            SettingDoc("header", "none", "Text in the top margin of every page: `header: Calculation 12` (centred), or `header: {left: \"{title}\", centre: …, right: \"Page {page} of {pages}\"}`. The fields are `{page}`, `{pages}`, `{title}` and `{date}`."),
            SettingDoc("footer", "none", "Text in the bottom margin of every page, as `header`."),
            SettingDoc("single_page_width", "`false`", "`true`: printing and PDF export leave out what is past the right margin, rather than printing it on extra pages (File → Print single page width)."),
            SettingDoc("result.decimals", "`3`", "Decimal places shown in results."),
            SettingDoc("result.significant", "none", "Significant figures shown instead of decimal places."),
            SettingDoc("result.strict", "`false`", "With `significant`: round digits left of the decimal point too (1234.567 to 3 figures is `1230`, not `1235`)."),
            SettingDoc("result.trailing_zeros", "`false`", "With `decimals`: pad results to the full number of places."),
            SettingDoc("result.exponential_threshold", "`6`", "Numbers of 10ⁿ or more, or below 10⁻ⁿ, are shown with an exponent."),
            SettingDoc("result.zero_threshold", "`15`", "Values below 10⁻ⁿ show as 0."),
            SettingDoc("result.complex_threshold", "`10`", "A real or imaginary part smaller than 10⁻ⁿ of the other is hidden."),
            SettingDoc("result.prefixes", "`true`", "Engineering prefixes on results with no unit asked for (`54 kN·m`); `false` turns them off."),
            SettingDoc("multiplication", "`dot`", "How `*` is drawn: `dot` (a·b) or `space` (a b) (Format → Multiplication)."),
            SettingDoc("origin", "`0`", "The first index of arrays, the starting value of `ORIGIN`."),
            SettingDoc("tolerance", "`1e-3`", "The starting value of `TOL`, used by the solvers and by comparisons (`==`, `<`, …)."),
            SettingDoc("angle", "`none`", "`dimension` makes angle a dimension of its own, with `rad` its unit (Format → Units)."),
            SettingDoc("angle_display", "`deg`", "With `angle: dimension`, how an angle shows when no unit is asked for: `deg` or `rad`."),
            SettingDoc("currency", "none", "A currency dimension whose unit has this name, such as `AUD` or `€` (Format → Units)."),
            SettingDoc("beermat", "`1`", "The version of the file format, written by Beermat."),
            SettingDoc("results", "", "A fingerprint of the sheet when its results were saved, written by Beermat, so that results left over from an older version of the sheet aren't shown."),
        )

        /** Known keys that don't affect evaluation yet. */
        private val passive = setOf("beermat", "page", "results")

        fun of(sheet: Worksheet): Settings {
            val lines = sheet.frontmatter ?: return Settings()
            val yaml = try {
                Load(LoadSettings()).loadOne(lines.joinToString("\n"))
            } catch (e: YamlEngineException) {
                return Settings(problems = listOf("frontmatter is not valid YAML: ${e.message?.lineSequence()?.first()}"))
            }
            if (yaml == null) return Settings()
            if (yaml !is Map<*, *>) return Settings(problems = listOf("frontmatter is not a set of key: value settings"))
            val problems = mutableListOf<String>()
            var format = ResultFormat()
            var tolerance = 1e-3
            var origin = 0
            var multiplication = Multiplication.DOT
            var title: String? = null
            var header: PageText? = null
            var footer: PageText? = null
            var units = UnitOptions()
            var angleInDegrees = true
            var singlePageWidth = false
            for ((key, value) in yaml) {
                when (key) {
                    "result" ->
                        if (value is Map<*, *>) {
                            val values = value.entries.associate { (k, v) -> k.toString() to v }
                            values.keys.filter { it !in resultKeys }.forEach { problems += "unknown setting: result.$it" }
                            format = resultFormat(format, values.filterKeys { it in resultKeys }, "result.", problems)
                        } else {
                            problems += "result: expected settings such as 'decimals: 3' below it"
                        }
                    "tolerance" -> number(value)?.takeIf { it >= 0 }?.let { tolerance = it }
                        ?: run { problems += "tolerance: expected a non-negative number, not '$value'" }
                    "origin" -> number(value)?.takeIf { it == floor(it) }?.let { origin = it.toInt() }
                        ?: run { problems += "origin: expected a whole number, not '$value'" }
                    "units" -> if (value != "SI") problems += "units: '$value' is not supported yet; results are shown in SI"
                    "multiplication" -> Multiplication.entries.firstOrNull { it.key == value }?.let { multiplication = it }
                        ?: run { problems += "multiplication: expected dot or space, not '$value'" }
                    "angle" -> when (value) {
                        "dimension" -> units = units.copy(angle = true)
                        "none" -> units = units.copy(angle = false)
                        else -> problems += "angle: expected dimension or none, not '$value'"
                    }
                    "angle_display" -> when (value) {
                        "deg" -> angleInDegrees = true
                        "rad" -> angleInDegrees = false
                        else -> problems += "angle_display: expected deg or rad, not '$value'"
                    }
                    "currency" -> value?.toString()?.trim()?.takeIf { it.isNotEmpty() && '`' !in it && '\n' !in it }?.let { units = units.copy(currency = it) }
                        ?: run { problems += "currency: expected the name of a currency, such as AUD, not '$value'" }
                    "single_page_width" -> (value as? Boolean)?.let { singlePageWidth = it }
                        ?: run { problems += "single_page_width: expected true or false, not '$value'" }
                    "title" -> title = value?.toString()
                    "header" -> header = PageText.of(value, "header", problems)
                    "footer" -> footer = PageText.of(value, "footer", problems)
                    in passive -> {}
                    else -> problems += "unknown setting: $key"
                }
            }
            format = format.copy(angleInDegrees = angleInDegrees, currency = units.currency)
            return Settings(format, tolerance, origin, multiplication, problems, title, header, footer, units, singlePageWidth)
        }

        /** Applies `result.*` style [values] (from YAML or from attributes) on top of [base]. */
        internal fun resultFormat(base: ResultFormat, values: Map<String, Any?>, prefix: String, problems: MutableList<String>): ResultFormat {
            fun int(key: String, min: Int): Int? {
                if (key !in values) return null
                val n = number(values[key])
                if (n == null || n != floor(n) || n < min) {
                    problems += "$prefix$key: expected a whole number of at least $min, not '${values[key]}'"
                    return null
                }
                return n.toInt()
            }
            fun flag(key: String): Boolean? {
                if (key !in values) return null
                return when (val v = values[key]) {
                    is Boolean -> v
                    "true" -> true
                    "false" -> false
                    else -> null.also { problems += "$prefix$key: expected true or false, not '$v'" }
                }
            }
            val decimals = int("decimals", 0)
            val significant = int("significant", 1)
            val strict = flag("strict")
            val zeros = flag("trailing_zeros")
            val rounding = when {
                significant != null -> Rounding.Significant(significant, strict ?: false)
                decimals != null -> Rounding.Decimals(decimals, zeros ?: false)
                else -> when (val r = base.rounding) {
                    is Rounding.Significant -> r.copy(strict = strict ?: r.strict)
                    is Rounding.Decimals -> r.copy(trailingZeros = zeros ?: r.trailingZeros)
                }
            }
            return base.copy(
                rounding = rounding,
                exponentialThreshold = int("exponential_threshold", 0) ?: base.exponentialThreshold,
                zeroThreshold = int("zero_threshold", 0) ?: base.zeroThreshold,
                complexThreshold = int("complex_threshold", 0) ?: base.complexThreshold,
                prefixes = flag("prefixes") ?: base.prefixes,
            )
        }

        private fun number(v: Any?): Double? = when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }
}
