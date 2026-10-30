package pub.beermat.format

/**
 * A region's attribute set, `{#r1 x=40 y=120 strict}` (docs/file-format.md#regions).
 * A bare key such as `strict` has the value `null`.
 */
data class Attributes(val id: String? = null, val values: Map<String, String?> = emptyMap()) {

    operator fun get(key: String): String? = values[key]

    fun has(key: String) = key in values

    companion object {
        val NONE = Attributes()

        /** Parses the text between the braces. Values may be double-quoted. */
        fun parse(text: String): Attributes {
            var id: String? = null
            val values = linkedMapOf<String, String?>()
            for (token in tokens(text)) {
                when {
                    token.startsWith("#") -> id = token.substring(1)
                    '=' in token -> values[token.substringBefore('=')] = token.substringAfter('=').removeSurrounding("\"")
                    else -> values[token] = null
                }
            }
            return Attributes(id, values)
        }

        private fun tokens(text: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var quoted = false
            for (c in text) {
                when {
                    c == '"' -> { quoted = !quoted; sb.append(c) }
                    c.isWhitespace() && !quoted -> if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
                    else -> sb.append(c)
                }
            }
            if (sb.isNotEmpty()) out += sb.toString()
            return out
        }
    }
}
