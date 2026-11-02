package pub.beermat.xmcd

import pub.beermat.engine.syntax.Unparsed
import pub.beermat.format.Annotation
import pub.beermat.format.Calculation
import pub.beermat.format.WorksheetText
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmcdImportTest {

    private fun synthetic() = XmcdImport.import(javaClass.getResourceAsStream("/synthetic.xmcd")!!, "Synthetic")

    @Test
    fun `imports a synthetic worksheet`() {
        val imported = synthetic()
        assertEquals(
            """
            |---
            |beermat: 1
            |title: Synthetic
            |---
            |
            |<!-- #t1 x=6 y=3 -->
            |# A **synthetic** sheet
            |Inline math: `η > 0` here.
            |
            |```calc {#r1 x=6 y=10}
            |ORIGIN ::= 1
            |```
            |
            |```calc {#r2 x=6 y=30}
            |M_max := q*L^2/8
            |```
            |
            |```calc {#r3 x=6 y=50}
            |f(x) :=
            |  -x if x < 0
            |  x otherwise
            |```
            |
            |```calc {#r4 x=6 y=100}
            |K := [1, 2; 3, 4]
            |```
            |
            |```calc {#r5 x=6 y=150}
            |K[1, 2] = ?
            |```
            |
            |```calc {#r6 x=200 y=150 disabled}
            |s := sum(k^2, k, 1, 10)
            |```
            |
            |```calc {#r7 x=6 y=200}
            |`θ*`_Ω.fo := try 1/0 on error -1
            |```
            |
            |```calc {#r8 x=6 y=250}
            |p :=
            |  s <- 0
            |  for i in 0, 2..10
            |    s <- s + i
            |  s
            |```
            |
            |<!-- #t2 x=6 y=300 -->
            |*(A plot from the original worksheet: not imported yet.)*
            |""".trimMargin(),
            imported.text,
        )
        assertEquals(
            listOf("a plot region is not imported yet"),
            imported.warnings,
        )
    }

    @Test
    fun `multiplication styles, for the sheet and per operator`() {
        fun mult(style: String?) = """<ml:apply><ml:mult${style?.let { " style=\"$it\"" } ?: ""}/><ml:id>a</ml:id><ml:id>b</ml:id></ml:apply>"""
        val regions = listOf(null, "dot", "x", "no-space", "auto-select").mapIndexed { k, style ->
            """<region region-id="$k" left="0" top="${k * 20}"><math><ml:define><ml:id>v$k</ml:id>${mult(style)}</ml:define></math></region>"""
        }
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<worksheet xmlns="http://schemas.mathsoft.com/worksheet30" xmlns:ml="http://schemas.mathsoft.com/math30">
            |<settings><calculation><operators multiplication="thin-space"/></calculation></settings>
            |<regions>${regions.joinToString("")}</regions></worksheet>""".trimMargin()
        val text = XmcdImport.import(xml.byteInputStream(), "Styles").text
        assertTrue("multiplication: space\n" in text, text)
        assertEquals(listOf("v0 := a*b", "v1 := a·b", "v2 := a·b", "v3 := a b", "v4 := a*b"), text.lines().filter { it.startsWith("v") })
    }

    @Test
    fun `a division on the line is drawn ÷`() {
        fun div(inline: String?) = """<ml:apply><ml:div${inline?.let { " inline=\"$it\"" } ?: ""}/><ml:id>a</ml:id><ml:id>b</ml:id></ml:apply>"""
        val regions = listOf(null, "false", "true").mapIndexed { k, inline ->
            """<region region-id="$k" left="0" top="${k * 20}"><math><ml:define><ml:id>v$k</ml:id>${div(inline)}</ml:define></math></region>"""
        }
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<worksheet xmlns="http://schemas.mathsoft.com/worksheet30" xmlns:ml="http://schemas.mathsoft.com/math30">
            |<regions>${regions.joinToString("")}</regions></worksheet>""".trimMargin()
        val text = XmcdImport.import(xml.byteInputStream(), "Divisions").text
        assertEquals(listOf("v0 := a/b", "v1 := a/b", "v2 := a÷b"), text.lines().filter { it.startsWith("v") })
    }

    @Test
    fun `the imported sheet evaluates`() {
        val calc = Calculation.of(WorksheetText.read(synthetic().text))
        val annotations = calc.lines.mapNotNull { it.annotation }
        // ORIGIN is 1, so K[1, 2] is the first row, second column.
        assertTrue(Annotation.Result("2") in annotations, annotations.toString())
        val errors = annotations.filterIsInstance<Annotation.Error>()
        assertEquals(listOf(Annotation.Error("q is not defined")), errors)
    }

    /** Runs only where the private samples are present (they are never committed). */
    @Test
    fun `local samples import and parse back`() {
        val samples = File("samples").listFiles { f -> f.name.endsWith(".xmcd") } ?: return
        for (file in samples) {
            val imported = file.inputStream().use { XmcdImport.import(it, file.nameWithoutExtension) }
            val sheet = WorksheetText.read(imported.text)
            val calc = Calculation.of(sheet)
            val syntaxErrors = calc.lines.filter { it.parsed is Unparsed }
            val evalErrors = calc.lines.filter { it.parsed !is Unparsed && it.annotation is Annotation.Error }
            println("${file.name}: ${sheet.blocks.size} math regions, ${imported.warnings.size} warnings, ${syntaxErrors.size} syntax errors, ${evalErrors.size} evaluation errors")
            imported.warnings.forEach { println("  warning: $it") }
            syntaxErrors.take(10).forEach { println("  syntax: ${it.statement.lineSequence().first()}: ${it.annotation}") }
            evalErrors.groupBy { (it.annotation as Annotation.Error).message.substringBefore(":") }.entries
                .sortedByDescending { it.value.size }.take(15).forEach { (m, l) -> println("  eval x${l.size}: $m") }
            assertTrue(syntaxErrors.isEmpty(), "${file.name}: the imported text must parse back")
        }
    }

    /** A worksheet in an older `.xmcd` file's namespaces (`worksheet20`, `math20`), with [body] as its regions. */
    private fun older(body: String) = XmcdImport.import(
        """<?xml version="1.0" encoding="utf-8"?>
        |<worksheet xmlns="http://schemas.mathsoft.com/worksheet20" xmlns:ml="http://schemas.mathsoft.com/math20"><regions>$body</regions></worksheet>""".trimMargin().byteInputStream(),
        "Older",
    )

    private fun math(left: Int, top: Int, alignY: Int?, ml: String) =
        """<region left="$left" top="$top"${alignY?.let { " align-y=\"$it\"" } ?: ""}><math><ml:define xmlns:ml="http://schemas.mathsoft.com/math20">$ml</ml:define></math></region>"""

    @Test
    fun `older versions, areas inside regions, baseline order and names defined together`() {
        val body = math(258, 20, 42, """<ml:id>A</ml:id><ml:apply><ml:id>f</ml:id><ml:id>OD</ml:id></ml:apply>""") +
            math(30, 27, 36, """<ml:id>OD</ml:id><ml:provenance><ml:comment>copied</ml:comment><ml:real>12</ml:real></ml:provenance>""") +
            """<region left="0" top="60"><area>""" +
            math(6, 70, null, """<ml:function><ml:id>f</ml:id><ml:boundVars><ml:id>x</ml:id></ml:boundVars></ml:function><ml:apply><ml:mult/><ml:real>2</ml:real><ml:id>x</ml:id></ml:apply>""") +
            """</area></region>""" +
            math(6, 100, null, """<ml:matrix rows="2" cols="1"><ml:id>a</ml:id><ml:id>b</ml:id></ml:matrix><ml:matrix rows="2" cols="1"><ml:real>1</ml:real><ml:real>2</ml:real></ml:matrix>""")
        val imported = older(body)
        assertEquals(emptyList(), imported.warnings)
        val statements = imported.text.lines().filter { ":=" in it }
        // OD's baseline (36) is above A's (42), though A's top is higher: OD comes first.
        assertEquals(listOf("OD := 12", "A := f(OD)", "f(x) := 2*x", "[a; b] := [1; 2]"), statements)
    }

    @Test
    fun `numbers in another base get their prefix`() {
        val body = math(6, 20, null, """<ml:id>h</ml:id><ml:real base="16">1F</ml:real>""") +
            math(6, 40, null, """<ml:id>b</ml:id><ml:real base="2">-101.01</ml:real>""") +
            math(6, 60, null, """<ml:id>o</ml:id><ml:real base="8">17</ml:real>""")
        val statements = older(body).text.lines().filter { ":=" in it }
        assertEquals(listOf("h := 0x1F", "b := -0b101.01", "o := 0o17"), statements)
    }

    @Test
    fun `a function written as an operator takes its own arguments first`() {
        val body = math(6, 20, null, """<ml:id>y</ml:id><ml:apply fixity="infix"><ml:apply><ml:id>op</ml:id><ml:real>5</ml:real></ml:apply><ml:id>a</ml:id><ml:id>b</ml:id></ml:apply>""") +
            math(6, 40, null, """<ml:id>z</ml:id><ml:apply fixity="infix"><ml:id>op2</ml:id><ml:id>a</ml:id><ml:id>b</ml:id></ml:apply>""")
        val statements = older(body).text.lines().filter { ":=" in it }
        assertEquals(listOf("y := op(5, a, b)", "z := op2(a, b)"), statements)
    }

    @Test
    fun `a symbolic result evaluated numerically evaluates its expression`() {
        val eval = """<ml:eval xmlns:ml="http://schemas.mathsoft.com/math20"><ml:symEval><ml:apply><ml:mult/><ml:id>A</ml:id><ml:id>h</ml:id></ml:apply>""" +
            """<ml:command><ml:id>explicit</ml:id></ml:command><ml:symResult><ml:id>V</ml:id></ml:symResult></ml:symEval>""" +
            """<ml:unitOverride><ml:id>L</ml:id></ml:unitOverride></ml:eval>"""
        val text = older("""<region left="6" top="20"><math>$eval</math></region>""").text
        assertTrue("A*h = ? L" in text, text)
        // `return x` outside a program is x.
        val bare = older(math(6, 20, null, """<ml:id>F</ml:id><ml:return><ml:id>x</ml:id></ml:return>""")).text
        assertTrue("F := x" in bare, bare)
    }

    @Test
    fun `a gzipped xmcdz imports as its xmcd`() {
        val plain = javaClass.getResourceAsStream("/synthetic.xmcd")!!.readBytes()
        val gzipped = java.io.ByteArrayOutputStream().also { b -> java.util.zip.GZIPOutputStream(b).use { it.write(plain) } }.toByteArray()
        assertEquals(synthetic().text, XmcdImport.import(gzipped.inputStream(), "Synthetic").text)
    }
}
