package pub.beermat.web

import pub.beermat.app.Host
import pub.beermat.app.Pasted
import pub.beermat.app.page.Session

/**
 * The sheet view's [Host] in the browser: its clipboard and question boxes. In VS Code's web view,
 * which has no question boxes, the extension asks instead and answers in a message ([answer]).
 */
internal object WebHost : Host {

    override fun copyText(text: String) = copyToClipboard(text)

    override fun copyImage(png: ByteArray) = copyPng(png)

    // The browser gives the clipboard only in a paste event, which the page handles (onPaste).
    override fun paste(): Pasted? = null

    override fun beep() {}

    override val undoesItself: Boolean get() = !inVsCode()

    override fun askMatrix(answer: (Int, Int) -> Unit) {
        if (inVsCode()) return ask("matrix") { text, _ -> numbers(text).takeIf { it.size == 2 }?.let { answer(it[0], it[1]) } }
        val n = numbers(prompt("Rows and columns:", "3, 1") ?: return)
        if (n.size == 2) answer(n[0], n[1])
    }

    override fun askMatrixResize(answer: (Int, Int, Boolean) -> Unit) {
        if (inVsCode()) return ask("resize") { text, insert -> numbers(text).takeIf { it.size == 2 }?.let { answer(it[0], it[1], insert) } }
        val n = numbers(prompt("Rows and columns, after the cursor's cell (insert) or from it (delete):", "1, 0") ?: return)
        if (n.size == 2) answer(n[0], n[1], confirm("OK to insert them, Cancel to delete them."))
    }

    override fun askText(question: String, initial: String, answer: (String) -> Unit) {
        // The extension reads the question and the starting text from the lines after the kind.
        if (inVsCode()) return ask("text\n$question\n$initial") { text, yes -> if (yes) answer(text) }
        prompt(question, initial)?.let(answer)
    }

    /** Questions sent to VS Code, waiting for their answers, by number. */
    private val waiting = mutableMapOf<Int, (String, Boolean) -> Unit>()
    private var asked = 0

    /** Shows the change an answer made (the page sets this). */
    var answered: () -> Unit = {}

    private fun ask(kind: String, then: (String, Boolean) -> Unit) {
        waiting[++asked] = then
        postToVsCode("ask", kind, asked)
    }

    /** VS Code's answer to question [id]: the text typed (empty when cancelled), and a yes or no. */
    fun answer(id: Int, text: String, yes: Boolean) {
        waiting.remove(id)?.invoke(text, yes)
        answered()
    }

    /** Opens the page's Result Format dialog (the page sets this to show it). */
    var resultFormat: (region: Int, statement: Int) -> Unit = { _, _ -> }

    override fun editResultFormat(session: Session, region: Int, statement: Int) = resultFormat(region, statement)

    private fun numbers(text: String) = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
}
