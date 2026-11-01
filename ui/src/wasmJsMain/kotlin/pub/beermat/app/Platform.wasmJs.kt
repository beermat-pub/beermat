package pub.beermat.app

internal actual fun logError(e: Throwable) = e.printStackTrace()

// The browser shell fetches the font and sets SkiaMath.fontBytes before the first page is drawn.
internal actual fun defaultFontBytes(): ByteArray? = null

internal actual inline fun <T> locked(lock: Any, block: () -> T): T = block()

// Compose has no tooltip area in the browser yet; the web shell's own comes in step 3 (docs/web.md).
@androidx.compose.runtime.Composable
internal actual fun WithTip(tip: @androidx.compose.runtime.Composable () -> Unit, content: @androidx.compose.runtime.Composable () -> Unit) = content()

internal actual val typedOnKeyDown: Boolean = true
