package pub.beermat.web

import pub.beermat.app.Host
import pub.beermat.app.Pasted
import pub.beermat.app.page.Session

/** The sheet view's [Host] in the browser: its clipboard and question boxes. */
internal object WebHost : Host {

    override fun copyText(text: String) = copyToClipboard(text)

    override fun copyImage(png: ByteArray) = copyPng(png)

    // The browser gives the clipboard only in a paste event, which the page handles (onPaste).
    override fun paste(): Pasted? = null

    override fun beep() {}

    override fun askMatrix(): Pair<Int, Int>? {
        val n = numbers(prompt("Rows and columns:", "3, 1") ?: return null)
        return if (n.size == 2) n[0] to n[1] else null
    }

    override fun askMatrixResize(): Triple<Int, Int, Boolean>? {
        val n = numbers(prompt("Rows and columns, after the cursor's cell (insert) or from it (delete):", "1, 0") ?: return null)
        if (n.size != 2) return null
        return Triple(n[0], n[1], confirm("OK to insert them, Cancel to delete them."))
    }

    override fun editResultFormat(session: Session, region: Int, statement: Int) =
        alert("The Result Format dialog is in the desktop version for now. In the sheet, a result's unit can be typed after it: = ? kN*m")

    private fun numbers(text: String) = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
}
