package pub.beermat.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import pub.beermat.app.Commands
import pub.beermat.app.ResultFormatPanel
import pub.beermat.app.MATH_SIZE
import pub.beermat.app.SheetView
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.PageFonts
import pub.beermat.app.page.Session
import pub.beermat.engine.Version
import pub.beermat.web.resources.Res
import kotlin.io.encoding.Base64

/** Where the desktop versions are, for what the browser can't do: the site's download page, which lists the latest release. */
private const val DOWNLOAD = "https://beermat.pub/download"

/** The manual (docs/manual.md). */
internal const val MANUAL = "https://beermat.pub/docs/"

/** The website, which the page's name and a button lead back to. */
private const val SITE = "https://beermat.pub"

/** The browser's storage keys: the sheet and its name, kept between visits. */
private const val SHEET = "beermat.sheet"
private const val NAME = "beermat.name"

/** The text fonts: the browser gives Skia none of its own. */
private val TEXT_FONTS = listOf("LiberationSans-Regular", "LiberationSans-Bold", "LiberationSans-Italic", "LiberationSans-BoldItalic", "LiberationMono-Regular")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // The same build runs in VS Code's web view (docs/vscode.md), as the extension's editor of .bbm files.
    ComposeViewport(document.body!!) { if (inVsCode()) VsCodeApp() else if (inVideo()) VideoApp() else WebApp() }
}

/**
 * The math font, and the text fonts: the browser gives Skia none of its own. They are also the
 * fallback for the toolbar's and dialogs' text ([resolver]), so symbols such as → and ⋮ come from
 * them, never from fonts downloaded from the internet (which VS Code's web view doesn't allow).
 */
internal suspend fun loadFonts(resolver: androidx.compose.ui.text.font.FontFamily.Resolver) {
    SkiaMath.load()
    val fonts = TEXT_FONTS.map { name -> name to Res.readBytes("files/fonts/$name.ttf") }
    for ((_, bytes) in fonts) PageFonts.register(bytes)
    val math = SkiaMath.fontBytes
    val family = androidx.compose.ui.text.font.FontFamily(
        fonts.map { (name, bytes) -> androidx.compose.ui.text.platform.Font(name, bytes) } +
            listOfNotNull(math?.let { androidx.compose.ui.text.platform.Font("STIXTwoMath", it) }),
    )
    resolver.preload(family)
    // The toolbar's symbols (⋮, ⟨⟩, ▦…) are the math font's.
    pub.beermat.app.toolbarSymbols = math?.let { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.platform.Font("STIXTwoMath", it)) }
}

@Composable
private fun WebApp() {
    var ready by remember { mutableStateOf(false) }
    val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    LaunchedEffect(Unit) {
        loadFonts(resolver)
        ready = true
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText("Loading Beermat…", style = TextStyle(fontSize = 18.sp)) }
        return
    }
    val session = remember { Session(stored(SHEET) ?: EXAMPLES.first { it.first == "beam.bbm" }.second, MathLayout(SkiaMath, MATH_SIZE)) }
    var name by remember { mutableStateOf(stored(NAME) ?: "beam.bbm") }
    var revision by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableIntStateOf(100) }
    var examples by remember { mutableStateOf(false) }
    var resultFormat by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    WebHost.resultFormat = { region, statement -> resultFormat = region to statement }
    fun changed() {
        revision++
        store(SHEET, session.text())
    }
    fun open(fileName: String, text: String) {
        session.load(text)
        name = fileName
        store(NAME, fileName)
        changed()
    }
    LaunchedEffect(Unit) { onPaste { text -> session.guard(Unit) { session.paste(text) }; changed() } }
    // "Open this example" in the manual (docs/manual.md#drawn-examples): /try?open=/docs/examples/…bbm.
    LaunchedEffect(Unit) {
        val path = openParameter() ?: return@LaunchedEffect
        if (!Regex("""^/docs/examples/[\w-]+/\d+\.bbm$""").matches(path)) return@LaunchedEffect
        fetchText(path) { text -> open(path.substringAfterLast('/').let { "example-$it" }, text) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF2B2118)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Beermat", Modifier.clickable { goTo(SITE) }, style = TextStyle(color = Color(0xFFF2B33D), fontSize = 16.sp))
            Spacer(Modifier.width(16.dp))
            BasicText(name, style = TextStyle(color = Color(0xFFDDDDDD), fontSize = 13.sp))
            Spacer(Modifier.width(24.dp))
            Button("New") { open("Untitled.bbm", "") }
            Button("Open…") { upload { fileName, text -> open(fileName, text) } }
            Button("Save") { download(name, session.save()); revision++ }
            // Export to Word, as on the desktop (docs/export.md#word).
            Button("Word") {
                session.guard(Unit) {
                    val docx = pub.beermat.export.Docx.write(pub.beermat.format.WorksheetText.read(session.text()), pub.beermat.app.WordPictures(null)::picture)
                    downloadBytes(name.substringBefore(".bbm") + ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Base64.encode(docx))
                }
            }
            Button("Examples ▾") { examples = !examples }
            Button("Picture…") {
                uploadPicture { fileName, mime, base64 ->
                    val bytes = Base64.decode(base64)
                    val big = bytes.size > 3_000_000
                    if (!big || confirm("$fileName is ${bytes.size / 100_000 / 10.0} MB; the sheet will grow by about a third more than that. Insert it anyway?")) {
                        if (!session.guard(false) { session.insertImage(bytes, mime, fileName.substringBeforeLast('.')) }) alert("$fileName isn't a picture Beermat can read (PNG, JPEG, GIF, WebP or SVG).")
                        changed()
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            // What only the desktop does, shown so a visitor knows it is there (docs/web.md#what-the-web-version-leaves-out).
            for (feature in listOf("Print", "PDF", "Import .xmcd", "AI assistant (MCP)")) Greyed(feature)
            Spacer(Modifier.weight(1f))
            // On a built-in function, at its reference entry, as F1 in the desktop app.
            Button("Manual") { openPage(MANUAL + (session.helpPage() ?: "")) }
            Button("beermat.pub") { goTo(SITE) }
            Button("Get the desktop app") { openPage(DOWNLOAD) }
        }
        if (examples) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF3A2E24)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                for ((fileName, text) in EXAMPLES) Button(fileName.removeSuffix(".bbm")) { open(fileName, text); examples = false }
            }
        }
        Box(Modifier.weight(1f)) {
            SheetView(
                session, revision, Commands.palettes, zoom, WebHost,
                onChange = ::changed,
                onSave = { download(name, session.save()); revision++ },
                onZoom = { step -> zoom = (zoom + 25 * step).coerceIn(50, 200) },
                statusBar = {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFF4F4F4)).padding(horizontal = 12.dp, vertical = 3.dp)) {
                        BasicText("Beermat ${Version.current} in the browser: your sheet stays in this browser until you save it.", style = TextStyle(fontSize = 12.sp, color = Color(0xFF555555)))
                        Spacer(Modifier.weight(1f))
                        // The browser keeps Ctrl+wheel and Ctrl+= for its own zoom: these buttons zoom the sheet.
                        val small = TextStyle(fontSize = 12.sp, color = Color(0xFF555555))
                        BasicText(" − ", Modifier.clickable { zoom = (zoom - 25).coerceAtLeast(50) }, style = small)
                        BasicText(" Zoom $zoom% ", style = small)
                        BasicText(" + ", Modifier.clickable { zoom = (zoom + 25).coerceAtMost(200) }, style = small)
                    }
                },
            )
        }
    }
    resultFormat?.let { (region, statement) ->
        ResultFormatPanel(
            session.resultSettings(region, statement),
            onApply = { settings ->
                session.guard(Unit) { session.setResultSettings(region, statement, settings) }
                resultFormat = null
                changed()
            },
            onCancel = { resultFormat = null },
        )
    }
}

@Composable
private fun Button(label: String, onClick: () -> Unit) {
    BasicText(
        label,
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        style = TextStyle(color = Color.White, fontSize = 13.sp),
    )
}

/** A desktop feature, greyed out: a click tells where it is. */
@Composable
private fun Greyed(label: String) {
    BasicText(
        label,
        Modifier.clickable { if (confirm("$label is available in the desktop version. Open the download page?")) openPage(DOWNLOAD) }.padding(horizontal = 6.dp, vertical = 2.dp),
        style = TextStyle(color = Color(0xFF8A7F74), fontSize = 13.sp),
    )
}
