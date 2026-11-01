package pub.beermat.app

import pub.beermat.app.editor.Key
import pub.beermat.app.page.Session

/** `--script`: clicks and typing, `;`-separated (one per line in a file), for checking the editor without a person at the keyboard. */
object Scripts {
    fun run(session: Session, script: String, separator: String = ";") {
        for (step in script.split(separator).map { it.trim() }.filter { it.isNotEmpty() }) {
            val (verb, rest) = step.substringBefore(' ') to step.substringAfter(' ', "")
            when (verb) {
                "click" -> rest.split(' ').map { it.toFloat() }.let { (x, y) -> session.click(x, y) }
                "type" -> keys(rest).forEach { k ->
                    when (k) {
                        "enter" -> session.enter()
                        "esc" -> session.leave()
                        else -> session.key(key(k) ?: Key.Type(k.single()))
                    }
                }
                "save" -> session.save()
                else -> error("unknown script step: $step")
            }
        }
    }

    /** The keys of [text]: characters, and `«name»` for special keys. */
    fun keys(text: String): List<String> {
        val out = mutableListOf<String>()
        var k = 0
        while (k < text.length) {
            if (text[k] == '«') {
                val end = text.indexOf('»', k)
                out += text.substring(k + 1, end)
                k = end + 1
            } else {
                out += text[k++].toString()
            }
        }
        return out
    }

    private fun key(name: String): Key? = when {
        name.length == 1 -> null
        name == "space" -> Key.Space
        name == "bs" -> Key.Backspace
        name == "del" -> Key.Delete
        name == "left" -> Key.Left
        name == "right" -> Key.Right
        name == "tab" -> Key.Tab
        name == "ins" -> Key.Insert
        name.startsWith("ctrl-shift-") -> Key.Ctrl(name.last(), true)
        name.startsWith("ctrl-") -> Key.Ctrl(name.last())
        else -> error("unknown key «$name»")
    }
}
