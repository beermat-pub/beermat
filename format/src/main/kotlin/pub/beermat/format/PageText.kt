package pub.beermat.format

/**
 * A page header or footer (docs/file-format.md#frontmatter): text at the left, centre and right of
 * the margin, with fields filled in for each page: `{page}`, `{pages}`, `{title}`, `{date}`.
 */
data class PageText(val left: String = "", val centre: String = "", val right: String = "") {

    /** The three parts with their fields filled in. */
    fun filled(page: Int, pages: Int, title: String?, date: java.time.LocalDate = java.time.LocalDate.now()): List<String> =
        listOf(left, centre, right).map { part ->
            FIELD.replace(part) { m ->
                when (m.groupValues[1]) {
                    "page" -> page.toString()
                    "pages" -> pages.toString()
                    "title" -> title.orEmpty()
                    "date" -> date.toString()
                    else -> m.value
                }
            }
        }

    companion object {
        private val FIELD = Regex("""\{(\w+)}""")

        /** `header: "text"` (centred) or `header: {left: …, centre: …, right: …}`; null, with a problem noted, otherwise. */
        fun of(value: Any?, key: String, problems: MutableList<String>): PageText? = when (value) {
            null -> null
            is Map<*, *> -> {
                val parts = value.entries.associate { (k, v) -> k.toString() to v?.toString().orEmpty() }
                parts.keys.filter { it !in setOf("left", "centre", "center", "right") }.forEach { problems += "unknown setting: $key.$it" }
                PageText(parts["left"].orEmpty(), parts["centre"] ?: parts["center"].orEmpty(), parts["right"].orEmpty())
            }
            else -> PageText(centre = value.toString())
        }
    }
}
