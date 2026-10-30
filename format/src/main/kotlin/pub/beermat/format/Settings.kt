package pub.beermat.format

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import pub.beermat.engine.display.ResultFormat
import pub.beermat.engine.display.Rounding
import pub.beermat.engine.syntax.Multiplication

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
            "exponential_threshold", "zero_threshold", "complex_threshold",
        )

        /** Known keys that don't affect evaluation yet. */
        private val passive = setOf("beermat", "page", "results")

        fun of(sheet: Worksheet): Settings {
            val lines = sheet.frontmatter ?: return Settings()
            val yaml = try {
                Load(LoadSettings.builder().build()).loadFromString(lines.joinToString("\n"))
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
                    "origin" -> number(value)?.takeIf { it == Math.floor(it) }?.let { origin = it.toInt() }
                        ?: run { problems += "origin: expected a whole number, not '$value'" }
                    "units" -> if (value != "SI") problems += "units: '$value' is not supported yet; results are shown in SI"
                    "multiplication" -> Multiplication.entries.firstOrNull { it.key == value }?.let { multiplication = it }
                        ?: run { problems += "multiplication: expected dot or space, not '$value'" }
                    "title" -> title = value?.toString()
                    "header" -> header = PageText.of(value, "header", problems)
                    "footer" -> footer = PageText.of(value, "footer", problems)
                    in passive -> {}
                    else -> problems += "unknown setting: $key"
                }
            }
            return Settings(format, tolerance, origin, multiplication, problems, title, header, footer)
        }

        /** Applies `result.*` style [values] (from YAML or from attributes) on top of [base]. */
        internal fun resultFormat(base: ResultFormat, values: Map<String, Any?>, prefix: String, problems: MutableList<String>): ResultFormat {
            fun int(key: String, min: Int): Int? {
                if (key !in values) return null
                val n = number(values[key])
                if (n == null || n != Math.floor(n) || n < min) {
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
            )
        }

        private fun number(v: Any?): Double? = when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }
}
