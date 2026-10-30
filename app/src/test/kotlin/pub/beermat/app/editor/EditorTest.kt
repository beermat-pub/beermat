package pub.beermat.app.editor

import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Typing statements key by key, and checking the text they save as. */
class EditorTest {

    /** Types [keys] into a new region; `«…»` names a special key, such as `«space»` or `«ctrl-g»`. */
    private fun typed(keys: String, from: EditState = EditState.empty()): EditState {
        var s = from
        var k = 0
        while (k < keys.length) {
            val key: Key = if (keys[k] == '«') {
                val end = keys.indexOf('»', k)
                val name = keys.substring(k + 1, end)
                k = end + 1
                when {
                    name == "space" -> Key.Space
                    name == "bs" -> Key.Backspace
                    name == "del" -> Key.Delete
                    name == "left" -> Key.Left
                    name == "right" -> Key.Right
                    name == "tab" -> Key.Tab
                    name == "home" -> Key.Home
                    name == "ins" -> Key.Insert
                    name.startsWith("ctrl-shift-") -> Key.Ctrl(name.last(), shift = true)
                    name.startsWith("ctrl-") -> Key.Ctrl(name.last())
                    else -> error(name)
                }
            } else {
                Key.Type(keys[k++])
            }
            s = Editor.key(s, key) ?: error("$key does nothing at ${s.cursor} in ${s.text}")
        }
        return s
    }

    private fun text(keys: String) = typed(keys).text

    @Test
    fun `definitions and evaluations`() {
        assertEquals("x := 3 m", text("x:3m"))
        assertEquals("T := 20 °C = ? °F", text("T:20°C=°F"))
        assertEquals("k := 5 W/Δ°C", text("k:5W/Δ°C"))
        assertEquals("M := q*L^2/8 = ? kN*m", text("M:q*L^2/8=kN*m"))
        assertEquals("x = ?", text("x="))
        assertEquals("x = ? mm", text("x=mm"))
        assertEquals("g ::= 9.81 m/s^2", text("g~9.81m/s^2"))
        assertEquals("f(x) := x^2", text("f(x):x^2"))
        assertEquals("v[i] := i^2", text("v[i]:i^2"))
        assertEquals("M_max := 1", text("M.max:1"))
    }

    @Test
    fun `operators follow precedence as you type`() {
        assertEquals("a + b*c", text("a+b*c"))
        assertEquals("a*b + c", text("a*b+c"))
        assertEquals("a/b/c", text("a/b/c"))
        assertEquals("2^3^4", text("2^3^4"))
        assertEquals("-x + 1", text("-x+1"))
        assertEquals("a - -b", text("a--b"))
        assertEquals("x <= 1", text("x<=1"))
        assertEquals("y := x != 1", text("y:x!=1"))
        assertEquals("5! = ?", text("5!="))
        assertEquals("a == b", text("a«ctrl-=»b"))
    }

    @Test
    fun `space widens the selection, which operators then take whole`() {
        assertEquals("(a + b)*c", text("a+b«space»«space»*c"))
        assertEquals("(a + b)*c", text("(a+b)*c"))
        assertEquals("x := (-b + sqrt(b^2 - 4*a*c))/(2 a)", text("x:(-b+\\b^2-4*a*c)/(2a"))
        assertEquals("a + b^2", text("a+b«space»^2"))
        assertEquals("(a + b)^2", text("a+b«space»«space»^2"))
    }

    @Test
    fun `numbers, units and names`() {
        assertEquals("x := 1.5e-3", text("x:1.5e-3"))
        assertEquals("d := 2 in", text("d:2in"))
        assertEquals("z := 2i", text("z:2i"))
        assertEquals("h := 0x1F", text("h:0x1F"))
        assertEquals("b := 0b101.01", text("b:0b101.01"))
        assertEquals("E := 2e5 MPa", text("E:2e5MPa"))
        assertEquals("p := 2 e", text("p:2ex«bs»"))
        assertEquals("s := \"hi there\"", text("s:\"hi there\""))
        assertEquals("α := 30 deg", text("a«ctrl-g»:30deg"))
        assertEquals("\\bf F := 1", text("F«ctrl-b»:1"))
        assertEquals("\\rm x := 1", text("x«ctrl-i»:1"))
        assertEquals("f'(x) := 1", text("f'(x):1"))
    }

    @Test
    fun `special text mode puts any character in a name`() {
        // On an empty slot, or after a name has started; again to end it, and the operators work again.
        assertEquals("`θ*` := 2", text("«ctrl-shift-k»θ*«ctrl-shift-k»:2"))
        assertEquals("`x%` + 1", text("x«ctrl-shift-k»%«ctrl-shift-k»+1"))
        assertEquals("`a b/c` := 1", text("a«ctrl-shift-k»«space»b/c«ctrl-shift-k»:1"))
        // A subscript after it, the usual way; and special characters in the subscript.
        assertEquals("`θ*`_max := 1", text("«ctrl-shift-k»θ*«ctrl-shift-k».max:1"))
        assertEquals("M_`a+b` := 1", text("M.«ctrl-shift-k»a+b«ctrl-shift-k»:1"))
        // An empty subscript, a look-alike variable.
        assertEquals("x_ := 5", text("x.:5"))
    }

    @Test
    fun `calls, indices, ranges and notation`() {
        assertEquals("g(a, b)", text("g(a,b"))
        assertEquals("M[i, j]", text("M[i,j"))
        assertEquals("i := 0..10", text("i:0;10"))
        assertEquals("i := 0, 2..10", text("i:0,2;10"))
        assertEquals("sqrt(2)", text("\\2"))
        assertEquals("sqrt(x)", text("x\\"))
        assertEquals("|x|", text("|x"))
        assertEquals("sum(i^2, i)", text("i^2«space»«space»\$i"))
        assertEquals("sum(?, ?, ?, ?)", text("«ctrl-shift-4»"))
        assertEquals("M^T", text("M«ctrl-1»"))
        assertEquals("nthroot(27, 3)", text("27«ctrl-\\»3"))
        assertEquals("vec(v*w)", text("v*w«space»«space»«ctrl--»"))
    }

    @Test
    fun `deleting`() {
        assertEquals("a + ?", text("a+b«bs»"))
        assertEquals("a", text("a+b«bs»«bs»"))
        assertEquals("x", text("x=«bs»"))
        assertEquals("x", text("x:«bs»"))
        assertEquals("ab", text("abc«bs»"))
        assertEquals("f", text("f(«bs»"))
        assertEquals("?", text("\\«bs»"))
        assertEquals("g(a)", text("g(a,«bs»"))
        assertEquals("?", text("a+b«space»«space»«bs»"))
    }

    @Test
    fun `moving`() {
        // Left into the name, then typing inserts there.
        assertEquals("abxc", text("abc«left»x"))
        assertEquals("xab + c", text("ab+c«left»«left»«left»«left»x"))
        // Tab goes to the next empty slot.
        assertEquals("sum(i, j, 1, 2)", text("«ctrl-shift-4»i«tab»j«tab»1«tab»2"))
        // A definition's target is checked: `a + b := ` is refused.
        assertNull(Editor.key(typed("a+b"), Key.Type(':')))
    }

    @Test
    fun `programs`() {
        // `]` makes the value a program; keywords then Space make their lines; widening to the loop adds a line after it.
        assertEquals(
            "f(x) :=\n  s <- 0\n  for k in 1..x\n    s <- s + k\n  s",
            text("f(x):]s{0«tab»for«space»k«tab»1;x«tab»s{s+k«space»«space»«space»«space»]s"),
        )
        assertEquals("g(x) :=\n  -x if x < 0\n  x otherwise", text("g(x):]-x}x<0«tab»otherwise«space»x"))
        assertEquals("h(n) :=\n  while n > 1\n    n <- n/2\n  return n", text("h(n):]while«space»n>1«tab»n{n/2«tab»return«space»n"))
        assertEquals("p :=\n  break if 1\n  ?", text("p:]break«space»}1«tab»"))
        assertEquals("y := try 1/0 on error 2", text("y:try«space»1/0«tab»2"))
        // An empty line goes with Backspace, and the cursor goes to the end of the line above.
        assertEquals("q :=\n  a\n  bc\n  ?", text("q:]a]b]«bs»c"))
    }

    @Test
    fun `operators on an empty spot bring their empty operands`() {
        assertEquals("? + ?", text("+"))
        assertEquals("a*?", text("*a"))
        assertEquals("a/b", text("/a«tab»b"))
        assertEquals("a < b", text("<a«tab»b"))
        assertEquals("a >= b", text(">=a«tab»b"))
        assertEquals("a^2", text("^a«tab»2"))
        assertEquals("n!", text("!n"))
        assertEquals("v[1]", text("[v«tab»1"))
        assertEquals("sqrt(2)", text("\\2"))
        assertEquals("-x", text("-x"))
        // `:` on nothing: a definition with its name still to type.
        assertEquals("x := 2", text(":x«tab»2"))
    }

    @Test
    fun `programs start anywhere a value can`() {
        // On an empty spot, `]`, `}` and the keywords make `■ := program`, the cursor inside it.
        // An empty `if`: the value first, as it is drawn (`■ if ■`), then Tab to the condition.
        assertEquals("? :=\n  1 if x > 0", text("}1«tab»x>0"))
        assertEquals("p :=\n  1 if x > 0\n  ?", text("p:]if«space»1«tab»x>0"))
        assertEquals("f :=\n  for i in 1..3\n    ?", text("for«space»i«tab»1;3«home»f"))
        assertEquals("f(x) :=\n  x if x > 0\n  0 otherwise", text("f(x):x}x>0]otherwise«space»0"))
        assertEquals("y :=\n  a <- 2\n  ?", text("y:a{2]"))
        // The toolbar's keyword buttons: in an empty slot, and below a line that says something.
        val f = Editor.keyword(typed("f(x):"), "while")!!
        assertEquals("f(x) :=\n  while x > 1\n    x", typed("x>1«tab»x", f).text)
        val g = Editor.keyword(typed("g:1]"), "otherwise")!!
        assertEquals("g :=\n  1\n  2 otherwise", typed("2", g).text)
        val h = Editor.keyword(typed("h:1]2"), "return")!!
        assertEquals("h :=\n  1\n  2\n  return 3", typed("3", h).text)
        assertEquals("given", Editor.keyword(EditState.empty(), "given")!!.text)
        assertNull(Editor.keyword(typed("x+1"), "for"), "not in the middle of an expression")
    }

    @Test
    fun `closing parentheses and symbolic evaluation`() {
        assertEquals("sqrt(8)/2", text("sqrt(8)/2"))
        assertEquals("(x + 1)^2 -> expand -> ?", text("(x+1)^2«ctrl-shift-.»expand"))
        assertEquals("diff(x^3, x) -> ?", text("diff(x^3,x«ctrl-.»"))
        assertEquals("x -> ?", text("x«ctrl-shift-.»«bs»"))
    }

    @Test
    fun `given, for a solve block`() {
        assertEquals("given", text("given«space»"))
        assertEquals("?", text("given«space»«bs»"))
        assertEquals("given + 1", text("given+1"))
    }

    @Test
    fun `matrices`() {
        val s = Editor.matrix(typed("M:"), 2, 2)!!
        assertEquals("M := [?, ?; ?, ?]", s.text)
        assertEquals("M := [1, 2; 3, 4]", typed("1«tab»2«tab»3«tab»4", s).text)
        assertNull(Editor.matrix(typed("x"), 2, 2), "only in an empty slot")
    }

    @Test
    fun `a dot after a module picks a function, and completion fills it in`() {
        Editor.holdsModule = { it == Id("bt") }
        try {
            val s = typed("y:bt.mo")
            assertEquals("y := bt.mo", s.text)
            assertEquals(Id("bt") to "mo", Editor.memberAt(s))
            val done = Editor.complete(s, "moment")!!
            assertEquals("y := bt.moment", done.text)
            assertEquals("y := bt.moment(12)", typed("(12", done).text)
            // Straight after the dot, the slot is empty and prints as such.
            assertEquals("y := bt.?", typed("y:bt.").text)
            // After any other name, `.` starts a subscript, as before.
            assertEquals("y := x_a", typed("y:x.a").text)
        } finally {
            Editor.holdsModule = { false }
        }
    }

    @Test
    fun `resizing a matrix, Insert and Delete`() {
        val full = typed("1«tab»2«tab»3«tab»4", Editor.matrix(typed("M:"), 2, 2)!!)
        // The cursor is in the 4: rows go below it, columns to its right.
        val grown = Editor.resizeMatrix(full, 1, 1, insert = true)!!
        assertEquals("M := [1, 2, ?; 3, 4, ?; ?, ?, ?]", grown.text)
        assertEquals("M := [1, 2, ?; 3, 45, ?; ?, ?, ?]", typed("5", grown).text, "the cursor stays in its cell")
        // From the first cell: one column after it, then its row deleted.
        val first = typed("1", Editor.matrix(typed("M:"), 2, 2)!!)
        assertEquals("M := [1, ?, ?; ?, ?, ?]", Editor.resizeMatrix(first, 0, 1, insert = true)!!.text)
        assertEquals("M := [?, ?]", Editor.resizeMatrix(first, 1, 0, insert = false)!!.text)
        // Deleting everything leaves an empty slot; outside a matrix there is nothing to resize.
        assertEquals("M := ?", Editor.resizeMatrix(first, 2, 2, insert = false)!!.text)
        assertNull(Editor.resizeMatrix(typed("x"), 1, 0, insert = true))
    }

    @Test
    fun `what the editor makes always parses back`() {
        for (keys in listOf("M:q*L^2/8=kN*m", "a+b«space»«space»*c", "x:1.5e-3", "-x+1", "y:x!=1", "f(x):x^2", "i:0,2;10", "F«ctrl-b»+1")) {
            val s = typed(keys)
            assertEquals(s.statement, Parser.parseStatement(s.text), keys)
        }
    }
}
