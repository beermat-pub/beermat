package pub.beermat.engine.syntax

/**
 * Turns the indented lines of a program into a tree by indentation, as Python does: a deeper
 * line belongs to the line above it, an equal one is its sibling, and a shallower one must
 * line up with an earlier level. [lines] includes the definition line `f(x) :=` as line 0.
 */
internal class ProgramParser(private val lines: List<String>) {

    /** One program line: its index in the statement, its indentation, its text, and the lines below it. */
    class Node(val line: Int, val indent: Int, val text: String) {
        val children = mutableListOf<Node>()
    }

    fun program(): Program = Program(tree().map { Parser.programLine(it) })

    private fun tree(): List<Node> {
        val headIndent = indentOf(lines[0], 0)
        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        for (k in 1 until lines.size) {
            val text = lines[k]
            if (text.isBlank()) throw ParseException("a program can't contain a blank line", 0, k)
            val indent = indentOf(text, k)
            if (indent <= headIndent) throw ParseException("program lines must be indented below the definition", indent, k)
            while (stack.isNotEmpty() && stack.last().indent > indent) stack.removeLast()
            if (stack.isNotEmpty() && stack.last().indent == indent) stack.removeLast()
            val siblings = stack.lastOrNull()?.children ?: roots
            if (siblings.isNotEmpty() && siblings[0].indent != indent) {
                throw ParseException("the indentation doesn't line up with the lines above", indent, k)
            }
            val node = Node(k, indent, text)
            siblings += node
            stack.addLast(node)
        }
        return roots
    }

    private fun indentOf(text: String, line: Int): Int {
        val indent = text.length - text.trimStart(' ').length
        if (text.getOrNull(indent) == '\t') throw ParseException("indent program lines with spaces, not tabs", indent, line)
        return indent
    }
}
