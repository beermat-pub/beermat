package pub.beermat.app.page

import kotlin.math.floor
import org.jetbrains.skia.paragraph.Paragraph
import pub.beermat.app.editor.Draft
import pub.beermat.app.editor.EditState
import pub.beermat.app.math.Box
import pub.beermat.app.math.Group
import pub.beermat.app.math.MathAlphabet
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.TextBox
import pub.beermat.engine.HostFile
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.export.Units
import pub.beermat.format.Annotation
import pub.beermat.format.AreaEnd
import pub.beermat.format.AreaStart
import pub.beermat.format.CalcBlock
import pub.beermat.format.CalcLine
import pub.beermat.format.Calculation
import pub.beermat.format.ImageRegion
import pub.beermat.format.LineOutcome
import pub.beermat.format.MathRegion
import pub.beermat.format.PageBreak
import pub.beermat.format.PlotData
import pub.beermat.format.PlotRegion
import pub.beermat.format.Region
import pub.beermat.format.Regions
import pub.beermat.format.TableRegion
import pub.beermat.format.TextRegion
import pub.beermat.format.Worksheet
import pub.beermat.format.identityMap

/** The statement being edited: statement [statement] of region [region]. */
data class Editing(val region: Int, val statement: Int, val state: EditState)

/** One statement of a math region as drawn: its box with the baseline at [baseline], and its error below. */
class StatementView(
    val parsed: Statement?,
    val box: Box,
    val baseline: Float,
    val error: Box?,
    val errorBaseline: Float,
    val outcome: LineOutcome?,
) {
    val top: Float get() = baseline - box.ascent
    val bottom: Float get() = error?.let { errorBaseline + it.descent } ?: (baseline + box.descent)
}

/** A region as laid out: at ([x], [y]) on the sheet (points, in the content area), [width] by [height]. */
sealed class RegionView(val index: Int, val region: Region, val x: Float, val y: Float, val width: Float, val height: Float, val hidden: Boolean)

class MathView(index: Int, region: Region, x: Float, y: Float, width: Float, height: Float, hidden: Boolean, val statements: List<StatementView>, val groups: List<List<CalcLine>>) :
    RegionView(index, region, x, y, width, height, hidden)

class TextView(index: Int, region: Region, x: Float, y: Float, width: Float, height: Float, hidden: Boolean, val paragraph: Paragraph) :
    RegionView(index, region, x, y, width, height, hidden)

class PlotView(index: Int, region: Region, x: Float, y: Float, width: Float, height: Float, hidden: Boolean, val data: PlotData?, val problems: List<String>) :
    RegionView(index, region, x, y, width, height, hidden)

/** A picture, [width] by [height] points; [picture] is null when it can't be read. */
class ImageView(index: Int, region: Region, x: Float, y: Float, width: Float, height: Float, hidden: Boolean, val picture: Pictures.Picture?) :
    RegionView(index, region, x, y, width, height, hidden)

/** Page breaks and area lines. */
class TableView(index: Int, region: Region, x: Float, y: Float, width: Float, height: Float, hidden: Boolean, val grid: TableGrid) :
    RegionView(index, region, x, y, width, height, hidden)

class MarkerView(index: Int, region: Region, x: Float, y: Float, width: Float, height: Float, hidden: Boolean) :
    RegionView(index, region, x, y, width, height, hidden)

/**
 * The sheet laid out on pages: every region evaluated, measured, placed (auto-flowing those
 * without a position) and paginated. Rebuilt after every change; the painter draws it and the
 * window hit-tests it. Screen coordinates are points, pages stacked [PAGE_GAP] apart.
 */
/** A page as printed: page [page] of the sheet, or with [column] above 0 its part that many page widths to the right. */
data class PrintedPage(val page: Int, val column: Int = 0)

class SheetLayout(
    val format: PageFormat,
    val views: List<RegionView>,
    val pieces: List<List<Piece>>,
    val calculation: Calculation,
    val sheet: Worksheet,
    /** The math layout used, with this sheet's units. */
    val math: MathLayout,
    private val blocks: List<Block>,
) {
    val pages: Int = maxOf(1, (pieces.flatten().maxOfOrNull { it.page } ?: 0) + 1)

    val totalHeight: Float get() = pages * (format.height + PAGE_GAP)

    /**
     * The pages printed and exported, in order (docs/editor.md#printing): each page, then its parts
     * past the right margin, a page width at a time, where regions reach there; only the pages
     * themselves when the sheet says `single_page_width: true`.
     */
    val printed: List<PrintedPage> by lazy {
        val single = calculation.settings.singlePageWidth
        (0 until pages).flatMap { page ->
            val reach = views.filter { v -> !v.hidden && pieces[v.index].any { it.page == page } }.maxOfOrNull { it.x + it.width } ?: 0f
            val columns = if (single) 1 else maxOf(1, kotlin.math.ceil((reach - 0.5f) / format.contentWidth).toInt())
            (0 until columns).map { PrintedPage(page, it) }
        }
    }

    /** The page's width, or more where regions reach past its right edge (docs/editor.md#the-page). */
    val totalWidth: Float get() = maxOf(format.width, (views.filter { !it.hidden }.maxOfOrNull { format.margin + it.x + it.width } ?: 0f) + format.margin)

    fun pageTop(page: Int) = page * (format.height + PAGE_GAP)

    /** Where [view]'s origin is drawn for [piece]. */
    fun origin(view: RegionView, piece: Piece): Pair<Float, Float> =
        (format.margin + view.x) to (pageTop(piece.page) + format.margin + piece.top - piece.from)

    /** The region under a screen point, and the point in that region's coordinates. */
    fun hit(px: Float, py: Float, pad: Float = 2f): Triple<RegionView, Float, Float>? {
        for (v in views.reversed()) {
            for (p in pieces[v.index]) {
                val (ox, oy) = origin(v, p)
                val rx = px - ox
                val ry = py - oy
                if (rx in -pad..v.width + pad && ry in p.from - pad..p.to + pad) return Triple(v, rx, ry)
            }
        }
        return null
    }

    /** The sheet position of a screen point (for the crosshair). */
    fun sheetPoint(px: Float, py: Float): Pair<Float, Float> {
        val page = floor(py / (format.height + PAGE_GAP)).toInt().coerceAtLeast(0)
        val top = py - pageTop(page) - format.margin
        return (px - format.margin) to Pagination.sheetY(blocks, pieces, page, top, format.contentHeight)
    }

    /** The screen position of a sheet point, as the region at or above it is drawn. */
    fun screenPoint(x: Float, y: Float): Pair<Float, Float> {
        var best: Pair<Float, Float>? = null
        for (v in views) {
            val p = pieces[v.index].firstOrNull() ?: continue
            if (v.y <= y && (best == null || v.y >= best.first)) best = v.y to (p.page * format.contentHeight + p.top - v.y)
        }
        val display = y + (best?.second ?: 0f)
        val page = floor(display / format.contentHeight).toInt().coerceAtLeast(0)
        return (format.margin + x) to (pageTop(page) + format.margin + display - page * format.contentHeight)
    }

    /** Regions drawn over each other (docs/editor.md: flagged, and "Separate regions" pulls them apart). */
    val overlaps: Set<Int> by lazy {
        val drawn = views.filter { !it.hidden && it.height > 0 && it !is MarkerView }
        drawn.filter { a -> drawn.any { b -> b !== a && a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height } }
            .map { it.index }.toSet()
    }

    companion object {
        const val PAGE_GAP = 20f
        const val FLOW_GAP = 10f
        const val STATEMENT_GAP = 6f

        /**
         * [scroll] is where each big array result's table is scrolled to, by region and statement
         * (docs/results.md#big-arrays): the first row and column shown.
         */
        fun build(
            frontmatter: List<String>?,
            regions: List<Region>,
            editing: Editing?,
            math: MathLayout,
            format: PageFormat,
            base: HostFile? = null,
            scroll: Map<Pair<Int, Int>, Pair<Int, Int>> = emptyMap(),
        ): SheetLayout {
            val evaluated = regions.mapIndexed { i, r -> if (editing != null && i == editing.region && r is MathRegion) withStatement(r, editing.statement, editing.state.text) else r }
            val sheet = Regions.sheet(frontmatter, evaluated)
            val calc = Calculation.of(sheet, base)
            val layout = math.forSheet(Units.of(calc.lines.mapNotNull { it.parsed }), calc.settings.multiplication)
            var line = 0
            var block = 0
            var plot = 0
            var tableCount = 0
            val views = mutableListOf<RegionView>()
            val blocks = mutableListOf<Block>()
            var below = 0f
            var area: AreaStart? = null
            var areaY = 0f
            evaluated.forEachIndexed { i, r ->
                val hidden = area?.collapsed == true && r !is AreaEnd
                val x = r.x ?: 0f
                val y = r.y ?: if (views.isEmpty()) 0f else below + FLOW_GAP
                val view: RegionView = when (r) {
                    is MathRegion -> {
                        val groups = Calculation.statements(sheet.blocks[block++])
                        val statements = mutableListOf<StatementView>()
                        var top = 0f
                        groups.forEachIndexed { j, _ ->
                            val o = calc.lines.getOrNull(line++)
                            val active = editing?.takeIf { it.region == i && it.statement == j }
                            val sv = statement(layout, o, active?.state, top, scroll[i to j], calc.settings.origin)
                            statements += sv
                            top = sv.bottom + STATEMENT_GAP
                        }
                        if (groups.isEmpty() && editing?.region == i) statements += statement(layout, null, editing.state, 0f)
                        val height = statements.lastOrNull()?.bottom ?: 0f
                        MathView(i, r, x, y, statements.maxOfOrNull { it.box.width } ?: 0f, height, hidden, statements, groups)
                    }
                    is TextRegion -> {
                        val width = r.attributes["w"]?.toFloatOrNull() ?: (format.contentWidth - x)
                        val p = TextBlock.paragraph(r.lines, width)
                        TextView(i, r, x, y, width, p.height, hidden, p)
                    }
                    is PlotRegion -> {
                        val o = calc.plots.getOrNull(plot++)
                        val w = r.attributes["w"]?.toFloatOrNull() ?: 420f
                        val h = r.attributes["h"]?.toFloatOrNull() ?: 260f
                        PlotView(i, r, x, y, w, h + 14f * (o?.problems?.size ?: 0), hidden, o?.data, o?.problems ?: emptyList())
                    }
                    is TableRegion -> {
                        val o = calc.tables.getOrNull(tableCount++)
                        val grid = TableGrid.of(o, r)
                        TableView(i, r, x, y, grid.width, grid.height, hidden, grid)
                    }
                    is ImageRegion -> {
                        val picture = Pictures.of(r, base)
                        val w = r.attributes["w"]?.toFloatOrNull() ?: picture?.width ?: 200f
                        val h = r.attributes["h"]?.toFloatOrNull() ?: picture?.height?.let { it * w / picture.width } ?: 120f
                        ImageView(i, r, x, y, w, h, hidden, picture)
                    }
                    is PageBreak -> MarkerView(i, r, 0f, y, format.contentWidth, 0f, hidden)
                    is AreaStart -> MarkerView(i, r, 0f, y, format.contentWidth, 16f, hidden)
                    is AreaEnd -> MarkerView(i, r, 0f, y, format.contentWidth, if (area?.collapsed == true) 0f else 8f, hidden = area?.collapsed == true)
                }
                views += view
                var shift = 0f
                when (r) {
                    is AreaStart -> {
                        area = r
                        areaY = y + 16f
                    }
                    is AreaEnd -> {
                        // What follows a collapsed area moves up to just below its line.
                        if (area?.collapsed == true) shift = -(y - areaY).coerceAtLeast(0f)
                        area = null
                    }
                    else -> {}
                }
                // Hidden regions keep their place on the sheet; only the display moves.
                below = maxOf(below, y + view.height)
                blocks += Block(y, view.height, breaks(view), pageBreak = r is PageBreak, hidden = view.hidden, shift = shift)
            }
            val pieces = Pagination.paginate(blocks, format.contentHeight)
            return SheetLayout(format, views, pieces, calc, sheet, layout, blocks)
        }

        /** [r] with statement [k] replaced by [text] (a new statement when [k] is past the end). */
        fun withStatement(r: MathRegion, k: Int, text: String): MathRegion {
            val groups = Calculation.statements(CalcBlock("```calc", r.attributes, r.lines, "```")).toMutableList()
            val lines = text.split('\n').map { CalcLine(it) }
            if (k < groups.size) groups[k] = lines.toMutableList() else groups += lines.toMutableList()
            return r.copy(lines = groups.flatten())
        }

        private fun statement(layout: MathLayout, o: LineOutcome?, editing: EditState?, top: Float, scrolled: Pair<Int, Int>? = null, origin: Int = 0): StatementView {
            // A big array shows a window at a time, as a table (docs/results.md#big-arrays).
            val big = o?.shown?.let { ResultText.table(it) }
            val window = big?.let { ResultText.Window(ResultText.Window.first(scrolled?.first ?: 0, it.rows), ResultText.Window.first(scrolled?.second ?: 0, it.cols)) }
            val table = big?.let { MathLayout.Table(it.rows, it.cols, window!!.row, window.col, origin) }
            // A symbolic result is an expression, cached as its text.
            val parts = o?.shown?.let { ResultText.parts(it, o.format, window) }
                ?: (o?.annotation as? Annotation.Result)?.takeIf { (editing?.statement ?: o.parsed) is pub.beermat.engine.syntax.Symbolic }?.let { ResultText.Parts(it.text, null) }
            val st = editing?.statement ?: o?.parsed ?: Unparsed(null, "?")
            val scrolledLayout = layout.scrolled(MathLayout.Scroll(scrolled?.first ?: 0, scrolled?.second ?: 0, origin))
            val box = if (editing != null) scrolledLayout.editing(drafts(editing)).statement(st, parts?.number, parts?.unit, table) else scrolledLayout.statement(st, parts?.number, parts?.unit, table)
            val message = (o?.annotation as? Annotation.Error)?.message?.takeIf { o.parsed !is Unparsed || editing != null }
            val error = message?.let { layout.message(it, o.errorNames) }
            val baseline = top + box.ascent
            val errorBaseline = baseline + box.descent + 3f + (error?.ascent ?: 0f)
            return StatementView(o?.parsed, box, baseline, error, errorBaseline, o)
        }

        /** The leaf being typed, drawn as typed (`1.`, `M_`). */
        private fun drafts(s: EditState): Map<Expr, String> {
            val d = s.cursor.draft ?: return emptyMap()
            val shown = d.shown() ?: return emptyMap()
            val node = s.node as? Expr ?: return emptyMap()
            val text = if (d.kind == Draft.Kind.NAME) MathAlphabet.italic(shown) else shown
            return identityMap<Expr, String>().apply { put(node, text) }
        }

        /** Where a region may be cut across pages: between statements and between program lines. */
        private fun breaks(v: RegionView): List<Float> = when (v) {
            is MathView -> v.statements.flatMapIndexed { k, s ->
                gaps(s.box).map { it + s.baseline } + (if (k > 0) listOf(s.top - STATEMENT_GAP / 2) else emptyList())
            }.sorted()
            else -> emptyList()
        }

        /** The heights inside [box] (baseline at 0) between lines of text, where it could be cut. */
        private fun gaps(box: Box): List<Float> {
            val spans = mutableListOf<Pair<Float, Float>>()
            fun walk(b: Box, y: Float) {
                when (b) {
                    is TextBox -> spans += (y - b.ascent) to (y + b.descent)
                    is Group -> b.children.forEach { walk(it.box, y + it.y) }
                    else -> {}
                }
            }
            walk(box, 0f)
            spans.sortBy { it.first }
            val out = mutableListOf<Float>()
            var end = spans.firstOrNull()?.second ?: return emptyList()
            for ((a, b) in spans.drop(1)) {
                if (a > end) out += (a + end) / 2
                end = maxOf(end, b)
            }
            return out
        }
    }
}
