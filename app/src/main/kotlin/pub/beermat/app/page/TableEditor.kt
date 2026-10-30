package pub.beermat.app.page

import pub.beermat.format.Table
import pub.beermat.format.TableEdits
import pub.beermat.format.TableRegion

/**
 * The cell cursor in a table region (docs/tables.md#editing): cell ([row], [col]) of region
 * [region], row 0 being the header. While a cell is being typed in, [draft] is its text and
 * [caret] where the next character goes.
 */
data class TableCursor(val region: Int, val row: Int, val col: Int, val draft: String? = null, val caret: Int = 0)

/** The keys the grid answers to. */
sealed interface CellKey {
    data class Type(val c: Char) : CellKey
    data object Backspace : CellKey
    data object Delete : CellKey
    data object Left : CellKey
    data object Right : CellKey
    data object Up : CellKey
    data object Down : CellKey
    data object Home : CellKey
    data object End : CellKey
    data object Tab : CellKey
    data object BackTab : CellKey
    data object Enter : CellKey
    data object Escape : CellKey
    /** F2 or a double-click: edit what the cell holds. */
    data object Edit : CellKey
}

/**
 * Editing a table as a grid, as in a spreadsheet: arrows, Tab and Enter move between cells
 * (Enter or Tab past the last row adds one); typing replaces a cell, F2 edits it; Delete
 * clears it; Escape cancels the typing, then leaves the table. A cell holds its linear text:
 * a number, a word, or `= expression`.
 */
object TableEditor {

    /** [key] at the cell cursor; false when it does nothing. */
    fun key(s: Session, key: CellKey): Boolean {
        val c = s.table ?: return false
        val t = s.tableAt(c.region) ?: return false
        val rows = t.rows.size + 1
        val cols = t.columns.size
        val d = c.draft
        if (d != null) {
            fun draft(text: String, caret: Int) = true.also { s.table = c.copy(draft = text, caret = caret) }
            return when (key) {
                is CellKey.Type -> draft(d.substring(0, c.caret) + key.c + d.substring(c.caret), c.caret + 1)
                CellKey.Backspace -> c.caret > 0 && draft(d.removeRange(c.caret - 1, c.caret), c.caret - 1)
                CellKey.Delete -> c.caret < d.length && draft(d.removeRange(c.caret, c.caret + 1), c.caret)
                CellKey.Left -> c.caret > 0 && draft(d, c.caret - 1)
                CellKey.Right -> c.caret < d.length && draft(d, c.caret + 1)
                CellKey.Home -> draft(d, 0)
                CellKey.End -> draft(d, d.length)
                CellKey.Escape -> true.also { s.table = c.copy(draft = null, caret = 0) }
                CellKey.Edit -> false
                else -> {
                    commit(s)
                    key(s, key)
                }
            }
        }
        fun go(row: Int, col: Int) = true.also { s.table = c.copy(row = row, col = col) }
        return when (key) {
            is CellKey.Type -> true.also { s.table = c.copy(draft = key.c.toString(), caret = 1) }
            CellKey.Edit -> TableEdits.text(t, c.row, c.col).let { text -> true.also { s.table = c.copy(draft = text, caret = text.length) } }
            CellKey.Backspace, CellKey.Delete -> true.also { if (TableEdits.text(t, c.row, c.col).isNotEmpty()) s.setTable(c.region, TableEdits.set(t, c.row, c.col, "")) }
            CellKey.Left -> c.col > 0 && go(c.row, c.col - 1)
            CellKey.Right -> c.col < cols - 1 && go(c.row, c.col + 1)
            CellKey.Up -> c.row > 0 && go(c.row - 1, c.col)
            CellKey.Down -> c.row < rows - 1 && go(c.row + 1, c.col)
            CellKey.Home -> go(c.row, 0)
            CellKey.End -> go(c.row, cols - 1)
            CellKey.Enter -> {
                if (c.row == rows - 1) s.setTable(c.region, TableEdits.insertRow(t, rows))
                go(c.row + 1, c.col)
            }
            CellKey.Tab -> when {
                c.col < cols - 1 -> go(c.row, c.col + 1)
                else -> {
                    if (c.row == rows - 1) s.setTable(c.region, TableEdits.insertRow(t, rows))
                    go(c.row + 1, 0)
                }
            }
            CellKey.BackTab -> when {
                c.col > 0 -> go(c.row, c.col - 1)
                c.row > 0 -> go(c.row - 1, cols - 1)
                else -> false
            }
            CellKey.Escape -> true.also { s.table = null }
        }
    }

    /** Writes the cell being typed in, if any. */
    fun commit(s: Session) {
        val c = s.table ?: return
        val d = c.draft ?: return
        s.table = c.copy(draft = null, caret = 0)
        val t = s.tableAt(c.region) ?: return
        if (d.trim() != TableEdits.text(t, c.row, c.col)) s.setTable(c.region, TableEdits.set(t, c.row, c.col, d))
    }

    /** Right-click and paste: the structural edits at the cursor. */
    fun insertRow(s: Session, below: Boolean) = edit(s) { t, c ->
        val at = maxOf(1, if (below) c.row + 1 else c.row)
        TableEdits.insertRow(t, at) to c.copy(row = at)
    }

    fun deleteRow(s: Session) = edit(s) { t, c -> TableEdits.deleteRow(t, c.row).let { it to c.copy(row = c.row.coerceAtMost(it.rows.size).coerceAtLeast(if (c.row == 0) 0 else 1)) } }

    fun insertColumn(s: Session, right: Boolean) = edit(s) { t, c ->
        val at = if (right) c.col + 1 else c.col
        TableEdits.insertColumn(t, at) to c.copy(col = at)
    }

    fun deleteColumn(s: Session) = edit(s) { t, c -> TableEdits.deleteColumn(t, c.col).let { it to c.copy(col = c.col.coerceAtMost(it.columns.size - 1)) } }

    /** Tab-separated text (from a spreadsheet) from the cursor on. */
    fun paste(s: Session, text: String) = edit(s) { t, c -> TableEdits.paste(t, c.row, c.col, text) to c }

    /** The text of the cell at the cursor, to copy. */
    fun copy(s: Session): String? = s.table?.let { c -> c.draft ?: s.tableAt(c.region)?.let { TableEdits.text(it, c.row, c.col) } }

    private fun edit(s: Session, change: (Table, TableCursor) -> Pair<Table, TableCursor>): Boolean {
        commit(s)
        val c = s.table ?: return false
        val t = s.tableAt(c.region) ?: return false
        val (next, cursor) = change(t, c)
        s.setTable(c.region, next)
        s.table = cursor.copy(draft = null, caret = 0)
        return true
    }

    /** The cell of [grid] at ([rx], [ry]) in the region's own coordinates, or null outside the cells. */
    fun cellAt(grid: TableGrid, rx: Float, ry: Float): Pair<Int, Int>? {
        if (rx < 0 || ry < 0) return null
        val row = (ry / TableGrid.ROW).toInt().takeIf { it < grid.cells.size } ?: return null
        var x = 0f
        grid.widths.forEachIndexed { k, w ->
            if (rx < x + w) return row to k
            x += w
        }
        return null
    }

    internal fun regionTable(r: TableRegion) = Table.parse(r.body)
}
