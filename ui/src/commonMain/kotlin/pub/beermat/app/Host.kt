package pub.beermat.app

import pub.beermat.app.page.Session

/**
 * What the sheet view asks of the shell it runs in (docs/web.md#step-2-in-detail): the desktop app's
 * window, or the browser page. The desktop does these with the system clipboard and Swing dialogs.
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

    /** Asks for a new matrix's rows and columns; null when cancelled. */
    fun askMatrix(): Pair<Int, Int>?

    /** Asks how to resize the matrix at the cursor: rows, columns, and whether to insert (or delete) them; null when cancelled. */
    fun askMatrixResize(): Triple<Int, Int, Boolean>?

    /** The Result Format dialog (docs/editor.md#result-format) for [statement] of [region], applying what is chosen. */
    fun editResultFormat(session: Session, region: Int, statement: Int)
}

/** What a paste brings: text (math, or a table's cells) or a picture. */
sealed interface Pasted {
    data class Text(val text: String) : Pasted

    class Picture(val png: ByteArray) : Pasted
}
