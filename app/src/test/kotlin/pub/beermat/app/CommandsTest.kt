package pub.beermat.app

import pub.beermat.app.editor.Key
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.app.page.Session
import pub.beermat.engine.syntax.Printer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Toolbar buttons do what their keystrokes do. */
class CommandsTest {

    private fun command(name: String) = Commands.palettes.flatMap { it.commands }.single { it.name == name }

    private fun Session.type(text: String) = text.forEach { assertTrue(key(Key.Type(it))) }

    private fun Session.statementText() = Printer.print(editing!!.state.statement)

    @Test
    fun `every command has a label, a name and its keys`() {
        val all = Commands.palettes.flatMap { it.commands }
        assertTrue(all.size > 50)
        assertTrue(all.all { it.label.isNotBlank() && it.name.isNotBlank() && it.keys.isNotBlank() })
    }

    @Test
    fun `buttons edit as their keys do`() {
        val s = Session("", MathLayout(SkiaMath, 12f))
        s.type("x:a")
        assertTrue(command("Fraction").run(s))
        s.type("b")
        assertEquals("x := a/b", s.statementText())
        assertTrue(command("Power").run(s))
        s.type("2")
        assertEquals("x := a/b^2", s.statementText())
        s.leave()

        s.click(s.format.margin + 1f, s.format.margin + 120f)
        s.type("f(n):")
        assertTrue(command("Add program line").run(s))
        assertTrue(command("For loop").run(s))
        assertTrue(s.statementText().startsWith("f(n) :=\n  for ? in ?"), s.statementText())
        s.leave()

        s.click(s.format.margin + 1f, s.format.margin + 240f)
        assertTrue(command("Solve block").run(s))
        assertEquals("given", s.statementText())
        assertTrue(command("Bold name (a different variable)").run(s).not(), "nothing to make bold on a given line")
    }
}
