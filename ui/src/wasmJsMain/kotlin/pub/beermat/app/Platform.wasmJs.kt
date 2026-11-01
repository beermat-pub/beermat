package pub.beermat.app

internal actual fun logError(e: Throwable) = e.printStackTrace()

// The browser shell fetches the font and sets SkiaMath.fontBytes before the first page is drawn.
internal actual fun defaultFontBytes(): ByteArray? = null

internal actual inline fun <T> locked(lock: Any, block: () -> T): T = block()
