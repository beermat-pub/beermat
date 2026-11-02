package pub.beermat.app

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import pub.beermat.app.editor.Key
import pub.beermat.app.page.CellKey
import pub.beermat.app.page.Session
import pub.beermat.app.page.TableEditor
import androidx.compose.ui.input.key.Key as K

/** The editor's keystrokes (docs/editor.md), from Compose key events. */
object Keys {

    fun handle(e: KeyEvent, session: Session, host: Host, onSave: () -> Unit): Boolean {
        val ctrl = e.isCtrlPressed && !e.isAltPressed
        val shift = e.isShiftPressed
        if (session.table != null) table(e, ctrl, shift, session, host)?.let { return it }
        // Characters arrive as typed events, after the keyboard layout (AltGr included).
        if (e.type == KeyEventType.Unknown) {
            val code = e.utf16CodePoint
            if (code < 32 || code == 127 || code == 0xFFFF) return false
            if (ctrl) return false
            val c = code.toChar()
            return session.key(if (c == ' ') Key.Space else Key.Type(c))
        }
        if (e.type != KeyEventType.KeyDown) return false
        // The list of a module's functions, after `bt.`, takes the arrows, Enter, Tab and Escape while open.
        if (session.completion != null) when (e.key) {
            K.DirectionUp -> return session.moveCompletion(-1)
            K.DirectionDown -> return session.moveCompletion(1)
            K.Enter, K.NumPadEnter, K.Tab -> return session.acceptCompletion()
            K.Escape -> return session.closeCompletion()
            else -> {}
        }
        return when (e.key) {
            K.Enter, K.NumPadEnter -> {
                if (ctrl) session.pageBreak() else session.enter()
                true
            }
            K.Escape -> session.leave().also { session.deselect() }.let { true }
            // With only the crosshair, they take out a blank line (issue #5).
            K.Backspace -> session.deleteSelected() || session.key(Key.Backspace) || session.removeLine(forward = false)
            K.Delete -> session.deleteSelected() || session.key(Key.Delete) || session.removeLine(forward = true)
            K.DirectionLeft -> session.key(Key.Left) || session.nudge(-1, 0)
            K.DirectionUp -> session.vertical(up = true) || session.nudge(0, -1)
            K.DirectionDown -> session.vertical(up = false) || session.nudge(0, 1)
            K.DirectionRight -> session.key(Key.Right) || session.nudge(1, 0)
            K.Tab -> session.tab(back = shift)
            K.MoveHome -> session.key(Key.Home)
            K.MoveEnd -> session.key(Key.End)
            K.Insert -> session.key(Key.Insert)
            K.F9 -> session.relayout().let { true }
            else -> when {
                ctrl -> control(e.key, shift, session, host, onSave)
                typedOnKeyDown -> typed(e)?.let { c -> session.key(if (c == ' ') Key.Space else Key.Type(c)) } ?: false
                else -> false
            }
        }
    }

    /** In the browser, the character a key-down event types: printable, without Ctrl. */
    private fun typed(e: KeyEvent): Char? {
        val code = e.utf16CodePoint
        return if (code < 32 || code == 127 || code == 0xFFFF) null else code.toChar()
    }

    /** A key for the table grid at the cell cursor (docs/tables.md#editing); null to handle it as usual (Ctrl+S, Ctrl+Z…). */
    private fun table(e: KeyEvent, ctrl: Boolean, shift: Boolean, session: Session, host: Host): Boolean? {
        if (e.type == KeyEventType.Unknown) {
            val code = e.utf16CodePoint
            if (code < 32 || code == 127 || code == 0xFFFF || ctrl) return null
            return TableEditor.key(session, CellKey.Type(code.toChar()))
        }
        if (e.type != KeyEventType.KeyDown) return null
        // In the browser, a letter typed into a cell comes with its key-down event.
        val typedCell = { if (typedOnKeyDown && !ctrl) typed(e)?.let { TableEditor.key(session, CellKey.Type(it)) } else null }
        val key = when (e.key) {
            K.Enter, K.NumPadEnter -> CellKey.Enter
            K.Escape -> CellKey.Escape
            K.Backspace -> CellKey.Backspace
            K.Delete -> CellKey.Delete
            K.DirectionLeft -> CellKey.Left
            K.DirectionRight -> CellKey.Right
            K.DirectionUp -> CellKey.Up
            K.DirectionDown -> CellKey.Down
            K.Tab -> if (shift) CellKey.BackTab else CellKey.Tab
            K.MoveHome -> CellKey.Home
            K.MoveEnd -> CellKey.End
            K.F2 -> CellKey.Edit
            K.V -> return if (ctrl) (host.paste() as? Pasted.Text)?.let { TableEditor.paste(session, it.text) } ?: false else typedCell()
            K.C -> return if (ctrl) TableEditor.copy(session)?.let { host.copyText(it); true } ?: false else typedCell()
            else -> return typedCell()
        }
        return TableEditor.key(session, key)
    }

    /** `Ctrl+M` and the matrix button: a new matrix in an empty slot; in a matrix, Insert or Delete of rows and columns. */
    fun matrixCommand(session: Session, host: Host): Boolean {
        if (!session.inMatrix) return host.askMatrix()?.let { (r, c) -> session.matrix(r, c) } ?: false
        val (rows, cols, insert) = host.askMatrixResize() ?: return false
        return session.resizeMatrix(rows, cols, insert)
    }

    /** The character a control-key chord names: Ctrl+G is 'g', Ctrl+= is '='. */
    private val CHORDS: Map<K, Char> = buildMap {
        listOf(K.A, K.B, K.C, K.D, K.E, K.F, K.G, K.H, K.I, K.J, K.K, K.L, K.M, K.N, K.O, K.P, K.Q, K.R, K.S, K.T, K.U, K.V, K.W, K.X, K.Y, K.Z)
            .forEachIndexed { k, key -> put(key, 'a' + k) }
        listOf(K.Zero, K.One, K.Two, K.Three, K.Four, K.Five, K.Six, K.Seven, K.Eight, K.Nine).forEachIndexed { k, key -> put(key, '0' + k) }
        put(K.Equals, '=')
        put(K.Minus, '-')
        put(K.Backslash, '\\')
        put(K.Period, '.')
        put(K.Slash, '/')
    }

    private fun control(key: K, shift: Boolean, session: Session, host: Host, onSave: () -> Unit): Boolean {
        val c = CHORDS[key] ?: return false
        return when {
            c == 's' && shift -> session.separate().let { true }
            c == 's' -> onSave().let { true }
            c == 'z' -> session.undo().let { true }
            c == 'y' -> session.redo().let { true }
            c == 'm' -> matrixCommand(session, host)
            c == 'c' -> session.copyText()?.let { host.copyText(it); true } ?: false
            // A picture copied from another program becomes an image region; text, a math region.
            c == 'v' -> when (val pasted = host.paste()) {
                is Pasted.Text -> session.paste(pasted.text).let { true }
                is Pasted.Picture -> session.insertImage(pasted.png, "image/png", "Pasted picture")
                null -> false
            }
            else -> session.key(Key.Ctrl(c, shift))
        }
    }
}
