package pub.beermat.app

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.Key as K
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.input.pointer.isSecondaryPressed
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Multiplication
import pub.beermat.engine.syntax.Printer
import pub.beermat.app.editor.Editor
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import pub.beermat.app.editor.Key
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.MathView
import pub.beermat.app.page.Overlay
import pub.beermat.app.page.PagePainter
import pub.beermat.app.page.CellKey
import pub.beermat.app.page.Session
import pub.beermat.app.page.TableEditor
import pub.beermat.engine.Version
import java.awt.FileDialog
import pub.beermat.app.print.Pdf
import pub.beermat.app.print.Printing
import javax.swing.JOptionPane
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** The math size on the page, in points. */
const val MATH_SIZE = 12f

/**
 * `beermat-app [file.bbm]` opens a worksheet to edit (docs/editor.md). The window follows the
 * file on disk, so edits by Claude show up at once (when there are no unsaved changes).
 *
 * `beermat-app file.bbm --png out.png` renders the pages to an image instead, without a window
 * (`--pdf out.pdf`, to a PDF);
 * `--script "click 100 50; type x:3m«enter»"` edits first (for checking the editor by eye).
 */
fun main(args: Array<String>) {
    val png = args.indexOf("--png").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val pdf = args.indexOf("--pdf").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val script = args.indexOf("--script").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val selftest = args.indexOf("--selftest").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val demo = args.indexOf("--demo").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    // Without a file, a blank sheet ("Untitled"), named when first saved.
    val opened = args.firstOrNull { !it.startsWith("--") && it != png && it != pdf && it != script && it != selftest && it != demo }?.let(::File)
    // The window asks before a sheet runs a Python file; images and PDFs made from the command line run none.
    if (png == null && pdf == null) PythonPermission.install()
    if ("--allow-python" in args) pub.beermat.format.PythonConsent.allowed = { true }
    if (png != null || pdf != null) {
        val session = Session(opened?.takeIf { it.exists() }?.readText() ?: "", MathLayout(SkiaMath, MATH_SIZE))
        session.base = opened?.absoluteFile?.parentFile
        // `--script @steps.txt` reads the steps from a file, one per line.
        script?.let { if (it.startsWith("@")) Scripts.run(session, File(it.drop(1)).readText(), "\n") else Scripts.run(session, it) }
        png?.let { File(it).writeBytes(render(session)) }
        pdf?.let { File(it).writeBytes(Pdf.write(session.layout, opened?.name?.substringBefore(".bbm"))) }
        return
    }
    application {
        val session = remember { Session(opened?.takeIf { it.exists() }?.readText() ?: "", MathLayout(SkiaMath, MATH_SIZE)) }
        var file by remember { mutableStateOf(opened) }
        var revision by remember { mutableIntStateOf(0) }
        // The MCP server for Claude Code, on the sheet open here (docs/mcp.md).
        val mcp = remember { McpHttp(session, { file }) { revision++ }.also { it.start() } }
        // The toolbar and the zoom, remembered between runs (Settings).
        var toolbar by remember { mutableStateOf(Settings.toolbar) }
        var hiddenPalettes by remember { mutableStateOf(Settings.hiddenPalettes) }
        var zoom by remember { mutableStateOf(Settings.zoom) }
        fun zoomTo(level: Int) {
            zoom = level
            Settings.zoom = level
        }
        var about by remember { mutableStateOf(false) }
        // The update check (docs/release.md#update-check): a quiet notice in the status bar.
        var checkAtStartup by remember { mutableStateOf(Updates.atStartup) }
        var notice by remember { mutableStateOf<Notice?>(null) }
        suspend fun checkForUpdates(quietIfLatest: Boolean) {
            val release = withContext(Dispatchers.IO) { runCatching { Updates.latest() } }.getOrNull()
            notice = when {
                release != null && Updates.newer(release.version, Version.current) -> Notice("Beermat ${release.version} is out", release.url)
                quietIfLatest -> null
                release != null -> Notice("Beermat ${Version.current} is up to date", null)
                else -> Notice("Couldn't reach GitHub to check for updates", null)
            }
        }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { if (checkAtStartup) checkForUpdates(quietIfLatest = true) }
        LaunchedEffect(file) { session.base = file?.absoluteFile?.parentFile }
        val icon = remember { Brand.icon() }
        val title = "${file?.name ?: "Untitled"}${if (session.dirty) " •" else ""} — Beermat"
        /** Saves to [to], or asks where (a new sheet, Save As). False when cancelled. */
        fun saveAs(to: File?): Boolean {
            val target = to ?: chooseFile(save = true) ?: return false
            saveTo(session, target)
            file = target
            revision++
            return true
        }
        fun saveNow() {
            saveAs(file)
        }
        fun open(next: File?) {
            if (!keepChanges(session, file) { saveAs(it) }) return
            session.load(next?.readText() ?: "")
            file = next
            revision++
        }
        fun quit() {
            if (keepChanges(session, file) { saveAs(it) }) exitApplication()
        }
        Window(onCloseRequest = ::quit, title = title.also { revision }, icon = icon, state = rememberWindowState(position = fittingPlacement(), size = fittingSize())) {
            MenuBar {
                Menu("File") {
                    Item("New", shortcut = KeyShortcut(K.N, ctrl = true), onClick = { open(null) })
                    Item("Open…", shortcut = KeyShortcut(K.O, ctrl = true), onClick = { chooseFile(save = false)?.let(::open) })
                    Item("Save    Ctrl+S", onClick = ::saveNow)
                    Item("Save As…", onClick = { saveAs(null) })
                    Item("Reload from disk", enabled = file != null, onClick = { file?.let { session.load(it.readText()) }; revision++ })
                    Separator()
                    Item("Export to Markdown…", onClick = {
                        chooseExport(file, "md", "Export to Markdown")?.let { target ->
                            session.guard(Unit) {
                                val sheet = pub.beermat.format.WorksheetText.read(session.text())
                                target.writeText(pub.beermat.export.SheetExport.markdown(sheet, pub.beermat.app.plot.PlotImages::dataUri, session.base))
                            }
                        }
                    })
                    Item("Export to PDF…", onClick = {
                        chooseExport(file, "pdf", "Export to PDF")?.let { target -> session.guard(Unit) { target.writeBytes(Pdf.write(session.layout, file?.name?.substringBefore(".bbm"))) } }
                    })
                    Item("Print…", shortcut = KeyShortcut(K.P, ctrl = true), onClick = {
                        session.guard(Unit) { Printing.print(session.layout, file?.name ?: "Untitled") }
                    })
                    Separator()
                    Item("Quit", onClick = ::quit)
                }
                Menu("Edit") {
                    Item("Undo    Ctrl+Z", onClick = { session.guard(Unit) { session.undo() }; revision++ })
                    Item("Redo    Ctrl+Y", onClick = { session.guard(Unit) { session.redo() }; revision++ })
                    Item("Copy as LaTeX", onClick = { session.copyMath(mathMl = false)?.let(::copy) })
                    Item("Copy as MathML (pastes into Word as an equation)", onClick = { session.copyMath(mathMl = true)?.let(::copy) })
                }
                Menu("Insert") {
                    Item("X-Y plot    @", onClick = { session.guard(Unit) { session.insertPlot() }; revision++ })
                    Item("Picture…", onClick = { choosePicture(file)?.let { p -> session.guard(Unit) { insertPicture(session, p) } }; revision++ })
                    Item("Page break    Ctrl+Enter", onClick = { session.guard(Unit) { session.pageBreak() }; revision++ })
                    Item("Area", onClick = { session.guard(Unit) { session.insertArea() }; revision++ })
                }
                Menu("View") {
                    // The toolbar, then which of its palettes to show (docs/editor.md#toolbar).
                    Menu("Toolbar") {
                        CheckboxItem("Show the toolbar", toolbar, onCheckedChange = { toolbar = it; Settings.toolbar = it })
                        Separator()
                        Commands.palettes.forEach { p ->
                            CheckboxItem(p.name, p.name !in hiddenPalettes, enabled = toolbar, onCheckedChange = { shown ->
                                hiddenPalettes = if (shown) hiddenPalettes - p.name else hiddenPalettes + p.name
                                Settings.hiddenPalettes = hiddenPalettes
                            })
                        }
                    }
                    Menu("Zoom") {
                        Settings.ZOOMS.forEach { level -> RadioButtonItem("$level%", zoom == level, onClick = { zoomTo(level) }) }
                    }
                }
                Menu("Format") {
                    Item("Result…    (or double-click a result)", onClick = {
                        session.guard(Unit) {
                            session.editing?.let { e ->
                                session.leave()
                                if (session.hasResult(e.region, e.statement)) ResultFormatDialog.edit(session, e.region, e.statement)
                            }
                        }
                        revision++
                    })
                    Item("Separate regions    Ctrl+Shift+S", onClick = { session.guard(Unit) { session.separate() }; revision++ })
                    // How `*` is drawn in this sheet; right-click on one operator to change just that one.
                    Menu("Multiplication") {
                        RadioButtonItem("Dot   a·b", session.multiplication == Multiplication.DOT, onClick = { session.guard(Unit) { session.setMultiplication(Multiplication.DOT) }; revision++ })
                        RadioButtonItem("Thin space   a b", session.multiplication == Multiplication.SPACE, onClick = { session.guard(Unit) { session.setMultiplication(Multiplication.SPACE) }; revision++ })
                    }
                }
                Menu("Tools") {
                    val command = mcp.command
                    if (command != null) {
                        Item("Connect Claude Code: copy the command (MCP on port ${mcp.port})", onClick = { copy(command) })
                    } else {
                        Item("MCP server off: port ${McpHttp.DEFAULT_PORT} is in use (another window?)", enabled = false, onClick = {})
                    }
                    Item("Recalculate    F9", onClick = { session.guard(Unit) { session.relayout() }; revision++ })
                }
                Menu("Help") {
                    Item("Keyboard shortcuts", onClick = { Brand.open(Brand.SHORTCUTS) })
                    Item("Support Beermat: buy us a beer", onClick = { Brand.open(Brand.BEER) })
                    Item("Check for updates now", onClick = { scope.launch { checkForUpdates(quietIfLatest = false) } })
                    CheckboxItem("Check for updates at startup", checkAtStartup, onCheckedChange = { checkAtStartup = it; Updates.atStartup = it })
                    Item("Terms and privacy", onClick = { Brand.open(Brand.TERMS) })
                    Item("About Beermat", onClick = { about = true })
                }
            }
            SheetWindow(
                file, session, revision, hiddenPalettes.takeIf { toolbar }, zoom, mcp.port, notice,
                onChange = { revision++ }, onSave = ::saveNow, onZoom = { zoomTo(Settings.step(zoom, it)) },
            )
            if (about) About(onClose = { about = false })
            selftest?.let { steps -> LaunchedEffect(Unit) { SelfTest.type(window, File(steps).readText().trim()) } }
            demo?.let { steps -> LaunchedEffect(Unit) { SelfTest.demo(window, File(steps).readText(), session) { revision++ } } }
            KeyBadge()
        }
    }
}

private fun copy(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

/** A worksheet to open, or (with [save]) where to save one: `.bbm` is added to a name without it. */
private fun chooseFile(save: Boolean): File? {
    val dialog = FileDialog(null as Frame?, if (save) "Save the worksheet" else "Open a worksheet", if (save) FileDialog.SAVE else FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".bbm") || name.endsWith(".bbm.md") }
    if (save) dialog.file = "Untitled.bbm"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val f = File(dialog.directory, name)
    return if (save && !name.endsWith(".bbm") && !name.endsWith(".bbm.md")) File(f.path + ".bbm") else f
}

/** A picture file to insert (Insert → Picture…), looked for beside the sheet first. */
private fun choosePicture(sheet: File?): File? {
    val dialog = FileDialog(null as Frame?, "Insert a picture", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in PICTURE_TYPES }
    sheet?.absoluteFile?.parentFile?.let { dialog.directory = it.path }
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

private val PICTURE_TYPES = mapOf("png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif", "webp" to "image/webp", "svg" to "image/svg+xml")

/** Embeds [file] at the crosshair, asking first when it would make the sheet much bigger (docs/file-format.md#images). */
private fun insertPicture(session: Session, file: File) {
    val bytes = file.readBytes()
    if (bytes.size > 3_000_000) {
        val mb = "%.1f".format(bytes.size / 1e6)
        val answer = JOptionPane.showConfirmDialog(null, "${file.name} is $mb MB; the sheet will grow by about a third more than that. Insert it anyway?", "Insert a picture", JOptionPane.OK_CANCEL_OPTION)
        if (answer != JOptionPane.OK_OPTION) return
    }
    val mime = PICTURE_TYPES[file.extension.lowercase()] ?: "application/octet-stream"
    if (!session.insertImage(bytes, mime, file.nameWithoutExtension)) {
        JOptionPane.showMessageDialog(null, "${file.name} isn't a picture Beermat can read (PNG, JPEG, GIF, WebP or SVG).", "Insert a picture", JOptionPane.WARNING_MESSAGE)
    }
}

/** A picture from the clipboard, as PNG bytes. */
private fun png(image: java.awt.Image): ByteArray {
    val buffered = image as? java.awt.image.BufferedImage ?: java.awt.image.BufferedImage(image.getWidth(null), image.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB).also {
        it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
    }
    return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(buffered, "png", it) }.toByteArray()
}

/** A region as a PNG on the clipboard, at twice its size on the page, to paste into a document or an email. */
private fun copyImage(session: Session, view: pub.beermat.app.page.RegionView) {
    val scale = 2f
    val surface = Surface.makeRasterN32Premul((view.width * scale).roundToInt().coerceAtLeast(1), (view.height * scale).roundToInt().coerceAtLeast(1))
    surface.canvas.clear(0xFFFFFFFF.toInt())
    surface.canvas.scale(scale, scale)
    pub.beermat.app.page.PagePainter.paintRegion(surface.canvas, session.layout, view)
    val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    val image = javax.imageio.ImageIO.read(png.inputStream())
    val transferable = object : java.awt.datatransfer.Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any = image
    }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
}

/** Where to export (as [extension], "pdf" or "md"): beside the sheet, with its name, by default. */
private fun chooseExport(sheet: File?, extension: String, title: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.setFilenameFilter { _, name -> name.endsWith(".$extension") }
    sheet?.parentFile?.let { dialog.directory = it.path }
    dialog.file = (sheet?.name?.substringBefore(".bbm") ?: "Untitled") + ".$extension"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, if (name.endsWith(".$extension")) name else "$name.$extension")
}

private fun saveTo(session: Session, file: File) = file.writeText(session.save())

/**
 * Before the sheet is closed or replaced: with unsaved changes, asks whether to save them first
 * ([save] gets the open file, null for a new sheet, and returns false when cancelled).
 * False means stay.
 */
private fun keepChanges(session: Session, file: File?, save: (File?) -> Boolean): Boolean {
    if (!session.dirty) return true
    val answer = JOptionPane.showConfirmDialog(
        null,
        "Save the changes to ${file?.name ?: "this new sheet"}?",
        "Beermat",
        JOptionPane.YES_NO_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE,
    )
    return when (answer) {
        JOptionPane.YES_OPTION -> save(file)
        JOptionPane.NO_OPTION -> true
        else -> false
    }
}

/** All pages as one PNG, at 2 pixels per point. */
private fun render(session: Session): ByteArray {
    val scale = 2f
    val layout = session.layout
    val surface = Surface.makeRasterN32Premul((layout.format.width * scale).roundToInt() + 8, (layout.totalHeight * scale).roundToInt())
    surface.canvas.clear(0xFFE8E8E8.toInt())
    surface.canvas.scale(scale, scale)
    PagePainter.paint(surface.canvas, layout, overlay(session))
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}

private fun overlay(session: Session, drag: Triple<Set<Int>, Float, Float>? = null, band: List<Float>? = null, resize: Triple<Int, Float, Float>? = null, hover: Overlay.Tip? = null) =
    Overlay(session.editing, session.crosshair, session.textEditing?.first, drag, session.selected, band, resize, session.table, session.completion, hover)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SheetWindow(
    file: File?, session: Session, revision: Int, hiddenPalettes: Set<String>?, zoom: Int, mcpPort: Int?, notice: Notice?,
    onChange: () -> Unit, onSave: () -> Unit, onZoom: (Int) -> Unit,
) {
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
    var tableMenu by remember { mutableStateOf<Offset?>(null) }
    var imageMenu by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    var stamp by remember(file) { mutableStateOf(file?.lastModified()) }
    var conflict by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        if (file == null) return@LaunchedEffect
        while (true) {
            delay(500)
            val now = file.lastModified()
            if (now != stamp) {
                stamp = now
                if (session.dirty) conflict = true else {
                    session.load(file.readText())
                    onChange()
                }
            }
        }
    }

    val layout = session.layout.also { revision }
    val left = maxOf(0f, (width / scale - layout.format.width) / 2)
    Column(Modifier.fillMaxSize().background(Color(0xFFE8E8E8))) {
        // Null when the toolbar is off; nothing either when every palette is.
        val palettes = hiddenPalettes?.let { hidden -> Commands.palettes.filter { it.name !in hidden } }.orEmpty()
        if (palettes.isNotEmpty()) Toolbar(session, palettes, onUsed = { focus.requestFocus(); onChange() })
        if (conflict) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFF3CD)).padding(8.dp)) {
                BasicText("${file?.name} changed on disk.  ")
                BasicText("Reload it", Modifier.clickable { file?.let { session.load(it.readText()) }; conflict = false; onChange() }, style = TextStyle(color = Color(0xFF1E5BD8)))
                BasicText("   ")
                BasicText("Keep mine", Modifier.clickable { conflict = false }, style = TextStyle(color = Color(0xFF1E5BD8)))
            }
        }
        val vertical = rememberScrollState()
        val horizontal = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.fillMaxSize()
                    .onSizeChanged { width = it.width }
                    // Ctrl+wheel zooms, before the scroll below takes the wheel.
                    .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
                        if (!e.keyboardModifiers.isCtrlPressed) return@onPointerEvent
                        val dy = e.changes.first().scrollDelta.y
                        if (dy != 0f) onZoom(if (dy < 0) 1 else -1)
                        e.changes.forEach { it.consume() }
                    }
                    .verticalScroll(vertical)
                    // A page wider than the window (zoomed in) scrolls sideways.
                    .horizontalScroll(horizontal)
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (session.textEditing != null) return@onPreviewKeyEvent false
                        val done = session.guard(false) { Keys.handle(e, session, onSave) }
                        if (done) onChange()
                        done
                    },
            ) {
                Canvas(
                    Modifier.size((maxOf(width / scale, layout.format.width) * scale / density).dp, (layout.totalHeight * scale / density).dp)
                        .pointerInput(layout) {
                            var lastClick: Triple<Long, Float, Float>? = null
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
                                        mulMenu = session.editing?.let { Editor.multiplicationAt(it.state)?.second }?.let { start to it }
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
                                                        val now = System.currentTimeMillis()
                                                        val again = lastClick?.let { (t, x, y) -> now - t < 450 && abs(x - px) < 4f && abs(y - py) < 4f } == true
                                                        lastClick = if (again) null else Triple(now, px, py)
                                                        if (again && session.table != null) {
                                                            TableEditor.key(session, CellKey.Edit)
                                                        } else if (again) {
                                                            session.leave()
                                                            session.resultAt(px, py)?.let { (r, k) -> ResultFormatDialog.edit(session, r, k) }
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
                    drawIntoCanvas {
                        val c = it.nativeCanvas
                        c.save()
                        c.scale(scale, scale)
                        PagePainter.paint(c, layout, overlay(session, drag, band, resizing, hover), width / scale)
                        c.restore()
                    }
                }
                session.textEditing?.let { (index, statement) -> TextEditor(session, index, statement, scale, left, density, onChange) }
                mulMenu?.let { (at, mul) ->
                    MultiplicationMenu(at, mul, session.multiplication, onDismiss = { mulMenu = null }) { style ->
                        mulMenu = null
                        if (!session.guard(false) { session.edit { Editor.multiplication(it, style) } }) Toolkit.getDefaultToolkit().beep()
                        focus.requestFocus()
                        onChange()
                    }
                }
                imageMenu?.let { (at, index) ->
                    TableMenu(at, title = "Image", items = listOf("Copy as image"), onDismiss = { imageMenu = null }) { _ ->
                        imageMenu = null
                        session.guard(Unit) { session.layout.views.firstOrNull { it.index == index }?.let { copyImage(session, it) } }
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
        StatusBar(session, mcpPort, revision, notice, zoom)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
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

/** The editor's keystrokes (docs/editor.md), from Compose key events. */
object Keys {

    fun handle(e: KeyEvent, session: Session, onSave: () -> Unit): Boolean {
        val ctrl = e.isCtrlPressed && !e.isAltPressed
        val shift = e.isShiftPressed
        if (session.table != null) table(e, ctrl, shift, session)?.let { return it }
        // Characters arrive as typed events, after the keyboard layout (AltGr included).
        if (e.type == KeyEventType.Unknown) {
            val code = e.utf16CodePoint
            if (code < 32 || code == 127 || code == 0xFFFF) return false
            if (ctrl) return false
            val c = code.toChar()
            return session.key(if (c == ' ') Key.Space else Key.Type(c))
        }
        if (e.type != KeyEventType.KeyDown) return false
        // The list of a module's functions, after `bt.`, takes the arrows, Enter, Tab and Escape while open.
        if (session.completion != null) when (e.key) {
            K.DirectionUp -> return session.moveCompletion(-1)
            K.DirectionDown -> return session.moveCompletion(1)
            K.Enter, K.NumPadEnter, K.Tab -> return session.acceptCompletion()
            K.Escape -> return session.closeCompletion()
            else -> {}
        }
        return when (e.key) {
            K.Enter, K.NumPadEnter -> {
                if (ctrl) session.pageBreak() else session.enter()
                true
            }
            K.Escape -> session.leave().also { session.deselect() }.let { true }
            K.Backspace -> session.deleteSelected() || session.key(Key.Backspace)
            K.Delete -> session.deleteSelected() || session.key(Key.Delete)
            K.DirectionLeft -> session.key(Key.Left)
            K.DirectionUp -> session.vertical(up = true)
            K.DirectionDown -> session.vertical(up = false)
            K.DirectionRight -> session.key(Key.Right)
            K.Tab -> session.key(if (shift) Key.BackTab else Key.Tab)
            K.MoveHome -> session.key(Key.Home)
            K.MoveEnd -> session.key(Key.End)
            K.Insert -> session.key(Key.Insert)
            K.F9 -> session.relayout().let { true }
            else -> if (ctrl) control(e.key, shift, session, onSave) else false
        }
    }

    /** A key for the table grid at the cell cursor (docs/tables.md#editing); null to handle it as usual (Ctrl+S, Ctrl+Z…). */
    private fun table(e: KeyEvent, ctrl: Boolean, shift: Boolean, session: Session): Boolean? {
        if (e.type == KeyEventType.Unknown) {
            val code = e.utf16CodePoint
            if (code < 32 || code == 127 || code == 0xFFFF || ctrl) return null
            return TableEditor.key(session, CellKey.Type(code.toChar()))
        }
        if (e.type != KeyEventType.KeyDown) return null
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val key = when (e.key) {
            K.Enter, K.NumPadEnter -> CellKey.Enter
            K.Escape -> CellKey.Escape
            K.Backspace -> CellKey.Backspace
            K.Delete -> CellKey.Delete
            K.DirectionLeft -> CellKey.Left
            K.DirectionRight -> CellKey.Right
            K.DirectionUp -> CellKey.Up
            K.DirectionDown -> CellKey.Down
            K.Tab -> if (shift) CellKey.BackTab else CellKey.Tab
            K.MoveHome -> CellKey.Home
            K.MoveEnd -> CellKey.End
            K.F2 -> CellKey.Edit
            K.V -> if (ctrl) return (clipboard.getData(DataFlavor.stringFlavor) as? String)?.let { TableEditor.paste(session, it) } ?: false else return null
            K.C -> if (ctrl) return TableEditor.copy(session)?.let { clipboard.setContents(StringSelection(it), null); true } ?: false else return null
            else -> return null
        }
        return TableEditor.key(session, key)
    }

    /** Asks for a matrix's rows and columns, as `3, 1` (a column of 3) or `2 2`. */
    /** `Ctrl+M` and the matrix button: a new matrix in an empty slot; in a matrix, Insert or Delete of rows and columns. */
    fun matrixCommand(session: Session): Boolean {
        if (!session.inMatrix) return askMatrix()?.let { (r, c) -> session.matrix(r, c) } ?: false
        val field = javax.swing.JTextField("1, 0", 8)
        val panel = javax.swing.JPanel(java.awt.BorderLayout(0, 6)).apply {
            add(javax.swing.JLabel("Rows and columns, after the cursor's cell (Insert) or from it (Delete):"), java.awt.BorderLayout.NORTH)
            add(field, java.awt.BorderLayout.CENTER)
        }
        val options = arrayOf("Insert", "Delete", "Cancel")
        val choice = javax.swing.JOptionPane.showOptionDialog(null, panel, "Resize matrix", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0])
        if (choice !in 0..1) return false
        val n = Regex("""\d+""").findAll(field.text).map { it.value.toInt() }.toList()
        if (n.size != 2) return false
        return session.resizeMatrix(n[0], n[1], insert = choice == 0)
    }

    fun askMatrix(): Pair<Int, Int>? {
        val answer = javax.swing.JOptionPane.showInputDialog(null, "Rows and columns:", "Insert matrix", javax.swing.JOptionPane.QUESTION_MESSAGE, null, null, "3, 1") as? String ?: return null
        val n = Regex("""\d+""").findAll(answer).map { it.value.toInt() }.toList()
        return if (n.size == 2) n[0] to n[1] else null
    }

    /** The character a control-key chord names: Ctrl+G is 'g', Ctrl+= is '='. */
    private val CHORDS: Map<K, Char> = buildMap {
        listOf(K.A, K.B, K.C, K.D, K.E, K.F, K.G, K.H, K.I, K.J, K.K, K.L, K.M, K.N, K.O, K.P, K.Q, K.R, K.S, K.T, K.U, K.V, K.W, K.X, K.Y, K.Z)
            .forEachIndexed { k, key -> put(key, 'a' + k) }
        listOf(K.Zero, K.One, K.Two, K.Three, K.Four, K.Five, K.Six, K.Seven, K.Eight, K.Nine).forEachIndexed { k, key -> put(key, '0' + k) }
        put(K.Equals, '=')
        put(K.Minus, '-')
        put(K.Backslash, '\\')
        put(K.Period, '.')
    }

    private fun control(key: K, shift: Boolean, session: Session, onSave: () -> Unit): Boolean {
        val c = CHORDS[key] ?: return false
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        return when {
            c == 's' && shift -> session.separate().let { true }
            c == 's' -> onSave().let { true }
            c == 'z' -> session.undo().let { true }
            c == 'y' -> session.redo().let { true }
            c == 'm' -> matrixCommand(session)
            c == 'c' -> session.copyText()?.let { clipboard.setContents(StringSelection(it), null); true } ?: false
            c == 'v' -> when {
                // A picture copied from another program becomes an image region; text, a math region.
                clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor) -> (clipboard.getData(DataFlavor.stringFlavor) as? String)?.let { session.paste(it); true } ?: false
                clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) -> (clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image)?.let { session.insertImage(png(it), "image/png", "Pasted picture") } ?: false
                else -> false
            }
            else -> session.key(Key.Ctrl(c, shift))
        }
    }
}

/**
 * `--selftest keys.txt` and `--demo steps.txt`: once the window is up, play keys into the
 * window's own event queue, as the keyboard would (nothing reaches other programs). The self
 * test types them quickly; the demo plays steps at a human pace, showing the keys worth
 * learning in a corner, for recording videos.
 *
 * Demo steps, one per line: `type <keys>` (as `--script`'s, with `«ctrl-x»` chords), `pause <ms>`,
 * `click <x> <y>` (sheet points, as `--script`), `save`.
 */
object SelfTest {
    /** The key the demo just pressed, for the overlay; null when none. */
    var shown by mutableStateOf<String?>(null)

    suspend fun type(window: java.awt.Window, keys: String) {
        delay(2500)
        window.toFront()
        play(window, Scripts.keys(keys), paced = false)
    }

    suspend fun demo(window: java.awt.Window, steps: String, session: Session, onChange: () -> Unit) {
        delay(2500)
        window.toFront()
        for (step in steps.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
            val (verb, rest) = step.substringBefore(' ') to step.substringAfter(' ', "")
            when (verb) {
                "type" -> play(window, Scripts.keys(rest), paced = true)
                "pause" -> delay(rest.toLong())
                "click" -> rest.split(' ').map { it.toFloat() }.let { (x, y) -> session.click(x, y); onChange() }
                "save" -> Unit
                else -> error("unknown demo step: $step")
            }
        }
        delay(1500)
        shown = null
    }

    private suspend fun play(window: java.awt.Window, keys: List<String>, paced: Boolean) {
        val queue = Toolkit.getDefaultToolkit().systemEventQueue
        // Compose listens on its drawing surface, the innermost component.
        fun deepest(c: java.awt.Component): java.awt.Component =
            (c as? java.awt.Container)?.components?.firstOrNull { it.isVisible }?.let(::deepest) ?: c
        fun target(): java.awt.Component = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: deepest(window)
        fun press(code: Int, modifiers: Int = 0) {
            val now = System.currentTimeMillis()
            queue.postEvent(AwtKeyEvent(target(), AwtKeyEvent.KEY_PRESSED, now, modifiers, code, AwtKeyEvent.CHAR_UNDEFINED))
            queue.postEvent(AwtKeyEvent(target(), AwtKeyEvent.KEY_RELEASED, now, modifiers, code, AwtKeyEvent.CHAR_UNDEFINED))
        }
        for (k in keys) {
            val code = when (k) {
                "enter" -> AwtKeyEvent.VK_ENTER
                "esc" -> AwtKeyEvent.VK_ESCAPE
                "bs" -> AwtKeyEvent.VK_BACK_SPACE
                "left" -> AwtKeyEvent.VK_LEFT
                "right" -> AwtKeyEvent.VK_RIGHT
                "up" -> AwtKeyEvent.VK_UP
                "down" -> AwtKeyEvent.VK_DOWN
                "tab" -> AwtKeyEvent.VK_TAB
                else -> null
            }
            when {
                code != null -> {
                    if (paced) shown = KEY_NAMES[k]
                    press(code)
                }
                k.startsWith("ctrl-") -> {
                    val shift = k.startsWith("ctrl-shift-")
                    val c = k.last()
                    if (paced) shown = "Ctrl+" + (if (shift) "Shift+" else "") + c.uppercaseChar()
                    press(AwtKeyEvent.getExtendedKeyCodeForChar(c.code), AwtKeyEvent.CTRL_DOWN_MASK or (if (shift) AwtKeyEvent.SHIFT_DOWN_MASK else 0))
                }
                else -> {
                    val c = if (k == "space") ' ' else k.single()
                    // Letters and digits are plain typing; operators and the like are worth showing.
                    if (paced && !c.isLetterOrDigit()) shown = if (c == ' ') "Space" else c.toString()
                    queue.postEvent(AwtKeyEvent(target(), AwtKeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, AwtKeyEvent.VK_UNDEFINED, c))
                }
            }
            delay(if (!paced) 40 else if (k == "enter") 600 else 125)
        }
    }

    private val KEY_NAMES = mapOf("enter" to "Enter", "esc" to "Esc", "bs" to "Backspace", "left" to "←", "right" to "→", "up" to "↑", "down" to "↓", "tab" to "Tab")
}

/** `--script`: clicks and typing, `;`-separated (one per line in a file), for checking the editor without a person at the keyboard. */
object Scripts {
    fun run(session: Session, script: String, separator: String = ";") {
        for (step in script.split(separator).map { it.trim() }.filter { it.isNotEmpty() }) {
            val (verb, rest) = step.substringBefore(' ') to step.substringAfter(' ', "")
            when (verb) {
                "click" -> rest.split(' ').map { it.toFloat() }.let { (x, y) -> session.click(x, y) }
                "type" -> keys(rest).forEach { k ->
                    when (k) {
                        "enter" -> session.enter()
                        "esc" -> session.leave()
                        else -> session.key(key(k) ?: Key.Type(k.single()))
                    }
                }
                "save" -> session.save()
                else -> error("unknown script step: $step")
            }
        }
    }

    /** The keys of [text]: characters, and `«name»` for special keys. */
    fun keys(text: String): List<String> {
        val out = mutableListOf<String>()
        var k = 0
        while (k < text.length) {
            if (text[k] == '«') {
                val end = text.indexOf('»', k)
                out += text.substring(k + 1, end)
                k = end + 1
            } else {
                out += text[k++].toString()
            }
        }
        return out
    }

    private fun key(name: String): Key? = when {
        name.length == 1 -> null
        name == "space" -> Key.Space
        name == "bs" -> Key.Backspace
        name == "del" -> Key.Delete
        name == "left" -> Key.Left
        name == "right" -> Key.Right
        name == "tab" -> Key.Tab
        name == "ins" -> Key.Insert
        name.startsWith("ctrl-shift-") -> Key.Ctrl(name.last(), true)
        name.startsWith("ctrl-") -> Key.Ctrl(name.last())
        else -> error("unknown key «$name»")
    }
}

/**
 * The toolbar (docs/editor.md#toolbar): every command as a small button, grouped in
 * palettes; hovering shows its name and keystroke. Buttons never take the focus from the sheet.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(session: Session, palettes: List<Palette>, onUsed: () -> Unit) {
    LaunchedEffect(Unit) { Commands.matrixCommand = Keys::matrixCommand }
    FlowRow(
        Modifier.fillMaxWidth().background(Color(0xFFF4F4F4)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Each palette wraps as a whole, so a divider never ends a row on its own.
        palettes.forEachIndexed { k, palette ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (k > 0) Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(22.dp).background(Color(0xFFCCCCCC)))
                palette.commands.forEach { c ->
                    TooltipArea(
                        tooltip = {
                            Box(Modifier.background(Color(0xFF333333)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                BasicText("${c.name}   ${c.keys}", style = TextStyle(color = Color.White, fontSize = 12.sp))
                            }
                        },
                        delayMillis = 350,
                    ) {
                        Box(
                            Modifier.padding(1.dp).height(24.dp).widthIn(min = 26.dp)
                                .focusProperties { canFocus = false }
                                .clickable {
                                    if (!session.guard(false) { c.run(session) }) Toolkit.getDefaultToolkit().beep()
                                    onUsed()
                                }
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(c.label, style = TextStyle(fontSize = 13.sp, color = Color(0xFF222222)))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The window's size: tall for a page, but wholly within the screen's work area (above the taskbar),
 * so every edge and corner can be grabbed to resize it.
 */
private fun fittingSize(): DpSize {
    // In logical pixels, as dp are.
    val work = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return DpSize(minOf(1000, work.width - 2 * WINDOW_MARGIN).dp, minOf(1100, work.height - 2 * WINDOW_MARGIN).dp)
}

/** The window centred in the work area. */
private fun fittingPlacement(): WindowPosition {
    val work = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val size = fittingSize()
    return WindowPosition((work.x + (work.width - size.width.value) / 2).dp, (work.y + (work.height - size.height.value) / 2).dp)
}

private const val WINDOW_MARGIN = 16

/** In a demo, the key just pressed, at the bottom right. */
@Composable
private fun KeyBadge() {
    val key = SelfTest.shown ?: return
    Box(Modifier.fillMaxSize().padding(bottom = 44.dp, end = 28.dp), contentAlignment = Alignment.BottomEnd) {
        Box(Modifier.background(Color(0xDD2B2118), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            BasicText(key, style = TextStyle(color = Color.White, fontSize = 22.sp))
        }
    }
}
