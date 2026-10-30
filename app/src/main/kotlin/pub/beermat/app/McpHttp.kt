package pub.beermat.app

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import pub.beermat.app.page.Session
import pub.beermat.mcp.Mcp
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.swing.SwingUtilities

/**
 * The MCP server inside the app (docs/mcp.md): the same tools as `beermat mcp`, over MCP's
 * streamable HTTP transport on 127.0.0.1, working on the sheet open in the window: Claude reads
 * it with unsaved changes, and its edits show at once, unsaved and undoable.
 *
 * Only local connections are accepted, and a request from a web page (an `Origin` header that
 * isn't localhost) is refused, so a website can't drive it.
 */
class McpHttp(private val session: Session, private val file: () -> File?, private val onChange: () -> Unit) {

    /** The port it listens on, once started; null if it couldn't start (the port was taken). */
    var port: Int? = null
        private set

    private var server: HttpServer? = null

    private val mcp = Mcp(
        documents = object : Mcp.Documents {
            override fun read(path: String?): String = if (isOpen(path)) onUi { session.text() } else Mcp.Documents.Files.read(path)

            override fun write(path: String?, text: String) {
                if (isOpen(path)) {
                    onUi {
                        session.replace(text)
                        onChange()
                    }
                } else {
                    Mcp.Documents.Files.write(path, text)
                }
            }

            override fun folder(path: String?): java.io.File? = if (isOpen(path)) session.base else super.folder(path)

            override val pathRequired = false

            override val editing =
                "Editing tools change the sheet open in the Beermat app at once (leave out 'path' for it); " +
                    "the changes are unsaved until the user saves, and Ctrl+Z undoes them."
        },
        plotImage = pub.beermat.app.plot.PlotImages::dataUri,
    )

    fun start(preferred: Int = System.getenv("BEERMAT_MCP_PORT")?.toIntOrNull() ?: DEFAULT_PORT) {
        server = try {
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), preferred), 0)
        } catch (_: java.io.IOException) {
            return
        }.apply {
            createContext("/mcp", ::exchange)
            start()
        }
        // 0 asks for any free port (tests).
        port = server!!.address.port
    }

    fun stop() {
        server?.stop(0)
        server = null
        port = null
    }

    /** What to run once in a terminal to connect Claude Code. */
    val command: String? get() = port?.let { "claude mcp add --transport http beermat http://127.0.0.1:$it/mcp" }

    private fun exchange(ex: HttpExchange) {
        ex.use {
            val origin = ex.requestHeaders.getFirst("Origin")
            if (origin != null && !LOCAL_ORIGIN.matches(origin)) return reply(ex, 403, null)
            if (ex.requestMethod != "POST") {
                ex.responseHeaders.add("Allow", "POST")
                return reply(ex, 405, null)
            }
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val response = mcp.handleJson(body)
            // A notification gets no response: 202 Accepted.
            if (response == null) reply(ex, 202, null) else reply(ex, 200, response)
        }
    }

    private fun reply(ex: HttpExchange, status: Int, json: String?) {
        if (json == null) {
            ex.sendResponseHeaders(status, -1)
            return
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.write(bytes)
    }

    private fun isOpen(path: String?) = path == null || File(path).canonicalFile == file()?.canonicalFile

    /** Runs [block] on the window's thread, where the session lives. */
    private fun <T> onUi(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    companion object {
        const val DEFAULT_PORT = 7341
        private val LOCAL_ORIGIN = Regex("""https?://(localhost|127\.0\.0\.1|\[::1])(:\d+)?""")
    }
}
