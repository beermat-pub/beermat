package pub.beermat.app

import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.MathView
import pub.beermat.app.page.PagePainter
import pub.beermat.app.page.Session
import pub.beermat.format.Annotation
import pub.beermat.format.Regions
import pub.beermat.format.WorksheetText
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The manual's drawn examples (docs/manual.md#drawn-examples): every ```` ```beermat ```` fence in its
 * pages, evaluated and drawn by Beermat as an SVG, with the sheet as a `.bbm` for "Open this example",
 * in `manual/public/examples/<page>/<n>`. The fences on a page continue one another, as a tutorial
 * builds one sheet; `new` starts afresh. The test fails when an example gives an error (unless marked
 * `errors`), or when the files in `manual/` differ from what the code gives;
 * `-Pbeermat.writeReference=true` writes them.
 */
class ManualExamplesTest {

    private val docs = File("../manual/src/content/docs")
    private val out = File("../manual/public/examples")
    private val files = File("../manual/examples").absoluteFile

    private companion object {
        /** How much larger an example is shown than on the page. */
        const val SCALE = 1.5f
    }

    /** A fence: its content, and the words after `beermat` (`new`, `errors`, `angle=dimension`). */
    class Fence(val page: String, val number: Int, val content: String, val meta: List<String>)

    @Test
    fun `the manual's examples are drawn and up to date`() {
        val files = examples()
        if (System.getProperty("beermat.writeReference") == "true") {
            out.deleteRecursively()
            files.forEach { (path, bytes) -> File(out, path).apply { parentFile.mkdirs() }.writeBytes(bytes) }
        }
        val there = out.walkTopDown().filter { it.isFile }.map { it.relativeTo(out).path.replace('\\', '/') }.toSet()
        // The sheets must match; a picture must be there. Its exact bytes differ between systems (text
        // regions use the system's fonts on the desktop), so CI on Linux can't compare what Windows drew.
        val stale = files.filter { (path, bytes) ->
            !File(out, path).isFile || (path.endsWith(".bbm") && !File(out, path).readBytes().contentEquals(bytes))
        }.keys + (there - files.keys)
        assertEquals(emptySet(), stale, "examples to draw again: ./gradlew :app:test --tests '*ManualExamples*' -Pbeermat.writeReference=true")
    }

    /** Every example's files, by their path under `examples/`. */
    private fun examples(): Map<String, ByteArray> = buildMap {
        val problems = mutableListOf<String>()
        for (page in pages()) {
            val fences = fences(page)
            var context = mutableListOf<String>()
            var settings = linkedMapOf<String, String>()
            for (f in fences) {
                if ("new" in f.meta) {
                    context = mutableListOf()
                    settings = linkedMapOf()
                }
                f.meta.filter { '=' in it }.forEach { settings[it.substringBefore('=')] = it.substringAfter('=') }
                // Statements make a math region; `text`, a text region; a fence holding fences, any regions.
                val fragment = when {
                    "text" in f.meta -> "<!-- #t${f.number} -->\n${f.content}"
                    f.content.lines().any { it.trimStart().startsWith("```") } -> f.content
                    else -> "```calc\n${f.content}\n```"
                }
                val front = if (settings.isEmpty()) "" else settings.entries.joinToString("\n", "---\n", "\n---\n\n") { (k, v) -> "$k: $v" }
                val before = front + context.joinToString("\n\n")
                val text = front + (context + fragment).joinToString("\n\n") + "\n"
                val drawn = runCatching { draw(text, before, f) }.getOrElse { problems += "${f.page} #${f.number}: ${it.message}"; null }
                if (drawn != null) {
                    put("${f.page}/${f.number}.svg", drawn.first)
                    put("${f.page}/${f.number}.bbm", drawn.second.toByteArray())
                }
                context += fragment
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /** The pages with examples, by their slug (`start-beam` for `start/beam.md`). */
    private fun pages(): List<File> = docs.walkTopDown().filter { it.isFile && (it.extension == "md" || it.extension == "mdx") }.sortedBy { it.path }.toList()

    private fun slug(page: File) = page.relativeTo(docs).path.replace('\\', '/').substringBeforeLast('.').replace('/', '-')

    /** The ```` ```beermat ```` fences of [page], in order (three or more backticks, closed by as many). */
    private fun fences(page: File): List<Fence> {
        val lines = page.readText().replace("\r\n", "\n").lines()
        val found = mutableListOf<Fence>()
        var k = 0
        while (k < lines.size) {
            val m = Regex("""^(`{3,})beermat(\s.*)?$""").matchEntire(lines[k].trim())
            if (m == null) {
                k++
                continue
            }
            val ticks = m.groupValues[1]
            val end = (k + 1 until lines.size).first { lines[it].trim() == ticks }
            found += Fence(slug(page), found.size + 1, lines.subList(k + 1, end).joinToString("\n"), m.groupValues[2].trim().split(Regex("""\s+""")).filter { it.isNotEmpty() })
            k = end + 1
        }
        return found
    }

    /** The example drawn as an SVG, and the whole sheet up to it as the text of a `.bbm`. */
    private fun draw(text: String, before: String, f: Fence): Pair<ByteArray, String> {
        // The examples' data and Python files are in manual/examples/, as if the sheet were there; their Python runs.
        pub.beermat.format.PythonConsent.allowed = { true }
        val session = Session(text, MathLayout(SkiaMath, MATH_SIZE))
        session.base = files
        val first = Regions.of(WorksheetText.read(before.ifBlank { "\n" })).size
        val layout = session.layout
        val calc = layout.calculation
        if ("errors" !in f.meta) {
            // This fence's own statements: one above it marked `errors` may be wrong on purpose.
            val own = f.content.lines().map { it.trim() }.toSet()
            calc.lines.firstOrNull { it.annotation is Annotation.Error && it.statement.lines().first().trim() in own }
                ?.let { error("${it.statement}: ${(it.annotation as Annotation.Error).message}") }
            calc.plots.firstOrNull { it.problems.isNotEmpty() }?.let { error("plot: ${it.problems.first()}") }
            calc.settingProblems.firstOrNull()?.let { error(it) }
        }
        val views = layout.views.filter { it.index >= first && !it.hidden }
        if (views.isEmpty()) error("nothing to draw")
        // Cropped to the ink: a math region's statements, not the width of the page.
        fun width(v: pub.beermat.app.page.RegionView) = when (v) {
            is MathView -> v.statements.maxOf { s -> maxOf(s.box.width, s.error?.width ?: 0f) }
            is pub.beermat.app.page.TextView -> minOf(v.width, v.paragraph.longestLine)
            else -> v.width
        }
        val pad = 6f
        val left = views.minOf { it.x } - pad
        val top = views.minOf { it.y } - pad
        val w = views.maxOf { it.x + width(it) } - left + pad
        val h = views.maxOf { it.y + it.height } - top + pad
        val bytes = ByteArrayOutputStream()
        val stream = OutputWStream(bytes)
        val canvas = SVGCanvas.make(Rect.makeWH(w, h), stream, true, false)
        canvas.drawRect(Rect.makeWH(w, h), Paint().apply { color = 0xFFFFFFFF.toInt() })
        for (v in views) {
            canvas.save()
            canvas.translate(v.x - left, v.y - top)
            PagePainter.paintRegion(canvas, layout, v)
            canvas.restore()
        }
        canvas.close()
        stream.close()
        // Shown 1.5 times its size on the page (math at 18 px beside the manual's 16 px text): the
        // size is in the file, since the manual's build keeps a page's output while its text is the same.
        val drawn = bytes.toByteArray().decodeToString()
        val m = Regex("""<svg([^>]*) width="([\d.]+)" height="([\d.]+)"""").find(drawn) ?: error("an SVG without a size")
        fun shown(v: String) = "%.1f".format(java.util.Locale.ROOT, v.toFloat() * SCALE)
        val (rest, sw, sh) = m.destructured
        val svg = drawn.replaceRange(m.range, """<svg$rest width="${shown(sw)}" height="${shown(sh)}" viewBox="0 0 $sw $sh"""")
        // The sheet as the page writes it, without positions (they depend on the system's fonts): it flows when opened.
        return svg.toByteArray() to text
    }
}
