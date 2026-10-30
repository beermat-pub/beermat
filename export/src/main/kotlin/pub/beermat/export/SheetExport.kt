package pub.beermat.export

import pub.beermat.engine.display.ResultText
import pub.beermat.format.Column
import pub.beermat.format.Table
import pub.beermat.format.TableOutcome
import pub.beermat.format.TableBlock
import pub.beermat.format.Annotation
import pub.beermat.format.CalcBlock
import pub.beermat.format.Calculation
import pub.beermat.format.LineOutcome
import pub.beermat.format.Markdown
import pub.beermat.format.PlotBlock
import pub.beermat.format.PlotOutcome
import pub.beermat.format.Worksheet
import java.util.IdentityHashMap

/**
 * A whole worksheet as a document (docs/export.md): Markdown with display math, or a standalone
 * LaTeX document. Text stays as written; each statement becomes one equation with its result.
 */
object SheetExport {

    /** One statement ready to write: its LaTeX, or its error. */
    private class Equation(val latex: String, val error: String?)

    /**
     * The sheet as Markdown. [plotImage] draws a plot as an image (a `data:` URI, an SVG in the app),
     * embedded so the export stays one file; without it a plot is a placeholder line. [folder] is
     * where the sheet's data files are read from.
     */
    fun markdown(sheet: Worksheet, plotImage: ((PlotOutcome) -> String?)? = null, folder: java.io.File? = null): String {
        val out = mutableListOf<String>()
        walk(
            sheet,
            text = { lines -> out += markdownText(lines) },
            equation = { eq ->
                out += listOf("$$", eq.latex, "$$")
                eq.error?.let { out += "*Error: ${it}*" }
                out += ""
            },
            // A table is Markdown already: written with its values.
            table = { t -> out += Table.write(t.table, t.shown) + "" },
            plot = { p -> out += (plotImage?.invoke(p)?.let { listOf("![Plot]($it)", "") } ?: listOf("*(A plot: not exported yet.)*", "")) },
            folder = folder,
        )
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
    }

    fun latex(sheet: Worksheet): String {
        val out = mutableListOf(
            "\\documentclass{article}",
            "\\usepackage{amsmath,amssymb}",
            "\\begin{document}",
            "",
        )
        walk(
            sheet,
            text = { lines -> out += latexText(lines) },
            equation = { eq ->
                out += "\\[ ${eq.latex} \\]"
                eq.error?.let { out += "\\textit{Error: ${escape(it)}}" }
                out += ""
            },
            table = { t -> out += tabular(t) + "" },
        )
        out += "\\end{document}"
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n") + "\n"
    }

    private fun walk(
        sheet: Worksheet,
        text: (List<String>) -> Unit,
        equation: (Equation) -> Unit,
        table: (TableOutcome) -> Unit,
        plot: (PlotOutcome) -> Unit = { text(listOf("*(A plot: not exported yet.)*")) },
        folder: java.io.File? = null,
    ) {
        val calc = Calculation.of(sheet, folder)
        val plots = IdentityHashMap<Any, PlotOutcome>().apply { calc.plots.forEach { put(it.block, it) } }
        val units = Units.of(calc.lines.mapNotNull { it.parsed })
        val latex = Latex(units, calc.settings.multiplication)
        val outcomes = IdentityHashMap<Any, LineOutcome>().apply { calc.lines.forEach { put(it.line, it) } }
        val tables = IdentityHashMap<Any, TableOutcome>().apply { sheet.parts.filterIsInstance<TableBlock>().zip(calc.tables).forEach { (b, t) -> put(b, t) } }
        for (part in sheet.parts) {
            when (part) {
                is Markdown -> text(part.lines)
                is PlotBlock -> plots[part]?.let(plot) ?: text(listOf("*(A plot: not exported yet.)*"))
                is TableBlock -> tables[part]?.let(table)
                is CalcBlock -> for (line in part.lines) {
                    val o = outcomes[line] ?: continue
                    val st = o.parsed ?: continue
                    // A symbolic result is an expression, cached as its text.
                    val result = o.shown?.let { Result.of(ResultText.parts(it, o.format)) }
                        ?: (o.annotation as? Annotation.Result)?.takeIf { st is pub.beermat.engine.syntax.Symbolic }?.let { Result.of(ResultText.Parts(it.text, null)) }
                    equation(Equation(latex.statement(st, result), (o.annotation as? Annotation.Error)?.message))
                }
            }
        }
    }

    /** A table as a LaTeX `tabular`: header in bold, units in brackets, computed cells as their values. */
    private fun tabular(t: TableOutcome): List<String> {
        val cols = t.table.columns
        val spec = cols.joinToString("") { when (it.align) { Column.Align.RIGHT -> "r"; Column.Align.CENTRE -> "c"; Column.Align.LEFT -> "l" } }
        val header = cols.joinToString(" & ") { "\\textbf{${escape(it.header)}}" }
        val rows = t.table.rows.mapIndexed { r, row ->
            row.mapIndexed { c, cell -> escape(t.shown[r to c] ?: if (Table.expression(cell) != null) "" else cell) }.joinToString(" & ")
        }
        return listOf("\\begin{center}", "\\begin{tabular}{$spec}", "\\hline", "$header \\\\", "\\hline") + rows.map { "$it \\\\" } +
            listOf("\\hline", "\\end{tabular}", "\\end{center}")
    }

    /** Markdown as written, without region markers, and with plots noted rather than drawn (for now). */
    private fun markdownText(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var fence: String? = null
        for (line in lines) {
            val t = line.trim()
            when {
                fence != null -> if (t.startsWith(fence)) fence = null
                t.startsWith("```plot") || t.startsWith("~~~plot") -> {
                    fence = t.takeWhile { it == t[0] }
                    out += "*(A plot: not exported yet.)*"
                }
                t.startsWith("<!--") && t.endsWith("-->") && t.removePrefix("<!--").trimStart().startsWith("#") -> {}
                else -> out += line
            }
        }
        return out
    }

    /** Headings as unnumbered sections, paragraphs as text; other Markdown is passed through escaped. */
    private fun latexText(lines: List<String>): List<String> = markdownText(lines).map { line ->
        val t = line.trim()
        val level = t.takeWhile { it == '#' }.length
        val body = t.drop(level).trim()
        when {
            level == 1 -> "\\section*{${inline(body)}}"
            level == 2 -> "\\subsection*{${inline(body)}}"
            level >= 3 -> "\\subsubsection*{${inline(body)}}"
            else -> inline(line)
        }
    }

    /** `**bold**` and `*italic*` to LaTeX; everything else escaped. */
    private fun inline(s: String): String = escape(s)
        .replace(Regex("""\\\*\\\*(.+?)\\\*\\\*|\*\*(.+?)\*\*"""), "\\\\textbf{$1$2}")
        .replace(Regex("""\*(.+?)\*"""), "\\\\textit{$1}")

    private fun escape(s: String) = s.replace("\\", "\\textbackslash{}").replace(Regex("([{}#$%&_^~])"), "\\\\$1")
}
