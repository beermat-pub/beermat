package pub.beermat.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import pub.beermat.app.page.Session
import pub.beermat.engine.Version
import java.awt.Desktop
import java.net.URI

/** The brand in the app: the logo, its colours, and the links (branding/README.md). */
object Brand {
    val AMBER = Color(0xFFC27D18)
    val AMBER_TEXT = Color(0xFF8F5810)
    val CREAM = Color(0xFFF6EFE3)
    val INK = Color(0xFF2B2118)

    const val SITE = "https://beermat.pub"

    /** One link for donations, which lists GitHub Sponsors and Ko-fi: the providers can change without a new app. */
    const val BEER = "https://beermat.pub/beer"
    /** The manual (docs/manual.md): Help → Manual and F1. */
    const val MANUAL = "https://beermat.pub/docs/"
    const val SOURCE = "https://github.com/beermat-pub/beermat"
    const val SHORTCUTS = "https://github.com/beermat-pub/beermat/blob/main/docs/editor.md"
    const val TERMS = "https://beermat.pub/terms"

    /** The no-warranty notice (the website's terms page), also on the installers' licence page and the website. */
    const val NOTICE = "Beermat is free software under the MIT licence, provided as is, without warranty of any kind. " +
        "It is a calculation aid: you are responsible for checking its results and for any decisions or designs based on them. " +
        "The authors accept no liability for any loss or damage arising from its use."

    private fun logo(size: Int) = Icons.png(Brand::class.java.getResourceAsStream("/brand/logo.svg")!!.readBytes(), size)

    /** The window's icon (taskbar, title bar, Alt+Tab). */
    fun icon(): Painter = BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(logo(256)).toComposeImageBitmap())

    fun open(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
}

/** Help → About Beermat. */
@Composable
fun About(onClose: () -> Unit) {
    DialogWindow(onCloseRequest = onClose, title = "About Beermat", state = rememberDialogState(size = DpSize(380.dp, 500.dp)), resizable = false, icon = Brand.icon()) {
        Column(Modifier.fillMaxSize().background(Brand.CREAM).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(Brand.icon(), contentDescription = null, Modifier.size(84.dp))
            Spacer(Modifier.height(10.dp))
            BasicText("beermat", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Brand.AMBER_TEXT))
            BasicText("Back-of-a-beer-mat calculations, done properly.", style = TextStyle(fontSize = 12.sp, color = Brand.INK))
            Spacer(Modifier.height(14.dp))
            BasicText("Version ${Version.current}", style = TextStyle(fontSize = 12.sp, color = Brand.INK))
            BasicText("Free and open source, MIT licence", style = TextStyle(fontSize = 12.sp, color = Brand.INK))
            Spacer(Modifier.height(14.dp))
            Link("beermat.pub", Brand.SITE)
            Link("Source code", Brand.SOURCE)
            Spacer(Modifier.height(14.dp))
            BasicText("Beermat is made in spare time. If it saves you some,", style = TextStyle(fontSize = 12.sp, color = Brand.INK))
            Link("buy us a beer 🍺", Brand.BEER)
            Spacer(Modifier.height(14.dp))
            BasicText(Brand.NOTICE, style = TextStyle(fontSize = 10.sp, color = Color(0xFF6B5A4A), textAlign = TextAlign.Center))
            Link("Terms and privacy", Brand.TERMS)
        }
    }
}

@Composable
private fun Link(text: String, url: String) {
    BasicText(text, Modifier.clickable { Brand.open(url) }.padding(2.dp), style = TextStyle(fontSize = 13.sp, color = Brand.AMBER_TEXT))
}

/**
 * The status bar: whether the sheet is saved, its pages, the MCP server; and, quietly at the
 * right, a [notice] (a new version) and the beer link. Always there, never in the way
 * (no nag screens).
 */
@Composable
fun StatusBar(session: Session, mcpPort: Int?, revision: Int, notice: Notice?, zoom: Int) {
    // [revision] changes with every edit, so the bar is redrawn (the session itself isn't observable).
    revision.let { }
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF4F4F4)).padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = TextStyle(fontSize = 11.sp, color = Color(0xFF666666))
        BasicText(if (session.dirty) "Unsaved changes" else "Saved", style = style)
        BasicText("${session.layout.pages} page${if (session.layout.pages == 1) "" else "s"}", style = style)
        BasicText("Zoom $zoom%", style = style)
        if (session.specialText) BasicText("Special text: every key goes into the name; Ctrl+Shift+K to end", style = TextStyle(fontSize = 11.sp, color = Brand.AMBER_TEXT, fontWeight = FontWeight.Bold))
        BasicText(if (mcpPort != null) "AI/MCP: on, port $mcpPort" else "AI/MCP: off", style = style)
        session.lastError?.let { BasicText("$it; details in ~/.beermat/errors.log", style = TextStyle(fontSize = 11.sp, color = Color(0xFFD32F2F))) }
        Box(Modifier.weight(1f))
        notice?.let { n ->
            val link = n.url?.let { url -> Modifier.clickable { Brand.open(url) } } ?: Modifier
            BasicText(n.text, link, style = TextStyle(fontSize = 11.sp, color = if (n.url != null) Brand.AMBER_TEXT else Color(0xFF666666), fontWeight = if (n.url != null) FontWeight.Bold else null))
        }
        BasicText(
            "🍺 Enjoying Beermat? Buy us a beer",
            Modifier.clickable { Brand.open(Brand.BEER) },
            style = TextStyle(fontSize = 11.sp, color = Brand.AMBER_TEXT),
        )
    }
}

/** A short message for the status bar, a link when it has a [url]. */
data class Notice(val text: String, val url: String?)
