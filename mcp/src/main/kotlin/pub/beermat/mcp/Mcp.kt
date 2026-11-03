package pub.beermat.mcp

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.units.Quantity
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.export.SheetExport
import pub.beermat.format.Table
import pub.beermat.format.TableOutcome
import pub.beermat.format.TableBlock
import pub.beermat.format.Annotation
import pub.beermat.format.Attributes
import pub.beermat.format.CalcBlock
import pub.beermat.format.Calculation
import pub.beermat.format.Check
import pub.beermat.format.Markdown
import pub.beermat.format.Part
import pub.beermat.format.PlotBlock
import pub.beermat.format.Worksheet
import pub.beermat.format.WorksheetText
import java.io.BufferedReader
import java.io.PrintStream
import java.nio.file.Path

/**
 * `beermat mcp`: a Model Context Protocol server on stdin/stdout (docs/mcp.md), so that Claude
 * Code and other MCP clients can read, evaluate and edit worksheets. Edits are written to the
 * file with fresh results; the app follows the file, so they show up at once.
 *
 * JSON-RPC 2.0, one message per line. JSON is read with the YAML parser (JSON is YAML).
 */
class Mcp(
    private val input: BufferedReader? = null,
    private val output: PrintStream? = null,
    private val documents: Documents = Documents.Files,
    /** Draws a plot as an image for the Markdown export (the app passes its plot painter); without it, a placeholder line. */
    private val plotImage: ((pub.beermat.format.PlotOutcome) -> String?)? = null,
) {

    /**
     * Where worksheets are read and written: files for `beermat mcp`; in the app, the open sheet
     * (live, with unsaved changes) when the path is left out or names it.
     */
    interface Documents {
        /** The worksheet's text; [path] null means the open sheet, where there is one. */
        fun read(path: String?): String

        fun write(path: String?, text: String)

        /** The folder the worksheet's data files are read from (`READPRN`): beside the file. */
        fun folder(path: String?): java.io.File? = path?.let { java.io.File(it).absoluteFile.parentFile }

        /** Whether tools need a path (no sheet is open). */
        val pathRequired: Boolean get() = true

        /** What editing does, for the instructions. */
        val editing: String get() = "Editing tools write the file with fresh results, which the Beermat app shows at once."

        object Files : Documents {
            override fun read(path: String?): String {
                val p = Path.of(path ?: throw ToolError("missing argument 'path'"))
                if (!java.nio.file.Files.exists(p)) throw ToolError("no such file: $p")
                return java.nio.file.Files.readString(p)
            }

            override fun write(path: String?, text: String) {
                java.nio.file.Files.writeString(Path.of(path ?: throw ToolError("missing argument 'path'")), text)
            }
        }
    }

    /** A problem a tool reports to the client. */
    class ToolError(message: String) : RuntimeException(message)

    /** Serves on stdin/stdout, one JSON-RPC message per line, until the input ends. */
    fun serve() {
        while (true) {
            val line = input!!.readLine() ?: return
            if (line.isBlank()) continue
            handleJson(line)?.let {
                output!!.println(it)
                output.flush()
            }
        }
    }

    /** The response to a JSON-RPC message given as JSON text, or null for a notification: for any transport. */
    fun handleJson(text: String): String? {
        val message = try {
            Load(LoadSettings.builder().build()).loadFromString(text) as? Map<*, *>
        } catch (e: Exception) {
            null
        } ?: return json(mapOf("jsonrpc" to "2.0", "id" to null, "error" to mapOf("code" to -32700, "message" to "parse error")))
        return handle(message)?.let(::json)
    }

    /** The response to [message], or null for a notification. */
    fun handle(message: Map<*, *>): Map<String, Any?>? {
        val id = message["id"]
        val method = message["method"] as? String
        val params = message["params"] as? Map<*, *> ?: emptyMap<String, Any?>()
        if (id == null) return null // a notification, such as notifications/initialized
        fun result(r: Any?) = mapOf("jsonrpc" to "2.0", "id" to id, "result" to r)
        return when (method) {
            "initialize" -> result(
                mapOf(
                    "protocolVersion" to (params["protocolVersion"] as? String ?: PROTOCOL),
                    "capabilities" to mapOf("tools" to emptyMap<String, Any?>()),
                    "serverInfo" to mapOf("name" to "beermat", "version" to pub.beermat.engine.Version.current),
                    "instructions" to INSTRUCTIONS + " " + documents.editing,
                ),
            )
            "ping" -> result(emptyMap<String, Any?>())
            "tools/list" -> result(mapOf("tools" to tools.map { it.describe() }))
            "tools/call" -> {
                val name = params["name"] as? String
                val args = params["arguments"] as? Map<*, *> ?: emptyMap<String, Any?>()
                val tool = tools.firstOrNull { it.name == name }
                val (text, error) = when {
                    tool == null -> "unknown tool '$name'" to true
                    else -> try {
                        tool.run(args) to false
                    } catch (e: ToolError) {
                        e.message!! to true
                    } catch (e: java.io.IOException) {
                        "cannot access the file: ${e.message}" to true
                    }
                }
                result(mapOf("content" to listOf(mapOf("type" to "text", "text" to text)), "isError" to error))
            }
            else -> mapOf("jsonrpc" to "2.0", "id" to id, "error" to mapOf("code" to -32601, "message" to "method not found: $method"))
        }
    }

    private class Tool(val name: String, val description: String, val params: List<Param>, val run: (Map<*, *>) -> String) {
        fun describe() = mapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to params.associate { it.name to mapOf("type" to it.type, "description" to it.description) },
                "required" to params.filter { it.required }.map { it.name },
            ),
        )
    }

    private class Param(val name: String, val type: String, val description: String, val required: Boolean = true)

    private val PATH = Param(
        "path",
        "string",
        "The worksheet file (.bbm or .bbm.md)" + if (documents.pathRequired) "" else "; left out: the sheet open in the app",
        required = documents.pathRequired,
    )

    private val REGION = Param("region", "string", "A region: its id (r1 or #r1), or its kind and position (calc 2, plot 1, text 3)")

    private val tools = listOf(
        Tool("guide", "Start here: how Beermat worksheets, their syntax and these tools work (a short guide). Call it once at the start of a session.", emptyList()) { _ ->
            Mcp::class.java.getResource("ai-guide.md")?.readText() ?: throw ToolError("the guide is missing from this build")
        },
        Tool("read_worksheet", "Read a worksheet with every statement's fresh result or error, numbered by region.", listOf(PATH)) { a ->
            describe(load(a), folder(a))
        },
        Tool(
            "evaluate",
            "Evaluate an expression in the worksheet's context: after all its statements, or after a given region. Nothing is written.",
            listOf(PATH, Param("expression", "string", "An expression in linear syntax, e.g. M_max/W or q*L^2/8 = ? kN*m"), REGION.optional()),
        ) { a -> evaluate(load(a), a.str("expression"), a["region"]?.toString(), folder(a)) },
        Tool("check", "Report syntax and evaluation errors, plot problems and stale cached results.", listOf(PATH)) { a ->
            Check.of(load(a), folder(a)).joinToString("\n") { p -> (p.line?.let { "line $it: " } ?: "") + p.message }.ifEmpty { "ok" }
        },
        Tool(
            "update_region",
            "Replace the content of a calc or plot region (the lines between its fences), or of a text region. Writes the file with fresh results.",
            listOf(PATH, REGION, Param("content", "string", "The new content, without the fence lines")),
        ) { a -> edit(a) { sheet -> replaceRegion(sheet, a.str("region"), a.str("content")) } },
        Tool(
            "insert_region",
            "Add a region after another one (or at the end). Writes the file with fresh results.",
            listOf(
                PATH,
                Param("kind", "string", "calc, plot, table or text"),
                Param("content", "string", "The statements, the plot YAML, the table (a Markdown pipe table), or the Markdown text"),
                Param("after", "string", "The region to insert after (id or kind and position); omitted: at the end", required = false),
            ),
        ) { a -> edit(a) { sheet -> insertRegion(sheet, a.str("kind"), a.str("content"), a["after"]?.toString()) } },
        Tool("delete_region", "Delete a region. Writes the file with fresh results.", listOf(PATH, REGION)) { a ->
            edit(a) { sheet -> sheet.copy(parts = sheet.parts.filterIndexed { k, _ -> k != find(sheet, a.str("region")) }) }
        },
        Tool(
            "export",
            "Return the worksheet as Markdown with display math, or as a LaTeX document.",
            listOf(PATH, Param("to", "string", "md or latex", required = false)),
        ) { a ->
            when (a["to"]?.toString() ?: "md") {
                "md", "markdown" -> SheetExport.markdown(load(a), plotImage, folder(a))
                "latex", "tex" -> SheetExport.latex(load(a))
                else -> throw ToolError("'to' must be md or latex")
            }
        },
    )

    private fun Param.optional() = Param(name, type, description, required = false)

    private fun Map<*, *>.str(key: String): String = this[key]?.toString() ?: throw ToolError("missing argument '$key'")

    private fun load(a: Map<*, *>): Worksheet = WorksheetText.read(documents.read(a["path"]?.toString()))

    private fun folder(a: Map<*, *>): java.io.File? = documents.folder(a["path"]?.toString())

    /** Applies [change], refreshes the results, writes the file, and describes the result. */
    private fun edit(a: Map<*, *>, change: (Worksheet) -> Worksheet): String {
        val sheet = change(load(a))
        val written = Calculation.of(sheet, folder(a)).written()
        documents.write(a["path"]?.toString(), WorksheetText.write(written))
        return "Written.\n\n" + describe(written)
    }


    private companion object {
        const val PROTOCOL = "2025-06-18"

        const val INSTRUCTIONS =
            "Beermat worksheets (.bbm) are Markdown with math in ```calc fences, one statement per line in Beermat's " +
                "linear syntax (x := 3 m defines, x = ? evaluates, x = ? mm shows in mm). Regions are calc fences, plot " +
                "fences and Markdown text; refer to them by id (#r1) or by position (calc 2). " +
                "Call the guide tool first for the syntax and how to work."

        /** The regions in order: kind, position, id and content, with fresh results. */
        fun describe(sheet: Worksheet, folder: java.io.File? = null): String {
            val calc = Calculation.of(sheet, folder)
            val annotations = java.util.IdentityHashMap<Any, Annotation?>().apply { calc.lines.forEach { put(it.line, it.annotation) } }
            val plots = java.util.IdentityHashMap<Any, List<String>>().apply { calc.plots.forEach { put(it.block, it.problems) } }
            val tables = java.util.IdentityHashMap<Any, TableOutcome>().apply { sheet.parts.filterIsInstance<TableBlock>().zip(calc.tables).forEach { (b, t) -> put(b, t) } }
            val counts = mutableMapOf<String, Int>()
            val out = mutableListOf<String>()
            for (part in sheet.parts) {
                val kind = kind(part)
                val n = counts.merge(kind, 1, Int::plus)!!
                val id = attributes(part)?.id
                out += "[$kind $n${id?.let { ", #$it" } ?: ""}]"
                when (part) {
                    is CalcBlock -> part.lines.forEach { line ->
                        out += when (val an = annotations[line]) {
                            is Annotation.Result -> "${line.source}   #= ${an.text}"
                            is Annotation.Error -> "${line.source}   #! ${an.message}"
                            null -> line.source
                        }
                    }
                    is PlotBlock -> {
                        out += part.body
                        plots[part]?.forEach { out += "#! $it" }
                    }
                    is TableBlock -> {
                        // With fresh values after the arrows.
                        val t = tables[part]
                        out += if (t != null) Table.write(t.table, t.shown) else part.body
                        t?.problems?.forEach { out += "#! $it" }
                    }
                    is Markdown -> out += part.lines.map(::elided)
                }
                out += ""
            }
            return out.joinToString("\n").trimEnd()
        }

        fun kind(part: Part) = when (part) {
            is CalcBlock -> "calc"
            is PlotBlock -> "plot"
            is TableBlock -> "table"
            is Markdown -> "text"
        }

        fun attributes(part: Part) = when (part) {
            is CalcBlock -> part.attributes
            is PlotBlock -> part.attributes
            is TableBlock -> part.attributes
            is Markdown -> null
        }

        /** The index in [Worksheet.parts] of a region given as `r1`, `#r1` or `calc 2`. */
        fun find(sheet: Worksheet, ref: String): Int {
            val r = ref.trim().removePrefix("#")
            val positional = Regex("""(calc|plot|text)\s+(\d+)""").matchEntire(r)
            val index = if (positional != null) {
                val (kind, n) = positional.destructured
                sheet.parts.withIndex().filter { kind(it.value) == kind }.getOrNull(n.toInt() - 1)?.index
            } else {
                sheet.parts.indexOfFirst { attributes(it)?.id == r }.takeIf { it >= 0 }
            }
            return index ?: throw ToolError("no region '$ref' (use read_worksheet to see the regions)")
        }

        /** A picture's data (docs/file-format.md#images) is no use to read: `[i1]: data:image/png;base64,… (12 KB, not shown)`. */
        fun elided(line: String): String = DATA.matchEntire(line)?.let { m ->
            "${m.groupValues[1]}${m.groupValues[2]},$ELIDED (${(m.groupValues[3].length * 3 / 4 + 1023) / 1024} KB, not shown)"
        } ?: line

        /** Lines sent back with their data [elided] get it back from [old], so editing the text never loses a picture. */
        fun restored(lines: List<String>, old: List<String>): List<String> {
            val data = old.mapNotNull { l -> DATA.matchEntire(l)?.let { it.groupValues[1] to l } }.toMap()
            return lines.map { l -> if (ELIDED in l) DATA_ELIDED.matchEntire(l)?.let { data[it.groupValues[1]] } ?: l else l }
        }

        private const val ELIDED = "…"
        private val DATA = Regex("""(\s{0,3}\[[^\]]+]:\s*)(data:[^,\s]*),(\S+)\s*""")
        private val DATA_ELIDED = Regex("""(\s{0,3}\[[^\]]+]:\s*)data:.*""")

        fun replaceRegion(sheet: Worksheet, ref: String, content: String): Worksheet {
            val k = find(sheet, ref)
            val lines = content.trimEnd('\n').split('\n')
            val part = when (val old = sheet.parts[k]) {
                is CalcBlock -> old.copy(lines = lines.mapIndexed { i, l -> WorksheetText.readLine(l, i + 1) })
                is PlotBlock -> old.copy(body = lines)
                is TableBlock -> old.copy(body = lines)
                is Markdown -> Markdown(restored(lines, old.lines))
            }
            return sheet.copy(parts = sheet.parts.toMutableList().also { it[k] = part })
        }

        fun insertRegion(sheet: Worksheet, kind: String, content: String, after: String?): Worksheet {
            val lines = content.trimEnd('\n').split('\n')
            val part: Part = when (kind) {
                "calc" -> CalcBlock("```calc", Attributes.NONE, lines.mapIndexed { i, l -> WorksheetText.readLine(l, i + 1) }, "```")
                "plot" -> PlotBlock("```plot", Attributes.NONE, lines, "```")
                "table" -> TableBlock("```table", Attributes.NONE, lines, "```")
                "text" -> Markdown(lines)
                else -> throw ToolError("kind must be calc, plot, table or text")
            }
            val at = after?.let { find(sheet, it) + 1 } ?: sheet.parts.size
            // Blank lines around the new region where its neighbours have none, so it stays separate.
            fun blankAround(p: Part?, last: Boolean) = (p as? Markdown)?.lines?.let { if (last) it.lastOrNull() else it.firstOrNull() }?.isBlank() == true
            val parts = sheet.parts.toMutableList()
            val inserted = listOfNotNull(
                if (blankAround(parts.getOrNull(at - 1), last = true)) null else Markdown(listOf("")),
                part,
                if (at == parts.size || blankAround(parts.getOrNull(at), last = false)) null else Markdown(listOf("")),
            )
            parts.addAll(at, inserted)
            return sheet.copy(parts = parts)
        }

        fun evaluate(sheet: Worksheet, expression: String, after: String?, folder: java.io.File? = null): String {
            val calc = Calculation.of(sheet, folder)
            val statement = try {
                Parser.parseStatement(expression.trim().let { if ("= ?" in it || ":=" in it || "-> ?" in it || "→ ?" in it) it else "$it = ?" })
            } catch (e: ParseException) {
                throw ToolError("syntax error: ${e.message}")
            }
            // The statements before the point asked for, then this one, in a fresh run.
            val upTo = after?.let { ref ->
                val k = find(sheet, ref)
                val before = sheet.parts.take(k + 1).filterIsInstance<CalcBlock>().flatMap { it.lines }.toSet()
                calc.lines.count { it.line in before }
            } ?: calc.lines.size
            val statements = calc.lines.take(upTo).mapNotNull { it.parsed } + statement
            val settings = calc.settings
            val (sheetScope, units) = Builtins.forSheet(settings.units)
            val builtins = sheetScope
                .with(Id("TOL"), Scalar(Quantity(settings.tolerance)))
                .with(Id("ORIGIN"), Scalar(Quantity(settings.origin.toDouble())))
            val outcome = Evaluator(builtins, units, symbolic = pub.beermat.symbolic.Symbolics, folder = folder).run(statements).last()
            return when (outcome) {
                is pub.beermat.engine.eval.Outcome.Evaluated -> ResultText.format(outcome.shown, calc.settings.format)
                is pub.beermat.engine.eval.Outcome.Defined -> outcome.shown?.let { ResultText.format(it, calc.settings.format) } ?: "defined"
                is pub.beermat.engine.eval.Outcome.Failed -> throw ToolError(outcome.error.message ?: "error")
                pub.beermat.engine.eval.Outcome.NotEvaluated -> "not evaluated"
                is pub.beermat.engine.eval.Outcome.Symbolic -> pub.beermat.format.Calculation.plainUnits(pub.beermat.engine.syntax.Printer.print(outcome.result))
            }
        }

        /** A JSON string literal. */
        fun jsonString(s: String): String = buildString {
            append('"')
            for (c in s) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

        /** JSON for maps, lists, strings, numbers, booleans and null. */
        fun json(v: Any?): String = when (v) {
            null -> "null"
            is String -> jsonString(v)
            is Boolean -> v.toString()
            is Int, is Long -> v.toString()
            is Number -> v.toDouble().let { if (it == Math.rint(it) && kotlin.math.abs(it) < 1e15) it.toLong().toString() else it.toString() }
            is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> jsonString(k.toString()) + ":" + json(x) }
            is List<*> -> v.joinToString(",", "[", "]") { json(it) }
            else -> jsonString(v.toString())
        }
    }
}
