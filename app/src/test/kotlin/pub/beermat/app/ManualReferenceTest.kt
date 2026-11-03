package pub.beermat.app

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.BuiltinUnits
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.FunctionDoc
import pub.beermat.engine.eval.FunctionDocs
import pub.beermat.engine.eval.FunctionGroup
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import pub.beermat.format.Settings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The manual's reference pages, written from the code (docs/manual.md#what-generates-itself):
 * the functions from `FunctionDocs`, the units from the unit table, the settings from
 * `Settings.reference`, the keys from the toolbar's palettes. The test fails when a page in
 * `manual/` differs from what the code gives; `-Pbeermat.writeReference=true` writes them.
 */
class ManualReferenceTest {

    private val folder = File("../manual/src/content/docs/reference")

    private val note = "<!-- Written from the code by ManualReferenceTest (app module): change the code, not this page. -->"

    @Test
    fun `the reference pages are up to date`() {
        val pages = pages()
        if (System.getProperty("beermat.writeReference") == "true") {
            File(folder, "functions").deleteRecursively()
            pages.forEach { (path, text) -> File(folder, path).apply { parentFile.mkdirs() }.writeText(text) }
        }
        val stale = pages.filter { (path, text) -> File(folder, path).takeIf { it.exists() }?.readText()?.replace("\r\n", "\n") != text }.keys
        assertEquals(emptySet(), stale, "pages to write again: ./gradlew :app:test --tests '*ManualReference*' -Pbeermat.writeReference=true")
    }

    private fun pages(): Map<String, String> = buildMap {
        put("functions/index.md", functionIndex())
        FunctionGroup.entries.forEach { put("functions/${it.slug}.md", functionPage(it)) }
        put("units.md", units())
        put("settings.md", settings())
        put("keys.md", keys())
    }

    private fun page(title: String, description: String, body: String) =
        "---\ntitle: $title\ndescription: $description\n---\n\n$note\n\n$body".trimEnd() + "\n"

    private fun functionIndex(): String {
        val groups = FunctionGroup.entries.joinToString("\n") { "- [${it.title}](${it.slug}/)" }
        val rows = FunctionDocs.all.sortedWith(compareBy({ it.name.lowercase() }, { it.name })).joinToString("\n") { d ->
            "| [`${d.name}`](${d.group.slug}/#fn-${d.name}) | ${bars(d.summary)} |"
        }
        return page(
            "Functions", "Every built-in function, from A to Z.",
            "The built-in functions, by group:\n\n$groups\n\nAnd all of them from A to Z:\n\n| Function | What it does |\n|---|---|\n$rows",
        )
    }

    private fun functionPage(group: FunctionGroup): String {
        val entries = FunctionDocs.all.filter { it.group == group }.joinToString("\n\n") { d ->
            buildString {
                append("<a id=\"fn-${d.name}\"></a>\n\n## `${d.signature}`\n\n${d.summary}")
                if (d.example.isNotEmpty()) append("\n\n```text\n${example(d).joinToString("\n")}\n```")
            }
        }
        return page(group.title, "The built-in functions for ${group.title.lowercase()}.", entries)
    }

    /** The example's lines, the result written in after its last `=`. */
    private fun example(d: FunctionDoc): List<String> {
        val outcomes = Evaluator().run(d.example.map(Parser::parseStatement))
        return d.example.mapIndexed { i, line ->
            when (val o = outcomes[i]) {
                is Outcome.Evaluated -> line.substringBeforeLast("= ?") + "= " + ResultText.format(o.shown)
                is Outcome.Failed -> fail("${d.name}: '$line': ${o.error.message}")
                else -> line
            }
        }
    }

    private fun units(): String {
        val sections = BuiltinUnits.sections.joinToString("\n\n") { s ->
            val rows = s.definitions.joinToString("\n") { line ->
                val (name, value) = line.split(" := ", limit = 2)
                "| ${code(name)} | ${code(value)} |"
            }
            "## ${s.title}\n\n" + (if (s.text.isNotEmpty()) "${s.text}\n\n" else "") + "| Unit | Defined as |\n|---|---|\n$rows"
        }
        return page(
            "Units", "Every built-in unit, by quantity.",
            "The seven SI base units are `m`, `kg`, `s`, `A`, `K`, `mol` and `cd`. Every other built-in unit is defined from them, below. " +
                "A unit is a value like any other, so `kip := 1000 lbf` in a sheet adds one, and a definition of the same name as a unit replaces it in that sheet.\n\n$sections",
        )
    }

    private fun settings(): String {
        val rows = Settings.reference.joinToString("\n") { s -> "| `${s.key}` | ${s.default} | ${bars(s.meaning)} |" }
        return page(
            "Worksheet settings", "The settings at the top of a worksheet file.",
            "A worksheet's settings are at the top of its file, between `---` lines, as `key: value` (YAML). " +
                "Every one is optional, and most are set from the Format menu. A `result.` setting is written below `result:`:\n\n" +
                "```yaml\n---\ntitle: Beam B1\nresult:\n  significant: 3\nheader: {right: \"Page {page} of {pages}\"}\n---\n```\n\n" +
                "| Setting | Default | What it does |\n|---|---|---|\n$rows",
        )
    }

    private fun keys(): String {
        val general = listOf(
            "Arrows" to "In math, move the cursor. On the page, move the red crosshair, or the selected regions.",
            "Space" to "In math, select more of the expression, a step at a time.",
            "Insert" to "In math, move the cursor to the other side of the selection.",
            "Tab, Shift+Tab" to "The next or previous placeholder (■), then the next or previous math region.",
            "Backspace, Delete" to "Delete before or after the cursor. On the page, take out the blank line above or below the crosshair.",
            "Enter" to "Leave the math region. On the page, move the crosshair down a line, moving the regions below with it.",
            "Escape" to "Leave the region.",
            "Ctrl+Z, Ctrl+Y" to "Undo and redo.",
            "Ctrl+C, Ctrl+V" to "Copy and paste; a picture pasted from another program becomes a picture region.",
            "Ctrl+Shift+S" to "Separate regions: move each region that overlaps one above it down below it.",
            "Ctrl+N, Ctrl+O, Ctrl+S" to "New, open and save a worksheet.",
            "Ctrl+P" to "Print.",
            "F9" to "Calculate the whole sheet again.",
            "F1" to "Open this manual; on a built-in function, at its page.",
        ).joinToString("\n") { (k, what) -> "| $k | $what |" }
        val palettes = Commands.palettes.joinToString("\n\n") { p ->
            "## ${p.name}\n\n| Button | What it does | Keys |\n|---|---|---|\n" +
                p.commands.joinToString("\n") { c -> "| ${plain(c.label)} | ${plain(c.name)} | ${plain(c.keys)} |" }
        }
        return page(
            "Keys and toolbar", "Every key and toolbar button of the editor.",
            "Each toolbar button does exactly what its keys do, and its tooltip shows them.\n\n## Moving and editing\n\n| Keys | What they do |\n|---|---|\n$general\n\n$palettes",
        )
    }

    /** Markdown for a table cell: a `|` escaped. */
    private fun bars(s: String) = s.replace("|", "\\|")

    /** Plain text for a table cell: every character Markdown would read as formatting, escaped. */
    private fun plain(s: String) = s.fold(StringBuilder()) { b, c -> if (c in "\\`*_[]<>#|") b.append('\\'); b.append(c) }.toString()

    /** Code: in double backticks when it holds one (`` `%` ``). */
    private fun code(s: String) = if ('`' in s) "`` $s ``" else "`$s`"
}
