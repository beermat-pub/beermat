package pub.beermat.symbolic

/**
 * Just enough JSON for the bridge to SymPy: strings, numbers, lists, objects, true, false
 * and null. Written as ASCII (anything else escaped), as Python's `json` writes it.
 */
object Json {

    fun write(v: Any?): String = StringBuilder().also { write(v, it) }.toString()

    private fun write(v: Any?, out: StringBuilder) {
        when (v) {
            null -> out.append("null")
            is String -> {
                out.append('"')
                for (c in v) {
                    when {
                        c == '"' -> out.append("\\\"")
                        c == '\\' -> out.append("\\\\")
                        c < ' ' || c > '~' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                        else -> out.append(c)
                    }
                }
                out.append('"')
            }
            is Int, is Long, is Boolean -> out.append(v)
            // As Kotlin writes it (1.0E-5, NaN, Infinity), which Python's json reads.
            is Double -> out.append(v.toString())
            is List<*> -> {
                out.append('[')
                v.forEachIndexed { k, e -> if (k > 0) out.append(','); write(e, out) }
                out.append(']')
            }
            is Map<*, *> -> {
                out.append('{')
                v.entries.forEachIndexed { k, (key, e) -> if (k > 0) out.append(','); write(key.toString(), out); out.append(':'); write(e, out) }
                out.append('}')
            }
            else -> error("can't write ${v::class.simpleName} as JSON")
        }
    }

    /** Strings, [Long]s (or [Double]s), lists, maps, booleans and null. */
    fun read(text: String): Any? = Reader(text).run { value().also { space(); if (i < s.length) fail() } }

    private class Reader(val s: String) {
        var i = 0

        fun fail(): Nothing = throw IllegalArgumentException("bad JSON at $i: ${s.take(200)}")

        fun space() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            space()
            if (i >= s.length) fail()
            return when (s[i]) {
                '"' -> string()
                '[' -> {
                    i++
                    val out = mutableListOf<Any?>()
                    space()
                    if (s[i] == ']') { i++; return out }
                    while (true) {
                        out += value()
                        space()
                        when (s.getOrNull(i++)) {
                            ',' -> continue
                            ']' -> return out
                            else -> fail()
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") out
                }
                '{' -> {
                    i++
                    val out = linkedMapOf<String, Any?>()
                    space()
                    if (s[i] == '}') { i++; return out }
                    while (true) {
                        space()
                        val key = string()
                        space()
                        if (s.getOrNull(i++) != ':') fail()
                        out[key] = value()
                        space()
                        when (s.getOrNull(i++)) {
                            ',' -> continue
                            '}' -> return out
                            else -> fail()
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") out
                }
                else -> word()
            }
        }

        fun string(): String {
            if (s[i] != '"') fail()
            i++
            val out = StringBuilder()
            while (true) {
                val c = s.getOrNull(i++) ?: fail()
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> when (val e = s.getOrNull(i++) ?: fail()) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'u' -> out.append(s.substring(i, i + 4).toInt(16).toChar()).also { i += 4 }
                        else -> out.append(e)
                    }
                    else -> out.append(c)
                }
            }
        }

        fun word(): Any? {
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "+-.")) i++
            return when (val w = s.substring(start, i)) {
                "true" -> true
                "false" -> false
                "null" -> null
                else -> w.toLongOrNull() ?: w.toDoubleOrNull() ?: fail()
            }
        }
    }
}
