package pub.beermat.engine.eval

import pub.beermat.engine.units.Quantity
import java.io.File

/**
 * Reading data files into matrices (docs/evaluation.md#data-files): `READPRN`,
 * `READCSV` and `READFILE`. A file name is relative to the sheet's folder (or absolute).
 * Files are read again at every evaluation, so a sheet follows its data.
 */
internal object DataFiles {

    val functions: List<Builtin> = listOf(
        // Numbers in columns, separated by spaces, tabs or commas; lines that aren't numbers (a header) are skipped.
        Builtin("READPRN", 1..1) { a -> numbers(read(a[0](), "READPRN"), "READPRN") },
        Builtin("READCSV", 1..1) { a -> table(read(a[0](), "READCSV"), ',') },
        // READFILE(file, type): "delimited" (spaces, tabs or commas) or "csv"; numbers and text kept apart.
        Builtin("READFILE", 1..Int.MAX_VALUE) { a ->
            val lines = read(a[0](), "READFILE")
            when (val type = (a.getOrNull(1)?.invoke() as? Text)?.value?.lowercase() ?: "delimited") {
                "delimited" -> table(lines, null)
                "csv" -> table(lines, ',')
                else -> throw EvalException("READFILE: the type '$type' isn't supported yet; use \"delimited\" or \"csv\"")
            }
        },
    )

    private fun Builtin.Context.read(name: Value, function: String): List<String> {
        val path = (name as? Text)?.value ?: throw EvalException("$function needs the file's name as a string: $function(\"data.txt\")")
        val file = File(path).takeIf { it.isAbsolute } ?: File(folder ?: throw EvalException("$function: save the sheet first, so '$path' can be found beside it"), path)
        if (!file.isFile) throw EvalException("$function: '$path' is not found${if (File(path).isAbsolute) "" else " beside the sheet"}")
        return file.readLines().map { it.trimEnd() }.filter { it.isNotBlank() }
    }

    private val SPLIT = Regex("""[\s,;]+""")

    private fun numbers(lines: List<String>, function: String): Value {
        val rows = lines.map { it.trim().split(SPLIT).filter(String::isNotEmpty) }.mapNotNull { cells -> cells.map { it.toDoubleOrNull() }.takeIf { r -> r.all { it != null } } }
        if (rows.isEmpty()) throw EvalException("$function: the file has no lines of numbers")
        val cols = rows[0].size
        if (rows.any { it.size != cols }) throw EvalException("$function: every line needs the same number of values ($cols on the first)")
        return ArrayValue.of(rows.size, cols) { r, c -> Scalar(Quantity(rows[r][c]!!)) }
    }

    /** Cells that are numbers become numbers; the rest, text. Short rows are padded with empty text. */
    private fun table(lines: List<String>, separator: Char?): Value {
        val rows = lines.map { line -> if (separator != null) csv(line, separator) else line.trim().split(SPLIT) }
        if (rows.isEmpty()) throw EvalException("the file is empty")
        val cols = rows.maxOf { it.size }
        return ArrayValue.of(rows.size, cols) { r, c ->
            val cell = rows[r].getOrNull(c)?.trim().orEmpty()
            cell.toDoubleOrNull()?.let { Scalar(Quantity(it)) } ?: Text(cell)
        }
    }

    /** One CSV line: quoted cells may hold the separator and doubled quotes. */
    private fun csv(line: String, separator: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var k = 0
        while (k < line.length) {
            val ch = line[k]
            when {
                quoted && ch == '"' && line.getOrNull(k + 1) == '"' -> cell.append('"').also { k++ }
                ch == '"' -> quoted = !quoted
                ch == separator && !quoted -> cells += cell.toString().also { cell.clear() }
                else -> cell.append(ch)
            }
            k++
        }
        cells += cell.toString()
        return cells
    }
}
