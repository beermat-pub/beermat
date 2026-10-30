package pub.beermat.app.page

import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The table grid editor (docs/tables.md#editing), driven as a person would: clicks and keys. */
class TableEditorTest {

    private val math = MathLayout(SkiaMath, 12f)

    private val sheet = """
        |```calc {#r1 x=0 y=0}
        |L := 6 m
        |```
        |
        |```table {#tb1 x=0 y=40}
        || Case | q [kN/m] | M [kN*m]  |
        ||------|---------:|----------:|
        || Dead | 4.5      | = q*L^2/8 |
        |```
        |""".trimMargin()

    /** A click in the middle of cell ([row], [col]) of the table. */
    private fun Session.clickCell(row: Int, col: Int) {
        val v = layout.views.first { it is TableView } as TableView
        val (ox, oy) = layout.origin(v, layout.pieces[v.index].first())
        click(ox + v.grid.widths.take(col).sum() + v.grid.widths[col] / 2, oy + TableGrid.ROW * row + TableGrid.ROW / 2)
    }

    private fun Session.type(text: String) = text.forEach { TableEditor.key(this, CellKey.Type(it)) }

    /** The table's lines, without its fence. */
    private fun Session.body() = save().substringAfter("```table").substringAfter("\n").substringBefore("```\n").trim()

    @Test
    fun `a click puts the cursor on a cell, typing replaces it, and Enter moves down`() {
        val s = Session(sheet, math)
        s.clickCell(1, 1)
        assertEquals(TableCursor(1, 1, 1), s.table)
        s.type("7.5")
        assertEquals("7.5", s.table?.draft)
        TableEditor.key(s, CellKey.Enter)
        // The last row: Enter adds one and goes to it.
        assertEquals(TableCursor(1, 2, 1), s.table)
        assertTrue("| Dead | 7.5 | = q*L^2/8 → 33.75 |" in s.body().replace(Regex(" +"), " "), s.body())
        assertTrue(s.body().lines().last().replace(" ", "") == "||||", s.body())
    }

    @Test
    fun `F2 edits what a cell holds, Escape cancels, Delete clears`() {
        val s = Session(sheet, math)
        s.clickCell(1, 2)
        TableEditor.key(s, CellKey.Edit)
        assertEquals("= q*L^2/8", s.table?.draft)
        TableEditor.key(s, CellKey.Backspace)
        TableEditor.key(s, CellKey.Type('4'))
        TableEditor.key(s, CellKey.Escape)
        assertNull(s.table?.draft)
        assertTrue("= q*L^2/8" in s.body())
        TableEditor.key(s, CellKey.Delete)
        assertFalse("q*L^2/8" in s.body())
        s.undo()
        assertTrue("= q*L^2/8" in s.body())
    }

    @Test
    fun `Tab runs along the row and on to a new one, and arrows stop at the edges`() {
        val s = Session(sheet, math)
        s.clickCell(0, 0)
        assertFalse(TableEditor.key(s, CellKey.Up))
        TableEditor.key(s, CellKey.Tab)
        TableEditor.key(s, CellKey.Tab)
        assertEquals(TableCursor(1, 0, 2), s.table)
        TableEditor.key(s, CellKey.Tab)
        TableEditor.key(s, CellKey.Tab)
        TableEditor.key(s, CellKey.Tab)
        TableEditor.key(s, CellKey.Tab)
        assertEquals(TableCursor(1, 2, 0), s.table)
        TableEditor.key(s, CellKey.BackTab)
        assertEquals(TableCursor(1, 1, 2), s.table)
    }

    @Test
    fun `typing in a header renames the column, and rows and columns come from the menu`() {
        val s = Session(sheet, math)
        s.clickCell(0, 0)
        s.type("Load case")
        TableEditor.key(s, CellKey.Tab)
        assertTrue(s.body().startsWith("| Load case |"), s.body())
        TableEditor.insertColumn(s, right = true)
        assertEquals(TableCursor(1, 0, 2), s.table)
        assertTrue("| c1 |" in s.body().replace(Regex(" +"), " "), s.body())
        TableEditor.deleteColumn(s)
        TableEditor.insertRow(s, below = true)
        assertEquals(1, s.table?.row)
        assertEquals(4, s.body().lines().size)
    }

    @Test
    fun `cells pasted from a spreadsheet, and the table left as Markdown`() {
        val s = Session(sheet, math)
        s.clickCell(1, 0)
        TableEditor.paste(s, "Dead\t4.5\nImposed\t7.5\n")
        assertTrue("| Imposed | 7.5 | |" in s.body().replace(Regex(" +"), " "), s.body())
        s.editTableText()
        assertNull(s.table)
        assertEquals(1 to null, s.textEditing)
    }
}
