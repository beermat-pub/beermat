package pub.beermat.app

import pub.beermat.app.page.Session

/**
 * What the sheet view asks of the shell it runs in (docs/web.md#step-2-in-detail): the desktop app's
 * window, the browser page, or VS Code's web view (docs/vscode.md). The desktop does these with the
 * system clipboard and Swing dialogs.
 */
interface Host {
    /** Puts [text] on the clipboard. */
    fun copyText(text: String)

    /** Puts a picture, as PNG bytes, on the clipboard. */
    fun copyImage(png: ByteArray)

    /** What the clipboard holds, as text or a picture; null when it holds neither. */
    fun paste(): Pasted?

    /** A short sound when a key or a button does nothing. */
    fun beep()

    /**
     * Whether Ctrl+Z and Ctrl+Y undo in the sheet's own history. In VS Code they are the editor's, on
     * the document, which the page then shows, so that undo works across the page and the text alike.
     */
    val undoesItself: Boolean get() = true

    /**
     * Asks for a new matrix's rows and columns, and gives them to [answer]; not at all when cancelled.
     * The desktop and the browser answer before returning; VS Code's web view, which can't wait for a
     * question box, answers later, and its shell then shows the change.
     */
    fun askMatrix(answer: (rows: Int, cols: Int) -> Unit)

    /** Asks how to resize the matrix at the cursor: rows, columns, and whether to insert (or delete) them; answered as [askMatrix] is. */
    fun askMatrixResize(answer: (rows: Int, cols: Int, insert: Boolean) -> Unit)

    /** The Result Format dialog (docs/editor.md#result-format) for [statement] of [region], applying what is chosen. */
    fun editResultFormat(session: Session, region: Int, statement: Int)
}

/** What a paste brings: text (math, or a table's cells) or a picture. */
sealed interface Pasted {
    data class Text(val text: String) : Pasted

    class Picture(val png: ByteArray) : Pasted
}
