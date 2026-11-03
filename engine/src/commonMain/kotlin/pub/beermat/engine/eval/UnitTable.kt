package pub.beermat.engine.eval

import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Parser

/** Loads the built-in unit table, a worksheet written in the linear syntax (docs/units.md). */
internal object UnitTable {
    fun load(scope: Scope, baseUnits: Map<Id, Value>): Map<Id, Value> {
        val lines = calcLines(pub.beermat.engine.UNITS_BBM)
        val outcomes = Evaluator(scope, baseUnits).run(lines.map { Parser.parseStatement(it) })
        return lines.zip(outcomes).associate { (line, outcome) ->
            when (outcome) {
                // Each unit its own object, so a sheet's `°C := Δ°C` is told apart from the built-in `°C` (both are K).
                is Outcome.Defined -> outcome.id to ((outcome.value as? Scalar)?.copy() ?: outcome.value)
                is Outcome.Failed -> error("units.bbm: '$line': ${outcome.error.message}")
                else -> error("units.bbm: '$line' is not a definition")
            }
        }
    }

    /** The non-blank lines inside ```` ```calc ```` fences. The engine cannot use the `format` module's reader, which depends on it. */
    fun calcLines(text: String): List<String> {
        val out = mutableListOf<String>()
        var inCalc = false
        for (line in text.lines()) {
            val trimmed = line.trim()
            when {
                !inCalc && trimmed.startsWith("```calc") -> inCalc = true
                inCalc && trimmed == "```" -> inCalc = false
                inCalc && trimmed.isNotEmpty() -> out += trimmed
            }
        }
        return out
    }

    /** The table's sections, for the manual's reference (docs/manual.md#what-generates-itself). */
    val sections: List<UnitSection> by lazy {
        val out = mutableListOf<UnitSection>()
        var inCalc = false
        for (line in pub.beermat.engine.UNITS_BBM.lines()) {
            val trimmed = line.trim()
            val last = out.lastOrNull()
            when {
                trimmed.startsWith("## ") -> out += UnitSection(trimmed.removePrefix("## "), "", emptyList())
                last == null -> {}
                !inCalc && trimmed.startsWith("```calc") -> inCalc = true
                inCalc && trimmed == "```" -> inCalc = false
                inCalc && trimmed.isNotEmpty() -> out[out.lastIndex] = last.copy(definitions = last.definitions + trimmed)
                !inCalc && last.definitions.isEmpty() -> out[out.lastIndex] = last.copy(text = (last.text + " " + trimmed).trim())
            }
        }
        out
    }
}

/** A section of the built-in unit table: its heading, the text below it, and its definitions as written (`kN := 1000 N`). */
data class UnitSection(val title: String, val text: String, val definitions: List<String>)

/** The built-in units, as the manual lists them. */
object BuiltinUnits {
    val sections: List<UnitSection> get() = UnitTable.sections
}
