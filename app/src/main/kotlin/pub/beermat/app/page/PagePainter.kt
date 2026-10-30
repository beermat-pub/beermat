package pub.beermat.app.page

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathEffect
import org.jetbrains.skia.Rect as SkRect
import pub.beermat.app.editor.Draft
import pub.beermat.app.editor.Side
import pub.beermat.app.math.BoxPaths
import pub.beermat.app.math.MathAlphabet
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.plot.PlotPainter
import pub.beermat.engine.syntax.Id
import pub.beermat.format.AreaEnd
import pub.beermat.format.AreaStart
import pub.beermat.format.PageBreak

/** What the window draws over the sheet: the cursor, the crosshair, the region being dragged. */
data class Overlay(
    val editing: Editing? = null,
    /** The red crosshair, in sheet coordinates. */
    val crosshair: Pair<Float, Float>? = null,
    /** A region being edited as text, or selected. */
    val active: Int? = null,
    /** The regions being dragged, and by how much. */
    val drag: Triple<Set<Int>, Float, Float>? = null,
    /** Regions selected as a whole. */
    val selected: Set<Int> = emptySet(),
    /** The selection rectangle being dragged out: left, top, right, bottom, in screen points. */
    val band: List<Float>? = null,
    /** A picture or plot being resized: its index and new size, in points. */
    val resize: Triple<Int, Float, Float>? = null,
    /** The cell cursor in a table being edited as a grid. */
    val table: TableCursor? = null,
    /** The list of a module's functions open at the cursor (`bt.`). */
    val completion: Session.Completion? = null,
    /** A tip by the mouse, in screen points: a module function's signature and docstring. */
    val hover: Tip? = null,
) {
    data class Tip(val x: Float, val y: Float, val lines: List<String>)
}

/** Draws a [SheetLayout] with Skia, in points (the caller scales the canvas): pages, regions, overlay. */
object PagePainter {

    private val BLUE = 0xFF1E5BD8.toInt()
    private val RED = 0xFFD32F2F.toInt()
    private val GREEN = 0xFF2E9E4F.toInt()

    fun paint(canvas: Canvas, layout: SheetLayout, overlay: Overlay = Overlay(), screenWidth: Float = layout.format.width) {
        val f = layout.format
        val left = maxOf(0f, (screenWidth - f.width) / 2)
        canvas.save()
        canvas.translate(left, 0f)
        for (p in 0 until layout.pages) {
            val top = layout.pageTop(p)
            canvas.drawRect(SkRect.makeXYWH(2f, top + 2f, f.width, f.height), fill(0x22000000))
            canvas.drawRect(SkRect.makeXYWH(0f, top, f.width, f.height), fill(0xFFFFFFFF.toInt()))
            // The right margin, faint, as a guide.
            canvas.drawLine(f.width - f.margin, top + f.margin * 0.5f, f.width - f.margin, top + f.height - f.margin * 0.5f, stroke(0x22000000, 0.6f, dashed = true))
            margins(canvas, layout, p, top)
        }
        for (v in layout.views) {
            if (v.hidden) continue
            val dx = overlay.drag?.takeIf { v.index in it.first }?.second ?: 0f
            val dy = overlay.drag?.takeIf { v.index in it.first }?.third ?: 0f
            for (piece in layout.pieces[v.index]) {
                val (ox, oy) = layout.origin(v, piece)
                canvas.save()
                canvas.translate(ox + dx, oy + dy)
                canvas.clipRect(SkRect.makeLTRB(-40f, piece.from - 4f, maxOf(v.width, f.contentWidth) + 40f, piece.to + 4f))
                region(canvas, layout, v, overlay, print = false)
                canvas.restore()
            }
        }
        overlay.completion?.let { list -> overlay.editing?.let { completion(canvas, layout, it, list) } }
        overlay.hover?.let { tip(canvas, it) }
        overlay.crosshair?.let { (x, y) ->
            val (sx, sy) = layout.screenPoint(x, y)
            val p = stroke(RED, 1.2f)
            canvas.drawLine(sx - 6f, sy, sx + 6f, sy, p)
            canvas.drawLine(sx, sy - 6f, sx, sy + 6f, p)
        }
        overlay.band?.let { (l, t, r, b) ->
            val rect = SkRect.makeLTRB(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
            // As in AutoCAD: left to right (wholly inside) solid blue; right to left (touching) dashed green.
            val crossing = r < l
            canvas.drawRect(rect, fill(if (crossing) 0x1A2E9E4F else 0x141E5BD8))
            canvas.drawRect(rect, stroke(if (crossing) GREEN else BLUE, 0.6f, dashed = crossing))
        }
        canvas.restore()
    }

    /**
     * One page as printed (docs/editor.md#printing), in page points from its top-left corner: its
     * regions only, without the screen's paper, guides, page-break lines and overlap marks.
     */
    fun paintPage(canvas: Canvas, layout: SheetLayout, page: Int) {
        margins(canvas, layout, page, 0f)
        val top = layout.pageTop(page)
        canvas.save()
        canvas.translate(0f, -top)
        for (v in layout.views) {
            if (v.hidden) continue
            for (piece in layout.pieces[v.index]) {
                if (piece.page != page) continue
                val (ox, oy) = layout.origin(v, piece)
                canvas.save()
                canvas.translate(ox, oy)
                canvas.clipRect(SkRect.makeLTRB(-40f, piece.from - 4f, maxOf(v.width, layout.format.contentWidth) + 40f, piece.to + 4f))
                region(canvas, layout, v, Overlay(), print = true)
                canvas.restore()
            }
        }
        canvas.restore()
    }

    /** The sheet's header and footer (docs/file-format.md#frontmatter) in the top and bottom margins of [page], whose top is at [top]. */
    private fun margins(canvas: Canvas, layout: SheetLayout, page: Int, top: Float) {
        val settings = layout.calculation.settings
        val f = layout.format
        val font = SkiaMath.font(9f)
        val paint = fill(0xFF444444.toInt())
        for ((text, baseline) in listOf(settings.header to top + f.margin / 2 + 3f, settings.footer to top + f.height - f.margin / 2 + 3f)) {
            val (left, centre, right) = text?.filled(page + 1, layout.pages, settings.title) ?: continue
            canvas.drawString(left, f.margin, baseline, font, paint)
            canvas.drawString(centre, (f.width - font.measureTextWidth(centre)) / 2, baseline, font, paint)
            canvas.drawString(right, f.width - f.margin - font.measureTextWidth(right), baseline, font, paint)
        }
    }

    /** One region on its own, from its top-left corner, as printed (Copy as image). */
    fun paintRegion(canvas: Canvas, layout: SheetLayout, v: RegionView) = region(canvas, layout, v, Overlay(), print = true)

    private fun region(canvas: Canvas, layout: SheetLayout, v: RegionView, overlay: Overlay, print: Boolean) {
        val editing = overlay.editing?.takeIf { it.region == v.index }
        if (v.index in overlay.selected) {
            val box = SkRect.makeXYWH(-3f, -3f, maxOf(v.width, 4f) + 6f, maxOf(v.height, 4f) + 6f)
            canvas.drawRect(box, fill(0x141E5BD8))
            canvas.drawRect(box, stroke(BLUE, 0.8f, dashed = true))
            // A picture or plot alone in the selection has a handle on its corner, to resize it.
            if (overlay.selected.size == 1 && (v is ImageView || v is PlotView)) {
                canvas.drawRect(SkRect.makeXYWH(v.width - 3f, v.height - 3f, 6f, 6f), fill(BLUE))
            }
            overlay.resize?.takeIf { it.first == v.index }?.let { (_, w, h) -> canvas.drawRect(SkRect.makeWH(w, h), stroke(BLUE, 0.8f, dashed = true)) }
        } else if (editing != null || overlay.active == v.index) {
            canvas.drawRect(SkRect.makeXYWH(-3f, -3f, v.width + 6f, v.height + 6f), stroke(0x55000000, 0.6f, dashed = true))
        } else if (v.index in layout.overlaps && !print) {
            canvas.drawRect(SkRect.makeXYWH(-2f, -2f, v.width + 4f, v.height + 4f), stroke(0x99D32F2F.toInt(), 0.6f))
        }
        when (v) {
            is MathView -> v.statements.forEachIndexed { k, s ->
                SkiaMath.paint(canvas, s.box, 0f, s.baseline)
                s.error?.let { SkiaMath.paint(canvas, it, 0f, s.errorBaseline) }
                if (editing != null && editing.statement == k) cursor(canvas, s, editing)
            }
            is TextView -> v.paragraph.paint(canvas, 0f, 0f)
            is TableView -> v.grid.paint(canvas, overlay.table?.takeIf { it.region == v.index })
            is PlotView -> {
                v.data?.let { PlotPainter.paint(canvas, it, 0f, 0f, v.width, v.height - 14f * v.problems.size, 9f) }
                v.problems.forEachIndexed { k, p ->
                    canvas.drawString(p, 0f, v.height - 14f * (v.problems.size - k) + 11f, SkiaMath.font(9f), fill(RED))
                }
            }
            is ImageView -> Pictures.paint(canvas, v.picture, v.width, v.height, (v.region as pub.beermat.format.ImageRegion).alt)
            is MarkerView -> if (!print || v.region !is PageBreak) marker(canvas, v)
        }
    }

    private fun marker(canvas: Canvas, v: MarkerView) {
        when (val r = v.region) {
            is PageBreak -> canvas.drawLine(0f, 0f, v.width, 0f, stroke(0x66000000, 0.6f, dashed = true))
            is AreaStart -> {
                val y = 11f
                canvas.drawString(if (r.collapsed) "▸" else "▾", 0f, y, SkiaMath.font(11f), fill(0xFF555555.toInt()))
                canvas.drawString(r.title, 12f, y, SkiaMath.font(10f), fill(0xFF555555.toInt()))
                val start = 18f + SkiaMath.measure(r.title, 10f).width
                canvas.drawLine(start, y - 4f, v.width, y - 4f, stroke(0x66000000, 0.6f))
            }
            is AreaEnd -> canvas.drawLine(0f, 4f, v.width, 4f, stroke(0x66000000, 0.6f))
            else -> {}
        }
    }

    /**
     * The list of a module's functions below the cursor, as in VS Code: the names that match what is
     * typed (eight at most, scrolled to the picked one), and the picked one's signature and the first
     * lines of its docstring below them.
     */
    private fun completion(canvas: Canvas, layout: SheetLayout, e: Editing, list: Session.Completion) {
        val v = layout.views.getOrNull(e.region) as? MathView ?: return
        val s = v.statements.getOrNull(e.statement) ?: return
        val r = BoxPaths.rect(s.box, e.state.cursor.path, 0f, s.baseline) ?: return
        val piece = layout.pieces[v.index].firstOrNull { r.bottom in it.from..it.to } ?: layout.pieces[v.index].firstOrNull() ?: return
        val (ox, oy) = layout.origin(v, piece)
        val font = SkiaMath.font(10f)
        val line = 14f
        val first = (list.selected - 7).coerceAtLeast(0)
        val shown = list.names.drop(first).take(8)
        val doc = list.docs[list.names[list.selected]]
        val details = doc?.let { listOf(it.signature) + it.text.lines().filter { l -> l.isNotBlank() }.take(3) }.orEmpty()
        val width = (shown + details).maxOf { SkiaMath.measure(it, 10f).width } + 16f
        val height = line * (shown.size + details.size) + (if (details.isEmpty()) 6f else 12f)
        val x = ox + r.x
        val y = oy + r.bottom + 4f
        canvas.drawRect(SkRect.makeXYWH(x + 1.5f, y + 1.5f, width, height), fill(0x22000000))
        canvas.drawRect(SkRect.makeXYWH(x, y, width, height), fill(0xFFFFFFFF.toInt()))
        canvas.drawRect(SkRect.makeXYWH(x, y, width, height), stroke(0x55000000, 0.6f))
        shown.forEachIndexed { k, name ->
            val top = y + 3f + k * line
            if (first + k == list.selected) canvas.drawRect(SkRect.makeXYWH(x + 1f, top, width - 2f, line), fill(0x221E5BD8))
            canvas.drawString(name, x + 8f, top + 10.5f, font, fill(0xFF1A1A1A.toInt()))
        }
        if (details.isNotEmpty()) {
            val top = y + 3f + shown.size * line + 3f
            canvas.drawLine(x, top, x + width, top, stroke(0x33000000, 0.6f))
            details.forEachIndexed { k, text ->
                canvas.drawString(text, x + 8f, top + 3f + k * line + 10.5f, font, fill(if (k == 0) 0xFF1A1A1A.toInt() else 0xFF555555.toInt()))
            }
        }
    }

    /** A tip just below the mouse: a signature, then the first lines of its docstring in grey. */
    private fun tip(canvas: Canvas, tip: Overlay.Tip) {
        val font = SkiaMath.font(10f)
        val line = 14f
        val width = tip.lines.maxOf { SkiaMath.measure(it, 10f).width } + 16f
        val height = line * tip.lines.size + 8f
        val (x, y) = tip.x + 4f to tip.y + 18f
        canvas.drawRect(SkRect.makeXYWH(x + 1.5f, y + 1.5f, width, height), fill(0x22000000))
        canvas.drawRect(SkRect.makeXYWH(x, y, width, height), fill(0xFFFFFDF2.toInt()))
        canvas.drawRect(SkRect.makeXYWH(x, y, width, height), stroke(0x55000000, 0.6f))
        tip.lines.forEachIndexed { k, text ->
            canvas.drawString(text, x + 8f, y + 4f + k * line + 10.5f, font, fill(if (k == 0) 0xFF1A1A1A.toInt() else 0xFF555555.toInt()))
        }
    }

    /** The insertion cursor:the selection underlined, and a bar at its side (or the caret, while typing). */
    private fun cursor(canvas: Canvas, s: StatementView, editing: Editing) {
        val c = editing.state.cursor
        val r = BoxPaths.rect(s.box, c.path, 0f, s.baseline) ?: return
        val p = stroke(BLUE, 1.1f)
        val bottom = r.bottom + 1.5f
        canvas.drawLine(r.x, bottom, r.right, bottom, p)
        val d = c.draft
        val x = when {
            d != null -> r.x + r.width * caretFraction(d)
            c.side == Side.LEFT -> r.x
            else -> r.right
        }
        canvas.drawLine(x, r.top - 1f, x, bottom, p)
    }

    /** How far along a leaf the caret is, measured in its own glyphs. */
    private fun caretFraction(d: Draft): Float {
        if (d.caret <= 0 || d.text.isEmpty()) return 0f
        if (d.caret >= d.text.length) return 1f
        fun shown(t: String) = when (d.kind) {
            Draft.Kind.NAME -> if (d.style == Id.Style.BOLD) MathAlphabet.bold(t) else if (d.style == Id.Style.PLAIN) MathAlphabet.italic(t) else t
            Draft.Kind.STRING -> "“$t"
            Draft.Kind.NUMBER -> t
        }
        val full = SkiaMath.measure(shown(d.text) + if (d.kind == Draft.Kind.STRING) "”" else "", 12f).width
        return if (full <= 0f) 1f else (SkiaMath.measure(shown(d.text.take(d.caret)), 12f).width / full).coerceIn(0f, 1f)
    }

    private fun fill(color: Int) = Paint().apply { this.color = color; isAntiAlias = true }

    private fun stroke(color: Int, width: Float, dashed: Boolean = false) = Paint().apply {
        this.color = color
        mode = PaintMode.STROKE
        strokeWidth = width
        isAntiAlias = true
        if (dashed) pathEffect = PathEffect.makeDash(floatArrayOf(3f, 3f), 0f)
    }
}
