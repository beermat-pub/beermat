package pub.beermat.format

import pub.beermat.engine.HostFile
import pub.beermat.engine.display.ResultFormat
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Shown
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.engine.units.Quantity

/**
 * The fresh outcome of one statement: what the annotation on its first [line] should be (null
 * for none). [statement] is its full text (a program spans several lines); [errorLine] is the
 * file line an error points at.
 */
data class LineOutcome(
    val line: CalcLine,
    val annotation: Annotation?,
    val statement: String = line.source.trim(),
    val errorLine: Int = line.number,
    /** The parsed statement, and the value it shows with the format it is shown in: for the app's 2D display. */
    val parsed: Statement? = null,
    val shown: Shown? = null,
    val format: ResultFormat = ResultFormat(),
    /** The variables an error message quotes (see `EvalException.names`), for the app to draw. */
    val errorNames: List<Id> = emptyList(),
)

/**
 * A worksheet evaluated: every statement in its `calc` fences, in file order (docs/file-format.md
 * explains why file order, for now), with the frontmatter's settings.
 */
class Calculation private constructor(
    val sheet: Worksheet,
    val settings: Settings,
    /** One per statement, in order. */
    val lines: List<LineOutcome>,
    /** Problems with settings, in the frontmatter or in block attributes. */
    val settingProblems: List<String>,
    /** One per plot fence, in order. */
    val plots: List<PlotOutcome> = emptyList(),
    /** One per table fence, in order. */
    val tables: List<TableOutcome> = emptyList(),
    /** The Python modules the sheet holds (`bt := use "file.py"`), by name, for completion and hover in the editor. */
    val modules: Map<pub.beermat.engine.syntax.Id, pub.beermat.engine.eval.Module> = emptyMap(),
) {
    val hasErrors: Boolean get() = lines.any { it.annotation is Annotation.Error }

    /** The sheet with these results cached in it and its `results:` hash updated. */
    fun written(): Worksheet {
        // By identity: two lines with the same text are still different lines.
        val fresh = identityMap<CalcLine, Annotation?>().apply { lines.forEach { put(it.line, it.annotation) } }
        val tableOutcomes = identityMap<TableBlock, TableOutcome>().apply { sheet.parts.filterIsInstance<TableBlock>().zip(tables).forEach { (b, o) -> put(b, o) } }
        val parts = sheet.parts.map { part ->
            when (part) {
                is CalcBlock -> part.copy(lines = part.lines.map { it.copy(annotation = fresh[it]) })
                // A table's expression cells get their values after the arrows.
                is TableBlock -> tableOutcomes[part]?.let { part.copy(body = Table.write(it.table, it.shown)) } ?: part
                else -> part
            }
        }
        val updated = sheet.copy(parts = parts)
        return WorksheetText.withHash(updated, WorksheetText.inputHash(updated))
    }

    companion object {
        /** [sheet] evaluated; [folder] is where its data files are read from (the sheet's folder, if it has one). */
        fun of(sheet: Worksheet, folder: HostFile? = null): Calculation {
            val settings = Settings.of(sheet)
            val problems = settings.problems.toMutableList()
            // The built-ins, with READEXCEL (here, not in the engine: it needs POI) and the sheet's TOL and ORIGIN.
            val builtins = Builtins.scope.withAll(platformFunctions.associateBy { Id(it.name) })
                .with(Id("TOL"), Scalar(Quantity(settings.tolerance)))
                .with(Id("ORIGIN"), Scalar(Quantity(settings.origin.toDouble())))

            // Parse everything first: the evaluator runs the sheet as one list of statements.
            // A statement with a syntax error stays in the list as Unparsed, so that later uses of
            // the name it defines report it.
            class Parsed(val lines: List<CalcLine>, val block: CalcBlock, val statement: Statement)
            val parsed = mutableListOf<Parsed>()
            // Each plot sees the statements above it: how many there are.
            val plotPositions = mutableListOf<Pair<PlotBlock, Int>>()
            // Tables run between statements (Evaluator.runWithScopes' steps), defining what they name.
            val tablePositions = mutableListOf<Pair<TableBlock, Int>>()
            for (part in sheet.parts) {
                when (part) {
                    is CalcBlock -> statements(part).forEach { lines ->
                        val indent = lines[0].source.takeWhile { it == ' ' }.length
                        parsed += Parsed(lines, part, Parser.parseOrKeep(lines.joinToString("\n") { it.source.drop(indent) }))
                    }
                    is PlotBlock -> plotPositions += part to parsed.size
                    is TableBlock -> tablePositions += part to parsed.size
                    is Markdown -> {}
                }
            }
            val evaluator = Evaluator(builtins, symbolic = pub.beermat.symbolic.Symbolics, folder = folder, modules = PythonConsent.modules)
            val tableOutcomes = arrayOfNulls<TableOutcome>(tablePositions.size)
            val steps = tablePositions.withIndex().groupBy({ it.value.second }) { (k, p) ->
                { scope: pub.beermat.engine.eval.Scope ->
                    val block = p.first
                    val o = Tables.evaluate(block.table, block.attributes["name"], scope, evaluator, settings.formatFor(block.attributes, problems))
                    tableOutcomes[k] = o
                    scope.withAll(o.defines)
                }
            }
            // A disabled region ("Disable evaluation") is shown but not evaluated: in its place the evaluator
            // gets a bare number, which defines nothing and shows nothing, so every statement keeps its position.
            val (outcomes, scopes) = evaluator.runWithScopes(parsed.map { if (it.block.attributes.has("disabled")) ExprStatement(pub.beermat.engine.syntax.Num("0")) else it.statement }, steps)
            val plots = plotPositions.map { (block, k) -> Plots.evaluate(block, scopes[k], evaluator) }
            val formats = sheet.blocks.associateWith { settings.formatFor(it.attributes, problems) }

            val lines = parsed.zip(outcomes) { p, o ->
                val annotation = when (o) {
                    is Outcome.Failed -> Annotation.Error(oneLine(o.error.message ?: "error"))
                    is Outcome.Evaluated -> Annotation.Result(ResultText.format(o.shown, formats.getValue(p.block)))
                    is Outcome.Defined -> o.shown?.let { Annotation.Result(ResultText.format(it, formats.getValue(p.block))) }
                    Outcome.NotEvaluated -> null
                    is Outcome.Symbolic -> Annotation.Result(pub.beermat.engine.syntax.Printer.print(o.result))
                }
                val errorLine = p.lines[minOf((p.statement as? Unparsed)?.line ?: 0, p.lines.size - 1)].number
                val shown = when (o) {
                    is Outcome.Evaluated -> o.shown
                    is Outcome.Defined -> o.shown
                    else -> null
                }
                val text = p.lines.joinToString("\n") { it.source }.trimIndent()
                val names = (o as? Outcome.Failed)?.error?.names.orEmpty()
                LineOutcome(p.lines[0], annotation, text, errorLine, p.statement, shown, formats.getValue(p.block), names)
            }
            val modules = outcomes.filterIsInstance<Outcome.Defined>().filter { it.value is pub.beermat.engine.eval.Module }.associate { it.id to it.value as pub.beermat.engine.eval.Module }
            return Calculation(sheet, settings, lines, problems.distinct(), plots, tableOutcomes.map { it!! }, modules)
        }

        /**
         * The statements of a block, each a list of lines: a line and the lines below it that are
         * indented further (a program). A blank line ends a statement.
         */
        fun statements(block: CalcBlock): List<List<CalcLine>> {
            val out = mutableListOf<MutableList<CalcLine>>()
            var headIndent = -1
            for (line in block.lines) {
                val indent = line.source.takeWhile { it == ' ' || it == '\t' }.length
                when {
                    line.isBlank -> headIndent = -1
                    headIndent >= 0 && indent > headIndent -> out.last() += line
                    else -> {
                        out += mutableListOf(line)
                        headIndent = indent
                    }
                }
            }
            return out
        }

        private fun oneLine(s: String) = s.lineSequence().joinToString(" ") { it.trim() }
    }
}

/** A problem `beermat check` reports; [line] is the 1-based file line, if there is one. */
data class Problem(val line: Int?, val message: String)

/** What `beermat check` finds (docs/cli.md). */
object Check {
    fun of(sheet: Worksheet, folder: HostFile? = null): List<Problem> {
        val calc = Calculation.of(sheet, folder)
        val problems = calc.settingProblems.map { Problem(null, it) }.toMutableList()
        calc.lines.forEach { if (it.annotation is Annotation.Error) problems += Problem(it.errorLine, it.annotation.message) }
        calc.plots.forEach { p -> p.problems.forEach { problems += Problem(null, "plot${p.block.attributes.id?.let { " #$it" } ?: ""}: $it") } }
        calc.sheet.parts.filterIsInstance<TableBlock>().zip(calc.tables).forEach { (b, t) -> t.problems.forEach { problems += Problem(null, "table${b.attributes.id?.let { " #$it" } ?: ""}: $it") } }

        val cached = sheet.blocks.flatMap { b -> b.lines.filterNot { it.isBlank } }
        val hasCache = sheet.resultsHash != null || cached.any { it.annotation != null }
        if (!hasCache) return problems
        if (sheet.resultsHash != WorksheetText.inputHash(sheet)) {
            problems += Problem(null, "cached results are stale: the sheet changed after they were written (run 'beermat eval --write')")
        } else {
            calc.lines.forEach { (line, fresh) ->
                if (line.annotation != fresh) {
                    problems += Problem(line.number, "cached ${show(line.annotation)}, but the sheet now gives ${show(fresh)}")
                }
            }
        }
        return problems
    }

    private fun show(a: Annotation?) = when (a) {
        null -> "nothing"
        is Annotation.Result -> "'#= ${a.text}'"
        is Annotation.Error -> "'#! ${a.message}'"
    }
}
