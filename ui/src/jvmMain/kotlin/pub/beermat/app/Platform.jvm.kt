package pub.beermat.app

import java.io.File

internal actual fun logError(e: Throwable) {
    runCatching {
        val dir = File(System.getProperty("user.home"), ".beermat").apply { mkdirs() }
        File(dir, "errors.log").appendText("${java.time.Instant.now()} ${pub.beermat.engine.Version.current}\n${e.stackTraceToString()}\n")
    }
    e.printStackTrace()
}

// On the desktop the resources are files in the jar: reading them now costs nothing.
internal actual fun defaultFontBytes(): ByteArray? =
    kotlinx.coroutines.runBlocking { pub.beermat.app.resources.Res.readBytes(pub.beermat.app.math.SkiaMath.FONT) }

internal actual inline fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
internal actual fun WithTip(tip: @androidx.compose.runtime.Composable () -> Unit, content: @androidx.compose.runtime.Composable () -> Unit) =
    androidx.compose.foundation.TooltipArea(tooltip = tip, delayMillis = 350) { content() }

internal actual val typedOnKeyDown: Boolean = false

actual val macOs: Boolean = System.getProperty("os.name").orEmpty().lowercase().startsWith("mac")
