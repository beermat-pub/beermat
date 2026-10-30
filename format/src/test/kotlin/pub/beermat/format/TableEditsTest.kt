package pub.beermat.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TableEditsTest {

    private val table = Table.parse(
        """
        | Case | q [kN/m] | M [kN*m]        |
        |------|---------:|----------------:|
        | Dead | 4.5      | = q*6^2/8 → 20.25 |
        """.trimIndent().lines(),
    )

    private fun md(t: Table) = Table.write(t).joinToString("\n")

    @Test
    fun `cells read and written as the grid shows them`() {
        assertEquals("q [kN/m]", TableEdits.text(table, 0, 1))
        assertEquals("= q*6^2/8", TableEdits.text(table, 1, 2))
        val t = TableEdits.set(table, 1, 1, " 5 ")
        assertEquals("5", t.rows[0][1])
        // A header keeps its alignment and reads its name and unit again.
        val h = TableEdits.set(table, 0, 1, "w [kN/m]")
        assertEquals("w", h.columns[1].label)
        assertEquals("kN/m", h.columns[1].unit)
        assertEquals(Column.Align.RIGHT, h.columns[1].align)
    }

    @Test
    fun `rows and columns come and go`() {
        val t = TableEdits.insertRow(table, 2)
        assertEquals(listOf("", "", ""), t.rows[1])
        assertEquals(table.rows, TableEdits.deleteRow(t, 2).rows)
        assertEquals(listOf(listOf("", "", "")), TableEdits.deleteRow(table, 1).rows)
        val c = TableEdits.insertColumn(table, 1)
        assertEquals(listOf("Case", "c1", "q", "M"), c.columns.map { it.label })
        assertEquals(Column.Align.RIGHT, c.columns[1].align)
        assertEquals(md(table), md(TableEdits.deleteColumn(c, 1)))
    }

    @Test
    fun `tab-separated text pastes in, growing the table`() {
        val t = TableEdits.paste(table, 1, 1, "4.5\t= q*L\t1\n7.5\t= q*L\t2\n")
        assertEquals(listOf("Case", "q", "M", "c1"), t.columns.map { it.label })
        assertEquals(listOf(listOf("Dead", "4.5", "= q*L", "1"), listOf("", "7.5", "= q*L", "2")), t.rows)
    }

    @Test
    fun `inserting and deleting rows and columns moves the references, as Excel does`() {
        val t = Table.parse(
            """
            | a | b             |
            |---|---------------|
            | 1 | = A2*2        |
            | 2 | = sum(A2:A3)  |
            | 3 | = A4 + "A4"   |
            """.trimIndent().lines(),
        )
        // A row above the first: every row moves down one; text in quotes doesn't.
        val inserted = TableEdits.insertRow(t, 1)
        assertEquals(listOf("= A3*2", "= sum(A3:A4)", "= A5 + \"A4\""), inserted.rows.drop(1).map { it[1] })
        // Deleting the first row: its references become #REF, the ones below move up.
        val deleted = TableEdits.deleteRow(t, 1)
        assertEquals(listOf("= sum(#REF:A2)", "= A3 + \"A4\""), deleted.rows.map { it[1] })
        // A column before A: A becomes B.
        assertEquals("= B2*2", TableEdits.insertColumn(t, 0).rows[0][2])
        assertEquals("= #REF*2", TableEdits.deleteColumn(TableEdits.insertColumn(t, 2), 0).rows[0][0].let { TableEdits.deleteColumn(t, 0).rows[0][0] })
    }
}
