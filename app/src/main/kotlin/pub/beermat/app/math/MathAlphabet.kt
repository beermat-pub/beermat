package pub.beermat.app.math

/**
 * Italic and bold letters as Unicode's mathematical alphanumeric characters (U+1D400 on), which
 * the math font draws; other characters are kept. Latin and Greek letters have both forms, and
 * digits have bold ones.
 */
object MathAlphabet {

    fun italic(text: String): String = map(text, latin = 0x1D434, greek = 0x1D6E2, digits = null) { c ->
        if (c == 'h') "ℎ" else null // ℎ, the one Latin italic that sits outside the block
    }

    fun bold(text: String): String = map(text, latin = 0x1D400, greek = 0x1D6A8, digits = 0x1D7CE)

    /** Maps letters to the alphabets starting at [latin] (A to Z, then a to z) and [greek] (Α to Ω, then α to ω). */
    private fun map(text: String, latin: Int, greek: Int, digits: Int?, special: (Char) -> String? = { null }): String = buildString {
        for (c in text) {
            val s = special(c)
            when {
                s != null -> append(s)
                c in 'A'..'Z' -> appendCodePoint(latin + (c - 'A'))
                c in 'a'..'z' -> appendCodePoint(latin + 26 + (c - 'a'))
                c == '΢' -> append(c) // no capital final sigma
                c in 'Α'..'Ω' -> appendCodePoint(greek + (c - 'Α'))
                c in 'α'..'ω' -> appendCodePoint(greek + 26 + (c - 'α'))
                c in '0'..'9' && digits != null -> appendCodePoint(digits + (c - '0'))
                else -> append(c)
            }
        }
    }
}
