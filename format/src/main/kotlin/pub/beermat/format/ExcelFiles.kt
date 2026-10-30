package pub.beermat.format

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.util.CellReference
import org.w3c.dom.Element
import pub.beermat.engine.eval.ArrayValue
import pub.beermat.engine.eval.Builtin
import pub.beermat.engine.eval.EvalException
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Text
import pub.beermat.engine.eval.Value
import pub.beermat.engine.units.Quantity
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `READEXCEL(file[, range])` (docs/evaluation.md#data-files): the values in an Excel workbook, as
 * Excel last calculated them. A `.xlsx` is read directly (zipped XML: sheets, shared strings);
 * a classic `.xls` with Apache POI. Here rather than in the engine, which stays free of the JVM's
 * libraries for the web version.
 */
object ExcelFiles {

    init {
        quietPoi()
    }

    val functions: List<Builtin> = listOf(
        Builtin("READEXCEL", 1..2) { a ->
            val path = (a[0]() as? Text)?.value ?: throw EvalException("READEXCEL needs the file's name as a string: READEXCEL(\"loads.xlsx\")")
            val range = a.getOrNull(1)?.invoke()?.let { (it as? Text)?.value ?: throw EvalException("READEXCEL: the range is a string, such as \"Sheet1!A1:C10\"") }
            val file = File(path).takeIf { it.isAbsolute } ?: File(folder ?: throw EvalException("READEXCEL: save the sheet first, so '$path' can be found beside it"), path)
            if (!file.isFile) throw EvalException("READEXCEL: '$path' is not found")
            val sheets = runCatching { if (file.name.endsWith(".xls", ignoreCase = true)) xls(file) else xlsx(file) }
                .getOrElse { throw EvalException("READEXCEL: '$path' can't be read as a workbook (${it.message})") }
            matrix(sheets, range)
        },
    )

    /** A sheet: its name and its cells by (row, column), from 0. */
    class Sheet(val name: String, val cells: Map<Pair<Int, Int>, Value>)

    /** The cells of [range] (`Sheet1!A1:C10`, `A1:C10`, or the first sheet's used cells), as a number, a string or a matrix. */
    fun matrix(sheets: List<Sheet>, range: String?): Value {
        val sheetName = range?.substringBefore('!', "")?.trim('\'')?.ifEmpty { null }
        val sheet = sheetName?.let { n -> sheets.firstOrNull { it.name.equals(n, ignoreCase = true) } ?: throw EvalException("READEXCEL: no sheet named '$n'") }
            ?: sheets.firstOrNull() ?: throw EvalException("READEXCEL: the workbook has no sheets")
        val area = range?.substringAfter('!')?.takeIf { it.isNotBlank() }
        val (rows, cols) = if (area == null) {
            if (sheet.cells.isEmpty()) throw EvalException("READEXCEL: the sheet is empty")
            (sheet.cells.keys.minOf { it.first }..sheet.cells.keys.maxOf { it.first }) to (sheet.cells.keys.minOf { it.second }..sheet.cells.keys.maxOf { it.second })
        } else {
            val ends = area.split(':').map { CellReference(it.trim().uppercase()) }
            (minOf(ends.first().row, ends.last().row)..maxOf(ends.first().row, ends.last().row)) to
                (minOf(ends.first().col.toInt(), ends.last().col.toInt())..maxOf(ends.first().col.toInt(), ends.last().col.toInt()))
        }
        val values = rows.map { r -> cols.map { c -> sheet.cells[r to c] ?: Scalar(Quantity(0.0)) } }
        if (values.size == 1 && values[0].size == 1) return values[0][0]
        return ArrayValue.of(values.size, values[0].size) { r, c -> values[r][c] }
    }

    /** A classic `.xls`, through POI: each cell's value, or a formula's last result. */
    fun xls(file: File): List<Sheet> = file.inputStream().use { HSSFWorkbook(it) }.let(::sheets)

    fun sheets(workbook: org.apache.poi.ss.usermodel.Workbook): List<Sheet> = (0 until workbook.numberOfSheets).map { s ->
        val sheet = workbook.getSheetAt(s)
        val cells = mutableMapOf<Pair<Int, Int>, Value>()
        for (row in sheet) for (cell in row) {
            val type = if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType
            cells[cell.rowIndex to cell.columnIndex] = when (type) {
                CellType.NUMERIC -> Scalar(Quantity(cell.numericCellValue))
                CellType.BOOLEAN -> Scalar(Quantity(if (cell.booleanCellValue) 1.0 else 0.0))
                CellType.STRING -> Text(cell.richStringCellValue.string)
                else -> continue
            }
        }
        Sheet(sheet.sheetName, cells)
    }

    /** A `.xlsx`: the workbook's sheet names, the shared strings, and each sheet's cells with their cached values. */
    fun xlsx(file: File): List<Sheet> = ZipFile(file).use { zip ->
        fun xml(name: String): Element? = zip.getEntry(name)?.let { e ->
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder().parse(zip.getInputStream(e)).documentElement
        }
        fun Element.all(tag: String) = getElementsByTagNameNS("*", tag).let { list -> (0 until list.length).map { list.item(it) as Element } }
        val strings = xml("xl/sharedStrings.xml")?.all("si")?.map { si -> si.all("t").joinToString("") { it.textContent } }.orEmpty()
        // Sheet names in order, and the file each one is in (through the workbook's relationships).
        val workbook = xml("xl/workbook.xml") ?: error("no xl/workbook.xml")
        val targets = xml("xl/_rels/workbook.xml.rels")?.all("Relationship")?.associate { it.getAttribute("Id") to it.getAttribute("Target") }.orEmpty()
        workbook.all("sheet").map { s ->
            val rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            val target = targets[rid]?.removePrefix("/")?.let { if (it.startsWith("xl/")) it else "xl/$it" } ?: "xl/worksheets/sheet1.xml"
            val cells = mutableMapOf<Pair<Int, Int>, Value>()
            xml(target)?.all("c")?.forEach { c ->
                val ref = CellReference(c.getAttribute("r"))
                val v = c.all("v").firstOrNull()?.textContent
                val value: Value = when (c.getAttribute("t")) {
                    "s" -> Text(strings.getOrNull(v?.toIntOrNull() ?: -1).orEmpty())
                    "inlineStr" -> Text(c.all("t").joinToString("") { it.textContent })
                    "str" -> Text(v.orEmpty())
                    "b" -> Scalar(Quantity(if (v == "1") 1.0 else 0.0))
                    "e" -> return@forEach
                    else -> Scalar(Quantity(v?.toDoubleOrNull() ?: return@forEach))
                }
                cells[ref.row to ref.col.toInt()] = value
            }
            Sheet(s.getAttribute("name"), cells)
        }
    }
}

/**
 * POI logs through Log4j, which, with no logging set up, prints an error the first time it's used.
 * Beermat has nothing to say through it: a plain, silent logger instead.
 */
fun quietPoi() {
    if (System.getProperty("log4j2.loggerContextFactory") == null) {
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF")
        System.setProperty("org.apache.logging.log4j.level", "OFF")
    }
}
