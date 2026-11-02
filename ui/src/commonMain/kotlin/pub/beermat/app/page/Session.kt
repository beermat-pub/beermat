package pub.beermat.app.page

import kotlin.math.abs
import kotlin.math.roundToInt
import pub.beermat.app.editor.Cursor
import pub.beermat.app.editor.EditState
import pub.beermat.app.editor.Editor
import pub.beermat.app.editor.Key
import pub.beermat.app.editor.Nodes
import pub.beermat.app.logError
import pub.beermat.app.math.BoxPaths
import pub.beermat.app.math.MathLayout
import pub.beermat.engine.HostFile
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.display.Rounding
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Multiplication
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.ResultSlot
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.format.AreaEnd
import pub.beermat.format.AreaStart
import pub.beermat.format.Attributes
import pub.beermat.format.CalcLine
import pub.beermat.format.Calculation
import pub.beermat.format.ImageRegion
import pub.beermat.format.MathRegion
import pub.beermat.format.PageBreak
import pub.beermat.format.PlotRegion
import pub.beermat.format.Region
import pub.beermat.format.Regions
import pub.beermat.format.TableRegion
import pub.beermat.format.TextRegion
import pub.beermat.format.WorksheetText
import pub.beermat.format.with
import pub.beermat.format.without

/**
 * An open worksheet being edited (docs/editor.md): its regions, the statement or text being
 * edited, the crosshair, undo. Everything the window does goes through here, so it is tested
 * without a window; [layout] is what the window draws.
 */
class Session(text: String, private val math: MathLayout) {

    var frontmatter: List<String>? = null
        private set
    var regions: List<Region> = emptyList()
        private set

    /** The statement being edited structurally. */
    var editing: Editing? = null
        private set

    /** A region (or a statement that doesn't parse, as `region to statement`) being edited as text. */
    var textEditing: Pair<Int, Int?>? = null
        private set

    /** The cell cursor, when a table region is being edited as a grid (docs/tables.md#editing). */
    var table: TableCursor? = null

    /** The red crosshair, in sheet coordinates. */
    var crosshair: Pair<Float, Float>? = 0f to 0f
        private set

    var dirty = false
        private set

    /** Regions selected as a whole (a rectangle dragged on the paper, or Ctrl+click), to move or delete together. */
    var selected: Set<Int> = emptySet()
        private set

    lateinit var layout: SheetLayout
        private set

    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()

    private data class Snapshot(val regions: List<Region>, val editing: Editing?, val crosshair: Pair<Float, Float>?, val frontmatter: List<String>?)

    /** Typing in one name coalesces into one undo step. */
    private var typingRun = false

    val format: PageFormat get() = PageFormat.of(frontmatter?.firstNotNullOfOrNull { Regex("""page:\s*(\S+)""").matchEntire(it.trim())?.groupValues?.get(1) })

    init {
        load(text)
    }

    /** (Re)reads the sheet from its text, placing every region (so its position no longer depends on the others). */
    fun load(text: String) {
        read(text)
        crosshair = 0f to (layout.views.filter { !it.hidden }.maxOfOrNull { it.y + it.height + SheetLayout.FLOW_GAP } ?: 0f).snap()
        dirty = false
        undo.clear()
        redo.clear()
    }

    private fun read(text: String) {
        val sheet = WorksheetText.read(text)
        frontmatter = sheet.frontmatter
        regions = Regions.of(sheet)
        editing = null
        textEditing = null
        relayout()
        regions = regions.mapIndexed { i, r ->
            val v = layout.views[i]
            if (r.x == null || r.y == null) r.movedTo(v.x, v.y) else r
        }
        relayout()
    }

    /**
     * The sheet as it stands, unsaved changes and the statement being edited included, with fresh results
     * (it doesn't save). New regions get ids in this text only, as they would when saved, so it reads back as it is.
     */
    fun text(): String {
        val e = editing
        val now = withIds(regions.mapIndexed { i, r ->
            if (e != null && i == e.region && r is MathRegion) SheetLayout.withStatement(r, e.statement, Printer.print(Editor.commit(e.state).statement)) else r
        })
        return WorksheetText.write(Calculation.of(Regions.sheet(frontmatter, now), base).written())
    }

    /** The sheet replaced by [text] (an edit from Claude through MCP): one undoable change, unsaved. */
    fun replace(text: String) {
        snapshot()
        val crosshairWas = crosshair
        read(text)
        crosshair = crosshairWas
        dirty = true
    }

    /** The sheet's folder, where pictures linked by file name are found; null for a new sheet. */
    var base: HostFile? = null
        set(value) {
            field = value
            relayout()
        }

    fun relayout() {
        layout = SheetLayout.build(frontmatter, regions, editing, math, format, base)
        // `.` after a name that holds a module picks one of its functions (docs/symbolic.md#python-functions-in-a-sheet).
        val modules = layout.calculation.modules
        Editor.holdsModule = { it in modules }
    }

    // Completion: the functions of a module, after `bt.`.

    /** The list open at the cursor: the module's functions that start with what is typed, the one picked, and their docs. */
    data class Completion(val names: List<String>, val selected: Int, val docs: Map<String, pub.beermat.engine.eval.Module.Doc>)

    private var completionIndex = 0
    private var completionTyped: String? = null

    /** Where Escape closed the list: it stays closed there until the cursor moves on or more is typed. */
    private var completionClosed: Pair<List<Int>, String>? = null

    val completion: Completion?
        get() {
            val e = editing ?: return null
            val (id, typed) = Editor.memberAt(e.state) ?: return null
            if (completionClosed == e.state.cursor.path to typed) return null
            val module = layout.calculation.modules[id] ?: return null
            val names = module.functions.keys.sorted().filter { it.startsWith(typed, ignoreCase = true) }
            if (names.isEmpty() || names == listOf(typed)) return null
            if (typed != completionTyped) {
                completionTyped = typed
                completionIndex = 0
            }
            return Completion(names, completionIndex.coerceIn(0, names.lastIndex), module.docs)
        }

    /**
     * What to show while the mouse rests at ([px], [py]): over a module's function (`bt.f`), its
     * signature and the first lines of its docstring; null elsewhere.
     */
    fun tip(px: Float, py: Float): List<String>? {
        val (view, rx, ry) = layout.hit(px, py) ?: return null
        if (view !is MathView) return null
        val s = view.statements.firstOrNull { ry <= it.bottom + SheetLayout.STATEMENT_GAP / 2 } ?: return null
        val parsed = s.parsed?.takeIf { it !is Unparsed } ?: return null
        val tagged = layout.math.editing(emptyMap()).statement(parsed, null, null)
        val path = BoxPaths.at(tagged, rx, ry, 0f, s.baseline) ?: return null
        // The member itself, or the name after its dot.
        for (p in listOf(path, path.dropLast(1))) {
            val member = Nodes.at(parsed, p) as? Apply ?: continue
            if (member.op != Op.MEMBER) continue
            val module = layout.calculation.modules[member.args[0] as? Id ?: continue] ?: continue
            val doc = module.docs[(member.args[1] as? Id)?.text ?: continue] ?: continue
            return listOf(doc.signature) + doc.text.lines().filter { it.isNotBlank() }.take(4)
        }
        return null
    }

    /** Up or Down in the open list. */
    fun moveCompletion(by: Int): Boolean {
        val c = completion ?: return false
        completionIndex = (c.selected + by).coerceIn(0, c.names.lastIndex)
        return true
    }

    /** Enter or Tab: the picked function goes in, ready for its `(`. */
    fun acceptCompletion(): Boolean {
        val c = completion ?: return false
        return edit { Editor.complete(it, c.names[c.selected]) }
    }

    /** Escape: the list closes, the cursor stays. */
    fun closeCompletion(): Boolean {
        val e = editing ?: return false
        val (_, typed) = Editor.memberAt(e.state) ?: return false
        completionClosed = e.state.cursor.path to typed
        return true
    }

    // The file.

    /** The text to save: regions in evaluation order, ids assigned, results cached. */
    fun save(): String {
        commitEditing()
        assignIds()
        val sheet = Regions.sheet(frontmatter, regions)
        dirty = false
        return WorksheetText.write(Calculation.of(sheet, base).written())
    }

    private fun assignIds() {
        regions = withIds(regions)
    }

    /** [list] with an id given to each region that needs one and has none (the file's text marks a region by its id). */
    private fun withIds(list: List<Region>): List<Region> {
        val used = list.mapNotNull { it.attributes.id }.toMutableSet()
        return list.map { r ->
            if (r.attributes.id != null || r is PageBreak || r is AreaEnd) return@map r
            val prefix = when (r) {
                is MathRegion -> "r"
                is TextRegion -> "t"
                is PlotRegion -> "p"
                is ImageRegion -> "i"
                else -> "a"
            }
            val id = generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in used }
            used += id
            r.withAttributes(r.attributes.copy(id = id))
        }
    }

    // Clicks.

    /** A click at a screen point (points, pages' left edge at 0). */
    fun click(px: Float, py: Float) {
        selected = emptySet()
        val hit = layout.hit(px, py)
        if (hit == null) {
            leave()
            val (x, y) = layout.sheetPoint(px, py)
            crosshair = x.snap().coerceAtLeast(0f) to y.snap().coerceAtLeast(0f)
            return
        }
        val (view, rx, ry) = hit
        when (view) {
            is MathView -> clickMath(view, rx, ry)
            is TableView -> {
                val cell = TableEditor.cellAt(view.grid, rx, ry)
                leave()
                crosshair = null
                val index = regions.indexOfFirst { it === view.region }
                if (cell == null || index < 0) textEditing = index.takeIf { it >= 0 }?.let { it to null }
                else table = TableCursor(index, cell.first, cell.second)
            }
            is TextView, is PlotView -> {
                leave()
                textEditing = view.index to null
                crosshair = null
            }
            is ImageView -> {
                // A picture is selected by a click, to move or delete; leaving may renumber the regions.
                leave()
                crosshair = null
                selected = setOf(regions.indexOfFirst { it === view.region }).filter { it >= 0 }.toSet()
            }
            is MarkerView -> {
                val r = view.region
                if (r is AreaStart) {
                    snapshot()
                    leave()
                    val a = if (r.collapsed) r.attributes.without("collapsed") else r.attributes.with("collapsed", null)
                    regions = regions.toMutableList().also { it[view.index] = r.withAttributes(a) }
                    changed()
                }
            }
        }
    }

    private fun clickMath(view: MathView, rx: Float, ry: Float) {
        val k = view.statements.indexOfFirst { ry <= it.bottom + SheetLayout.STATEMENT_GAP / 2 }.let { if (it < 0) view.statements.lastIndex else it }
        if (k < 0) return
        val sv = view.statements[k]
        val current = editing
        if (current != null && current.region == view.index && current.statement == k) {
            // Within the statement being edited: move the cursor.
            BoxPaths.at(sv.box, rx, ry, 0f, sv.baseline)?.let { path -> setEditing(current.copy(state = select(current.state, sv, path, rx)), changed = false) }
            return
        }
        leave()
        // Leaving may have removed an empty region above: find this one again.
        val v = layout.views.firstOrNull { it.region === view.region } as? MathView ?: return
        val s = v.statements.getOrNull(k) ?: return
        val parsed = s.parsed
        if (parsed == null || parsed is Unparsed) {
            textEditing = v.index to k
            crosshair = null
            relayout()
            return
        }
        val parts = s.outcome?.shown?.let { ResultText.parts(it, s.outcome.format) }
        val tagged = layout.math.editing(emptyMap()).statement(parsed, parts?.number, parts?.unit)
        val path = BoxPaths.at(tagged, rx, ry, 0f, s.baseline)
        val state = EditState.of(parsed)
        setEditing(Editing(v.index, k, if (path == null) state else select(state, s, path, rx, tagged)), changed = false)
        crosshair = null
    }

    /** The cursor at [path], with the caret where the click fell in a name or number. */
    private fun select(state: EditState, sv: StatementView, path: List<Int>, rx: Float, box: pub.beermat.app.math.Box = sv.box): EditState {
        val node = Nodes.at(state.statement, path)
        val r = BoxPaths.rect(box, path, 0f, sv.baseline)
        val len = when (node) {
            is pub.beermat.engine.syntax.Id -> node.label.length
            is pub.beermat.engine.syntax.Num -> node.text.length
            is pub.beermat.engine.syntax.Str -> node.value.length
            else -> null
        }
        val caret = if (r != null && len != null && r.width > 0) ((rx - r.x) / r.width * len).roundToInt().coerceIn(0, len) else null
        return Editor.select(state, path, caret)
    }

    // Keys.

    /** A key for the statement being edited, or for a new region at the crosshair. Returns false when it does nothing. */
    fun key(k: Key): Boolean {
        val e = editing
        if (e == null) {
            val c = crosshair ?: return false
            if (k !is Key.Type && k !is Key.Ctrl) return false
            if (k is Key.Type && k.c == '"') {
                snapshot()
                val r = TextRegion(Attributes.NONE, emptyList()).movedTo(c.first, c.second)
                regions = regions + r
                sortRegions()
                textEditing = regions.indexOfFirst { it === r } to null
                crosshair = null
                changed()
                return true
            }
            if (k is Key.Type && k.c == '@') {
                insertPlot()
                return true
            }
            if (k is Key.Type && k.c == ' ') return false
        }
        return edit(typing = k is Key.Type) { Editor.key(it, k) }
    }

    /**
     * An edit of the statement being edited ([change] gives null when it does nothing there);
     * with none, of a new math region at the crosshair. [typing] coalesces a run of typing in one
     * name into one undo step.
     */
    fun edit(typing: Boolean = false, change: (EditState) -> EditState?): Boolean {
        selected = emptySet()
        val e = editing
        if (e == null) {
            val c = crosshair ?: return false
            val next = change(EditState.empty()) ?: return false
            snapshot()
            val r = MathRegion(Attributes.NONE, emptyList()).movedTo(c.first, c.second)
            regions = regions + r
            sortRegions()
            crosshair = null
            setEditing(Editing(regions.indexOfFirst { it === r }, 0, next))
            typingRun = next.cursor.draft != null
            return true
        }
        val next = change(e.state) ?: return false
        val run = typing && next.cursor.draft != null && e.state.cursor.draft != null && next.cursor.path == e.state.cursor.path
        if (!run || !typingRun) snapshot()
        typingRun = run
        setEditing(e.copy(state = next))
        return true
    }

    /** Up and Down: to the nearest leaf above or below (a numerator, a denominator, the next program line). */
    fun vertical(up: Boolean): Boolean {
        val e = editing ?: return false
        val sv = (layout.views.getOrNull(e.region) as? MathView)?.statements?.getOrNull(e.statement) ?: return false
        val here = BoxPaths.rect(sv.box, e.state.cursor.path, 0f, sv.baseline) ?: return false
        val cy = (here.top + here.bottom) / 2
        val cx = (here.x + here.right) / 2
        val best = Nodes.leaves(e.state.statement)
            .mapNotNull { p -> BoxPaths.rect(sv.box, p, 0f, sv.baseline)?.let { p to it } }
            .filter { (_, r) -> val y = (r.top + r.bottom) / 2; if (up) y < cy - 2f else y > cy + 2f }
            .minByOrNull { (_, r) -> abs((r.top + r.bottom) / 2 - cy) * 2 + abs((r.x + r.right) / 2 - cx) } ?: return false
        setEditing(e.copy(state = Editor.enter(e.state, best.first, atEnd = true)), changed = false)
        return true
    }

    /** `Ctrl+M`: a matrix in the empty slot at the cursor. */
    fun matrix(rows: Int, cols: Int): Boolean = edit { Editor.matrix(it, rows, cols) }

    /** Whether the cursor is in a matrix, so `Ctrl+M` offers to resize it. */
    val inMatrix: Boolean get() = editing?.let { Editor.matrixAt(it.state) } != null

    /** `Ctrl+M` in a matrix: rows and columns inserted after the cursor's cell, or deleted from it. */
    fun resizeMatrix(rows: Int, cols: Int, insert: Boolean): Boolean = edit { Editor.resizeMatrix(it, rows, cols, insert) }

    private fun setEditing(e: Editing, changed: Boolean = true) {
        editing = e
        textEditing = null
        if (changed) dirty = true
        relayout()
    }

    /** Enter: leave the region, the crosshair below it; with only the crosshair, the crosshair down a line. */
    fun enter() {
        val v = (editing?.region ?: textEditing?.first)?.let { layout.views.getOrNull(it) }
        if (v == null && selected.isEmpty()) crosshair?.let { (x, y) -> crosshair = x to y + LINE }
        leave()
        if (v != null) crosshair = v.x.snap() to (v.y + v.height + SheetLayout.FLOW_GAP).snap()
    }

    /** An arrow key off the math: the selected regions, or else the crosshair, one grid step that way. */
    fun nudge(dx: Int, dy: Int): Boolean {
        if (selected.isNotEmpty()) {
            move(selected, dx * GRID, dy * GRID)
            return true
        }
        val (x, y) = crosshair ?: return false
        crosshair = (x + dx * GRID).coerceAtLeast(0f) to (y + dy * GRID).coerceAtLeast(0f)
        return true
    }

    /** Tab: the next placeholder; with none left (or only the crosshair), the next math region. Shift+Tab goes back. */
    fun tab(back: Boolean): Boolean {
        if (key(if (back) Key.BackTab else Key.Tab)) return true
        val maths = layout.views.filterIsInstance<MathView>().filter { !it.hidden }
        val here = editing?.region
        val c = crosshair
        val after = { v: MathView -> if (here != null) v.index > here else c != null && (v.y > c.second || v.y == c.second && v.x > c.first) }
        val before = { v: MathView -> if (here != null) v.index < here else c != null && (v.y < c.second || v.y == c.second && v.x < c.first) }
        val target = (if (back) maths.lastOrNull(before) else maths.firstOrNull(after)) ?: return false
        clickMath(target, 0f, 0f)
        return true
    }

    /** Stops editing: the statement goes back into its region (an empty one is removed). */
    fun leave() {
        TableEditor.commit(this)
        table = null
        commitEditing(remove = true)
        editing = null
        textEditing = null
        typingRun = false
        relayout()
    }

    private fun commitEditing(remove: Boolean = false) {
        val e = editing ?: return
        val r = regions.getOrNull(e.region) as? MathRegion ?: return
        val st = Editor.commit(e.state).statement
        val empty = st is ExprStatement && st.expr == Placeholder
        val updated = if (empty && remove) withoutStatement(r, e.statement) else SheetLayout.withStatement(r, e.statement, Printer.print(st))
        regions = regions.toMutableList().also { if (updated.lines.isEmpty() && remove) it.removeAt(e.region) else it[e.region] = updated }
        if (remove) editing = null
    }

    private fun withoutStatement(r: MathRegion, k: Int): MathRegion {
        val groups = Calculation.statements(pub.beermat.format.CalcBlock("```calc", r.attributes, r.lines, "```")).toMutableList()
        if (k < groups.size) groups.removeAt(k)
        return r.copy(lines = groups.flatten())
    }

    /** New text for a text region, a plot's YAML, or a statement typed as linear text. */
    fun setText(region: Int, statement: Int?, text: String) {
        snapshot()
        val r = regions.getOrNull(region) ?: return
        val updated: Region? = when {
            statement != null && r is MathRegion -> if (text.isBlank()) withoutStatement(r, statement) else SheetLayout.withStatement(r, statement, text.trimEnd())
            r is TextRegion -> if (text.isBlank()) null else r.copy(lines = text.trimEnd().lines())
            r is PlotRegion -> r.copy(body = text.trimEnd().lines())
            r is pub.beermat.format.TableRegion -> r.copy(body = text.trimEnd().lines())
            else -> r
        }
        regions = regions.toMutableList().also { if (updated == null || (updated is MathRegion && updated.lines.isEmpty())) it.removeAt(region) else it[region] = updated }
        textEditing = null
        changed()
    }

    /** The text a text-edited region starts with. */
    fun textOf(region: Int, statement: Int?): String = when (val r = regions[region]) {
        is TextRegion -> r.lines.joinToString("\n")
        is PlotRegion -> r.body.joinToString("\n")
        is pub.beermat.format.TableRegion -> r.body.joinToString("\n")
        is MathRegion -> (layout.views[region] as MathView).groups.getOrNull(statement ?: 0)?.joinToString("\n") { it.source }?.trimIndent() ?: ""
        else -> ""
    }

    // Regions.

    /** Moves region [index] by ([dx], [dy]) points, snapped to the grid. */
    /**
     * The picture or plot whose resize handle (its bottom-right corner, when it is the one region
     * selected) is under the screen point, with its current size; null if none.
     */
    fun resizeHandle(px: Float, py: Float): Triple<Int, Float, Float>? {
        val index = selected.singleOrNull() ?: return null
        val v = layout.views.firstOrNull { it.index == index }?.takeIf { it is ImageView || it is PlotView } ?: return null
        val piece = layout.pieces[v.index].lastOrNull() ?: return null
        val (ox, oy) = layout.origin(v, piece)
        val (cx, cy) = ox + v.width to oy + v.height
        return if (abs(px - cx) <= HANDLE && abs(py - cy) <= HANDLE) Triple(index, v.width, v.height) else null
    }

    /** Resizes a picture or plot to [w] × [h] points (at least 24), snapped to the grid. */
    fun resize(index: Int, w: Float, h: Float) {
        val r = regions.getOrNull(index)?.takeIf { it is ImageRegion || it is PlotRegion } ?: return
        snapshot()
        val a = r.attributes.with("w", w.snap().coerceAtLeast(24f).roundToInt().toString()).with("h", h.snap().coerceAtLeast(24f).roundToInt().toString())
        regions = regions.toMutableList().also { it[index] = r.withAttributes(a) }
        changed()
    }

    fun move(index: Int, dx: Float, dy: Float) = move(setOf(index), dx, dy)

    /** Moves [indices] together, by the same amount; when they were the selection, they stay selected. */
    fun move(indices: Set<Int>, dx: Float, dy: Float) {
        val old = indices.mapNotNull { regions.getOrNull(it) }
        if (old.isEmpty()) return
        val keep = indices == selected
        snapshot()
        leave()
        // The group keeps its shape: one step, snapped from the topmost region's corner.
        val lead = old.minBy { it.y ?: 0f }
        val sx = ((lead.x ?: 0f) + dx).snap().coerceAtLeast(0f) - (lead.x ?: 0f)
        val sy = ((lead.y ?: 0f) + dy).snap().coerceAtLeast(0f) - (lead.y ?: 0f)
        val moved = old.map { r -> r to r.movedTo(((r.x ?: 0f) + sx).coerceAtLeast(0f), ((r.y ?: 0f) + sy).coerceAtLeast(0f)) }
        regions = regions.map { r -> moved.firstOrNull { it.first === r }?.second ?: r }
        sortRegions()
        selected = if (keep) moved.map { (_, n) -> regions.indexOfFirst { it === n } }.toSet() else emptySet()
        changed()
    }

    /**
     * Selects regions with a rectangle dragged from ([x0], [y0]) to ([x1], [y1]) (screen points, as
     * [click]). As in AutoCAD: dragged left to right, the regions wholly inside it; right to left,
     * every region it touches. [add] keeps those already selected.
     */
    fun selectIn(x0: Float, y0: Float, x1: Float, y1: Float, add: Boolean = false) {
        leave()
        val l = minOf(x0, x1)
        val r = maxOf(x0, x1)
        val t = minOf(y0, y1)
        val b = maxOf(y0, y1)
        val crossing = x1 < x0
        val inside = layout.views.filter { v ->
            if (v.hidden || v.region is AreaStart || v.region is AreaEnd) return@filter false
            val boxes = layout.pieces[v.index].map { p ->
                val (ox, oy) = layout.origin(v, p)
                listOf(ox, oy + p.from, ox + maxOf(v.width, 4f), oy + maxOf(p.to, p.from + 4f))
            }
            if (boxes.isEmpty()) return@filter false
            if (crossing) boxes.any { (bl, bt, br, bb) -> bl < r && br > l && bt < b && bb > t }
            else boxes.all { (bl, bt, br, bb) -> bl >= l && br <= r && bt >= t && bb <= b }
        }.map { it.index }
        selected = if (add) selected + inside else inside.toSet()
        crosshair = null
        relayout()
    }

    /** Ctrl+click: a region in or out of the selection. */
    fun toggle(index: Int) {
        val r = regions.getOrNull(index) ?: return
        if (r is AreaStart || r is AreaEnd) return
        val others = selected.map { regions[it] }
        // Leaving may remove an empty region being edited: find these again after.
        leave()
        val i = regions.indexOfFirst { it === r }
        val now = others.map { o -> regions.indexOfFirst { it === o } }.filter { it >= 0 }.toSet()
        // The region may have gone: an empty one being edited is removed when left.
        selected = if (i < 0) now else if (i in now) now - i else now + i
        crosshair = null
    }

    fun deselect() {
        selected = emptySet()
    }

    /** Delete: the selected regions go (one undoable step). False when nothing is selected. */
    fun deleteSelected(): Boolean {
        if (selected.isEmpty()) return false
        snapshot()
        val gone = selected
        regions = regions.filterIndexed { i, _ -> i !in gone }
        selected = emptySet()
        changed()
        return true
    }

    /** "Separate regions": each region overlapping one above it moves down below it. */
    fun separate() {
        snapshot()
        leave()
        var changedAny = false
        for (round in 0 until regions.size) {
            val v = layout.views.filter { !it.hidden && it.height > 0 && it !is MarkerView }.sortedBy { it.y }
            val clash = v.firstNotNullOfOrNull { b -> v.firstOrNull { a -> a !== b && a.y <= b.y && a.x < b.x + b.width && b.x < a.x + a.width && b.y < a.y + a.height }?.let { a -> a to b } } ?: break
            val (a, b) = clash
            regions = regions.toMutableList().also { it[b.index] = b.region.movedTo(b.x, (a.y + a.height + SheetLayout.FLOW_GAP).snap()) }
            sortRegions()
            relayout()
            changedAny = true
        }
        if (changedAny) changed()
    }

    /** Whether the name being typed is in special text mode (`Ctrl+Shift+K`), for the status bar. */
    val specialText: Boolean get() = editing?.state?.cursor?.draft?.literal == true

    /** How `*` is drawn in this sheet (docs/syntax.md#multiplication-style). */
    val multiplication: Multiplication get() = layout.calculation.settings.multiplication

    /** Format → Multiplication: the sheet's default, kept in the frontmatter (`multiplication: space`; a dot is the default). */
    fun setMultiplication(style: Multiplication) {
        if (style == multiplication) return
        snapshot()
        val kept = frontmatter.orEmpty().filterNot { it.trimStart().startsWith("multiplication:") }
        frontmatter = (if (style == Multiplication.DOT) kept else kept + "multiplication: ${style.key}").ifEmpty { null }
        changed()
    }

    /** The sheet's angle and currency dimensions (docs/units.md#angle-and-currency-as-dimensions). */
    val units: pub.beermat.engine.eval.UnitOptions get() = layout.calculation.settings.units

    /** A one-line frontmatter setting, `key: value`, replaced, or removed when [value] is null (Format → Units). */
    fun setFrontmatter(key: String, value: String?) {
        val kept = frontmatter.orEmpty().filterNot { it.trimStart().startsWith("$key:") }
        val next = (if (value == null) kept else kept + "$key: $value").ifEmpty { null }
        if (next == frontmatter) return
        snapshot()
        frontmatter = next
        changed()
    }

    // Tables as grids (TableEditor).

    /** The table in region [region], if it is a table region. */
    fun tableAt(region: Int): pub.beermat.format.Table? = (regions.getOrNull(region) as? TableRegion)?.let(TableEditor::regionTable)

    /** Replaces the table in region [region] (one undo step). */
    fun setTable(region: Int, t: pub.beermat.format.Table) {
        val r = regions.getOrNull(region) as? TableRegion ?: return
        snapshot()
        regions = regions.toMutableList().also { it[region] = r.copy(body = pub.beermat.format.Table.write(t)) }
        changed()
    }

    /** The table at the cursor edited as its Markdown instead, as text regions are. */
    fun editTableText() {
        val c = table ?: return
        TableEditor.commit(this)
        table = null
        textEditing = c.region to null
    }

    // The Result Format dialog (docs/editor.md#result-format).

    /** A result's settings as the dialog shows them; [unit] in linear syntax, empty for automatic. */
    data class ResultSettings(
        val significant: Boolean,
        val digits: Int,
        val trailingZeros: Boolean,
        val exponentialThreshold: Int,
        val unit: String,
    )

    /** The result under the screen point: its region and statement, or null when the point isn't on a result. */
    fun resultAt(px: Float, py: Float): Pair<Int, Int>? {
        val (view, rx, ry) = layout.hit(px, py) ?: return null
        if (view !is MathView) return null
        val k = view.statements.indexOfFirst { ry <= it.bottom + SheetLayout.STATEMENT_GAP / 2 }.takeIf { it >= 0 } ?: return null
        val s = view.statements[k]
        val o = s.outcome ?: return null
        val shown = o.shown ?: return null
        val parts = ResultText.parts(shown, o.format)
        // The result is the end of the statement's row: everything right of where it starts.
        val start = s.box.width - layout.math.result(parts.number, parts.unit).width
        return if (rx >= start && ry >= s.top && ry <= s.baseline + s.box.descent) view.index to k else null
    }

    /** Whether that statement shows a numeric result (for Format → Result…). */
    fun hasResult(region: Int, statement: Int): Boolean =
        ((layout.views.firstOrNull { it.index == region } as? MathView)?.statements?.getOrNull(statement)?.outcome?.shown) != null

    fun resultSettings(region: Int, statement: Int): ResultSettings {
        val s = (layout.views.first { it.index == region } as MathView).statements[statement]
        val f = s.outcome?.format ?: layout.calculation.settings.format
        val unit = resultUnit(s.parsed)?.let(Printer::print).orEmpty()
        return when (val r = f.rounding) {
            is Rounding.Decimals -> ResultSettings(false, r.places, r.trailingZeros, f.exponentialThreshold, unit)
            is Rounding.Significant -> ResultSettings(true, r.figures, false, f.exponentialThreshold, unit)
        }
    }

    /**
     * Applies the dialog's [settings] to the result's region (rounding, as region attributes that
     * differ from the sheet's) and statement (the unit placeholder). Null puts the region back
     * on the sheet's settings, keeping the unit. Throws a [ParseException] for a unit that doesn't parse.
     */
    fun setResultSettings(region: Int, statement: Int, settings: ResultSettings?) {
        check(editing == null) { "leave the statement being edited before finding its result" }
        val r = regions.getOrNull(region) as? MathRegion ?: return
        val unit = settings?.unit?.trim()?.ifEmpty { null }?.let(Parser::parseExpression)
        snapshot()
        var attributes = RESULT_KEYS.fold(r.attributes) { a, key -> a.without(key) }
        if (settings != null) {
            val sheet = layout.calculation.settings.format
            val rounding = if (settings.significant) Rounding.Significant(settings.digits) else Rounding.Decimals(settings.digits, settings.trailingZeros)
            if (rounding != sheet.rounding) {
                attributes = when (rounding) {
                    is Rounding.Significant -> attributes.with("significant", rounding.figures.toString())
                    is Rounding.Decimals -> attributes.with("decimals", rounding.places.toString())
                        .let { if (rounding.trailingZeros) it.with("trailing_zeros", null) else it }
                }
            }
            if (settings.exponentialThreshold != sheet.exponentialThreshold) attributes = attributes.with("exponential_threshold", settings.exponentialThreshold.toString())
        }
        var updated = r.withAttributes(attributes) as MathRegion
        val view = layout.views.firstOrNull { it.index == region } as? MathView
        val parsed = view?.statements?.getOrNull(statement)?.parsed
        if (settings != null && parsed != null) {
            val st = when (parsed) {
                is Evaluate -> parsed.copy(result = ResultSlot(unit))
                is Define -> if (parsed.result != null) parsed.copy(result = ResultSlot(unit)) else parsed
                else -> parsed
            }
            if (st != parsed) updated = SheetLayout.withStatement(updated, statement, Printer.print(st))
        }
        regions = regions.toMutableList().also { it[region] = updated }
        changed()
    }

    private fun resultUnit(st: Statement?): Expr? = when (st) {
        is Evaluate -> st.result.unit
        is Define -> st.result?.unit
        else -> null
    }

    /** `Ctrl+Enter`: a page break at the crosshair. */
    fun pageBreak() {
        val c = crosshair ?: return
        snapshot()
        regions = regions + PageBreak().movedTo(0f, c.second)
        sortRegions()
        crosshair = 0f to c.second + 1f
        changed()
    }

    /**
     * `@`: an X-Y plot at the crosshair, a working example opened for editing as
     * its YAML (docs/plots.md), to change into the plot wanted.
     */
    fun insertPlot() {
        val c = crosshair ?: return
        snapshot()
        val r = PlotRegion(Attributes.NONE, PLOT_TEMPLATE.lines()).movedTo(c.first, c.second)
        regions = regions + r
        sortRegions()
        textEditing = regions.indexOfFirst { it === r } to null
        crosshair = null
        changed()
    }

    /**
     * A picture at the crosshair (or the top of the sheet), embedded in the file, at its natural
     * size or narrowed to fit the page. False when [bytes] isn't a picture Beermat can read.
     */
    fun insertImage(bytes: ByteArray, mime: String, alt: String): Boolean {
        val picture = Pictures.decode(bytes) ?: return false
        val (x, y) = crosshair ?: (0f to layout.views.maxOfOrNull { it.y + it.height + SheetLayout.FLOW_GAP }.let { it ?: 0f })
        val (w, h) = Pictures.fit(picture, layout.format.contentWidth - x)
        leave()
        snapshot()
        val a = Attributes.NONE.with("x", x.roundToInt().toString()).with("y", y.roundToInt().toString())
            .with("w", w.roundToInt().toString()).with("h", h.roundToInt().toString())
        val r = ImageRegion(a, alt, ImageRegion.dataUri(mime, bytes))
        regions = regions + r
        sortRegions()
        crosshair = null
        selected = setOf(regions.indexOfFirst { it === r })
        changed()
        return true
    }

    /** An area from the crosshair, around the regions below it down to the next 60 points. */
    fun insertArea(title: String = "Area") {
        val c = crosshair ?: return
        snapshot()
        val start = AreaStart(Attributes(null, linkedMapOf("area" to null, "title" to title))).movedTo(0f, c.second)
        val end = AreaEnd().movedTo(0f, c.second + 60f)
        regions = regions + start + end
        sortRegions()
        changed()
    }

    /** The statement being edited as linear text (to copy), or null. */
    fun copyText(): String? = editing?.let { Printer.print(Editor.commit(it.state).statement) }

    /** The statement being edited, with its result, as LaTeX or (with [mathMl]) MathML for Word. */
    fun copyMath(mathMl: Boolean): String? {
        val e = editing ?: return null
        val sv = (layout.views.getOrNull(e.region) as? MathView)?.statements?.getOrNull(e.statement)
        val st = Editor.commit(e.state).statement
        val units = pub.beermat.export.Units.of(layout.calculation.lines.mapNotNull { it.parsed })
        val result = sv?.outcome?.shown?.let { pub.beermat.export.Result.of(ResultText.parts(it, sv.outcome.format)) }
        val style = layout.calculation.settings.multiplication
        return if (mathMl) pub.beermat.export.MathMl(units, style).math(st, result) else "$$" + pub.beermat.export.Latex(units, style).statement(st, result) + "$$"
    }

    /** Pastes linear text: a new region at the crosshair, one statement per line. */
    fun paste(text: String) {
        val c = crosshair ?: return
        val lines = text.trimEnd().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        snapshot()
        val parses = try {
            Parser.parseStatement(lines.first())
            true
        } catch (_: ParseException) {
            false
        }
        val r = (if (parses) MathRegion(Attributes.NONE, lines.map { CalcLine(it) }) else TextRegion(Attributes.NONE, text.trimEnd().lines())).movedTo(c.first, c.second)
        regions = regions + r
        sortRegions()
        changed()
        val v = layout.views.first { it.region === r }
        crosshair = c.first to (v.y + v.height + SheetLayout.FLOW_GAP).snap()
    }

    // Errors.

    /** The last action that failed (a bug), shown in the status bar until the next one succeeds. */
    var lastError: String? = null
        private set

    /**
     * Runs a user action; if it throws (a bug), the sheet goes back to how it was before it, the
     * error is logged (`~/.beermat/errors.log`), and [fallback] is returned. The window wraps every
     * key, click and command in this, so a bug costs one action, never the sheet or the window.
     */
    fun <T> guard(fallback: T, action: () -> T): T {
        val before = current()
        val text = textEditing
        val sel = selected
        val wasDirty = dirty
        return try {
            action().also { lastError = null }
        } catch (e: Exception) {
            regions = before.regions
            editing = before.editing
            crosshair = before.crosshair
            textEditing = text
            selected = sel
            dirty = wasDirty
            typingRun = false
            runCatching { relayout() }.onFailure { editing = null; textEditing = null; relayout() }
            lastError = "That didn't work (a bug, undone): ${e::class.simpleName}"
            log(e)
            fallback
        }
    }

    private fun log(e: Exception) = logError(e)

    // Undo.

    fun undo() {
        val s = undo.removeLastOrNull() ?: return
        redo += current()
        restore(s)
    }

    fun redo() {
        val s = redo.removeLastOrNull() ?: return
        undo += current()
        restore(s)
    }

    private fun current() = Snapshot(regions, editing, crosshair, frontmatter)

    private fun restore(s: Snapshot) {
        regions = s.regions
        editing = s.editing
        crosshair = s.crosshair
        frontmatter = s.frontmatter
        textEditing = null
        table = table?.takeIf { (s.regions.getOrNull(it.region) as? TableRegion)?.let { r -> TableEditor.regionTable(r).let { t -> it.row <= t.rows.size && it.col < t.columns.size } } == true }?.copy(draft = null)
        selected = emptySet()
        typingRun = false
        dirty = true
        relayout()
    }

    private fun snapshot() {
        undo += current()
        if (undo.size > 500) undo.removeFirst()
        redo.clear()
    }

    private fun changed() {
        dirty = true
        relayout()
    }

    /**
     * Evaluation order: top to bottom, then left to right; the file is saved in this order.
     * A math region counts from the baseline of its first line, not its top, so a fraction
     * reaching up beside a definition doesn't come before it (docs/editor.md#regions-on-the-page).
     */
    /** How far below its top a region's first baseline is, in the current layout; 0 for all but math. */
    private fun baseline(r: Region): Float {
        if (r !is MathRegion || !::layout.isInitialized) return 0f
        val view = layout.views.firstOrNull { it.region === r } ?: r.attributes.id?.let { id -> layout.views.firstOrNull { it.region.attributes.id == id } }
        return (view as? MathView)?.statements?.firstOrNull()?.baseline ?: 0f
    }

    private fun sortRegions() {
        val editingRegion = editing?.let { regions.getOrNull(it.region) }
        val textRegion = textEditing?.let { regions.getOrNull(it.first) }
        val selectedRegions = selected.mapNotNull { regions.getOrNull(it) }
        // Baselines within 3 points are one line (fonts differ between sheets), read left to right.
        val byLine = regions.withIndex().map { it to (it.value.y ?: 0f) + baseline(it.value) }.sortedWith(compareBy({ it.second }, { it.first.index }))
        var line = 0
        var lineStart = Float.NEGATIVE_INFINITY
        val lines = byLine.associate { (r, key) ->
            if (key - lineStart > 3f) {
                line++
                lineStart = key
            }
            r.index to line
        }
        regions = regions.withIndex().sortedWith(compareBy({ lines.getValue(it.index) }, { it.value.x ?: 0f }, { it.index })).map { it.value }
        selected = selectedRegions.map { s -> regions.indexOfFirst { it === s } }.filter { it >= 0 }.toSet()
        editing = editing?.let { e -> e.copy(region = regions.indexOfFirst { it === editingRegion }) }
        textEditing = textEditing?.let { t -> regions.indexOfFirst { it === textRegion } to t.second }
    }

    private fun Float.snap() = (this / GRID).roundToInt() * GRID

    companion object {
        const val GRID = 6f

        /** How far Enter moves the crosshair down when there is no region to go below: a line of text. */
        const val LINE = 3 * GRID

        /** How near the corner of a selected picture or plot a press grabs its resize handle, in points. */
        const val HANDLE = 5f

        /** Region attributes that override the sheet's result format (docs/file-format.md#attributes). */
        private val RESULT_KEYS = listOf("decimals", "significant", "strict", "trailing_zeros", "exponential_threshold", "zero_threshold", "complex_threshold")

        /** A new plot: one that draws, showing the keys to change. */
        val PLOT_TEMPLATE = """
            x: 0 .. 2*π
            traces:
              - y: sin(x)
                label: sin(x)
            axes:
              x: { grid: true }
              y: { grid: true }
        """.trimIndent()
    }

    /** For tests: the cursor of the statement being edited. */
    internal val cursor: Cursor? get() = editing?.state?.cursor
}
