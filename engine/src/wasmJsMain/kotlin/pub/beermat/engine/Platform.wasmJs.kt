package pub.beermat.engine

/** In the browser there are no files on disk: only a path, so messages can name it. */
actual class HostFile(val path: String)

actual fun hostFile(folder: HostFile?, path: String): HostFile =
    HostFile(if (isAbsolutePath(path) || folder == null) path else folder.path.trimEnd('/') + "/" + path)

actual fun isAbsolutePath(path: String): Boolean = path.startsWith("/") || Regex("^[A-Za-z]:[\\/]").containsMatchIn(path)

// A file is there only when a host provides it (RemoteFiles, in VS Code); a file it is still reading is an error that says so.
actual fun isHostFile(file: HostFile): Boolean = RemoteFiles.bytes(file.path) != null

internal actual fun hostLines(file: HostFile): List<String> =
    RemoteFiles.bytes(file.path)?.decodeToString()?.lines() ?: throw UnsupportedOperationException("files need the desktop version")

actual fun hostBytes(file: HostFile): ByteArray? = RemoteFiles.bytes(file.path)

/**
 * Files a host reads for the page (docs/vscode.md#step-3-in-detail): VS Code's extension, for data
 * files and pictures beside the sheet. A page can't wait, so a file asked for the first time is
 * requested through [request] and reported as being read; when it comes ([answer]), the sheet is
 * worked out again ([answered]) and finds it. Without a [request] (the browser), there are no files.
 */
object RemoteFiles {
    /** Asks the host for the file at a path (the page sets this in VS Code). */
    var request: ((String) -> Unit)? = null

    /** Works the sheet out again once a file has come (the page sets this). */
    var answered: () -> Unit = {}

    private val known = mutableMapOf<String, ByteArray?>()
    private val asked = mutableSetOf<String>()

    /** Whether files are being read: the page doesn't send its text meanwhile. */
    val busy: Boolean get() = asked.isNotEmpty()

    /** The file's bytes; null when it isn't there or there are no files. Throws while it is being read. */
    fun bytes(path: String): ByteArray? {
        val ask = request ?: return null
        if (path in known) return known[path]
        if (asked.add(path)) ask(path)
        throw pub.beermat.engine.eval.EvalException("reading $path…")
    }

    /** The host's answer: the file's [bytes], or null when it isn't there (or may not be read). */
    fun answer(path: String, bytes: ByteArray?) {
        known[path] = bytes
        asked.remove(path)
        answered()
    }

    /** Forgets what was read, so changed files are read again (the sheet came again from the host). */
    fun forget() = known.clear()
}

internal actual fun stopRequested(): Boolean = false

internal actual fun epochSeconds(): Double = now() / 1000.0

private fun now(): Double = js("Date.now()")

// A JavaScript engine's stack is much smaller than the JVM's.
internal actual val maxCallDepth: Int = 300

// A stack overflow in WebAssembly is a trap the program can't catch.
internal actual fun isStackOverflow(t: Throwable): Boolean = false

internal actual abstract class Jump actual constructor() : RuntimeException()
