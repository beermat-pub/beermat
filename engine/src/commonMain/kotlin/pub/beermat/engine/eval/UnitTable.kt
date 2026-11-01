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
}
