package pub.beermat.app

import pub.beermat.app.math.Box
import pub.beermat.app.math.MathLayout
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Unparsed
import pub.beermat.export.Latex
import pub.beermat.export.MathMl
import pub.beermat.export.Result
import pub.beermat.export.Units
import pub.beermat.format.Annotation
import pub.beermat.format.CalcBlock
import pub.beermat.format.Calculation
import pub.beermat.format.Markdown
import pub.beermat.format.PlotBlock
import pub.beermat.format.PlotData
import pub.beermat.format.PlotOutcome
import pub.beermat.format.Worksheet
import java.util.IdentityHashMap

/** What the read-only view shows, top to bottom (milestone 4: one column, in file order). */
sealed interface SheetItem {
    /** A line of text; [level] is the heading level, 0 for body text. */
    data class Text(val text: String, val level: Int = 0) : SheetItem

    /**
     * A statement laid out as math, with its error message if it has one. [source] is its linear
     * form; [latex] and [mathMl] are what "Copy as" puts on the clipboard. [errorBox] is the error laid out.
     */
    data class Math(val box: Box, val error: String?, val source: String, val latex: String = "", val mathMl: String = "", val errorBox: Box? = null) : SheetItem

    /** A plot, [width] by [height] points, or why it can't be drawn. */
    data class Plot(val data: PlotData?, val problems: List<String>, val width: Float, val height: Float) : SheetItem

    /** Something not drawn, such as a code sample. */
    data class Pending(val what: String) : SheetItem
}

object SheetItems {

    fun of(sheet: Worksheet, layout: MathLayout): List<SheetItem> {
        val calc = Calculation.of(sheet)
        val units = Units.of(calc.lines.mapNotNull { it.parsed })
        val latex = Latex(units, calc.settings.multiplication)
        val mathMl = MathMl(units, calc.settings.multiplication)
        val sheetLayout = layout.forSheet(units, calc.settings.multiplication)
        val plots = IdentityHashMap<PlotBlock, PlotOutcome>().apply { calc.plots.forEach { put(it.block, it) } }
        val outcomes = IdentityHashMap<Any, pub.beermat.format.LineOutcome>().apply { calc.lines.forEach { put(it.line, it) } }
        val items = mutableListOf<SheetItem>()
        for (part in sheet.parts) {
            when (part) {
                is Markdown -> items += markdown(part.lines)
                is PlotBlock -> {
                    val p = plots[part] ?: continue
                    val w = part.attributes["w"]?.toFloatOrNull() ?: 420f
                    val h = part.attributes["h"]?.toFloatOrNull() ?: 260f
                    items += SheetItem.Plot(p.data, p.problems, w, h)
                }
                is pub.beermat.format.TableBlock -> items += SheetItem.Pending("table")
                is CalcBlock -> for (line in part.lines) {
                    val o = outcomes[line] ?: continue
                    val st = o.parsed ?: continue
                    val parts = o.shown?.let { ResultText.parts(it, o.format) }
                    val error = (o.annotation as? Annotation.Error)?.message
                    val box = sheetLayout.statement(st, parts?.number, parts?.unit)
                    val result = parts?.let(Result::of)
                    // An unparsed statement shows its message as the box itself.
                    val shownError = if (st is Unparsed) null else error
                    items += SheetItem.Math(box, shownError, o.statement, latex.statement(st, result), mathMl.math(st, result), shownError?.let { sheetLayout.message(it, o.errorNames) })
                }
            }
        }
        return items
    }

    /** Headings and paragraphs; HTML comments (region markers) are skipped, other fences noted. */
    internal fun markdown(lines: List<String>): List<SheetItem> {
        val items = mutableListOf<SheetItem>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) items += SheetItem.Text(paragraph.toString().trim())
            paragraph.clear()
        }
        var fence: String? = null
        var comment = false
        for (line in lines) {
            val t = line.trim()
            when {
                fence != null -> if (t.startsWith(fence)) fence = null
                comment -> if ("-->" in t) comment = false
                t.startsWith("```") || t.startsWith("~~~") -> {
                    flush()
                    fence = t.takeWhile { it == t[0] }
                    items += SheetItem.Pending(t.drop(fence.length).substringBefore('{').trim().ifEmpty { "code" })
                }
                t.startsWith("<!--") -> {
                    flush()
                    comment = "-->" !in t
                }
                t.startsWith("#") -> {
                    flush()
                    val level = t.takeWhile { it == '#' }.length
                    items += SheetItem.Text(t.drop(level).trim(), level)
                }
                t.isEmpty() -> flush()
                else -> paragraph.append(t.replace(Regex("""\*\*(.+?)\*\*|\*(.+?)\*"""), "$1$2")).append(' ')
            }
        }
        flush()
        return items
    }
}
