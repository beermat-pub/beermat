package pub.beermat.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pub.beermat.app.Commands
import pub.beermat.app.MATH_SIZE
import pub.beermat.app.ResultFormatPanel
import pub.beermat.app.SheetView
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.Session
import pub.beermat.app.WordPictures
import pub.beermat.engine.HostFile
import pub.beermat.engine.RemoteFiles
import pub.beermat.engine.Version
import pub.beermat.export.Docx
import pub.beermat.format.WorksheetText
import kotlin.io.encoding.Base64

/**
 * The page in VS Code's web view (docs/vscode.md): a `.bbm` file opened on its page. The document VS
 * Code holds is the truth: the extension sends its text ("load"), every change made here goes back
 * as the whole text ("edit"), and a change made elsewhere (the text editor, undo, git, an AI
 * assistant) comes in again and is shown. Saving, links and questions are the extension's.
 */
@Composable
internal fun VsCodeApp() {
    var ready by remember { mutableStateOf(false) }
    // The document's text as VS Code last sent it, and the sheet's own text that matches it: written
    // as the page writes (ids, positions, results), so that opening a file and clicking on it changes nothing.
    var received by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableIntStateOf(100) }
    var resultFormat by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Whether the page has changed nothing since the text came: SymPy's answers then only complete the text it started from.
    var untouched by remember { mutableStateOf(true) }

    /** Sends the sheet's text when it has changed, once SymPy has answered what it was asked. */
    fun flush() {
        val s = session ?: return
        if (VsCodeBridge.busy || RemoteFiles.busy || VsCodeConsent.busy) return
        val text = s.guard(null) { s.text() } ?: return
        if (text != sent) {
            sent = text
            untouched = false
            // VS Code holds this text now: an undo back to the text it sent before is news.
            received = null
            postToVsCode("edit", text, 0)
        }
    }
    fun changed() {
        revision++
    }
    // Changes go to VS Code once typing pauses, not at every key: fewer, whole steps to undo.
    LaunchedEffect(revision) {
        delay(400)
        flush()
    }
    LaunchedEffect(Unit) {
        loadFonts()
        ready = true
        WebHost.resultFormat = { region, statement -> resultFormat = region to statement }
        WebHost.answered = ::changed
        // An answer from outside (SymPy, a file): the sheet worked out again, which now finds it.
        val reworked = {
            session?.let { s ->
                s.guard(Unit) { s.relayout() }
                if (untouched && !VsCodeBridge.busy && !RemoteFiles.busy && !VsCodeConsent.busy) sent = s.guard(null) { s.text() }
            }
            changed()
        }
        VsCodeBridge.answered = reworked
        RemoteFiles.answered = reworked
        VsCodeConsent.answered = reworked
        // A sheet's Python file runs once the user says it may (the extension asks, and checks again).
        pub.beermat.format.PythonConsent.allowed = { file -> VsCodeConsent.allowed(file.path) }
        // Data files and pictures beside the sheet, read by the extension.
        val files = mutableMapOf<Int, String>()
        RemoteFiles.request = { path ->
            files[files.size + 1] = path
            postToVsCode("file", path, files.size)
        }
        onVsCodeMessage { type, text, id, yes ->
            when (type) {
                // Not the text this page sent, coming back.
                "load" -> if (text != received && text != sent) {
                    received = text
                    val s = session
                    // The first text makes the page; a later one replaces the sheet, the crosshair staying.
                    val now = s ?: Session(text, MathLayout(SkiaMath, MATH_SIZE)).also { session = it }
                    if (s != null) {
                        // Data files are read again, in case they changed too.
                        RemoteFiles.forget()
                        s.guard(Unit) { s.replace(text) }
                    }
                    sent = now.guard(null) { now.text() }
                    untouched = true
                    revision++
                }
                "answer" -> WebHost.answer(id, text, yes)
                // The extension found the installed beermat: SymPy through it (docs/vscode.md#step-3-in-detail).
                "helper" -> session?.let { s ->
                    pub.beermat.symbolic.Helper.bridge = VsCodeBridge
                    s.guard(Unit) { s.relayout() }
                    if (untouched) sent = s.guard(null) { s.text() }
                    revision++
                }
                "bridged" -> VsCodeBridge.answer(id, text)
                "consented" -> VsCodeConsent.answer(id, yes)
                // The sheet's folder, where its data files and pictures are found.
                "folder" -> session?.let { s -> s.guard(Unit) { s.base = HostFile(text) }; revision++ }
                "filed" -> files[id]?.let { path -> RemoteFiles.answer(path, if (yes) Base64.decode(text) else null) }
            }
        }
        onPaste { text -> session?.let { s -> s.guard(Unit) { s.paste(text) }; changed() } }
        postToVsCode("ready", "", 0)
    }
    val s = session
    if (!ready || s == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFFE8E8E8)), contentAlignment = Alignment.Center) {
            BasicText("Loading Beermat…", style = TextStyle(fontSize = 16.sp))
        }
        return
    }
    SheetView(
        s, revision, Commands.palettes, zoom, WebHost,
        onChange = ::changed,
        onSave = {
            flush()
            postToVsCode("save", "", 0)
        },
        onZoom = { step -> zoom = (zoom + 25 * step).coerceIn(50, 200) },
        statusBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xFFF4F4F4)).padding(horizontal = 12.dp, vertical = 3.dp)) {
                val small = TextStyle(fontSize = 12.sp, color = Color(0xFF555555))
                BasicText("Beermat ${Version.current}", style = small)
                Spacer(Modifier.weight(1f))
                // On a built-in function, at its reference entry, as F1 in the desktop app.
                BasicText(" Manual ", Modifier.clickable { postToVsCode("open", MANUAL + (s.helpPage() ?: ""), 0) }, style = small)
                BasicText(" Text ", Modifier.clickable { postToVsCode("text", "", 0) }, style = small)
                // Export to Word (docs/export.md#word): the extension asks where to save it.
                BasicText(" Word ", Modifier.clickable {
                    s.guard(Unit) {
                        val docx = Docx.write(WorksheetText.read(s.text()), WordPictures(null)::picture)
                        postToVsCode("word", Base64.encode(docx), 0)
                    }
                }, style = small)
                BasicText(" − ", Modifier.clickable { zoom = (zoom - 25).coerceAtLeast(50) }, style = small)
                BasicText(" Zoom $zoom% ", style = small)
                BasicText(" + ", Modifier.clickable { zoom = (zoom + 25).coerceAtMost(200) }, style = small)
            }
        },
    )
    resultFormat?.let { (region, statement) ->
        ResultFormatPanel(
            s.resultSettings(region, statement),
            onApply = { settings ->
                s.guard(Unit) { s.setResultSettings(region, statement, settings) }
                resultFormat = null
                changed()
            },
            onCancel = { resultFormat = null },
        )
    }
}
