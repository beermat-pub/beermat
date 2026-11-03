package pub.beermat.web

import pub.beermat.engine.eval.EvalException
import pub.beermat.symbolic.Bridge

/**
 * SymPy in VS Code (docs/vscode.md#step-3-in-detail): requests go to the extension, which asks the
 * installed `beermat bridge`. A web page can't wait for the answer, so the first time a request is
 * met it is sent and the statement says it is working; when the answer comes it is kept, and the sheet
 * is worked out again ([answered]), meeting it this time.
 */
internal object VsCodeBridge : Bridge {
    private val answers = mutableMapOf<String, String>()
    private val waiting = mutableMapOf<Int, String>()
    private var sent = 0

    /** Works the sheet out again with the answers that came (the page sets this). */
    var answered: () -> Unit = {}

    /** Whether requests are out, unanswered: the page doesn't send its text meanwhile, so "working" never reaches the file. */
    val busy: Boolean get() = waiting.isNotEmpty()

    override fun ask(request: String): String {
        answers[request]?.let { return it }
        if (request !in waiting.values) {
            waiting[++sent] = request
            postToVsCode("bridge", request, sent)
        }
        throw EvalException("SymPy is working on this…")
    }

    /** The extension's [reply] to request [id]. */
    fun answer(id: Int, reply: String) {
        val request = waiting.remove(id) ?: return
        answers[request] = reply
        answered()
    }
}
