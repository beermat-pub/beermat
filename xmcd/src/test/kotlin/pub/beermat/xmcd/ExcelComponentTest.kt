package pub.beermat.xmcd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Excel formulas as table expressions: only their own row's cells, or whole columns. */
class ExcelComponentTest {

    // Column B (1) is q, C (2) is L; the body is rows 2 to 6 of the sheet (1 to 5 from 0).
    private val names = mapOf(1 to "q", 2 to "L", 3 to null)
    private fun f(formula: String, row: Int = 2) = ExcelComponent.formula(formula, row, 1..5, names)

    @Test
    fun `own row and whole columns translate`() {
        assertEquals("q*L^2/8", f("B3*C3^2/8"))
        assertEquals("sum(q)", f("SUM(B2:B6)"))
        assertEquals("if(q > 1, sqrt(q), 0)", f("IF(B3>1,SQRT(B3),0)"))
        assertEquals("π*q", f("PI()*B3"))
        assertEquals("q == L", f("B3=C3"))
    }

    @Test
    fun `other rows, unnamed columns and unknown functions keep the last value`() {
        assertNull(f("B4*2"))
        assertNull(f("SUM(B2:B4)"))
        assertNull(f("D3*2"))
        assertNull(f("VLOOKUP(B3,A1:C9,2)"))
        assertNull(f("\"text\""))
    }
}
