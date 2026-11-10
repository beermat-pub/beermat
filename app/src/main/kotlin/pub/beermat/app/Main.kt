package pub.beermat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key as K
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import java.awt.Frame
import java.awt.Toolkit
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import javax.swing.JOptionPane
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.PagePainter
import pub.beermat.app.page.Session
import pub.beermat.app.print.Pdf
import pub.beermat.app.print.Printing
import pub.beermat.engine.Version
import pub.beermat.engine.syntax.Multiplication

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
    // With `--selftest`: once the keys are typed, the sheet as it stands, saved as a PNG, and the app closes.
    val snapshot = args.indexOf("--snapshot").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    // Without a file, a blank sheet ("Untitled"), named when first saved.
    val opened = args.firstOrNull { !it.startsWith("--") && it != png && it != pdf && it != script && it != selftest && it != demo && it != snapshot }?.let(::File)
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
    // An .xmcd file given to the app (Open With) is imported once the window is up.
    val xmcd = opened?.takeIf { it.name.endsWith(".xmcd", ignoreCase = true) && it.exists() }
    application {
        val session = remember { Session(opened?.takeIf { it.exists() && xmcd == null }?.readText() ?: "", MathLayout(SkiaMath, MATH_SIZE)) }
        var file by remember { mutableStateOf(opened?.takeIf { xmcd == null }) }
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
        /**
         * An `.xmcd` worksheet, converted (docs/xmcd.md): a new sheet, unsaved, to be saved as the `.bbm`
         * beside it (or, when that exists already, wherever Save As puts it). What didn't come across is listed.
         */
        fun importXmcd(source: File) {
            val imported = session.guard(null) { source.inputStream().use { pub.beermat.xmcd.XmcdImport.import(it, source.nameWithoutExtension) } } ?: return
            session.load(imported.text, unsaved = true)
            file = File(source.absoluteFile.parentFile, source.nameWithoutExtension + ".bbm").takeUnless { it.exists() }
            revision++
            val warnings = imported.warnings.distinct()
            if (warnings.isNotEmpty()) {
                val list = warnings.take(20).joinToString("\n") { "• $it" } + if (warnings.size > 20) "\n…and ${warnings.size - 20} more" else ""
                JOptionPane.showMessageDialog(null, "${source.name} was imported; some of it didn't come across as it was:\n\n$list", "Import", JOptionPane.INFORMATION_MESSAGE)
            }
        }
        fun open(next: File?) {
            if (!keepChanges(session, file) { saveAs(it) }) return
            if (next != null && next.name.endsWith(".xmcd", ignoreCase = true)) return importXmcd(next)
            session.load(next?.readText() ?: "")
            file = next
            revision++
        }
        fun quit() {
            if (keepChanges(session, file) { saveAs(it) }) exitApplication()
        }
        LaunchedEffect(Unit) { xmcd?.let(::importXmcd) }
        Window(onCloseRequest = ::quit, title = title.also { revision }, icon = icon, state = rememberWindowState(position = fittingPlacement(), size = fittingSize())) {
            MenuBar {
                Menu("File") {
                    Item("New", shortcut = KeyShortcut(K.N, ctrl = !macOs, meta = macOs), onClick = { open(null) })
                    Item("Open…", shortcut = KeyShortcut(K.O, ctrl = !macOs, meta = macOs), onClick = { chooseFile(save = false)?.let(::open) })
                    Item(shortcutText("Save    Ctrl+S"), onClick = ::saveNow)
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
                    // Text, Word equations, tables, plots and pictures (docs/export.md#word).
                    Item("Export to Word…", onClick = {
                        chooseExport(file, "docx", "Export to Word")?.let { target ->
                            session.guard(Unit) {
                                val sheet = pub.beermat.format.WorksheetText.read(session.text())
                                target.writeBytes(pub.beermat.export.Docx.write(sheet, WordPictures(session.base)::picture, session.base))
                            }
                        }
                    })
                    Item("Export to PDF…", onClick = {
                        chooseExport(file, "pdf", "Export to PDF")?.let { target -> session.guard(Unit) { target.writeBytes(Pdf.write(session.layout, file?.name?.substringBefore(".bbm"))) } }
                    })
                    // What is past the right margin prints on pages of its own, unless the sheet says not to (docs/editor.md#printing).
                    CheckboxItem("Print single page width", session.layout.calculation.settings.singlePageWidth, onCheckedChange = { on ->
                        session.guard(Unit) { session.setFrontmatter("single_page_width", if (on) "true" else null) }
                        revision++
                    })
                    Item("Print…", shortcut = KeyShortcut(K.P, ctrl = !macOs, meta = macOs), onClick = {
                        session.guard(Unit) { Printing.print(session.layout, file?.name ?: "Untitled") }
                    })
                    Separator()
                    Item("Quit", onClick = ::quit)
                }
                Menu("Edit") {
                    Item(shortcutText("Undo    Ctrl+Z"), onClick = { session.guard(Unit) { session.undo() }; revision++ })
                    Item(shortcutText(if (macOs) "Redo    Ctrl+Shift+Z" else "Redo    Ctrl+Y"), onClick = { session.guard(Unit) { session.redo() }; revision++ })
                    Item("Copy as LaTeX", onClick = { session.copyMath(mathMl = false)?.let(DesktopHost::copyText) })
                    Item("Copy as MathML (pastes into Word as an equation)", onClick = { session.copyMath(mathMl = true)?.let(DesktopHost::copyText) })
                }
                Menu("Insert") {
                    Item("X-Y plot    @", onClick = { session.guard(Unit) { session.insertPlot() }; revision++ })
                    Item("Table", onClick = { session.guard(Unit) { session.insertTable() }; revision++ })
                    Item("Picture…", onClick = { choosePicture(file)?.let { p -> session.guard(Unit) { insertPicture(session, p) } }; revision++ })
                    Item(shortcutText("Page break    Ctrl+Enter"), onClick = { session.guard(Unit) { session.pageBreak() }; revision++ })
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
                    Item(shortcutText("Separate regions    Ctrl+Shift+S"), onClick = { session.guard(Unit) { session.separate() }; revision++ })
                    // How `*` is drawn in this sheet; right-click on one operator to change just that one.
                    Menu("Multiplication") {
                        RadioButtonItem("Dot   a·b", session.multiplication == Multiplication.DOT, onClick = { session.guard(Unit) { session.setMultiplication(Multiplication.DOT) }; revision++ })
                        RadioButtonItem("Thin space   a b", session.multiplication == Multiplication.SPACE, onClick = { session.guard(Unit) { session.setMultiplication(Multiplication.SPACE) }; revision++ })
                    }
                    // Angle and currency as dimensions (docs/units.md#angle-and-currency-as-dimensions), off by default.
                    Menu("Units") {
                        val units = session.units
                        CheckboxItem("Angles as a dimension (rad)", units.angle, onCheckedChange = { on -> session.guard(Unit) { session.setFrontmatter("angle", if (on) "dimension" else null) }; revision++ })
                        Item(units.currency?.let { "Currency: $it…" } ?: "Currency…", onClick = {
                            val name = javax.swing.JOptionPane.showInputDialog(null, "The currency of this sheet's money, such as AUD (empty for none):", units.currency ?: "")
                            if (name != null) session.guard(Unit) { session.setFrontmatter("currency", name.trim().ifEmpty { null }) }
                            revision++
                        })
                    }
                }
                Menu("Tools") {
                    val command = mcp.command
                    if (command != null) {
                        Item("Connect Claude Code: copy the command (MCP on port ${mcp.port})", onClick = { DesktopHost.copyText(command) })
                    } else {
                        Item("MCP server off: port ${McpHttp.DEFAULT_PORT} is in use (another window?)", enabled = false, onClick = {})
                    }
                    Item("Recalculate    F9", onClick = { session.guard(Unit) { session.relayout() }; revision++ })
                    // The packages a sheet's Python files import (docs/symbolic.md#the-users-own-packages).
                    Item("Python packages…", onClick = { PythonPackages.show(); session.guard(Unit) { session.relayout() }; revision++ })
                    // The beermat command on the PATH (docs/cli.md#installing); Windows's installer does it there.
                    if (CommandLine.available) {
                        Separator()
                        Item("Install the beermat command…", onClick = {
                            javax.swing.JOptionPane.showMessageDialog(null, CommandLine.install(), "The beermat command", javax.swing.JOptionPane.INFORMATION_MESSAGE)
                        })
                    }
                }
                Menu("Help") {
                    // F1 on a built-in function opens its reference entry (docs/manual.md#in-the-app-and-for-ai).
                    Item("Manual", shortcut = KeyShortcut(K.F1), onClick = { Brand.open(Brand.MANUAL + (session.helpPage() ?: "")) })
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
            selftest?.let { steps ->
                LaunchedEffect(Unit) {
                    SelfTest.type(window, File(steps).readText().trim())
                    snapshot?.let {
                        delay(1000)
                        File(it).writeBytes(render(session))
                        exitApplication()
                    }
                }
            }
            demo?.let { steps -> LaunchedEffect(Unit) { SelfTest.demo(window, File(steps).readText(), session) { revision++ } } }
            KeyBadge()
        }
    }
}

/**
 * Shows one of the system's file dialogs ([block]: FileKit, Windows's modern one rather than the old
 * one AWT opens) and waits for the answer, the window still drawing meanwhile, as under a modal dialog.
 */
private fun <T> waitFor(block: suspend () -> T): T {
    val loop = Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
    var result: Result<T>? = null
    Thread {
        result = runCatching { kotlinx.coroutines.runBlocking { block() } }
        loop.exit()
    }.apply { isDaemon = true }.start()
    loop.enter()
    // The loop may have been left before it was entered; the answer is there either way.
    while (result == null) Thread.onSpinWait()
    return result!!.getOrThrow()
}

private fun dialog(title: String) = FileKitDialogSettings(title = title)

/** A worksheet to open, or (with [save]) where to save one: `.bbm` is added to a name without it. */
private fun chooseFile(save: Boolean): File? = waitFor {
    if (save) {
        FileKit.openFileSaver("Untitled", "bbm", dialogSettings = dialog("Save the worksheet"))?.file?.let { f ->
            if (f.name.endsWith(".bbm") || f.name.endsWith(".bbm.md")) f else File(f.path + ".bbm")
        }
    } else {
        // An .xmcd worksheet opens converted (docs/xmcd.md).
        FileKit.openFilePicker(FileKitType.File(setOf("bbm", "md", "xmcd")), dialogSettings = dialog("Open a worksheet"))?.file
    }
}

/** A picture file to insert (Insert → Picture…), looked for beside the sheet first. */
private fun choosePicture(sheet: File?): File? = waitFor {
    val folder = sheet?.absoluteFile?.parentFile?.let { PlatformFile(it) }
    FileKit.openFilePicker(FileKitType.File(PICTURE_TYPES.keys), directory = folder, dialogSettings = dialog("Insert a picture"))?.file
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

/** Where to export (as [extension], "pdf", "docx" or "md"): beside the sheet, with its name, by default. */
private fun chooseExport(sheet: File?, extension: String, title: String): File? = waitFor {
    val folder = sheet?.absoluteFile?.parentFile?.let { PlatformFile(it) }
    val name = sheet?.name?.substringBefore(".bbm") ?: "Untitled"
    FileKit.openFileSaver(name, extension, folder, dialog(title))?.file?.let { f ->
        if (f.name.endsWith(".$extension")) f else File(f.path + ".$extension")
    }
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

/**
 * The window's content: the sheet view (ui), with what the desktop adds around it: following the
 * file on disk (a banner when it changed under unsaved edits) and the status bar.
 */
@Composable
private fun SheetWindow(
    file: File?, session: Session, revision: Int, hiddenPalettes: Set<String>?, zoom: Int, mcpPort: Int?, notice: Notice?,
    onChange: () -> Unit, onSave: () -> Unit, onZoom: (Int) -> Unit,
) {
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

    // Null when the toolbar is off; nothing either when every palette is.
    val palettes = hiddenPalettes?.let { hidden -> Commands.palettes.filter { it.name !in hidden } }.orEmpty()
    SheetView(
        session, revision, palettes, zoom, DesktopHost, onChange, onSave, onZoom,
        banner = {
            if (conflict) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFFFF3CD)).padding(8.dp)) {
                    BasicText("${file?.name} changed on disk.  ")
                    BasicText("Reload it", Modifier.clickable { file?.let { session.load(it.readText()) }; conflict = false; onChange() }, style = TextStyle(color = Color(0xFF1E5BD8)))
                    BasicText("   ")
                    BasicText("Keep mine", Modifier.clickable { conflict = false }, style = TextStyle(color = Color(0xFF1E5BD8)))
                }
            }
        },
        statusBar = { StatusBar(session, mcpPort, revision, notice, zoom) },
    )
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
