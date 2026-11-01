package pub.beermat.app.page

import kotlin.math.floor

/** A page's size and margins, in points (docs/editor.md#regions-on-the-page). */
data class PageFormat(val width: Float = 595f, val height: Float = 842f, val margin: Float = 54f) {
    val contentWidth: Float get() = width - 2 * margin
    val contentHeight: Float get() = height - 2 * margin

    companion object {
        val A4 = PageFormat()
        val LETTER = PageFormat(612f, 792f)

        /** The frontmatter's `page:` value. */
        fun of(name: String?): PageFormat = if (name?.trim()?.lowercase() == "letter") LETTER else A4
    }
}

/** Where a slice of a region is drawn: its part from [from] to [to] (region coordinates), on [page], with [from] at [top] in the page's content area. */
data class Piece(val from: Float, val to: Float, val page: Int, val top: Float)

/** A region to paginate: its sheet position and height, where it may be cut, and whether it is a page break. */
data class Block(val y: Float, val height: Float, val breaks: List<Float> = emptyList(), val pageBreak: Boolean = false, val hidden: Boolean = false, val shift: Float = 0f)

/**
 * Pagination (docs/editor.md): sheet coordinates run continuously down the content area; on
 * screen and in print, a region that would be cut by a page boundary moves to the next page,
 * and everything below moves with it. A region taller than a page is cut at one of its
 * [Block.breaks] instead (between program lines). A page break starts a new page.
 *
 * A collapsed area's regions are [Block.hidden]; a block's [Block.shift] (negative) moves it and
 * everything below it up, by the height the collapsed area no longer takes.
 */
object Pagination {

    /** The pieces of each block (none for hidden ones and page breaks), in the order given. */
    fun paginate(blocks: List<Block>, pageHeight: Float): List<List<Piece>> {
        val order = blocks.indices.sortedBy { blocks[it].y }
        val out = MutableList(blocks.size) { emptyList<Piece>() }
        var push = 0f
        for (k in order) {
            val b = blocks[k]
            push += b.shift
            if (b.hidden) continue
            val d = b.y + push
            if (b.pageBreak) {
                val next = (floor(d / pageHeight) + 1) * pageHeight
                push += next - d
                continue
            }
            var page = floor(d / pageHeight).toInt().coerceAtLeast(0)
            var top = d - page * pageHeight
            when {
                top + b.height <= pageHeight + 0.01f -> out[k] = listOf(Piece(0f, b.height, page, top))
                b.height <= pageHeight -> {
                    push += pageHeight - top
                    out[k] = listOf(Piece(0f, b.height, page + 1, 0f))
                }
                else -> {
                    val pieces = mutableListOf<Piece>()
                    var from = 0f
                    while (from < b.height) {
                        val room = pageHeight - top
                        val cut = if (from + room >= b.height) b.height
                        else b.breaks.filter { it > from + 1f && it <= from + room }.maxOrNull() ?: (from + room)
                        pieces += Piece(from, cut, page, top)
                        if (cut < b.height) push += room - (cut - from)
                        from = cut
                        page++
                        top = 0f
                    }
                    out[k] = pieces
                }
            }
        }
        return out
    }

    /** The sheet position of a point on [page] at [top] in its content area, undoing the pushes of [blocks] above it. */
    fun sheetY(blocks: List<Block>, pieces: List<List<Piece>>, page: Int, top: Float, pageHeight: Float): Float {
        val display = page * pageHeight + top
        // The nearest block drawn above the point tells how far things there were pushed.
        var best: Pair<Float, Float>? = null
        for (k in blocks.indices) {
            val p = pieces[k].firstOrNull() ?: continue
            val start = p.page * pageHeight + p.top
            if (start <= display && (best == null || start > best.first)) best = start to (start - blocks[k].y)
        }
        return display - (best?.second ?: 0f)
    }
}
