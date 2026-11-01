package pub.beermat.engine

/** In the browser there are no files on disk: only a path, so messages can name it. */
actual class HostFile(val path: String)

actual fun hostFile(folder: HostFile?, path: String): HostFile =
    HostFile(if (isAbsolutePath(path) || folder == null) path else folder.path.trimEnd('/') + "/" + path)

actual fun isAbsolutePath(path: String): Boolean = path.startsWith("/") || Regex("^[A-Za-z]:[\\/]").containsMatchIn(path)

actual fun isHostFile(file: HostFile): Boolean = false

internal actual fun hostLines(file: HostFile): List<String> = throw UnsupportedOperationException("files need the desktop version")

actual fun hostBytes(file: HostFile): ByteArray? = null

internal actual fun stopRequested(): Boolean = false

internal actual fun epochSeconds(): Double = now() / 1000.0

private fun now(): Double = js("Date.now()")

// A JavaScript engine's stack is much smaller than the JVM's.
internal actual val maxCallDepth: Int = 300

// A stack overflow in WebAssembly is a trap the program can't catch.
internal actual fun isStackOverflow(t: Throwable): Boolean = false

internal actual abstract class Jump actual constructor() : RuntimeException()
