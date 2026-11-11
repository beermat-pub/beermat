package pub.beermat.app

import pub.beermat.app.editor.Editor
import pub.beermat.app.editor.Key
import pub.beermat.app.page.Session

/**
 * A command on the toolbar: what its button shows ([label]), its [name] and keystroke ([keys])
 * for the tooltip, and what it does, which is exactly what the keystroke does.
 */
class Command(val label: String, val name: String, val keys: String, val run: (Session) -> Boolean)

/** A group of commands, a palette (Calculator, Matrix, Calculus…). */
class Palette(val name: String, val commands: List<Command>)

/** Every toolbar command (docs/editor.md#toolbar). */
object Commands {

    /** The keys, one after another; stops (false) at the first that does nothing there. */
    private fun keys(vararg k: Key): (Session) -> Boolean = { s -> k.all { s.key(it) } }

    private fun type(c: Char) = keys(Key.Type(c))

    private fun ctrl(c: Char, shift: Boolean = false) = keys(Key.Ctrl(c, shift))

    /** A keyword's structure at the cursor, as typing it then Space does: `for`, `given`… */
    private fun word(w: String): (Session) -> Boolean = { s -> s.edit { Editor.keyword(it, w) } }

    private fun cmd(label: String, name: String, keys: String, run: (Session) -> Boolean) = Command(label, name, keys, run)

    /** `Ctrl+M`'s dialogs, set by the window: a new matrix, or in a matrix, inserting or deleting rows and columns. */
    var matrixCommand: (Session) -> Boolean = { false }

    val palettes = listOf(
        Palette(
            "Calculator",
            listOf(
                cmd(":=", "Definition", ":", type(':')),
                cmd("≡", "Global definition", "~", type('~')),
                cmd("=", "Evaluate", "=", type('=')),
                cmd("+", "Add", "+", type('+')),
                cmd("−", "Subtract", "-", type('-')),
                cmd("·", "Multiply", "*", type('*')),
                cmd("x/y", "Fraction", "/", type('/')),
                cmd("xⁿ", "Power", "^", type('^')),
                cmd("√", "Square root", "\\", type('\\')),
                cmd("ⁿ√", "Nth root", "Ctrl+\\", ctrl('\\')),
                cmd("|x|", "Absolute value", "|", type('|')),
                cmd("( )", "Parentheses", "'", type('\'')),
                cmd("n!", "Factorial", "!", type('!')),
                cmd("TeX", "Type or show the math as LaTeX", "Ctrl+L", { s -> s.startLatex() }),
            ),
        ),
        Palette(
            "Comparisons",
            listOf(
                cmd("=", "Equal to (bold equals)", "Ctrl+=", ctrl('=')),
                cmd("≠", "Not equal", "Ctrl+3 or !=", ctrl('3')),
                cmd("<", "Less than", "<", type('<')),
                cmd(">", "Greater than", ">", type('>')),
                cmd("≤", "Less or equal", "Ctrl+9 or <=", ctrl('9')),
                cmd("≥", "Greater or equal", "Ctrl+0 or >=", ctrl('0')),
            ),
        ),
        Palette(
            "Matrix",
            listOf(
                cmd("[⋮]", "Matrix (in a matrix: insert or delete rows and columns)", "Ctrl+M", { s -> matrixCommand(s) }),
                cmd("xᵢ", "Index", "[", type('[')),
                cmd("Mᵀ", "Transpose", "Ctrl+1", ctrl('1')),
                cmd("M⟨⟩", "Column", "Ctrl+6", ctrl('6')),
                cmd("→", "Vectorize", "Ctrl+-", ctrl('-')),
                cmd("m..n", "Range", ";", type(';')),
            ),
        ),
        Palette(
            "Calculus",
            listOf(
                cmd("d/dx", "Derivative", "?", type('?')),
                cmd("∫", "Definite integral", "&", type('&')),
                cmd("Σ", "Sum over a range variable", "$", type('$')),
                cmd("Σᵃᵇ", "Sum with limits", "Ctrl+Shift+4", ctrl('4', shift = true)),
                cmd("Π", "Product over a range variable", "#", type('#')),
                cmd("Πᵃᵇ", "Product with limits", "Ctrl+Shift+3", ctrl('3', shift = true)),
            ),
        ),
        Palette(
            "Programming",
            listOf(
                cmd("⎸", "Add program line", "]", type(']')),
                cmd("←", "Local assignment", "{", type('{')),
                cmd("if", "If", "}", type('}')),
                cmd("otherwise", "Otherwise", "otherwise, then Space", word("otherwise")),
                cmd("for", "For loop", "for, then Space", word("for")),
                cmd("while", "While loop", "while, then Space", word("while")),
                cmd("break", "Break", "break, then Space", word("break")),
                cmd("continue", "Continue", "continue, then Space", word("continue")),
                cmd("return", "Return", "return, then Space", word("return")),
                cmd("try", "Try … on error", "try, then Space", word("try")),
            ),
        ),
        Palette(
            "Symbolic and solving",
            listOf(
                cmd("→", "Symbolic evaluation", "Ctrl+.", ctrl('.')),
                cmd("→ kw", "Symbolic keyword", "Ctrl+Shift+.", ctrl('.', shift = true)),
                cmd("Given", "Solve block", "given, then Space", word("given")),
                cmd("find", "Find (solve block)", "find(", keys(*"find(".map { Key.Type(it) }.toTypedArray())),
                cmd("root", "Root of an equation", "root(", keys(*"root(".map { Key.Type(it) }.toTypedArray())),
            ),
        ),
        Palette(
            "Greek",
            "αβγδεθλμπρστφωΔΘΣΦΩ".map { c -> cmd(c.toString(), "Greek $c", "a letter, then Ctrl+G", type(c)) } +
                cmd("°", "Degree sign, for °C and °F (Δ°C for a difference)", "the ° key", type('°')),
        ),
        Palette(
            "Style",
            listOf(
                cmd("𝐁", "Bold name (a different variable)", "Ctrl+B", ctrl('b')),
                cmd("𝐼", "Italic name (plain)", "Ctrl+I", ctrl('i')),
                cmd("θ*", "Special text: any character in a name (again to end)", "Ctrl+Shift+K", ctrl('k', shift = true)),
            ),
        ),
        Palette(
            "Insert",
            listOf(
                cmd("T", "Text region", "\" at the crosshair", type('"')),
                cmd("∿", "X-Y plot", "@ at the crosshair", type('@')),
                cmd("▦", "Table", "Insert menu", { s -> s.crosshair != null && s.insertTable().let { true } }),
                cmd("⤓", "Page break", "Ctrl+Enter", { s -> s.pageBreak(); true }),
                cmd("▾", "Area (collapsible)", "Insert menu", { s -> s.insertArea(); true }),
            ),
        ),
    )
}
