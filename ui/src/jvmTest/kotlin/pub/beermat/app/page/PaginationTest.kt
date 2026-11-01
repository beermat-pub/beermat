package pub.beermat.app.page

import kotlin.test.Test
import kotlin.test.assertEquals

class PaginationTest {

    private val h = 100f

    @Test
    fun `a region cut by the page boundary moves to the next page, with everything below`() {
        val pieces = Pagination.paginate(listOf(Block(10f, 20f), Block(80f, 30f), Block(115f, 10f)), h)
        assertEquals(listOf(Piece(0f, 20f, 0, 10f)), pieces[0])
        assertEquals(listOf(Piece(0f, 30f, 1, 0f)), pieces[1])
        // Pushed down by the 20 points left at the bottom of page 0.
        assertEquals(listOf(Piece(0f, 10f, 1, 35f)), pieces[2])
    }

    @Test
    fun `regions side by side move together`() {
        val pieces = Pagination.paginate(listOf(Block(90f, 20f), Block(90f, 5f)), h)
        assertEquals(1, pieces[0].single().page)
        assertEquals(Piece(0f, 5f, 1, 0f), pieces[1].single())
    }

    @Test
    fun `a region taller than a page breaks between its lines`() {
        val pieces = Pagination.paginate(listOf(Block(50f, 180f, breaks = listOf(30f, 45f, 60f, 120f, 150f))), h)
        assertEquals(listOf(Piece(0f, 45f, 0, 50f), Piece(45f, 120f, 1, 0f), Piece(120f, 180f, 2, 0f)), pieces[0])
    }

    @Test
    fun `page breaks and collapsed areas`() {
        val blocks = listOf(Block(10f, 10f), Block(30f, 0f, pageBreak = true), Block(40f, 10f))
        assertEquals(Piece(0f, 10f, 1, 10f), Pagination.paginate(blocks, h)[2].single())
        // A collapsed area hides its region and pulls the next one up by 30.
        val area = listOf(Block(10f, 10f), Block(30f, 20f, hidden = true), Block(60f, 10f, shift = -30f))
        val p = Pagination.paginate(area, h)
        assertEquals(emptyList(), p[1])
        assertEquals(Piece(0f, 10f, 0, 30f), p[2].single())
    }

    @Test
    fun `a click finds its sheet position back`() {
        val blocks = listOf(Block(80f, 30f), Block(115f, 10f))
        val pieces = Pagination.paginate(blocks, h)
        // Page 1, 40 down: 5 below the second block's top, which was pushed by 20.
        assertEquals(120f, Pagination.sheetY(blocks, pieces, 1, 40f, h))
    }
}
