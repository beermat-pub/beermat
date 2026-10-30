package pub.beermat.xmcd

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.poifs.filesystem.DocumentInputStream
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellReference
import org.w3c.dom.Element
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Str
import pub.beermat.format.Table

/**
 * An Excel component of an `.xmcd` worksheet (docs/xmcd.md#excel-components). Its binary item
 * holds two OLE compound files: the worksheet's own (a `Contents` stream listing the cells of the
 * inputs, then the ranges of the outputs) and the embedded workbook (a classic `.xls`, read with
 * Apache POI). The outputs come out as the values Excel last calculated.
 */
internal object ExcelComponent {

    init {
        // POI logs through Log4j, which complains on first use when no logging is set up: a silent logger instead (as format's quietPoi).
        if (System.getProperty("log4j2.loggerContextFactory") == null) {
            System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
            System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF")
        }
    }

    /** Each output's value in linear syntax (a number, a string, or a matrix), or null if the item can't be read. */
    fun outputs(component: Element, item: ByteArray?, count: Int): List<String>? {
        if (item == null || count == 0) return null
        val containers = (0 until item.size - 8).filter { k -> OLE.indices.all { item[k + it] == OLE[it] } }
            .mapNotNull { k -> runCatching { POIFSFileSystem(item.copyOfRange(k, item.size).inputStream()) }.getOrNull() }
        val contents = containers.firstNotNullOfOrNull { fs -> runCatching { DocumentInputStream(fs.root.getEntry("Contents") as org.apache.poi.poifs.filesystem.DocumentEntry).readAllBytes() }.getOrNull() } ?: return null
        val workbook = containers.firstNotNullOfOrNull { fs -> runCatching { HSSFWorkbook(fs.root, true) }.getOrNull() } ?: return null
        fun children(e: Element) = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }
        // Entries for the inputs: one per element of an input written as a matrix, one for any other.
        val inputs = children(component).firstOrNull { it.localName == "inputs" }?.let(::children).orEmpty().sumOf { e ->
            if (e.localName == "matrix") (e.getAttribute("rows").toIntOrNull() ?: 1) * (e.getAttribute("cols").toIntOrNull() ?: 1) else 1
        }
        val ranges = RANGE.findAll(text(contents)).map { it.value }.toList()
        if (ranges.size < inputs + count) return null
        return ranges.drop(inputs).take(count).map { value(workbook, it) ?: return null }
    }

    /**
     * The component as a Beermat table (docs/tables.md#import-from-mcdx-and-xmcd):
     * its [table] lines (a Markdown pipe table, without the fence), the [definitions] of its outputs, and
     * [warnings] naming the cells that keep Excel's last value.
     */
    class Converted(val table: List<String>, val definitions: List<String>, val warnings: List<String>)

    /**
     * The component's sheet as a table named [name]: its used cells (and the inputs' and outputs'),
     * the first row as the header when it is all text. Input cells take the sheet's values (`= D`);
     * formulas become expressions where they only use cells of their own row (by the column's name)
     * or whole columns (`SUM(B2:B9)` as `sum(q)`); others keep the value Excel last calculated.
     * An output covering a whole numeric column is defined from it (`q_Name`), so it follows the
     * table; any other from its values. Null when the item can't be read, or the sheet is too big for a table.
     */
    fun convert(component: Element, item: ByteArray?, inputs: List<pub.beermat.engine.syntax.Expr>, outputs: List<String>, name: String): Converted? {
        if (item == null) return null
        val containers = (0 until item.size - 8).filter { k -> OLE.indices.all { item[k + it] == OLE[it] } }
            .mapNotNull { k -> runCatching { POIFSFileSystem(item.copyOfRange(k, item.size).inputStream()) }.getOrNull() }
        val contents = containers.firstNotNullOfOrNull { fs -> runCatching { DocumentInputStream(fs.root.getEntry("Contents") as org.apache.poi.poifs.filesystem.DocumentEntry).readAllBytes() }.getOrNull() } ?: return null
        val workbook = containers.firstNotNullOfOrNull { fs -> runCatching { HSSFWorkbook(fs.root, true) }.getOrNull() } ?: return null
        val ranges = RANGE.findAll(text(contents)).map { it.value }.toList()
        /** Every (row, column) of a range such as `b2:b21` or `C2`. */
        fun cells(range: String): List<Pair<Int, Int>> {
            val ends = range.substringAfter('!').split(':').map { CellReference(it.uppercase()) }
            val (a, b) = ends.first() to ends.last()
            return (minOf(a.row, b.row)..maxOf(a.row, b.row)).flatMap { r -> (minOf(a.col.toInt(), b.col.toInt())..maxOf(a.col.toInt(), b.col.toInt())).map { c -> r to c } }
        }
        // The file lists an input written as a matrix cell by cell, one entry per element; any other input, and each output, has one entry.
        val inputTargets = mutableListOf<List<Pair<Int, Int>>>()
        var next = 0
        for (e in inputs) {
            val n = (e as? pub.beermat.engine.syntax.Matrix)?.rows?.flatten()?.size ?: 1
            if (next + n > ranges.size) return null
            inputTargets += ranges.subList(next, next + n).flatMap(::cells)
            next += n
        }
        if (next + outputs.size > ranges.size) return null
        val outRanges = ranges.subList(next, next + outputs.size)
        val sheet = workbook.getSheetAt(0)
        // An input fills its cells: one value, or a vector over a range (element by element when written as a matrix).
        val inputAt = mutableMapOf<Pair<Int, Int>, String>()
        // An input that can't be written in a cell (a program) leaves the component to its values.
        if (inputs.any { e -> runCatching { Printer.print(e) }.isFailure }) return null
        inputTargets.zip(inputs).forEach { (targets, e) ->
            val elements = (e as? pub.beermat.engine.syntax.Matrix)?.rows?.flatten()
            targets.forEachIndexed { k, at ->
                inputAt[at] = when {
                    targets.size == 1 -> Printer.print(e)
                    elements != null && elements.size == targets.size -> Printer.print(elements[k])
                    else -> "(${Printer.print(e)})[ORIGIN + $k]"
                }
            }
        }
        val used = mutableSetOf<Pair<Int, Int>>()
        for (row in sheet) for (cell in row) if (cell.cellType != CellType.BLANK) used += cell.rowIndex to cell.columnIndex
        used += inputAt.keys
        outRanges.forEach { used += cells(it) }
        if (used.isEmpty()) return null
        val rows = used.minOf { it.first }..used.maxOf { it.first }
        val cols = used.minOf { it.second }..used.maxOf { it.second }
        if (rows.count() > 200 || cols.count() > 20) return null

        // The header: the first row when it is all text, else the columns' letters.
        val first = cols.map { c -> sheet.getRow(rows.first)?.getCell(c) }
        val filled = first.filter { it != null && it.cellType != CellType.BLANK && !(it.cellType == CellType.STRING && it.stringCellValue.isBlank()) }
        val labelled = rows.count() > 1 && filled.isNotEmpty() && filled.all { it!!.cellType == CellType.STRING }
        val labels = cols.mapIndexed { k, c -> first[k]?.takeIf { labelled && it.cellType == CellType.STRING && it.stringCellValue.isNotBlank() }?.stringCellValue?.let(::label) ?: CellReference.convertNumToColString(c) }
        val body = if (labelled) (rows.first + 1)..rows.last else rows
        val names = cols.mapIndexed { k, c -> c to labels[k].takeIf { NAME.matches(it) } }.toMap()
        val warnings = mutableListOf<String>()

        val grid = body.map { r ->
            cols.map { c ->
                val at = r to c
                inputAt[at]?.let { return@map "= $it" }
                val cell = sheet.getRow(r)?.getCell(c) ?: return@map ""
                when (cell.cellType) {
                    CellType.FORMULA -> formula(cell.cellFormula, r, body, names)?.let { "= $it" }
                        ?: cached(cell).also { warnings += "Excel cell ${CellReference(r, c).formatAsString()} (${cell.cellFormula}) is kept as its last value" }
                    else -> cached(cell)
                }
            }
        }
        val numeric = cols.associateWith { c -> body.all { r -> sheet.getRow(r)?.getCell(c)?.let { it.cellType == CellType.NUMERIC || (it.cellType == CellType.FORMULA && it.cachedFormulaResultType == CellType.NUMERIC) } == true || (r to c) in inputAt } }
        val header = "| " + labels.joinToString(" | ") { it.replace("|", "\\|") } + " |"
        val rule = "| " + cols.joinToString(" | ") { if (numeric.getValue(it)) "---:" else "---" } + " |"
        val table = Table.write(Table.parse(listOf(header, rule) + grid.map { r -> "| " + r.joinToString(" | ") { it.replace("|", "\\|") } + " |" }))

        // Outputs: a whole numeric column follows the table; anything else, its values.
        val definitions = outputs.zip(outRanges).map { (out, range) ->
            val area = cells(range)
            val col = area.map { it.second }.distinct().singleOrNull()
            val column = col?.let { c -> names[c]?.takeIf { area.map { it.first }.toSet() == body.toSet() && numeric.getValue(c) } }
            "$out := " + (column?.let { "${it}_$name" } ?: value(workbook, range) ?: "0")
        }
        return Converted(table, definitions, warnings)
    }

    private val NAME = Regex("""\p{L}[\p{L}\p{N}']*""")

    /** An Excel heading as a column label: on one line, and with brackets that wouldn't read as a unit. */
    private fun label(text: String) = text.replace(Regex("""\s+"""), " ").trim().replace('[', '(').replace(']', ')')

    /** The worksheet's `Contents` stream as text: plain bytes in some versions, UTF-16 in others (its zero bytes dropped). */
    private fun text(contents: ByteArray) = String(contents.filter { it != 0.toByte() }.toByteArray(), Charsets.ISO_8859_1)

    private fun cached(cell: Cell): String {
        val type = if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType
        return when (type) {
            CellType.NUMERIC -> cell.numericCellValue.let { if (it == Math.rint(it) && kotlin.math.abs(it) < 1e15) it.toLong().toString() else it.toString().replace("E", "e") }
            CellType.BOOLEAN -> if (cell.booleanCellValue) "1" else "0"
            CellType.STRING -> cell.richStringCellValue.string.replace("|", "/").let { if (it.trim().startsWith("=")) "'$it" else it }
            else -> ""
        }
    }

    private val TOKEN = Regex("""\$?[A-Z]{1,3}\$?[0-9]{1,6}(?::\$?[A-Z]{1,3}\$?[0-9]{1,6})?|[A-Z][A-Z0-9.]*(?=\()|[0-9]+(?:\.[0-9]*)?(?:[Ee][-+]?[0-9]+)?|<>|<=|>=|[-+*/^(),<>=]|\s+""")

    private val FUNCTIONS = mapOf(
        "SUM" to "sum", "AVERAGE" to "mean", "MIN" to "min", "MAX" to "max", "SQRT" to "sqrt", "ABS" to "abs",
        "EXP" to "exp", "LN" to "ln", "LOG10" to "log", "SIN" to "sin", "COS" to "cos", "TAN" to "tan",
        "ASIN" to "asin", "ACOS" to "acos", "ATAN" to "atan", "IF" to "if",
    )

    /**
     * An Excel formula as a Beermat expression for the table, when it only uses its own row's cells
     * (by their column's name) or whole columns of the body; null otherwise.
     */
    internal fun formula(f: String, row: Int, body: IntRange, names: Map<Int, String?>): String? {
        val out = StringBuilder()
        var k = 0
        val text = f.trim()
        while (k < text.length) {
            val m = TOKEN.matchAt(text, k) ?: return null
            val t = m.value
            k += t.length
            when {
                t.isBlank() -> {}
                t.first().isLetter() && t.any { it.isDigit() } && t.none { it == '(' } && CELL.matches(t) -> {
                    val ends = t.split(':').map { CellReference(it.replace("$", "")) }
                    val c = ends.map { it.col.toInt() }.distinct().singleOrNull() ?: return null
                    val colName = names[c] ?: return null
                    val rs = ends.map { it.row }
                    out.append(
                        when {
                            ends.size == 1 && rs[0] == row -> colName
                            ends.size == 2 && minOf(rs[0], rs[1]) == body.first && maxOf(rs[0], rs[1]) == body.last -> colName
                            else -> return null
                        },
                    )
                }
                t.first().isLetter() -> out.append(if (t == "PI") "π" else FUNCTIONS[t] ?: return null).also { if (t == "PI") { if (text.substring(k).trimStart().startsWith("()")) k = text.indexOf(')', k) + 1 } }
                t == "=" -> out.append(" == ")
                t == "<>" -> out.append(" != ")
                t == "<" || t == ">" || t == "<=" || t == ">=" -> out.append(" $t ")
                t == "," -> out.append(", ")
                else -> out.append(t)
            }
        }
        return out.toString().takeIf { it.isNotBlank() }
    }

    private val CELL = Regex("""\$?[A-Z]{1,3}\$?[0-9]{1,6}(?::\$?[A-Z]{1,3}\$?[0-9]{1,6})?""")

    private val OLE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    /** `b2:b21`, `C2`, or with a sheet, `Sheet2!A1:B3`. */
    private val RANGE = Regex("""(?:[A-Za-z0-9_ ]+!)?\$?[A-Za-z]{1,3}\$?[0-9]{1,6}(?::\$?[A-Za-z]{1,3}\$?[0-9]{1,6})?""")

    /** The range's cells as a number or string, or a matrix of them. */
    private fun value(workbook: HSSFWorkbook, range: String): String? {
        val sheetName = range.substringBefore('!', "").ifEmpty { null }
        val sheet = (sheetName?.let { workbook.getSheet(it) } ?: workbook.getSheetAt(0)) ?: return null
        val cells = range.substringAfter('!').split(':').map { CellReference(it.uppercase()) }
        val (from, to) = cells.first() to cells.last()
        val rows = (minOf(from.row, to.row)..maxOf(from.row, to.row))
        val cols = (minOf(from.col.toInt(), to.col.toInt())..maxOf(from.col.toInt(), to.col.toInt()))
        val texts = rows.map { r -> cols.map { c -> cell(sheet, r, c) } }
        return if (texts.size == 1 && texts[0].size == 1) texts[0][0] else texts.joinToString("; ", "[", "]") { it.joinToString(", ") }
    }

    private fun cell(sheet: Sheet, r: Int, c: Int): String {
        val cell: Cell = sheet.getRow(r)?.getCell(c) ?: return "0"
        val type = if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType
        return when (type) {
            CellType.NUMERIC -> number(cell.numericCellValue)
            CellType.BOOLEAN -> if (cell.booleanCellValue) "1" else "0"
            CellType.STRING -> Printer.print(Str(cell.richStringCellValue.string))
            else -> "0"
        }
    }

    private fun number(x: Double): String = when {
        x == Math.rint(x) && kotlin.math.abs(x) < 1e15 -> x.toLong().toString().let { if (x < 0) "($it)" else it }
        x < 0 -> "(${x.toString().replace("E", "e")})"
        else -> x.toString().replace("E", "e")
    }
}
