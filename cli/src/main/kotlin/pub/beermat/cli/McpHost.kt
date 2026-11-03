package pub.beermat.cli

import pub.beermat.mcp.Mcp
import pub.beermat.symbolic.Json
import java.io.BufferedReader
import java.io.File
import java.io.Writer

/**
 * `beermat mcp --host`: the MCP tools for a host that holds the sheets itself, the VS Code extension
 * (docs/vscode.md#step-4-in-detail). One JSON object per line each way. The host sends
 * `{"mcp": message}` (a JSON-RPC message, as text) and gets `{"reply": response}` (null for a
 * notification). Meanwhile a tool reads or writes a sheet by asking the host: `{"read": path}`,
 * answered `{"text": …, "folder": …}`; `{"write": path, "text": …}`, answered `{"done": true}`;
 * either answered `{"error": message}` when it can't. A null path is the host's active sheet.
 */
class McpHost(private val input: BufferedReader, private val output: Writer) {
    private val folders = mutableMapOf<String?, File?>()

    private val mcp = Mcp(
        documents = object : Mcp.Documents {
            override fun read(path: String?): String {
                val answer = ask(mapOf("read" to path))
                folders[path] = (answer["folder"] as? String)?.let(::File)
                return answer["text"] as? String ?: throw Mcp.ToolError("the sheet couldn't be read")
            }

            override fun write(path: String?, text: String) {
                ask(mapOf("write" to path, "text" to text))
            }

            override fun folder(path: String?): File? = folders[path] ?: super.folder(path)

            override val pathRequired = false

            override val editing =
                "Editing tools change the sheet open in VS Code at once (leave out 'path' for the active Beermat page); " +
                    "the changes are unsaved until the user saves, and Ctrl+Z undoes them."
        },
    )

    fun serve() {
        while (true) {
            val line = input.readLine() ?: return
            if (line.isBlank()) continue
            val message = (Json.read(line) as? Map<*, *>)?.get("mcp") as? String ?: continue
            send(mapOf("reply" to mcp.handleJson(message)))
        }
    }

    /** Asks the host, and waits for its answer: the next line. */
    private fun ask(request: Map<String, Any?>): Map<*, *> {
        send(request)
        val answer = Json.read(input.readLine() ?: throw Mcp.ToolError("VS Code stopped answering")) as? Map<*, *>
            ?: throw Mcp.ToolError("VS Code's answer couldn't be read")
        (answer["error"] as? String)?.let { throw Mcp.ToolError(it) }
        return answer
    }

    private fun send(message: Map<String, Any?>) {
        output.write(Json.write(message))
        output.write("\n")
        output.flush()
    }
}
