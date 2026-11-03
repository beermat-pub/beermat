package pub.beermat.format

import pub.beermat.engine.HostFile
import pub.beermat.engine.display.ResultFormat
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Shown
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Otherwise
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.ProgramLine
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.Return
import pub.beermat.engine.syntax.Symbolic
import pub.beermat.engine.syntax.While
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
    /**
     * A symbolic result as it is drawn: its units marked upright (`54000 \rm N*\rm m`), since every other
     * name in a symbolic statement is a variable. The [annotation] writes them plainly, as units are written.
     */
    val symbolic: String? = null,
    /**
     * A warning that isn't an error: a number written beside a name the sheet has defined that is also
     * a unit (`3 m` after `m := 5`), which is the variable, not the unit (docs/units.md). Shown on the
     * page and by `beermat check`; not written in the file.
     */
    val warning: String? = null,
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
            // With angle or currency as dimensions, their units and functions (docs/units.md#angle-and-currency-as-dimensions).
            val (sheetScope, units) = Builtins.forSheet(settings.units)
            val builtins = sheetScope.withAll(platformFunctions.associateBy { Id(it.name) })
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
            val evaluator = Evaluator(builtins, units, symbolic = pub.beermat.symbolic.Symbolics, folder = folder, modules = PythonConsent.modules)
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

            val hidden = hiddenUnits(parsed.map { it.statement })
            val lines = parsed.zip(outcomes).mapIndexed { k, (p, o) ->
                val annotation = when (o) {
                    is Outcome.Failed -> Annotation.Error(oneLine(o.error.message ?: "error"))
                    is Outcome.Evaluated -> Annotation.Result(ResultText.format(o.shown, formats.getValue(p.block)))
                    is Outcome.Defined -> o.shown?.let { Annotation.Result(ResultText.format(it, formats.getValue(p.block))) }
                    Outcome.NotEvaluated -> null
                    is Outcome.Symbolic -> Annotation.Result(plainUnits(pub.beermat.engine.syntax.Printer.print(o.result)))
                }
                val errorLine = p.lines[minOf((p.statement as? Unparsed)?.line ?: 0, p.lines.size - 1)].number
                val shown = when (o) {
                    is Outcome.Evaluated -> o.shown
                    is Outcome.Defined -> o.shown
                    else -> null
                }
                val text = p.lines.joinToString("\n") { it.source }.trimIndent()
                val names = (o as? Outcome.Failed)?.error?.names.orEmpty()
                val symbolic = (o as? Outcome.Symbolic)?.let { pub.beermat.engine.syntax.Printer.print(it.result) }
                val warning = unitWarning(p.statement, k, hidden)
                LineOutcome(p.lines[0], annotation, text, errorLine, p.statement, shown, formats.getValue(p.block), names, symbolic, warning)
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

        /** The built-in units the sheet defines as its own names, and the statement that first does. */
        private fun hiddenUnits(statements: List<Statement>): Map<Id, Int> = buildMap {
            statements.forEachIndexed { k, st ->
                val target = (st as? Define)?.target as? Id ?: return@forEachIndexed
                if (target !in this && Builtins.units.containsKey(target)) put(target, k)
            }
        }

        /** The warning for statement [k]: a number beside a name in [hidden] defined above it, written as a unit would be. */
        private fun unitWarning(st: Statement, k: Int, hidden: Map<Id, Int>): String? {
            if (hidden.isEmpty() || st is Symbolic) return null
            val found = mutableListOf<Id>()
            fun visit(e: Expr) {
                if (e is Apply && e.op == Op.MUL && e.implicit && unwrapNumber(e.args[0])) {
                    val id = e.args[1] as? Id
                    if (id != null && (hidden[id] ?: Int.MAX_VALUE) < k) found += id
                }
                exprChildren(e).forEach(::visit)
            }
            when (st) {
                is Define -> visit(st.value)
                is Evaluate -> visit(st.expr)
                is ExprStatement -> visit(st.expr)
                else -> {}
            }
            val id = found.firstOrNull() ?: return null
            return "${id.name} here is the variable defined above, not the unit ${id.name}: rename the variable to use the unit"
        }

        private fun unwrapNumber(e: Expr): Boolean = e is Num || (e is Apply && e.op == Op.NEG && e.args[0] is Num)

        private fun exprChildren(e: Expr): List<Expr> = when (e) {
            is Apply -> e.args
            is Call -> listOf(e.fn) + e.args
            is Parens -> listOf(e.inner)
            is Index -> listOf(e.target) + e.indices
            is Matrix -> e.rows.flatten()
            is Range -> listOfNotNull(e.first, e.second, e.last)
            is Program -> e.lines.flatMap(::lineExprs)
            else -> emptyList()
        }

        private fun lineExprs(l: ProgramLine): List<Expr> = when (l) {
            is Line -> listOf(l.expr)
            is LocalDefine -> listOf(l.value)
            is IfThen -> listOf(l.condition) + l.body.flatMap(::lineExprs)
            is Otherwise -> l.body.flatMap(::lineExprs)
            is For -> listOf(l.over) + l.body.flatMap(::lineExprs)
            is While -> listOf(l.condition) + l.body.flatMap(::lineExprs)
            is Return -> listOf(l.value)
            else -> emptyList()
        }

        /** A symbolic result's text with its units written as units are (`N*m`), not as the upright names it draws them with. */
        fun plainUnits(text: String): String = Regex("""\\rm ([\p{L}°Ω]+)""").replace(text) { m ->
            val name = m.groupValues[1]
            if (pub.beermat.engine.eval.Builtins.units.containsKey(Id(name))) name else m.value
        }
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
        calc.lines.forEach { o -> o.warning?.let { problems += Problem(o.line.number, "warning: $it") } }
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
