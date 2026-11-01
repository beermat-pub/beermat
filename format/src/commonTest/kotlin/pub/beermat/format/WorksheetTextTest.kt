package pub.beermat.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorksheetTextTest {

    private val beam = """
        |---
        |beermat: 1
        |title: Beam check
        |---
        |
        |# Beam check
        |
        |<!-- #t1 x=40 y=40 w=500 -->
        |Simply supported beam.
        |
        |```calc {#r1 x=40 y=100}
        |L := 6 m
        |q := 12 kN/m
        |```
        |
        |```calc {#r2 x=40 y=160}
        |M_max := q*L^2/8 = ? kN*m
        |```
        |
        |```plot {#p1 x=320 y=100 w=300 h=200}
        |x: 0 m .. L
        |```
        |""".trimMargin()

    @Test
    fun `reads blocks, text and frontmatter`() {
        val sheet = WorksheetText.read(beam)
        assertEquals(listOf("beermat: 1", "title: Beam check"), sheet.frontmatter)
        assertEquals(2, sheet.blocks.size)
        val r1 = sheet.blocks[0]
        assertEquals(Attributes("r1", mapOf("x" to "40", "y" to "100")), r1.attributes)
        assertEquals(listOf("L := 6 m", "q := 12 kN/m"), r1.lines.map { it.source })
        assertEquals(listOf(12, 13), r1.lines.map { it.number })
        assertTrue(sheet.parts.last() is PlotBlock)
    }

    @Test
    fun `writing an unchanged sheet gives the same text`() {
        assertEquals(beam, WorksheetText.write(WorksheetText.read(beam)))
        assertEquals(beam, WorksheetText.write(WorksheetText.read(beam.replace("\n", "\r\n"))))
        assertEquals(beam, WorksheetText.write(WorksheetText.read("﻿" + beam)))
    }

    @Test
    fun `annotations are split off and aligned per fence`() {
        val text = "```calc\nx := 3 m = ?  #= 3 m\nlonger_name = ?#! x is not defined\n\ny := \"a #= b\"\n```\n"
        val block = WorksheetText.read(text).blocks.single()
        assertEquals(Annotation.Result("3 m"), block.lines[0].annotation)
        assertEquals("x := 3 m = ?", block.lines[0].source)
        assertEquals(Annotation.Error("x is not defined"), block.lines[1].annotation)
        assertNull(block.lines[3].annotation, "a #= inside a string is not an annotation")
        assertEquals(
            "```calc\nx := 3 m = ?      #= 3 m\nlonger_name = ?   #! x is not defined\n\ny := \"a #= b\"\n```\n",
            WorksheetText.write(WorksheetText.read(text)),
        )
    }

    @Test
    fun `calc fences inside other fences are not calc`() {
        val text = "````markdown\n```calc\nx := 1\n```\n````\n"
        val sheet = WorksheetText.read(text)
        assertEquals(0, sheet.blocks.size)
        assertEquals(text, WorksheetText.write(sheet))
    }

    @Test
    fun `the hash ignores annotations, alignment and the results line`() {
        val plain = WorksheetText.read("---\ntitle: t\n---\n```calc\nx := 2 = ?\n```\n")
        val cached = WorksheetText.read("---\ntitle: t\nresults: 0123456789abcdef\n---\n```calc\nx := 2 = ?      #= 2\n```\n")
        assertEquals(WorksheetText.inputHash(plain), WorksheetText.inputHash(cached))
        val edited = WorksheetText.read("---\ntitle: t\n---\n```calc\nx := 3 = ?\n```\n")
        assertNotEquals(WorksheetText.inputHash(plain), WorksheetText.inputHash(edited))
        assertEquals(16, WorksheetText.inputHash(plain).length)
    }

    @Test
    fun `attributes`() {
        assertEquals(
            Attributes("r3", mapOf("x" to "4", "strict" to null, "highlight" to "light yellow")),
            Attributes.parse("#r3 x=4 strict highlight=\"light yellow\""),
        )
    }
}
