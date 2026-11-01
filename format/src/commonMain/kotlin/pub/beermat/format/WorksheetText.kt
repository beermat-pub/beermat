package pub.beermat.format


/** Reads and writes the text of a `.bbm` file (docs/file-format.md). */
object WorksheetText {

    fun read(text: String): Worksheet {
        // A byte-order mark (some Windows editors write one) is dropped, like CRLF line endings.
        val lines = text.removePrefix("﻿").replace("\r\n", "\n").split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
        var k = 0
        var frontmatter: List<String>? = null
        if (lines.firstOrNull() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it] == "---" || lines[it] == "..." }
            if (end != null) {
                frontmatter = lines.subList(1, end)
                k = end + 1
            }
        }
        val parts = mutableListOf<Part>()
        val markdown = mutableListOf<String>()
        fun flush() {
            if (markdown.isNotEmpty()) parts += Markdown(markdown.toList())
            markdown.clear()
        }
        while (k < lines.size) {
            val open = fenceOpen.matchEntire(lines[k])
            if (open == null) {
                markdown += lines[k++]
                continue
            }
            val fence = open.groupValues[2]
            val info = open.groupValues[3].trim()
            val end = (k + 1 until lines.size).firstOrNull { isFenceClose(lines[it], fence) } ?: lines.size
            val calc = calcInfo.matchEntire(info)
            val plot = plotInfo.matchEntire(info)
            val table = tableInfo.matchEntire(info)
            if (table != null) {
                flush()
                val attributes = table.groupValues[1].takeIf { it.isNotEmpty() }?.let { Attributes.parse(it) } ?: Attributes.NONE
                parts += TableBlock(lines[k], attributes, lines.subList(k + 1, minOf(end, lines.size)).toList(), lines.getOrElse(end) { fence })
            } else if (plot != null) {
                flush()
                val attributes = plot.groupValues[1].takeIf { it.isNotEmpty() }?.let { Attributes.parse(it) } ?: Attributes.NONE
                parts += PlotBlock(lines[k], attributes, lines.subList(k + 1, minOf(end, lines.size)).toList(), lines.getOrElse(end) { fence })
            } else if (calc == null) {
                // Another fence (a code sample): kept verbatim, and never searched for calc fences.
                markdown += lines.subList(k, minOf(end + 1, lines.size))
            } else {
                flush()
                val attributes = calc.groupValues[1].takeIf { it.isNotEmpty() }?.let { Attributes.parse(it) } ?: Attributes.NONE
                val body = (k + 1 until end).map { readLine(lines[it], it + 1) }
                parts += CalcBlock(lines[k], attributes, body, lines.getOrElse(end) { fence })
            }
            k = end + 1
        }
        flush()
        return Worksheet(frontmatter, parts)
    }

    /** The canonical text: LF line endings, a final newline, and annotations aligned per fence. */
    fun write(sheet: Worksheet): String {
        val out = mutableListOf<String>()
        sheet.frontmatter?.let { out += "---"; out += it; out += "---" }
        for (part in sheet.parts) {
            when (part) {
                is Markdown -> out += part.lines
                is PlotBlock -> {
                    out += part.open
                    out += part.body
                    out += part.close
                }
                is TableBlock -> {
                    out += part.open
                    out += part.body
                    out += part.close
                }
                is CalcBlock -> {
                    out += part.open
                    val width = part.lines.filter { it.annotation != null }.maxOfOrNull { it.source.length } ?: 0
                    out += part.lines.map { line ->
                        when (val a = line.annotation) {
                            null -> line.source
                            is Annotation.Result -> line.source.padEnd(width) + "$GAP#= ${a.text}"
                            is Annotation.Error -> line.source.padEnd(width) + "$GAP#! ${a.message}"
                        }
                    }
                    out += part.close
                }
            }
        }
        return out.joinToString("\n", postfix = "\n")
    }

    /** The hash of the sheet's inputs, for the frontmatter's `results:` key. */
    fun inputHash(sheet: Worksheet): String {
        val digest = Sha256.digest(write(sheet.inputs()).encodeToByteArray())
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }.take(16)
    }

    /** [sheet] with its `results:` line set to [hash], creating the frontmatter if needed. */
    fun withHash(sheet: Worksheet, hash: String): Worksheet {
        val line = "results: $hash"
        val fm = sheet.frontmatter ?: emptyList()
        val k = fm.indexOfFirst { Worksheet.resultsLine.matches(it) }
        return sheet.copy(frontmatter = if (k >= 0) fm.toMutableList().also { it[k] = line } else fm + line)
    }

    /** Splits a fence line into the statement and its trailing `#=` or `#!` annotation. */
    fun readLine(text: String, number: Int): CalcLine {
        var inString = false
        var k = 0
        while (k < text.length) {
            val c = text[k]
            when {
                inString && c == '\\' -> k++
                c == '"' -> inString = !inString
                !inString && c == '#' && k + 1 < text.length && (text[k + 1] == '=' || text[k + 1] == '!') -> {
                    val body = text.substring(k + 2).trim()
                    val annotation = if (text[k + 1] == '=') Annotation.Result(body) else Annotation.Error(body)
                    return CalcLine(text.substring(0, k).trimEnd(), annotation, number)
                }
            }
            k++
        }
        return CalcLine(text.trimEnd(), null, number)
    }

    private const val GAP = "   "

    /** A fence opening: up to 3 spaces, 3 or more backticks or tildes, an info string (CommonMark). */
    private val fenceOpen = Regex("""( {0,3})(`{3,}(?=[^`]*$)|~{3,})(.*)""")

    /** `calc`, optionally followed by `{attributes}`. */
    private val calcInfo = Regex("""calc(?:\s*\{(.*)}|\s*)""")

    /** `plot`, optionally followed by `{attributes}`. */
    private val plotInfo = Regex("""plot(?:\s*\{(.*)}|\s*)""")

    /** `table`, optionally followed by `{attributes}`. */
    private val tableInfo = Regex("""table(?:\s*\{(.*)}|\s*)""")

    private fun isFenceClose(line: String, fence: String): Boolean {
        val t = line.trimEnd()
        val indent = t.length - t.trimStart().length
        val marks = t.trimStart()
        return indent <= 3 && marks.length >= fence.length && marks.all { it == fence[0] }
    }
}
