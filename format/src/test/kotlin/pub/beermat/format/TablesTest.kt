package pub.beermat.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TablesTest {

    private val sheet = """
        |```calc
        |L := 6 m
        |```
        |
        |```table {#tb1 name=Loads}
        || Case    | q [kN/m] | M [kN*m]  |
        ||---------|---------:|----------:|
        || Dead    | 4.5      | = q*L^2/8 |
        || Imposed | 7.5      | = q*L^2/8 |
        || Total   | = sum(q) | = sum(M)  |
        |```
        |
        |```calc
        |M_max := max(M_Loads) = ? kN*m
        |```
        |""".trimMargin()

    @Test
    fun `rows use their own values, a column's own name in its cell is the whole column`() {
        val calc = Calculation.of(WorksheetText.read(sheet))
        val t = calc.tables.single()
        assertEquals(emptyList(), t.problems)
        assertEquals("20.25", t.shown[0 to 2])
        assertEquals("33.75", t.shown[1 to 2])
        assertEquals("12", t.shown[2 to 1])
        assertEquals("54", t.shown[2 to 2])
    }

    @Test
    fun `a named table defines its columns for the statements below`() {
        val calc = Calculation.of(WorksheetText.read(sheet))
        assertEquals(Annotation.Result("54 kN*m"), calc.lines.last().annotation)
    }

    @Test
    fun `values are cached after arrows, written lined up, and don't change the hash`() {
        val written = WorksheetText.write(Calculation.of(WorksheetText.read(sheet)).written())
        assertTrue("| Dead    |           4.5 | = q*L^2/8 → 20.25 |" in written, written)
        assertTrue("| Total   | = sum(q) → 12 |     = sum(M) → 54 |" in written, written)
        // Writing again changes nothing: the cached values are output, not input.
        val again = WorksheetText.write(Calculation.of(WorksheetText.read(written)).written())
        assertEquals(written, again)
        assertEquals(Check.of(WorksheetText.read(written)), emptyList())
    }

    @Test
    fun `the pipe table parses and prints`() {
        val t = Table.parse(listOf("| a [m] | Name | b |", "|---:|:---:|---|", "| 1 | x \\| y | = a*2 → 2 m |"))
        assertEquals(listOf("a", "Name", "b"), t.columns.map { it.label })
        assertEquals("m", t.columns[0].unit)
        assertEquals(listOf(Column.Align.RIGHT, Column.Align.CENTRE, Column.Align.LEFT), t.columns.map { it.align })
        assertEquals(listOf("1", "x | y", "= a*2 → 2 m"), t.rows[0])
        assertEquals("a*2", Table.expression(t.rows[0][2]))
        // The cached value is written from the evaluation, not kept from the text.
        assertEquals(t, Table.parse(Table.write(t, mapOf((0 to 2) to "2 m"))))
    }

    @Test
    fun `problems are reported`() {
        val bad = "```table\n| a | b |\n|---|---|\n| 1 | = a + c |\n```\n"
        val problems = Check.of(WorksheetText.read(bad)).map { it.message }
        assertEquals(listOf("table: B2 (row 1, b): c is not defined"), problems)
    }

    /** A parameter list, as Excel components are: formulas use other rows' cells by their Excel names. */
    private val parameters = """
        |```calc
        |D := 0.3 m
        |```
        |
        |```table {#tb1 name=Pipe}
        || Parameter | Value          | Unit |
        ||-----------|---------------:|------|
        || D         | = D/(1 m)      | m    |
        || t         | 0.02           | m    |
        || A         | = π*(B2 - B3)*B3 | m²   |
        || Total     | = sum(B2:B4)   |      |
        || Label     | = A1           |      |
        |```
        |
        |```calc
        |area := cells(Pipe, "B4") = ?
        |both := cells(Pipe, "B2:B3") = ?
        |```
        |""".trimMargin()

    @Test
    fun `cells by their Excel names, ranges, and the header as row 1`() {
        val calc = Calculation.of(WorksheetText.read(parameters))
        val t = calc.tables.single()
        assertEquals(emptyList(), t.problems)
        assertEquals("0.018", t.shown[2 to 1])
        assertEquals("0.338", t.shown[3 to 1])
        assertEquals("\"Parameter\"", t.shown[4 to 1])
        // The sheet takes ranges from the named table.
        val results = calc.lines.mapNotNull { (it.annotation as? Annotation.Result)?.text }
        assertEquals(listOf("0.018", "[0.3; 0.02]"), results)
    }

    @Test
    fun `a circular reference is reported`() {
        val calc = Calculation.of(WorksheetText.read("```table\n| a | b |\n|--:|--:|\n| = B2 | = A2 |\n```\n"))
        assertTrue(calc.tables.single().problems.any { "circular" in it }, calc.tables.single().problems.toString())
    }

    @Test
    fun `ranges and absolute references are read`() {
        assertEquals("sum(cells(\"B2:B4\")) + B5", Tables.ranges("sum(\$B\$2:B4) + \$B5"))
        // A range inside a longer name is left alone.
        assertEquals("xB2:B4", Tables.ranges("xB2:B4"))
    }
}
