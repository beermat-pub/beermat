package pub.beermat.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import pub.beermat.app.editor.Editor
import pub.beermat.app.page.CellKey
import pub.beermat.app.page.MathView
import pub.beermat.app.page.Overlay
import pub.beermat.app.page.PagePainter
import pub.beermat.app.page.RegionView
import pub.beermat.app.page.Session
import pub.beermat.app.page.TableEditor
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Division
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Multiplication
import pub.beermat.engine.syntax.Printer
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.Key as K

/** The math size on the page, in points. */
const val MATH_SIZE = 12f

/**
 * The worksheet as the user sees and edits it (docs/editor.md): the toolbar, then the pages, with
 * the mouse and the keys, the text editor over a text region, and the right-click menus. The shell
 * around it, the desktop window or the browser page, gives [host], and puts a [banner] under the
 * toolbar (the file changed on disk) and a [statusBar] at the bottom.
 *
 * [palettes] are the toolbar's (none hides it); [revision] changes whenever the sheet does, to draw it again.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetView(
    session: Session, revision: Int, palettes: List<Palette>, zoom: Int, host: Host,
    onChange: () -> Unit, onSave: () -> Unit, onZoom: (Int) -> Unit,
    banner: @Composable () -> Unit = {}, statusBar: @Composable () -> Unit = {},
) {
    LaunchedEffect(host) { Commands.matrixCommand = { s -> Keys.matrixCommand(s, host) } }
    val density = LocalDensity.current.density
    // Points to pixels: 96 pixels to 72 points, times the screen's density and the zoom.
    val scale = density * 96f / 72f * zoom / 100f
    val focus = remember { FocusRequester() }
    var width by remember { mutableStateOf(0) }
    var drag by remember { mutableStateOf<Triple<Set<Int>, Float, Float>?>(null) }
    var resizing by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var band by remember { mutableStateOf<List<Float>?>(null) }
    // A module function's signature and docstring, while the mouse rests on `bt.f`.
    var hover by remember { mutableStateOf<Overlay.Tip?>(null) }
    // The right-click menu on a multiplication: where it opens (in the sheet's pixels), and the operator it is for.
    var mulMenu by remember { mutableStateOf<Pair<Offset, Apply>?>(null) }
    // The last click (time, place), to tell a double click: kept here, as the mouse handler below starts
    // again whenever the layout changes, which the first click of a double click does.
    val lastClick = remember { object { var value: Triple<Long, Float, Float>? = null } }
    var tableMenu by remember { mutableStateOf<Offset?>(null) }
    var imageMenu by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    val layout = session.layout.also { revision }
    val left = maxOf(0f, (width / scale - layout.format.width) / 2)
    Column(Modifier.fillMaxSize().background(Color(0xFFE8E8E8))) {
        if (palettes.isNotEmpty()) Toolbar(session, palettes, host, onUsed = { focus.requestFocus(); onChange() })
        banner()
        val vertical = rememberScrollState()
        val horizontal = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.fillMaxSize()
                    .onSizeChanged { width = it.width }
                    // Ctrl+wheel zooms, before the scroll below takes the wheel.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.type != PointerEventType.Scroll) continue
                                val change = e.changes.first()
                                val dy = change.scrollDelta.y
                                if (e.keyboardModifiers.isCtrlPressed) {
                                    if (dy != 0f) onZoom(if (dy < 0) 1 else -1)
                                    e.changes.forEach { it.consume() }
                                    continue
                                }
                                // Over a big array result, the wheel scrolls its table (Shift+wheel sideways; docs/results.md#big-arrays).
                                val px = (change.position.x + horizontal.value) / scale - left
                                val py = (change.position.y + vertical.value) / scale
                                val sideways = e.keyboardModifiers.isShiftPressed || change.scrollDelta.x != 0f
                                val step = (if (sideways) change.scrollDelta.x.takeIf { it != 0f } ?: dy else dy).let { if (it > 0) 3 else if (it < 0) -3 else 0 }
                                if (step != 0 && session.guard(false) { session.scrollTable(px, py, if (sideways) 0 else step, if (sideways) step else 0) }) {
                                    e.changes.forEach { it.consume() }
                                    onChange()
                                }
                            }
                        }
                    }
                    .verticalScroll(vertical)
                    // A page wider than the window (zoomed in) scrolls sideways.
                    .horizontalScroll(horizontal)
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (session.textEditing != null) return@onPreviewKeyEvent false
                        val done = session.guard(false) { Keys.handle(e, session, host, onSave) }
                        if (done) onChange()
                        done
                    },
            ) {
                // As wide as the regions reach, past the page's edge too, so the scroll bar can get there.
                val canvasWidth = maxOf(width / scale, left + layout.totalWidth) * scale
                // Compose can't size anything much taller than 2^18 px (half that when it is wide): past that a
                // very long sheet's end can't be scrolled to, but the window doesn't fail (it did, before big arrays became tables).
                val canvasHeight = minOf(layout.totalHeight * scale, if (canvasWidth < 8000f) 260_000f else 130_000f)
                Canvas(
                    Modifier.size((canvasWidth / density).dp, (canvasHeight / density).dp)
                        .pointerInput(layout, host) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent()
                                    if (down.type == PointerEventType.Move) {
                                        val at = down.changes.first().position
                                        val (hx, hy) = at.x / scale - left to at.y / scale
                                        val tip = session.guard(null) { session.tip(hx, hy) }?.let { Overlay.Tip(hx, hy, it) }
                                        if (tip?.lines != hover?.lines) hover = tip
                                        continue
                                    }
                                    if (down.type != PointerEventType.Press) continue
                                    focus.requestFocus()
                                    val start = down.changes.first().position
                                    val px = start.x / scale - left
                                    val py = start.y / scale
                                    if (down.buttons.isSecondaryPressed) {
                                        // Right-click: the cursor goes there, and on a multiplication a menu of its styles opens.
                                        session.guard(Unit) { session.click(px, py) }
                                        mulMenu = session.editing?.let { Editor.styledAt(it.state)?.second }?.let { start to it }
                                        tableMenu = start.takeIf { session.table != null }
                                        // On a plot or picture: Copy as image.
                                        imageMenu = runCatching { session.layout.hit(px, py)?.first }.getOrNull()?.takeIf { it is pub.beermat.app.page.PlotView || it is pub.beermat.app.page.ImageView }?.let { start to it.index }
                                        onChange()
                                        continue
                                    }
                                    // The handle on a selected picture's or plot's corner resizes it; a picture keeps its shape unless Shift is held.
                                    val handle = runCatching { session.resizeHandle(px, py) }.getOrNull()
                                    if (handle != null) {
                                        val (index, w0, h0) = handle
                                        val picture = session.layout.views.firstOrNull { it.index == index } is pub.beermat.app.page.ImageView
                                        while (true) {
                                            val e = awaitPointerEvent()
                                            val p = e.changes.first().position
                                            val w = maxOf(24f, w0 + (p.x - start.x) / scale)
                                            val h = if (picture && !e.keyboardModifiers.isShiftPressed) w * h0 / w0 else maxOf(24f, h0 + (p.y - start.y) / scale)
                                            resizing = Triple(index, w, h)
                                            if (e.type == PointerEventType.Release) {
                                                session.guard(Unit) { session.resize(index, w, h) }
                                                resizing = null
                                                onChange()
                                                break
                                            }
                                        }
                                        continue
                                    }
                                    val ctrl = down.keyboardModifiers.isCtrlPressed
                                    val hit = runCatching { session.layout.hit(px, py)?.first }.getOrNull()
                                    // A marker (an area's line, a page break) is dragged only as part of a selection: a click on it folds the area.
                                    val grabbed = hit?.takeIf { it !is pub.beermat.app.page.MarkerView || it.index in session.selected }
                                    // Dragging a selected region moves the whole selection; any other, just itself.
                                    val group = grabbed?.let { g -> if (g.index in session.selected) session.selected else setOf(g.index) }
                                    var moved = false
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val p = e.changes.first().position
                                        val dx = (p.x - start.x) / scale
                                        val dy = (p.y - start.y) / scale
                                        val far = (dx * dx + dy * dy) > 16f
                                        if (!moved && far && !ctrl && (group == null || session.editing?.region != grabbed?.index)) moved = true
                                        if (moved && group != null) drag = Triple(group, dx, dy)
                                        // On empty paper, dragging draws the selection rectangle.
                                        if (moved && hit == null) band = listOf(px, py, px + dx, py + dy)
                                        if (e.type == PointerEventType.Release) {
                                            session.guard(Unit) {
                                                when {
                                                    ctrl && hit != null -> session.toggle(hit.index)
                                                    moved && group != null -> session.move(group, dx, dy)
                                                    moved && hit == null -> session.selectIn(px, py, px + dx, py + dy)
                                                    else -> {
                                                        session.click(px, py)
                                                        // A second click soon after, at the same spot: on a table cell, edit it;
                                                        // on a result, its Result Format dialog.
                                                        val now = e.changes.first().uptimeMillis
                                                        val again = lastClick.value?.let { (t, x, y) -> now - t < 450 && abs(x - px) < 4f && abs(y - py) < 4f } == true
                                                        lastClick.value = if (again) null else Triple(now, px, py)
                                                        if (again && session.table != null) {
                                                            TableEditor.key(session, CellKey.Edit)
                                                        } else if (again) {
                                                            session.leave()
                                                            session.resultAt(px, py)?.let { (r, k) -> host.editResultFormat(session, r, k) }
                                                        }
                                                    }
                                                }
                                            }
                                            drag = null
                                            band = null
                                            onChange()
                                            break
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    // Read here so the page is drawn again at every change, also one that leaves the layout as
                    // it was: the crosshair or a selection moved by the arrow keys.
                    revision
                    drawIntoCanvas {
                        val c = it.nativeCanvas
                        c.save()
                        c.scale(scale, scale)
                        PagePainter.paint(c, layout, overlay(session, drag, band, resizing, hover), width / scale)
                        c.restore()
                    }
                }
                session.textEditing?.let { (index, statement) -> TextEditor(session, index, statement, scale, left, density, onChange) }
                mulMenu?.takeIf { it.second.op == Op.DIV }?.let { (at, div) ->
                    DivisionMenu(at, div, onDismiss = { mulMenu = null }) { style ->
                        mulMenu = null
                        if (!session.guard(false) { session.edit { Editor.division(it, style) } }) host.beep()
                        onChange()
                    }
                }
                mulMenu?.takeIf { it.second.op == Op.MUL }?.let { (at, mul) ->
                    MultiplicationMenu(at, mul, session.multiplication, onDismiss = { mulMenu = null }) { style ->
                        mulMenu = null
                        if (!session.guard(false) { session.edit { Editor.multiplication(it, style) } }) host.beep()
                        focus.requestFocus()
                        onChange()
                    }
                }
                imageMenu?.let { (at, index) ->
                    TableMenu(at, title = "Image", items = listOf("Copy as image"), onDismiss = { imageMenu = null }) { _ ->
                        imageMenu = null
                        session.guard(Unit) { session.layout.views.firstOrNull { it.index == index }?.let { host.copyImage(regionPng(session, it)) } }
                        focus.requestFocus()
                    }
                }
                tableMenu?.let { at ->
                    TableMenu(at, onDismiss = { tableMenu = null }) { item ->
                        tableMenu = null
                        session.guard(Unit) {
                            when (item) {
                                "Insert row above" -> TableEditor.insertRow(session, below = false)
                                "Insert row below" -> TableEditor.insertRow(session, below = true)
                                "Delete row" -> TableEditor.deleteRow(session)
                                "Insert column left" -> TableEditor.insertColumn(session, right = false)
                                "Insert column right" -> TableEditor.insertColumn(session, right = true)
                                "Delete column" -> TableEditor.deleteColumn(session)
                                else -> session.editTableText()
                            }
                        }
                        focus.requestFocus()
                        onChange()
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(vertical), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            HorizontalScrollbar(rememberScrollbarAdapter(horizontal), Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        statusBar()
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** What the screen marks on the page, from the session and the mouse: the cursor, the crosshair, a drag or a selection. */
fun overlay(session: Session, drag: Triple<Set<Int>, Float, Float>? = null, band: List<Float>? = null, resize: Triple<Int, Float, Float>? = null, hover: Overlay.Tip? = null) =
    Overlay(session.editing, session.crosshair, session.textEditing?.first, drag, session.selected, band, resize, session.table, session.completion, hover)

/** A region as PNG bytes, at twice its size on the page (Copy as image), to paste into a document or an email. */
fun regionPng(session: Session, view: RegionView): ByteArray {
    val scale = 2f
    val surface = Surface.makeRasterN32Premul((view.width * scale).roundToInt().coerceAtLeast(1), (view.height * scale).roundToInt().coerceAtLeast(1))
    surface.canvas.clear(0xFFFFFFFF.toInt())
    surface.canvas.scale(scale, scale)
    PagePainter.paintRegion(surface.canvas, session.layout, view)
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}

/**
 * A right-click menu with a [title] and [items]: by default a table's (docs/tables.md#editing),
 * rows and columns, or the table as its Markdown.
 */
@Composable
private fun TableMenu(
    at: Offset,
    title: String = "Table",
    items: List<String> = listOf("Insert row above", "Insert row below", "Delete row", "Insert column left", "Insert column right", "Delete column", "Edit as Markdown"),
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    Popup(offset = IntOffset(at.x.roundToInt(), at.y.roundToInt()), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(IntrinsicSize.Max).background(Color.White).border(1.dp, Color(0xFFBBBBBB)).padding(vertical = 4.dp)) {
            BasicText(title, Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = TextStyle(fontSize = 11.sp, color = Color(0xFF888888)))
            for (label in items) {
                BasicText(label, Modifier.fillMaxWidth().clickable { onPick(label) }.padding(horizontal = 12.dp, vertical = 4.dp), style = TextStyle(fontSize = 13.sp, color = Color(0xFF222222)))
            }
        }
    }
}

/**
 * Right-click on a multiplication (docs/syntax.md#multiplication-style): draw it with a dot, a thin
 * space, or as the sheet does. A thin space needs a name on the right (`q L`), so it is greyed out otherwise.
 */
@Composable
private fun MultiplicationMenu(at: Offset, mul: Apply, sheet: Multiplication, onDismiss: () -> Unit, onPick: (Multiplication?) -> Unit) {
    val current = when {
        mul.dot -> Multiplication.DOT
        mul.implicit -> Multiplication.SPACE
        else -> null
    }
    val sheetName = if (sheet == Multiplication.DOT) "a dot" else "a thin space"
    val items = listOf(
        Triple<String, Multiplication?, Boolean>("Dot   a·b", Multiplication.DOT, true),
        Triple("Thin space   a b", Multiplication.SPACE, Printer.canJuxtapose(mul.args[1])),
        Triple("As the sheet ($sheetName)", null, true),
    )
    Popup(offset = IntOffset(at.x.roundToInt(), at.y.roundToInt()), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(IntrinsicSize.Max).background(Color.White).border(1.dp, Color(0xFFBBBBBB)).padding(vertical = 4.dp)) {
            BasicText("Multiplication", Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = TextStyle(fontSize = 11.sp, color = Color(0xFF888888)))
            for ((label, style, enabled) in items) {
                val mark = if (style == current) "✓ " else "    "
                BasicText(
                    mark + label,
                    Modifier.fillMaxWidth().let { if (enabled) it.clickable { onPick(style) } else it }.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 13.sp, color = if (enabled) Color(0xFF222222) else Color(0xFFAAAAAA)),
                )
            }
        }
    }
}

/** Right-click on a division (docs/syntax.md#division-style): stacked, or on the line as ÷ or /. */
@Composable
private fun DivisionMenu(at: Offset, div: Apply, onDismiss: () -> Unit, onPick: (Division) -> Unit) {
    val items = listOf("Stacked   a over b" to Division.STACKED, "Inline   a ÷ b" to Division.OBELUS, "Inline   a / b" to Division.SLASH)
    Popup(offset = IntOffset(at.x.roundToInt(), at.y.roundToInt()), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(IntrinsicSize.Max).background(Color.White).border(1.dp, Color(0xFFBBBBBB)).padding(vertical = 4.dp)) {
            BasicText("Division", Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = TextStyle(fontSize = 11.sp, color = Color(0xFF888888)))
            for ((label, style) in items) {
                val mark = if (style == div.division) "✓ " else "    "
                BasicText(
                    mark + label,
                    Modifier.fillMaxWidth().clickable { onPick(style) }.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 13.sp, color = Color(0xFF222222)),
                )
            }
        }
    }
}

/** A text region, a plot's YAML, or a statement that doesn't parse, edited as plain text over its place on the page. */
@Composable
private fun TextEditor(session: Session, index: Int, statement: Int?, scale: Float, left: Float, density: Float, onChange: () -> Unit) {
    val layout = session.layout
    val view = layout.views.getOrNull(index) ?: return
    val piece = layout.pieces[index].firstOrNull() ?: return
    val (ox, oy) = layout.origin(view, piece)
    val y = oy + if (view is MathView && statement != null) view.statements.getOrNull(statement)?.top ?: 0f else 0f
    var text by remember(index, statement) { mutableStateOf(session.textOf(index, statement)) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    fun done() {
        session.guard(Unit) { session.setText(index, statement, text) }
        onChange()
    }
    BasicTextField(
        text,
        { text = it },
        Modifier.offset { IntOffset(((ox + left) * scale).roundToInt(), (y * scale).roundToInt()) }
            .width(((if (view is MathView) maxOf(view.width, 240f) else view.width) * scale / density).dp)
            .background(Color(0xFFFFFDF0))
            .focusRequester(focus)
            .onFocusChanged { if (focused && !it.isFocused) done(); focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == K.Escape || (e.key == K.Enter && e.isCtrlPressed))) {
                    done()
                    true
                } else {
                    false
                }
            },
        textStyle = TextStyle(fontSize = (11f * scale / density).sp),
    )
    LaunchedEffect(index, statement) { focus.requestFocus() }
}

/**
 * The toolbar (docs/editor.md#toolbar): every command as a small button, grouped in
 * palettes; hovering shows its name and keystroke. Buttons never take the focus from the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(session: Session, palettes: List<Palette>, host: Host, onUsed: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().background(Color(0xFFF4F4F4)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Each palette wraps as a whole, its divider after it: a wrapped row ends with one, and none starts a row.
        palettes.forEachIndexed { k, palette ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                palette.commands.forEach { c ->
                    WithTip(
                        tip = {
                            Box(Modifier.background(Color(0xFF333333)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                BasicText("${c.name}   ${c.keys}", style = TextStyle(color = Color.White, fontSize = 12.sp))
                            }
                        },
                    ) {
                        Box(
                            Modifier.padding(1.dp).height(24.dp).widthIn(min = 26.dp)
                                .focusProperties { canFocus = false }
                                .clickable {
                                    if (!session.guard(false) { c.run(session) }) host.beep()
                                    onUsed()
                                }
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(c.label, style = TextStyle(fontSize = 13.sp, color = Color(0xFF222222)))
                        }
                    }
                }
                if (k < palettes.lastIndex) Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(22.dp).background(Color(0xFFCCCCCC)))
            }
        }
    }
}
