package pub.beermat.mcp

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpTest {

    private val dir: Path = Files.createTempDirectory("beermat-mcp")

    @AfterTest
    fun cleanUp() = dir.toFile().deleteRecursively().let {}

    private val sheet = dir.resolve("beam.bbm").also {
        it.writeText("# Beam\n\n```calc {#r1}\nL := 6 m\nq := 12 kN/m\n```\n\n```calc\nM := q*L^2/8 = ? kN*m\n```\n")
    }

    private val mcp = Mcp()

    /** Calls a tool; returns its text and whether it is an error. */
    private fun call(tool: String, vararg args: Pair<String, String>): Pair<String, Boolean> {
        val response = mcp.handle(
            mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "tools/call", "params" to mapOf("name" to tool, "arguments" to mapOf("path" to sheet.toString(), *args))),
        )!!
        val result = response["result"] as Map<*, *>
        val text = ((result["content"] as List<*>).single() as Map<*, *>)["text"] as String
        return text to (result["isError"] as Boolean)
    }

    @Test
    fun `initialize and list tools`() {
        val init = mcp.handle(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "initialize", "params" to mapOf("protocolVersion" to "2025-06-18")))!!
        assertEquals("beermat", ((init["result"] as Map<*, *>)["serverInfo"] as Map<*, *>)["name"])
        assertNull(mcp.handle(mapOf("jsonrpc" to "2.0", "method" to "notifications/initialized")))
        val tools = ((mcp.handle(mapOf("jsonrpc" to "2.0", "id" to 2, "method" to "tools/list"))!!["result"] as Map<*, *>)["tools"] as List<*>)
            .map { (it as Map<*, *>)["name"] }
        assertEquals(listOf("guide", "read_worksheet", "evaluate", "check", "update_region", "insert_region", "delete_region", "export"), tools)
    }

    @Test
    fun `the guide`() {
        val (text, error) = call("guide")
        assertTrue(!error && text.startsWith("# Beermat for AI assistants") && "nthroot" in text, text.take(200))
    }

    @Test
    fun `read and evaluate`() {
        val (text, error) = call("read_worksheet")
        assertTrue(!error && "[calc 1, #r1]" in text && "M := q*L^2/8 = ? kN*m   #= 54 kN*m" in text, text)
        assertEquals("72 kN" to false, call("evaluate", "expression" to "q*L"))
        assertEquals("3*x^2" to false, call("evaluate", "expression" to "diff(x^3, x) -> ?"))
        assertEquals("6000 mm" to false, call("evaluate", "expression" to "L = ? mm", "region" to "r1"))
        assertEquals("M is not defined" to true, call("evaluate", "expression" to "M", "region" to "#r1"))
        assertEquals("no region 'calc 9' (use read_worksheet to see the regions)" to true, call("evaluate", "expression" to "1", "region" to "calc 9"))
    }

    @Test
    fun `edits write the file with fresh results`() {
        call("update_region", "region" to "r1", "content" to "L := 8 m\nq := 12 kN/m")
        assertTrue("M := q*L^2/8 = ? kN*m   #= 96 kN*m" in sheet.readText(), sheet.readText())
        call("insert_region", "kind" to "calc", "content" to "V := q*L/2 = ? kN", "after" to "calc 2")
        assertTrue(sheet.readText().endsWith("```\n\n```calc\nV := q*L/2 = ? kN   #= 48 kN\n```\n"), sheet.readText())
        call("delete_region", "region" to "calc 3")
        assertTrue("V :=" !in sheet.readText())
        assertEquals("ok" to false, call("check"))
    }

    @Test
    fun `a picture's data is not shown, and survives an edit of its text`() {
        val data = "A".repeat(4000)
        sheet.writeText("<!-- #i1 x=0 y=0 -->\n![Sketch][i1]\n\nSome text.\n\n[i1]: data:image/png;base64,$data\n")
        val (text, _) = call("read_worksheet")
        assertTrue("[i1]: data:image/png;base64,… (3 KB, not shown)" in text && data !in text, text)
        // Claude sends the region back as it read it, with one word changed.
        val edited = text.substringAfter("[text 1]\n").replace("Some text.", "Other text.")
        call("update_region", "region" to "text 1", "content" to edited)
        assertTrue("Other text." in sheet.readText() && "[i1]: data:image/png;base64,$data" in sheet.readText(), sheet.readText())
    }
}
