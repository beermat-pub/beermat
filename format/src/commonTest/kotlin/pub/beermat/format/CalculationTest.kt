package pub.beermat.format

import pub.beermat.engine.display.Rounding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculationTest {

    private fun sheet(text: String) = WorksheetText.read(text.trimMargin())

    private fun annotations(text: String) = Calculation.of(sheet(text)).lines.map { it.annotation }

    @Test
    fun `statements run in file order across fences`() {
        val sheet = sheet(
            """
            |```calc
            |L := 6 m
            |q := 12 kN/m
            |```
            |Some text.
            |```calc
            |M_max := q*L^2/8 = ? kN*m
            |M_max = ?
            |```
            |""",
        )
        assertEquals(
            listOf(null, null, Annotation.Result("54 kN*m"), Annotation.Result("54 kN*m")),
            Calculation.of(sheet).lines.map { it.annotation },
        )
    }

    @Test
    fun `syntax and evaluation errors become error annotations`() {
        val a = annotations(
            """
            |```calc
            |x := 2 +
            |y := x + 1 = ?
            |z := 1 m + 1 s
            |```
            |""",
        )
        assertTrue((a[0] as Annotation.Error).message.startsWith("incomplete expression"))
        assertEquals(Annotation.Error("x has an error: incomplete expression: use '?' for an empty slot (column 9)"), a[1])
        assertTrue((a[2] as Annotation.Error).message.startsWith("units do not match"))
    }

    @Test
    fun `frontmatter and block settings`() {
        val text = """
            |---
            |result:
            |  significant: 3
            |tolerance: 0.1
            |---
            |```calc
            |a := 1234.567 = ?
            |1.05 == 1 = ?
            |```
            |```calc {decimals=1}
            |a = ?
            |```
            |```calc {strict}
            |a = ?
            |```
            |"""
        assertEquals(
            listOf(Annotation.Result("1235"), Annotation.Result("1.00"), Annotation.Result("1234.6"), Annotation.Result("1230")),
            annotations(text),
        )
        assertEquals(Rounding.Significant(3), Settings.of(sheet(text)).format.rounding)
    }

    @Test
    fun `programs span their indented lines`() {
        val text = "```calc\nf(x) :=\n  -x if x < 0\n  x otherwise\nf(-2) = ?\ng(x) :=\n  y <- x +\n  y\ng(1) = ?\n```\n"
        val calc = Calculation.of(WorksheetText.read(text))
        assertEquals(listOf("f(x) :=\n  -x if x < 0\n  x otherwise", "f(-2) = ?", "g(x) :=\n  y <- x +\n  y", "g(1) = ?"), calc.lines.map { it.statement })
        assertEquals(Annotation.Result("2"), calc.lines[1].annotation)
        // A syntax error inside a program is reported on its own line of the file.
        assertEquals(Problem(7, "incomplete expression: use '?' for an empty slot (program line 1, column 11)"), Check.of(WorksheetText.read(text))[0])
        assertEquals(
            "```calc\nf(x) :=\n  -x if x < 0\n  x otherwise\nf(-2) = ?   #= 2\n" +
                "g(x) :=     #! incomplete expression: use '?' for an empty slot (program line 1, column 11)\n  y <- x +\n  y\n" +
                "g(1) = ?    #! g has an error: incomplete expression: use '?' for an empty slot (program line 1, column 11)\n```\n",
            WorksheetText.write(calc.written()).substringAfter("---\n").substringAfter("---\n"),
        )
    }

    @Test
    fun `origin sets the first index`() {
        assertEquals(listOf(null, Annotation.Result("10")), annotations("---\norigin: 1\n---\n```calc\nv := [10; 20]\nv[1] = ?\n```\n"))
    }

    @Test
    fun `every setting in the manual's reference is one the sheet reads`() {
        for (doc in Settings.reference) {
            val yaml = if ('.' in doc.key) doc.key.split('.').let { (a, b) -> "$a: {$b: 1}" } else "${doc.key}: 1"
            val problems = Settings.of(sheet("---\n$yaml\n---\n")).problems
            assertTrue(problems.none { "unknown setting" in it }, "${doc.key}: $problems")
        }
        assertEquals(Settings.reference.size, Settings.reference.map { it.key }.toSet().size)
    }

    @Test
    fun `the sheet's multiplication style`() {
        assertEquals(pub.beermat.engine.syntax.Multiplication.DOT, Settings.of(sheet("---\nbeermat: 1\n---\n")).multiplication)
        assertEquals(pub.beermat.engine.syntax.Multiplication.SPACE, Settings.of(sheet("---\nmultiplication: space\n---\n")).multiplication)
        assertEquals(listOf("multiplication: expected dot or space, not 'cross'"), Settings.of(sheet("---\nmultiplication: cross\n---\n")).problems)
    }

    @Test
    fun `a disabled region is shown but not evaluated`() {
        val text = "```calc\na := 1\n```\n\n```calc {disabled}\na := 5 = ?\n```\n\n```calc\na = ?\n```\n"
        val calc = Calculation.of(sheet(text))
        // Its line keeps its place, with no result; what follows still sees a = 1.
        assertEquals(listOf(null, null, Annotation.Result("1")), calc.lines.map { it.annotation })
        assertEquals("a := 5 = ?", calc.lines[1].statement)
    }

    @Test
    fun `headers and footers`() {
        val s = Settings.of(sheet("---\ntitle: Beam\nheader: {left: \"{title}\", right: \"Page {page} of {pages}\"}\nfooter: Checked by DC\n---\n"))
        assertEquals(listOf("Beam", "", "Page 2 of 3"), s.header!!.filled(2, 3, s.title))
        assertEquals(listOf("", "Checked by DC", ""), s.footer!!.filled(1, 1, s.title))
        assertEquals(listOf("", "2026-10-29", ""), PageText(centre = "{date}").filled(1, 1, null, "2026-10-29"))
        assertEquals(listOf("unknown setting: header.middle"), Settings.of(sheet("---\nheader: {middle: x}\n---\n")).problems)
    }

    @Test
    fun `engineering prefixes, on unless the sheet or a region turns them off`() {
        val body = "```calc\nM := 54000 N*m = ?\n```\n```calc {prefixes=true}\nM = ?\n```\n"
        assertEquals(listOf(Annotation.Result("54 kN*m"), Annotation.Result("54 kN*m")), annotations(body))
        assertEquals(
            listOf(Annotation.Result("54000 N*m"), Annotation.Result("54 kN*m")),
            annotations("---\nresult:\n  prefixes: false\n---\n$body"),
        )
    }

    @Test
    fun `angle and currency as dimensions, from the frontmatter`() {
        val body = "```calc\nθ := asin(0.5) = ?\ncost := 120 AUD/m^3*4 m^3 = ?\n```\n"
        assertEquals(
            listOf(Annotation.Result("30 deg"), Annotation.Result("480 AUD")),
            annotations("---\nangle: dimension\ncurrency: AUD\n---\n$body"),
        )
        assertEquals(Annotation.Result("0.524 rad"), annotations("---\nangle: dimension\nangle_display: rad\ncurrency: AUD\n---\n$body")[0])
        assertEquals(Annotation.Result("0.524"), annotations("---\ncurrency: AUD\n---\n$body")[0])
        assertEquals(listOf("angle: expected dimension or none, not 'yes'"), Settings.of(sheet("---\nangle: yes\n---\n")).problems)
    }

    @Test
    fun `setting problems`() {
        val settings = Settings.of(sheet("---\nunits: USCS\nresult:\n  decimals: -1\ncolour: red\n---\n"))
        assertEquals(
            listOf(
                "units: 'USCS' is not supported yet; results are shown in SI",
                "result.decimals: expected a whole number of at least 0, not '-1'",
                "unknown setting: colour",
            ),
            settings.problems,
        )
    }

    @Test
    fun `writing results caches them and makes the sheet current`() {
        val sheet = sheet(
            """
            |---
            |title: t
            |---
            |```calc
            |x := 3 m
            |
            |x*2 = ? mm
            |y = ?
            |```
            |""",
        )
        assertEquals(listOf(Problem(8, "y is not defined")), Check.of(sheet), "no cache yet: nothing stale")
        val written = Calculation.of(sheet).written()
        val text = WorksheetText.write(written)
        assertTrue(text.startsWith("---\ntitle: t\nresults: ${WorksheetText.inputHash(sheet)}\n---\n"), text)
        assertTrue(text.endsWith("x := 3 m\n\nx*2 = ? mm   #= 6000 mm\ny = ?        #! y is not defined\n```\n"), text)

        // Errors are still reported, but nothing is stale.
        assertEquals(listOf(Problem(9, "y is not defined")), Check.of(WorksheetText.read(text)))

        // Writing again changes nothing.
        assertEquals(text, WorksheetText.write(Calculation.of(WorksheetText.read(text)).written()))

        // An edit makes the whole cache stale.
        val edited = WorksheetText.read(text.replace("x := 3 m", "x := 4 m"))
        assertTrue(Check.of(edited).any { it.message.startsWith("cached results are stale") })

        // A cached result that the engine no longer agrees with, under a matching hash.
        val tampered = WorksheetText.read(text.replace("#= 6000 mm", "#= 5000 mm"))
        assertEquals(
            Problem(8, "cached '#= 5000 mm', but the sheet now gives '#= 6000 mm'"),
            Check.of(tampered)[1],
        )
    }
}
