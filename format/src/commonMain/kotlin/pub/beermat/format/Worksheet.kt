package pub.beermat.format

/**
 * A `.bbm` file (docs/file-format.md), kept close to its text so that writing it back changes
 * nothing but the result cache. Everything outside `calc` fences, including text regions and
 * plots, is kept verbatim as [Markdown] for now.
 */
data class Worksheet(
    /** The frontmatter lines between the `---` markers, or null when the file has none. */
    val frontmatter: List<String>?,
    val parts: List<Part>,
) {
    val blocks: List<CalcBlock> get() = parts.filterIsInstance<CalcBlock>()

    val plots: List<PlotBlock> get() = parts.filterIsInstance<PlotBlock>()

    /** The `results:` hash in the frontmatter, if any. */
    val resultsHash: String? get() = frontmatter?.firstNotNullOfOrNull { resultsLine.matchEntire(it)?.groupValues?.get(1) }

    /** The same sheet with every annotation and the `results:` line removed (and the frontmatter, if that was all of it): the cache's inputs. */
    fun inputs(): Worksheet = Worksheet(
        frontmatter?.filterNot { resultsLine.matches(it) }?.takeIf { it.isNotEmpty() },
        parts.map { p ->
            when (p) {
                is CalcBlock -> p.copy(lines = p.lines.map { it.copy(annotation = null) })
                // A table's cached values (after the arrows) are output too.
                is TableBlock -> p.copy(body = Table.write(p.table))
                else -> p
            }
        },
    )

    companion object {
        internal val resultsLine = Regex("""results:\s*(\S*)\s*""")
    }
}

sealed interface Part

/** Lines of Markdown, kept as written. */
data class Markdown(val lines: List<String>) : Part

/** A ```` ```calc ```` fence. [open] and [close] are the fence lines as written. */
data class CalcBlock(
    val open: String,
    val attributes: Attributes,
    val lines: List<CalcLine>,
    val close: String,
) : Part

/** A ```` ```plot ```` fence (docs/plots.md): its [body] is YAML, kept as written. */
data class PlotBlock(
    val open: String,
    val attributes: Attributes,
    val body: List<String>,
    val close: String,
) : Part

/**
 * One line in a `calc` fence: the statement as written ([source], without trailing spaces)
 * and its cached result, if any. [number] is the 1-based line number in the file it was read
 * from (0 for a line made in code).
 */
data class CalcLine(val source: String, val annotation: Annotation? = null, val number: Int = 0) {
    val isBlank: Boolean get() = source.isBlank()
}

/** A cached outcome after a statement: `#= 54 kN*m` or `#! units do not match`. */
sealed interface Annotation {
    data class Result(val text: String) : Annotation
    data class Error(val message: String) : Annotation
}

/** A ```` ```table ```` fence (docs/tables.md): its [body] is a Markdown pipe table. */
data class TableBlock(
    val open: String,
    val attributes: Attributes,
    val body: List<String>,
    val close: String,
) : Part {
    val table: Table get() = Table.parse(body)
}
