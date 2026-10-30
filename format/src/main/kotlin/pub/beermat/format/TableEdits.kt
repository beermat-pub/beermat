package pub.beermat.format

/**
 * Edits to a [Table], for the grid editor (docs/tables.md#editing). Cells are addressed as the
 * grid shows them: row 0 is the header, rows 1 … are the body. Each edit gives a new table.
 */
object TableEdits {

    /** What the cell holds, to edit: the header as written, or a body cell without its cached value. */
    fun text(t: Table, row: Int, col: Int): String {
        if (row == 0) return t.columns.getOrNull(col)?.header.orEmpty()
        val cell = t.rows.getOrNull(row - 1)?.getOrNull(col) ?: return ""
        return Table.expression(cell)?.let { "= $it" } ?: cell
    }

    /** [text] in a cell; a header cell's label and unit are read again from it, keeping the alignment. */
    fun set(t: Table, row: Int, col: Int, text: String): Table {
        val value = text.trim()
        if (row == 0) {
            val parsed = Table.parse(listOf("| $value |", "|---|")).columns.single()
            return t.copy(columns = t.columns.toMutableList().also { it[col] = parsed.copy(align = t.columns[col].align) })
        }
        return t.copy(rows = t.rows.mapIndexed { r, cells -> if (r == row - 1) cells.mapIndexed { c, s -> if (c == col) value else s } else cells })
    }

    /** An empty body row at grid row [row] (1 to put it first; past the end to add it last). */
    fun insertRow(t: Table, row: Int): Table {
        val at = (row - 1).coerceIn(0, t.rows.size)
        val shifted = moved(t, row = { if (it >= at + 1) it + 1 else it })
        return shifted.copy(rows = shifted.rows.toMutableList().also { it.add(at, List(t.columns.size) { "" }) })
    }

    /** Removes body row [row]; the header stays, and so does the last body row (emptied instead). */
    fun deleteRow(t: Table, row: Int): Table {
        if (row < 1 || row > t.rows.size) return t
        if (t.rows.size == 1) return t.copy(rows = listOf(List(t.columns.size) { "" }))
        // Excel's row of the deleted one is [row] + 1 (the header being row 1); references to it become #REF.
        val shifted = moved(t, row = { if (it == row) null else if (it > row) it - 1 else it })
        return shifted.copy(rows = shifted.rows.filterIndexed { r, _ -> r != row - 1 })
    }

    /** A new column at [col] (past the end to add it last), headed with a free name and right-aligned for numbers. */
    fun insertColumn(t: Table, col: Int): Table {
        val at = col.coerceIn(0, t.columns.size)
        val used = t.columns.map { it.label }.toSet()
        val label = generateSequence(1) { it + 1 }.map { "c$it" }.first { it !in used }
        val column = Table.parse(listOf("| $label |", "|---:|")).columns.single()
        val shifted = moved(t, col = { if (it >= at) it + 1 else it })
        return Table(
            shifted.columns.toMutableList().also { it.add(at, column) },
            shifted.rows.map { r -> r.toMutableList().also { it.add(at, "") } },
        )
    }

    /**
     * [t] with the cell references in its expressions moved (docs/tables.md#cell-references): [row]
     * and [col] map an old row or column (from 0, the header being row 0) to the new one, or to
     * null where it was deleted, which makes the reference `#REF`, an error.
     */
    private fun moved(t: Table, row: (Int) -> Int? = { it }, col: (Int) -> Int? = { it }): Table =
        t.copy(rows = t.rows.map { r -> r.map { cell -> if (Table.expression(cell) != null) shift(cell, row, col) else cell } })

    /** The references in one cell's text, moved; text in quotes is left alone. */
    internal fun shift(cell: String, row: (Int) -> Int?, col: (Int) -> Int?): String {
        val parts = cell.split('"')
        return parts.mapIndexed { k, part ->
            if (k % 2 == 1) part else REFERENCE.replace(part) { m ->
                val (c0, r0) = m.groupValues[2] to m.groupValues[4].toInt() - 1
                val r = row(r0)
                val c = col(pub.beermat.engine.eval.CellRange.column(c0))
                if (r == null || c == null) "#REF" else m.groupValues[1] + pub.beermat.engine.eval.CellRange.letters(c) + m.groupValues[3] + (r + 1)
            }
        }.joinToString("\"")
    }

    /** `B5` or `$B$5`, not inside a longer name. */
    private val REFERENCE = Regex("""(?<![\p{L}\p{N}_'.])(\$?)([A-Z]{1,3})(\$?)([0-9]{1,7})(?![\p{L}\p{N}_'])""")

    /** Removes column [col]; the last column stays. */
    fun deleteColumn(t: Table, col: Int): Table {
        if (t.columns.size <= 1 || col !in t.columns.indices) return t
        val shifted = moved(t, col = { if (it == col) null else if (it > col) it - 1 else it })
        return Table(shifted.columns.filterIndexed { c, _ -> c != col }, shifted.rows.map { r -> r.filterIndexed { c, _ -> c != col } })
    }

    /**
     * Tab-separated text (as Excel and other spreadsheets copy cells) put in from ([row], [col])
     * on, adding body rows and columns as needed. Pasted into the header row, its first line is headers.
     */
    fun paste(t: Table, row: Int, col: Int, text: String): Table {
        val lines = text.trimEnd('\n', '\r').split(Regex("\r?\n")).map { it.split('\t') }
        var out = t
        while (out.columns.size < col + lines.maxOf { it.size }) out = insertColumn(out, out.columns.size)
        while (out.rows.size < row - 1 + lines.size) out = insertRow(out, out.rows.size + 1)
        lines.forEachIndexed { i, cells -> cells.forEachIndexed { j, s -> out = set(out, row + i, col + j, s) } }
        return out
    }
}
