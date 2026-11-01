package pub.beermat.app

import java.io.File

internal actual fun logError(e: Throwable) {
    runCatching {
        val dir = File(System.getProperty("user.home"), ".beermat").apply { mkdirs() }
        File(dir, "errors.log").appendText("${java.time.Instant.now()} ${pub.beermat.engine.Version.current}\n${e.stackTraceToString()}\n")
    }
    e.printStackTrace()
}

internal actual fun defaultFontBytes(): ByteArray? =
    pub.beermat.app.math.SkiaMath::class.java.getResourceAsStream("/fonts/STIXTwoMath-Regular.ttf")?.readBytes()

internal actual inline fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)
