package pub.beermat.format

import pub.beermat.engine.display.ResultFormat
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.ArrayValue
import pub.beermat.engine.eval.Builtin
import pub.beermat.engine.eval.CellRange
import pub.beermat.engine.eval.EvalException
import pub.beermat.engine.eval.DisplayUnit
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Scope
import pub.beermat.engine.eval.Shown
import pub.beermat.engine.eval.Text
import pub.beermat.engine.eval.Value
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.units.Quantity

/** A column of a table: its header label, the name it is known by (if any), its unit and its alignment. */
data class Column(val label: String, val name: Id?, val unit: String?, val align: Align) {
    enum class Align { LEFT, RIGHT, CENTRE }

    /** The header cell as written: `q [kN/m]`. */
    val header: String get() = label + (unit?.let { " [$it]" } ?: "")
}

/**
 * A table region's grid (docs/tables.md): the columns, and the cells as written. A cell is a
 * number, text, or `= expression`; an expression's last value follows an arrow, `= q*L → 54`,
 * output like the `#=` of a statement.
 */
data class Table(val columns: List<Column>, val rows: List<List<String>>) {

    companion object {
        private val HEADER = Regex("""(.*?)\s*(?:\[(.+)])?\s*""")
        private val NAME = Regex("""\p{L}[\p{L}\p{N}']*(?:_[\p{L}\p{N}'_]+)?""")
        private const val ARROW = "→"

        /** The Markdown pipe table in [lines]. */
        fun parse(lines: List<String>): Table {
            val grid = lines.map { it.trim() }.filter { it.startsWith("|") || it.contains('|') }.map(::cells)
            if (grid.size < 2) return Table(emptyList(), emptyList())
            val header = grid[0]
            val aligns = grid[1].map {
                val t = it.trim()
                when {
                    t.startsWith(":") && t.endsWith(":") -> Column.Align.CENTRE
                    t.endsWith(":") -> Column.Align.RIGHT
                    else -> Column.Align.LEFT
                }
            }
            val columns = header.mapIndexed { k, h ->
                val m = HEADER.matchEntire(h)!!
                val label = m.groupValues[1].trim()
                val unit = m.groupValues[2].trim().ifEmpty { null }
                Column(label, if (NAME.matches(label)) parseId(label) else null, unit, aligns.getOrElse(k) { Column.Align.LEFT })
            }
            val rows = grid.drop(2).map { r -> List(columns.size) { r.getOrElse(it) { "" } } }
            return Table(columns, rows)
        }

        private fun parseId(label: String): Id? = try {
            Parser.parseExpression(label) as? Id
        } catch (_: ParseException) {
            null
        }

        private fun cells(line: String): List<String> {
            val t = line.trim().removePrefix("|").removeSuffix("|")
            // `\|` inside a cell is a literal bar.
            return t.split(Regex("""(?<!\\)\|""")).map { it.trim().replace("\\|", "|") }
        }

        /** An expression cell's expression, without the cached value: `= q*L → 54` is `q*L`. */
        fun expression(cell: String): String? = cell.trim().takeIf { it.startsWith("=") }?.drop(1)?.substringBefore(ARROW)?.trim()

        /** The table as Markdown, columns padded to line up, expression cells with [values] after the arrow. */
        fun write(table: Table, values: Map<Pair<Int, Int>, String> = emptyMap()): List<String> {
            if (table.columns.isEmpty()) return emptyList()
            val body = table.rows.mapIndexed { r, row ->
                row.mapIndexed { c, cell ->
                    val e = expression(cell)
                    val v = values[r to c]
                    (if (e != null) "= $e" + (v?.let { " $ARROW $it" } ?: "") else cell).replace("|", "\\|")
                }
            }
            val headers = table.columns.map { it.header }
            val widths = table.columns.indices.map { c -> maxOf(3, headers[c].length, body.maxOfOrNull { it[c].length } ?: 0) }
            fun line(cells: List<String>) = cells.mapIndexed { c, s ->
                if (table.columns[c].align == Column.Align.RIGHT) s.padStart(widths[c]) else s.padEnd(widths[c])
            }.joinToString(" | ", "| ", " |")
            val rule = table.columns.mapIndexed { c, col ->
                val w = widths[c]
                when (col.align) {
                    Column.Align.LEFT -> "-".repeat(w)
                    Column.Align.RIGHT -> "-".repeat(w - 1) + ":"
                    Column.Align.CENTRE -> ":" + "-".repeat(w - 2) + ":"
                }
            }.joinToString(" | ", "| ", " |")
            return listOf(line(headers), rule) + body.map(::line)
        }
    }
}

/** A table evaluated: what each expression cell shows, problems, and the values it defines for the sheet. */
data class TableOutcome(val table: Table, val shown: Map<Pair<Int, Int>, String>, val problems: List<String>, val defines: Map<Id, Value>)

/**
 * Evaluating a table in the scope where it stands (docs/tables.md#in-the-sheet). In a row, a
 * column's name means that row's value; in a cell, its own column's name means the whole column
 * (the other rows), as in a totals row: `= sum(q)`. A named table (`name=Loads`) defines each
 * named column as a vector, `q_Loads`.
 */
object Tables {

    fun evaluate(table: Table, name: String?, scope: Scope, evaluator: Evaluator, format: ResultFormat): TableOutcome {
        val problems = mutableListOf<String>()
        val shown = mutableMapOf<Pair<Int, Int>, String>()
        val units = table.columns.map { col ->
            col.unit?.let { u ->
                try {
                    evaluator.displayUnit(Parser.parseExpression(u), scope)
                } catch (e: Exception) {
                    problems += "column ${col.label}: the unit '$u' is not a unit (${e.message})"
                    null
                }
            }
        }
        // Input cells first: numbers in the column's unit, or text.
        val values = Array(table.rows.size) { r -> arrayOfNulls<Value>(table.columns.size) }
        table.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                if (Table.expression(cell) == null && cell.isNotBlank()) values[r][c] = input(cell, units[c], scope, evaluator)
            }
        }
        // Then expressions, each when a cell needs it (and in order otherwise): a cell can use any other
        // by its Excel name (`B5`, `B2:B6`), so they run in the order their references need.
        val state = Array(table.rows.size) { IntArray(table.columns.size) }
        val failed = mutableSetOf<Pair<Int, Int>>()
        fun value(r: Int, c: Int): Value {
            val e = Table.expression(table.rows[r][c]) ?: return values[r][c] ?: Scalar(Quantity(0.0))
            when (state[r][c]) {
                2 -> return values[r][c] ?: throw EvalException("${ref(r, c)} has an error")
                1 -> throw EvalException("a circular reference: ${ref(r, c)} uses its own value")
            }
            state[r][c] = 1
            try {
                val expr = Parser.parseExpression(ranges(e))
                val used = names(expr)
                var s = scope.with(Id("cells"), cells(table) { rr, cc -> value(rr, cc) })
                // A column's name: that row's value, or, in the cell's own column, the column's other rows.
                table.columns.forEachIndexed { k, col ->
                    val id = col.name ?: return@forEachIndexed
                    if (id !in used) return@forEachIndexed
                    s = s.with(id, if (k != c) value(r, k) else column(table.rows.indices.filter { it != r }.map { value(it, k) }) ?: return@forEachIndexed)
                }
                // A cell by its Excel name: row 1 is the header, the rows below it 2, 3, ….
                for (id in used) {
                    val (rr, cc) = cellAt(id) ?: continue
                    s = s.with(id, cellValue(table, rr, cc) { a, b -> value(a, b) })
                }
                val v = evaluator.eval(expr, s)
                values[r][c] = v
                shown[r to c] = display(v, units[c], table.columns[c].unit, format)
                return v
            } catch (ex: EvalException) {
                if (failed.add(r to c)) problems += "${ref(r, c)} (row ${r + 1}, ${table.columns[c].label}): ${ex.message}"
                throw ex
            } catch (ex: ParseException) {
                if (failed.add(r to c)) problems += "${ref(r, c)} (row ${r + 1}, ${table.columns[c].label}): ${ex.message}"
                throw EvalException(ex.message ?: "syntax error")
            } finally {
                state[r][c] = 2
            }
        }
        table.rows.indices.forEach { r -> table.columns.indices.forEach { c -> if (Table.expression(table.rows[r][c]) != null) runCatching { value(r, c) } } }
        val defines = mutableMapOf<Id, Value>()
        if (name != null) {
            table.columns.forEachIndexed { k, col ->
                val id = col.name ?: return@forEachIndexed
                column(values.map { it[k] })?.let { defines[Id(id.name, listOfNotNull(id.subscript, name).joinToString("_"), id.style)] = it }
            }
        }
        // The whole grid too, header as row 1, for `cells(Loads, "B2:B9")` in the sheet; an empty cell is 0, as in Excel.
        if (name != null) defines[Id(name)] = ArrayValue.of(table.rows.size + 1, table.columns.size) { r, c ->
            if (r == 0) Text(table.columns[c].label) else values[r - 1][c] ?: Scalar(Quantity(0.0))
        }
        return TableOutcome(table, shown, problems, defines)
    }

    /** A body cell's Excel name: the header is row 1, so body row 0 is row 2. */
    private fun ref(r: Int, c: Int) = CellRange.name(r + 1, c)

    /** `B2:B6` in a cell's expression is `cells("B2:B6")`, which the parser reads; `$B$5` is `B5`. */
    internal fun ranges(e: String): String = RANGE_REF.replace(e) { m -> "cells(\"${m.value.replace("$", "")}\")" }
        .replace(Regex("""\$([A-Z]{1,3})\$?([0-9]+)|([A-Z]{1,3})\$([0-9]+)""")) { m -> m.value.replace("$", "") }

    private val RANGE_REF = Regex("""(?<![\p{L}\p{N}_'.])\$?[A-Z]{1,3}\$?[0-9]{1,7}:\$?[A-Z]{1,3}\$?[0-9]{1,7}(?![\p{L}\p{N}])""")

    /** A plain name such as `B5`: the cell's (row, column) in Excel's terms (row 1 being the header); null for any other name. */
    private fun cellAt(id: Id): Pair<Int, Int>? =
        if (id.subscript == null && id.style == Id.Style.PLAIN && CELL_NAME.matches(id.name)) CellRange.cell(id.name) else null

    private val CELL_NAME = Regex("""[A-Z]{1,3}[0-9]{1,7}""")

    /** The cell at Excel's ([row], [col]), from 0: the header's label, or a body cell's value. */
    private fun cellValue(table: Table, row: Int, col: Int, value: (Int, Int) -> Value): Value {
        if (col !in table.columns.indices || row > table.rows.size) throw EvalException("${CellRange.name(row, col)} is outside the table (rows 1 to ${table.rows.size + 1}, columns A to ${CellRange.letters(table.columns.size - 1)})")
        return if (row == 0) Text(table.columns[col].label) else value(row - 1, col)
    }

    /** `cells("B2:B6")` inside the table; `cells(T, "B2:B6")` takes from another table's grid, as in the sheet. */
    private fun cells(table: Table, value: (Int, Int) -> Value) = Builtin("cells", 1..2) { a ->
        if (a.size == 2) return@Builtin CellRange.take(a[0]() as? ArrayValue ?: throw EvalException("cells takes a table and a range"), (a[1]() as Text).value)
        val text = (a[0]() as? Text)?.value ?: throw EvalException("cells: the range is a string, such as \"B2:B6\"")
        val range = CellRange.parse(text) ?: throw EvalException("cells: '$text' is not a range")
        val got = range.rows.map { r -> range.cols.map { c -> cellValue(table, r, c, value) } }
        if (got.size == 1 && got[0].size == 1) got[0][0] else ArrayValue.of(got.size, got[0].size) { i, j -> got[i][j] }
    }

    /** Every name in [e]. */
    private fun names(e: pub.beermat.engine.syntax.Expr): Set<Id> = when (e) {
        is Id -> setOf(e)
        is pub.beermat.engine.syntax.Apply -> e.args.flatMap(::names).toSet()
        is pub.beermat.engine.syntax.Call -> names(e.fn) + e.args.flatMap(::names)
        is pub.beermat.engine.syntax.Index -> names(e.target) + e.indices.flatMap(::names)
        is pub.beermat.engine.syntax.Parens -> names(e.inner)
        is pub.beermat.engine.syntax.Matrix -> e.rows.flatten().flatMap(::names).toSet()
        is pub.beermat.engine.syntax.Range -> names(e.first) + (e.second?.let(::names) ?: emptySet()) + names(e.last)
        else -> emptySet()
    }

    private fun input(cell: String, unit: DisplayUnit?, scope: Scope, evaluator: Evaluator): Value = try {
        val v = evaluator.eval(Parser.parseExpression(cell), scope)
        if (unit != null && v is Scalar && v.q.dimension.isNone) Scalar(unit.quantity(v.q.value)) else v
    } catch (_: Exception) {
        Text(cell)
    }

    /** The numbers of column [k] (all rows but [except]) as a column vector; null if it has none. */
    /** The numbers among [values] (a column) as a column vector; null if it has none. */
    private fun column(values: List<Value?>): Value? {
        val vs = values.mapNotNull { it as? Scalar }
        if (vs.isEmpty()) return null
        return ArrayValue.of(vs.size, 1) { r, _ -> vs[r] }
    }

    private fun display(v: Value, unit: DisplayUnit?, unitText: String?, format: ResultFormat): String {
        if (unit != null && v is Scalar && v.q.dimension == unit.one.dimension) {
            return ResultText.format(Shown(v, unit.one, Parser.parseExpression(unitText!!), unit.zero), format).let { it.removeSuffix(" " + Printer.print(Parser.parseExpression(unitText))) }
        }
        return ResultText.format(Shown(v), format)
    }

    private val Printer = pub.beermat.engine.syntax.Printer
}
