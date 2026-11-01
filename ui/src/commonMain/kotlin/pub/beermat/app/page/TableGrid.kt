package pub.beermat.app.page

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Typeface
import pub.beermat.format.Column
import pub.beermat.format.Table
import pub.beermat.format.TableOutcome
import pub.beermat.format.TableRegion

/**
 * A table region laid out (docs/tables.md): the header and cells as text, columns as wide as
 * their widest cell, computed cells showing their values. Drawn by [paint]; edited as its
 * Markdown for now (the grid editor is a later step).
 */
class TableGrid(
    val columns: List<Column>,
    /** The header row, then the body rows: the text each cell shows. */
    val cells: List<List<String>>,
    /** Which body cells are computed (shown slightly tinted). */
    val computed: Set<Pair<Int, Int>>,
    val widths: List<Float>,
    val problems: List<String>,
) {
    val width: Float get() = widths.sum()
    val height: Float get() = cells.size * ROW + problems.size * 13f

    /** Draws the grid; with [cursor], its cell outlined, and the text being typed in it with the caret. */
    fun paint(canvas: Canvas, cursor: TableCursor? = null) {
        var y = 0f
        val header = Paint().apply { color = 0x26C27D18 }
        val tint = Paint().apply { color = 0x10C27D18 }
        val rule = Paint().apply { color = 0x40000000; mode = PaintMode.STROKE; strokeWidth = 0.6f; isAntiAlias = true }
        val ink = Paint().apply { color = 0xFF1A1A1A.toInt(); isAntiAlias = true }
        canvas.drawRect(Rect.makeXYWH(0f, 0f, width, ROW), header)
        cells.forEachIndexed { r, row ->
            var x = 0f
            row.forEachIndexed { c, text ->
                if (r > 0 && (r - 1 to c) in computed) canvas.drawRect(Rect.makeXYWH(x, y, widths[c], ROW), tint)
                val font = if (r == 0) BOLD else REGULAR
                val w = font.measureTextWidth(text)
                val tx = when (columns[c].align) {
                    Column.Align.RIGHT -> x + widths[c] - PAD - w
                    Column.Align.CENTRE -> x + (widths[c] - w) / 2
                    Column.Align.LEFT -> x + PAD
                }
                canvas.drawString(text, tx, y + ROW - 6f, font, ink)
                x += widths[c]
            }
            y += ROW
            canvas.drawLine(0f, y, width, y, rule)
        }
        canvas.drawLine(0f, 0f, width, 0f, rule)
        cursor?.takeIf { it.row < cells.size && it.col < widths.size }?.let { c ->
            val x = widths.take(c.col).sum()
            val top = c.row * ROW
            val cell = Rect.makeXYWH(x, top, widths[c.col], ROW)
            val blue = 0xFF1E5BD8.toInt()
            c.draft?.let { d ->
                // Typing: the cell shows what is typed, from its left edge, wider than the column if need be.
                val font = if (c.row == 0) BOLD else REGULAR
                val w = maxOf(widths[c.col], font.measureTextWidth(d) + 2 * PAD)
                canvas.drawRect(Rect.makeXYWH(x, top, w, ROW), Paint().apply { color = 0xFFFFFFFF.toInt() })
                canvas.drawString(d, x + PAD, top + ROW - 6f, font, ink)
                val cx = x + PAD + font.measureTextWidth(d.take(c.caret))
                canvas.drawLine(cx, top + 3f, cx, top + ROW - 3f, Paint().apply { color = blue; strokeWidth = 1f })
                canvas.drawRect(Rect.makeXYWH(x, top, w, ROW), Paint().apply { color = blue; mode = PaintMode.STROKE; strokeWidth = 1.5f; isAntiAlias = true })
            } ?: canvas.drawRect(cell, Paint().apply { color = blue; mode = PaintMode.STROKE; strokeWidth = 1.5f; isAntiAlias = true })
        }
        problems.forEachIndexed { k, p ->
            canvas.drawString(p, 0f, y + 11f + 13f * k, SMALL, Paint().apply { color = 0xFFD32F2F.toInt(); isAntiAlias = true })
        }
    }

    companion object {
        const val ROW = 18f
        const val PAD = 6f

        private val typeface: Typeface by lazy {
            listOf("Segoe UI", "Helvetica Neue", "Arial", "Liberation Sans").firstNotNullOfOrNull { FontMgr.default.matchFamilyStyle(it, FontStyle.NORMAL) }
                ?: PageFonts.provider.matchFamilyStyle("Liberation Sans", FontStyle.NORMAL)
                ?: FontMgr.default.legacyMakeTypeface("", FontStyle.NORMAL)!!
        }
        private val REGULAR by lazy { Font(typeface, 11f) }
        private val BOLD by lazy { Font(typeface, 11f).apply { isEmboldened = true } }
        private val SMALL by lazy { Font(typeface, 9f) }

        fun of(o: TableOutcome?, r: TableRegion): TableGrid {
            val table = o?.table ?: Table.parse(r.body)
            val header = table.columns.map { it.header }
            val body = table.rows.mapIndexed { i, row ->
                row.mapIndexed { c, cell -> o?.shown?.get(i to c) ?: if (Table.expression(cell) != null) "?" else cell }
            }
            val computed = table.rows.indices.flatMap { i -> table.columns.indices.filter { c -> Table.expression(table.rows[i][c]) != null }.map { i to it } }.toSet()
            val all = listOf(header) + body
            val widths = table.columns.indices.map { c -> all.maxOf { row -> (if (row === header) BOLD else REGULAR).measureTextWidth(row[c]) } + 2 * PAD }
            return TableGrid(table.columns, all, computed, widths, o?.problems ?: emptyList())
        }
    }
}
