package pub.beermat.app

import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.Session
import pub.beermat.format.MathRegion
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The in-app MCP server, over HTTP as Claude Code uses it, on a live session. */
class McpHttpTest {

    private val file = File.createTempFile("beermat", ".bbm").apply {
        writeText("```calc {#r1 x=0 y=0}\nL := 6 m\n```\n")
        deleteOnExit()
    }
    private val session = Session(file.readText(), MathLayout(SkiaMath, 12f))
    private var changes = 0
    private val server = McpHttp(session, { file }) { changes++ }.apply { start(0) }

    @AfterTest
    fun stop() = server.stop()

    private fun post(body: String, origin: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply { if (origin != null) header("Origin", origin) }
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun call(tool: String, args: String) =
        post("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"$tool","arguments":$args}}""")

    @Test
    fun `initialize, and a notification gets 202`() {
        val init = post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")
        assertEquals(200, init.statusCode())
        assertTrue("\"name\":\"beermat\"" in init.body() && "open in the Beermat app" in init.body(), init.body())
        assertEquals(202, post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").statusCode())
    }

    @Test
    fun `tools work on the open sheet, unsaved, without a path`() {
        session.key(pub.beermat.app.editor.Key.Type('q')) // the crosshair is below r1: a new region, being typed
        assertTrue("L := 6 m" in call("read_worksheet", "{}").body())
        val insert = call("insert_region", """{"kind":"calc","content":"A := L^2 = ? m^2"}""")
        assertTrue("#= 36 m^2" in insert.body(), insert.body())
        assertTrue(session.dirty && changes == 1)
        assertTrue(session.regions.filterIsInstance<MathRegion>().any { r -> r.lines.any { it.source.startsWith("A := L^2") } })
        // Not saved: the file is as it was, and Ctrl+Z undoes Claude's edit.
        assertEquals("```calc {#r1 x=0 y=0}\nL := 6 m\n```\n", file.readText())
        session.undo()
        assertTrue(session.regions.filterIsInstance<MathRegion>().none { r -> r.lines.any { it.source.startsWith("A :=") } })
    }

    @Test
    fun `web pages and other methods are refused`() {
        assertEquals(403, post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", origin = "https://example.com").statusCode())
        assertEquals(200, post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", origin = "http://localhost:3000").statusCode())
        val get = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/mcp")).GET().build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(405, get.statusCode())
    }
}
