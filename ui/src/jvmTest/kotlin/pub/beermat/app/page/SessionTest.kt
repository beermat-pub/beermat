package pub.beermat.app.page

import pub.beermat.app.editor.Key
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import pub.beermat.format.MathRegion
import pub.beermat.format.Regions
import pub.beermat.format.TextRegion
import pub.beermat.format.WorksheetText
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {

    private val math = MathLayout(SkiaMath, 12f)

    private fun Session.type(text: String) = text.forEach { assertTrue(key(Key.Type(it)), "'$it' in ${copyText()}") }

    @Test
    fun `typing at the crosshair makes a region, saved with its result`() {
        val s = Session("", math)
        s.type("x:3m")
        s.enter()
        assertNull(s.editing)
        assertEquals(0f to 18f, s.crosshair)
        s.type("x=mm")
        s.enter()
        val saved = s.save()
        assertTrue("```calc {#r1 x=0 y=0}\nx := 3 m\n```" in saved, saved)
        assertTrue("```calc {#r2 x=0 y=18}\nx = ? mm   #= 3000 mm\n```" in saved, saved)
        assertTrue(saved.startsWith("---\nresults: "), saved)
    }

    @Test
    fun `@ at the crosshair inserts a plot that draws, open for editing`() {
        val s = Session("", math)
        s.type("@")
        assertTrue(s.textEditing != null)
        val plot = s.layout.calculation.plots.single()
        assertTrue(plot.problems.isEmpty(), plot.problems.toString())
        assertTrue("```plot {#p1 x=0 y=0}\nx: 0 .. 2*π" in s.save(), s.save())
    }

    @Test
    fun `a result's format and unit, from the Result Format dialog's settings`() {
        val s = Session("```calc {#r1 x=0 y=0}\nM := 54321.25 N*m = ?\n```\n", math)
        val sv = (s.layout.views.single() as MathView).statements.single()
        // On the result (the right end of the statement), not on the name.
        val (ox, oy) = s.layout.origin(s.layout.views.single(), s.layout.pieces[0].single())
        assertEquals(0 to 0, s.resultAt(ox + sv.box.width - 2f, oy + sv.baseline - 3f))
        assertNull(s.resultAt(ox + 2f, oy + sv.baseline - 3f))
        assertEquals(Session.ResultSettings(false, 3, false, 6, ""), s.resultSettings(0, 0))

        s.setResultSettings(0, 0, Session.ResultSettings(true, 3, false, 6, "kN*m"))
        val saved = s.save()
        assertTrue("```calc {#r1 x=0 y=0 significant=3}\nM := 54321.25 N*m = ? kN*m   #= 54.3 kN*m\n```" in saved, saved)
        assertEquals(Session.ResultSettings(true, 3, false, 6, "kN*m"), s.resultSettings(0, 0))

        // Back to the sheet's format: the attributes go, the unit stays.
        s.setResultSettings(0, 0, null)
        assertTrue("```calc {#r1 x=0 y=0}\nM := 54321.25 N*m = ? kN*m   #= 54.321 kN*m\n```" in s.save(), s.save())
        s.undo()
        assertTrue("significant=3" in s.save())
    }

    @Test
    fun `an emptied region disappears, and undo brings things back`() {
        val s = Session("", math)
        s.type("ab")
        s.key(Key.Backspace)
        s.key(Key.Backspace)
        s.leave()
        assertEquals(emptyList(), s.regions)
        s.undo()
        assertEquals(1, s.regions.size)
        s.undo()
        s.undo()
        assertEquals(emptyList(), s.regions)
    }

    @Test
    fun `regions are kept in evaluation order as they move`() {
        val s = Session("```calc {#r1 x=0 y=0}\na := 1\n```\n\n```calc {#r2 x=0 y=40}\nb := a + 1 = ?\n```\n", math)
        assertEquals("2", (s.layout.views[1] as MathView).statements[0].outcome?.annotation?.let { (it as pub.beermat.format.Annotation.Result).text })
        s.move(1, 200f, -40f)
        // Now b is beside a, to its right: still after it.
        assertEquals(listOf("r1", "r2"), s.regions.map { it.attributes.id })
        s.move(0, 0f, 60f)
        assertEquals(listOf("r2", "r1"), s.regions.map { it.attributes.id })
        assertTrue("a is not defined" in s.save())
    }

    @Test
    fun `a rectangle selects regions, which move and delete together`() {
        val sheet = "```calc {#r1 x=0 y=0}\na := 1\n```\n\n```calc {#r2 x=120 y=0}\nb := 2\n```\n\n```calc {#r3 x=0 y=120}\nc := 3\n```\n"
        val s = Session(sheet, math)
        // Screen points: the page's margin is 54, so the sheet's (0, 0) is at (54, 54).
        // Left to right, only what is wholly inside: this box cuts through b (from x = 174).
        s.selectIn(40f, 40f, 190f, 90f)
        assertEquals(setOf(0), s.selected)
        // Right to left, the same box takes everything it touches.
        s.selectIn(190f, 40f, 40f, 90f)
        assertEquals(setOf(0, 1), s.selected)
        s.move(s.selected, 0f, 200f)
        assertEquals(listOf("r3", "r1", "r2"), s.regions.map { it.attributes.id })
        assertEquals(listOf(0f to 120f, 0f to 198f, 120f to 198f), s.regions.map { it.x to it.y }, "snapped to the 6 pt grid")
        assertEquals(setOf(1, 2), s.selected, "still selected after the move")
        // Ctrl+click takes one out; Delete removes the rest; undo brings it back.
        s.toggle(2)
        assertEquals(setOf(1), s.selected)
        assertTrue(s.deleteSelected())
        assertEquals(listOf("r3", "r2"), s.regions.map { it.attributes.id })
        s.undo()
        assertEquals(3, s.regions.size)
        // A click anywhere ends the selection.
        s.selectIn(0f, 0f, 600f, 800f)
        assertEquals(3, s.selected.size)
        s.click(500f, 700f)
        assertEquals(emptySet(), s.selected)
    }

    @Test
    fun `an action that fails is undone, and the sheet carries on`() {
        val s = Session("", math)
        s.type("x:3")
        val before = s.copyText()
        val result = s.guard(false) {
            s.type("+1")
            error("a bug")
        }
        assertEquals(false, result)
        assertEquals(before, s.copyText(), "back to how it was")
        assertNotNull(s.lastError)
        s.type("*2")
        assertEquals("x := 3*2", s.copyText())
        assertTrue(s.guard(false) { true })
        assertNull(s.lastError, "cleared by the next action that works")
    }

    @Test
    fun `division style - Ctrl+slash on the line, and the right-click choices`() {
        val s = Session("", math)
        s.type("y:a+b")
        s.key(pub.beermat.app.editor.Key.Ctrl('/'))
        s.type("c")
        assertEquals("y := a + b÷c", s.copyText())
        assertTrue(s.edit { pub.beermat.app.editor.Editor.division(it, pub.beermat.engine.syntax.Division.STACKED) })
        assertEquals("y := a + b/c", s.copyText())
        // A sum typed into a fraction needs no parentheses there; going on the line adds them.
        val t = Session("", math)
        t.type("z:x/2")
        t.vertical(up = true)
        t.type("+1")
        assertTrue(t.edit { pub.beermat.app.editor.Editor.division(it, pub.beermat.engine.syntax.Division.SLASH) })
        assertEquals("z := (x + 1)∕2", t.copyText())
    }

    @Test
    fun `multiplication style - the sheet's default, and one operator's own`() {
        val s = Session("", math)
        s.type("y:q*L+a*2")
        // The cursor is on 2: its multiplication can be a dot, but not a thin space (no name on the right).
        assertTrue(s.edit { pub.beermat.app.editor.Editor.multiplication(it, pub.beermat.engine.syntax.Multiplication.DOT) })
        assertEquals("y := q*L + a·2", s.copyText())
        assertEquals(false, s.edit { pub.beermat.app.editor.Editor.multiplication(it, pub.beermat.engine.syntax.Multiplication.SPACE) })
        s.leave()
        s.setMultiplication(pub.beermat.engine.syntax.Multiplication.SPACE)
        assertTrue("multiplication: space" in s.save())
        s.undo()
        assertTrue("multiplication:" !in s.save())
    }

    @Test
    fun `a new sheet starts empty and the toolbar's keywords make programs`() {
        val s = Session("", math)
        assertTrue(s.edit { pub.beermat.app.editor.Editor.keyword(it, "if") })
        s.type("x>0")
        assertEquals("? :=\n  x > 0 if ?", s.copyText())
    }

    @Test
    fun `a click puts the cursor on what was clicked`() {
        val s = Session("```calc {#r1 x=0 y=0}\nlonger := 12 + 345\n```\n", math)
        val v = s.layout.views[0] as MathView
        val box = v.statements[0].box
        val (ox, oy) = s.layout.origin(v, s.layout.pieces[0][0])
        // Near the right end of the statement: on 345.
        s.click(ox + box.width - 2f, oy + v.statements[0].baseline - 3f)
        val e = assertNotNull(s.editing)
        assertEquals(listOf(1, 1), e.state.cursor.path)
        s.key(Key.Type('6'))
        s.leave()
        assertEquals("longer := 12 + 3456", (s.regions[0] as MathRegion).lines.single().source)
    }

    @Test
    fun `Enter makes room below the crosshair, Backspace and Delete take blank lines out`() {
        val s = Session("```calc {#a x=0 y=0}\na := 1\n```\n```calc {#b x=0 y=120}\nb := 2\n```\n", math)
        fun yOf(name: String) = s.layout.views.single { it.region.attributes.id == name }.y
        s.click(s.format.margin + 0f, s.format.margin + 60f)
        val (_, y) = s.crosshair!!
        s.enter()
        assertEquals(y + Session.LINE, s.crosshair!!.second)
        assertEquals(120f + Session.LINE, yOf("b"), "the region below moves down with the crosshair")
        assertEquals(0f, yOf("a"), "the region above stays")
        s.enter()
        assertEquals(120f + 2 * Session.LINE, yOf("b"))
        // Backspace: the blank line above the crosshair goes, and the crosshair and the region below come up.
        assertTrue(s.removeLine(forward = false))
        assertEquals(y + Session.LINE, s.crosshair!!.second)
        assertEquals(120f + Session.LINE, yOf("b"))
        // Delete: the blank line below the crosshair goes; the crosshair stays.
        assertTrue(s.removeLine(forward = true))
        assertEquals(y + Session.LINE, s.crosshair!!.second)
        assertEquals(120f, yOf("b"))
        // Not where a region is: nothing moves, and no beep.
        s.click(s.format.margin + 0f, s.format.margin + 120f + Session.LINE)
        assertTrue(s.removeLine(forward = false))
        assertEquals(120f, yOf("b"))
        // One undo step each.
        s.undo()
        assertEquals(120f + Session.LINE, yOf("b"))
    }

    @Test
    fun `big arrays are tables of 20 rows, which the wheel scrolls`() {
        val rows = (1..300).joinToString("; ")
        val s = Session("```calc {#a x=0 y=0}\ni := 0..999\nx[i] := i\nx = ?\n```\n```calc {#b x=0 y=900}\nK := [$rows]\n```\n", math)
        val result = s.layout.views[0] as MathView
        val literal = s.layout.views[1] as MathView
        // 20 rows high, not a thousand: each table well under a page.
        assertTrue(result.height < 700f, "${result.height}")
        assertTrue(literal.height < 700f, "${literal.height}")
        // The wheel over the result scrolls its table, not the page; over empty paper it doesn't.
        val (ox, oy) = s.layout.origin(result, s.layout.pieces[0][0])
        val k = result.statements.indices.last()
        val sv = result.statements[k]
        val over = oy + (sv.top + sv.bottom) / 2
        assertTrue(s.scrollTable(ox + 20f, over, 3, 0))
        assertTrue(s.scrollTable(ox + 20f, over, 10_000, 0), "clamped at the end")
        assertEquals(false, s.scrollTable(s.format.margin + 600f, s.format.margin + 600f, 3, 0))
        // And over the written one.
        val (lx, ly) = s.layout.origin(literal, s.layout.pieces[1][0])
        assertTrue(s.scrollTable(lx + 20f, ly + literal.height / 2, 3, 0))
    }

    @Test
    fun `F1 on a built-in function opens its page in the manual`() {
        val s = Session("", math)
        s.type("y:sqrt(2")
        assertEquals("reference/functions/numbers/#fn-sqrt", s.helpPage())
        val t = Session("", math)
        t.type("y:q")
        assertEquals(null, t.helpPage())
    }

    @Test
    fun `a word and a space in a new region make it text`() {
        val s = Session("", math)
        s.type("the")
        assertTrue(s.key(Key.Space))
        assertIs<TextRegion>(s.regions.single())
        assertEquals(0 to null, s.textEditing)
        assertEquals("the ", s.textOf(0, null))
        // A keyword still makes its structure, and a name in a statement stays math.
        val k = Session("", math)
        k.type("if")
        k.key(Key.Space)
        assertIs<MathRegion>(k.regions.single())
        val m = Session("", math)
        m.type("x:a")
        m.key(Key.Space)
        assertIs<MathRegion>(m.regions.single())
    }

    @Test
    fun `a click on empty paper moves the crosshair, snapped to the grid`() {
        val s = Session("", math)
        s.click(s.format.margin + 100f, s.format.margin + 50f)
        assertEquals(102f to 48f, s.crosshair)
        s.type("\"")
        assertIs<TextRegion>(s.regions.single())
        s.setText(0, null, "Some *text*.")
        assertTrue("<!-- #t1 x=102 y=48 -->\nSome *text*." in s.save())
    }

    @Test
    fun `arrows move the crosshair or the selection a grid step, and Enter moves the crosshair down a line`() {
        val s = Session("```calc {#r1 x=0 y=0}\na := 1\n```\n", math)
        s.click(s.format.margin + 102f, s.format.margin + 48f)
        assertTrue(s.nudge(1, 0))
        assertTrue(s.nudge(0, -1))
        assertEquals(108f to 42f, s.crosshair)
        s.enter()
        assertEquals(108f to 60f, s.crosshair)
        s.selectIn(0f, 0f, 600f, 800f)
        assertTrue(s.nudge(0, 1))
        assertEquals(0f to 6f, s.regions.single().let { it.x to it.y })
        assertEquals(setOf(0), s.selected, "still selected, for the next step")
    }

    @Test
    fun `Tab goes to the next placeholder, then to the next math region`() {
        val s = Session("```calc {#r1 x=0 y=0}\na := 1\n```\n\n```calc {#r2 x=0 y=60}\nb := 2\n```\n", math)
        s.click(s.format.margin + 200f, s.format.margin + 30f)
        assertTrue(s.tab(back = false))
        assertEquals(1, s.editing?.region, "from the crosshair, the first region below it")
        assertTrue(!s.tab(back = false), "nothing after the last region")
        assertTrue(s.tab(back = true))
        assertEquals(0, s.editing?.region)
        // A placeholder comes first.
        s.leave()
        s.click(s.format.margin + 200f, s.format.margin + 120f)
        s.type("c:")
        assertTrue(s.tab(back = false))
        assertEquals("c := ?", s.copyText())
        assertEquals(2, s.editing?.region, "still on its placeholder")
        s.type("5")
        assertTrue(s.tab(back = true))
        assertEquals(1, s.editing?.region, "filled: on to the region before")
    }

    @Test
    fun `the text of a sheet with new regions reads back as the same sheet, before any save`() {
        val s = Session("", math)
        s.click(s.format.margin + 0f, s.format.margin + 0f)
        s.type("\"")
        s.setText(0, null, "A note.")
        s.click(s.format.margin + 0f, s.format.margin + 60f)
        s.type("x:3m")
        s.enter()
        val text = s.text()
        assertTrue("<!-- #t1 x=0 y=0 -->\nA note." in text, text)
        val again = Session(text, math)
        assertEquals(s.regions.map { it::class to it.y }, again.regions.map { it::class to it.y })
        assertEquals(listOf(null, null), s.regions.map { it.attributes.id }, "text() gives ids in its text only")
    }

    @Test
    fun `the examples load, place every region, and save without losing anything`() {
        for (name in listOf("beam.bbm", "beam-table.bbm")) {
            val text = File("../examples/$name").readText()
            val s = Session(text, math)
            assertTrue(s.regions.all { it.x != null && it.y != null }, name)
            val before = Regions.of(WorksheetText.read(text)).filterIsInstance<MathRegion>().flatMap { r -> r.lines.map { it.source } }
            val after = Regions.of(WorksheetText.read(s.save())).filterIsInstance<MathRegion>().flatMap { r -> r.lines.map { it.source } }
            assertEquals(before, after, name)
            assertTrue(s.layout.views.filterIsInstance<MathView>().all { v -> v.statements.all { it.outcome?.annotation !is pub.beermat.format.Annotation.Error } }, name)
        }
    }

    @Test
    fun `up and down move between a numerator and a denominator`() {
        val s = Session("", math)
        s.type("x:a+b/c")
        assertEquals(listOf(1, 1, 1), s.cursor?.path)
        assertTrue(s.vertical(up = true))
        assertEquals(listOf(1, 1, 0), s.cursor?.path)
        assertTrue(s.vertical(up = false))
        assertEquals(listOf(1, 1, 1), s.cursor?.path)
        assertTrue(s.matrix(2, 1).not(), "a matrix only goes in an empty slot")
    }

    @Test
    fun `page breaks and separating regions`() {
        val s = Session("```calc {#r1 x=0 y=0}\na := 1\n```\n\n```calc {#r2 x=0 y=6}\nb := 2\n```\n", math)
        assertEquals(setOf(0, 1), s.layout.overlaps)
        s.separate()
        assertEquals(emptySet(), s.layout.overlaps)
        s.click(s.format.margin + 1f, s.format.margin + 200f)
        s.pageBreak()
        s.click(s.format.margin + 1f, s.format.margin + 210f)
        s.type("c:3")
        s.leave()
        val c = s.layout.views.first { it.region is MathRegion && (it.region as MathRegion).lines.single().source == "c := 3" }
        assertEquals(1, s.layout.pieces[c.index].single().page)
    }

    @Test
    fun `a module's functions - completion after the dot, and a tip over a call`() {
        val folder = kotlin.io.path.createTempDirectory("beermat-py").toFile()
        File(folder, "tools.py").writeText(
            """
            |def moment(q: "kN/m", L: "m") -> "kN*m":
            |    '''The largest bending moment.'''
            |    return q * L**2 / 8
            |
            |def mass(w, L):
            |    return w * L
            |""".trimMargin(),
        )
        val before = pub.beermat.format.PythonConsent.allowed
        pub.beermat.format.PythonConsent.allowed = { true }
        try {
            val sheet = "```calc {#r1 x=0 y=0}\nbt := use \"tools.py\"\n```\n\n```calc {#r2 x=0 y=40}\nM := bt.moment(1 kN/m, 2 m)\n```\n"
            val s = Session(sheet, math)
            s.base = folder
            s.relayout()
            // A tip somewhere over the call, and none far from it.
            val r = s.layout.views.first { it.region is MathRegion && "moment" in (it.region as MathRegion).lines.single().source }
            val (ox, oy) = s.layout.origin(r, s.layout.pieces[r.index].first())
            val tips = (0..80).flatMap { x -> (0..6).map { y -> s.tip(ox + x * 2f, oy + y * 2f) } }.filterNotNull()
            assertEquals(listOf("moment(q: 'kN/m', L: 'm') -> 'kN*m'", "The largest bending moment."), tips.firstOrNull())
            assertNull(s.tip(ox + 400f, oy + 300f))
            // Typing after the dot lists the functions that match; Down and Enter pick one.
            s.click(ox, oy + 80f)
            "y:bt.m".forEach { s.key(Key.Type(it)) }
            assertEquals(listOf("mass", "moment"), s.completion?.names)
            s.moveCompletion(1)
            s.acceptCompletion()
            assertEquals("y := bt.moment", s.editing?.state?.text)
        } finally {
            pub.beermat.format.PythonConsent.allowed = before
        }
    }
}
