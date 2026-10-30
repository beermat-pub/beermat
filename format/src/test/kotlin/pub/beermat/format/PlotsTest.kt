package pub.beermat.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlotsTest {

    private fun plot(text: String): PlotOutcome = Calculation.of(WorksheetText.read(text.trimMargin())).plots.single()

    private val beam = """
        |```calc
        |L := 6 m
        |q := 12 kN/m
        |M(x) := q*x*(L - x)/2
        |```
        |```plot {#p1 w=300 h=200}
        |x: 0 m .. L
        |samples: 7
        |traces:
        |  - y: M(x)
        |    unit: kN*m
        |    label: Moment
        |```
        |"""

    @Test
    fun `a sampled curve in display units`() {
        val p = plot(beam)
        assertEquals(emptyList(), p.problems)
        val s = p.data!!.series.single()
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0), s.xs)
        assertEquals(listOf(0.0, 30.0, 48.0, 54.0, 48.0, 30.0, 0.0), s.ys.map { Math.round(it * 1e9) / 1e9 })
        assertEquals("kN*m", s.unit)
        assertEquals("x [m]", p.data.x.label)
        assertEquals("Moment [kN*m]", p.data.y.label)
    }

    @Test
    fun `plots and their text round-trip`() {
        val sheet = WorksheetText.read(beam.trimMargin())
        assertEquals(1, sheet.plots.size)
        assertEquals("300", sheet.plots[0].attributes["w"])
        assertEquals(beam.trimMargin(), WorksheetText.write(sheet))
    }

    @Test
    fun `a plot sees only what is defined above it`() {
        val p = plot("```plot\nx: 0 .. 1\ntraces:\n  - y: a*x\n```\n```calc\na := 2\n```\n")
        assertEquals(listOf("trace 1 (a*x): a is not defined"), p.problems)
    }

    @Test
    fun `stepped ranges, data vectors, named variables and the right axis`() {
        val p = plot(
            """
            |```calc
            |i := 0..4
            |d[i] := i^2
            |```
            |```plot
            |x: t = 0, 1..4
            |traces:
            |  - y: t^2
            |  - y: d
            |    axis: right
            |```
            |""",
        )
        val (curve, data) = p.data!!.series
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), curve.xs)
        assertEquals(curve.ys, data.ys)
        assertTrue(data.right)
        assertEquals("t", p.data.x.label)
        assertEquals("d", p.data.y2!!.label)
    }

    @Test
    fun `problems are reported`() {
        assertEquals("the plot needs an x axis, such as 'x: 0 m .. L'", plot("```plot\ntraces: []\n```\n").problems.single())
        assertNull(plot("```plot\nx: [\n```\n").data)
        assertEquals(
            listOf("x_unit: 's' is not a unit of L"),
            plot("```plot\nx: 0 m .. 1 m\nx_unit: s\ntraces:\n  - y: x\n```\n").problems,
        )
    }

    @Test
    fun `title, log axes and tick steps`() {
        val p = plot(
            """
            |```plot
            |title: Frequency response
            |x: 1 .. 1000
            |traces:
            |  - y: 1/x
            |axes:
            |  x: { log: true }
            |  y: { log: true, min: 0.001, step: 2 }
            |```
            |""",
        )
        assertEquals(emptyList(), p.problems)
        val d = p.data!!
        assertEquals("Frequency response", d.title)
        assertTrue(d.x.log && d.y.log)
        assertEquals(0.001, d.y.min)
        assertEquals(2.0, d.y.step)
        // Sampled evenly in log: from 1 to 1000 in 200 points, the second is about 1.035, not 6.
        val xs = d.series.single().xs
        assertEquals(1000.0, xs.last(), 1e-9)
        assertTrue(xs[1] < 1.04, xs[1].toString())
        assertEquals(listOf("a log axis needs limits above 0"), plot("```plot\nx: 1 .. 10\ntraces: [y: x]\naxes:\n  y: { log: true, min: 0 }\n```\n").problems)
    }

    @Test
    fun `a parametric curve has its own x`() {
        val p = plot(
            """
            |```plot
            |x: t = 0 .. 2*π
            |samples: 5
            |traces:
            |  - x: 2 m*cos(t)
            |    y: 1 m*sin(t)
            |```
            |""",
        )
        assertEquals(emptyList(), p.problems)
        val s = p.data!!.series.single()
        assertEquals(listOf(2.0, 0.0, -2.0, 0.0, 2.0), s.xs.map { Math.round(it * 1e9) / 1e9 })
        assertEquals(listOf(0.0, 1.0, 0.0, -1.0, 0.0), s.ys.map { Math.round(it * 1e9) / 1e9 + 0.0 })
        assertEquals("2 m*cos(t) [m]", p.data.x.label)
    }
}
