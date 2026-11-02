package pub.beermat.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliTest {

    private val dir: Path = Files.createTempDirectory("beermat-cli")

    @AfterTest
    fun cleanUp() = dir.toFile().deleteRecursively().let {}

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = Cli(PrintStream(out, true, Charsets.UTF_8), PrintStream(err, true, Charsets.UTF_8)).run(args.toList())
        return Run(code, out.toString(Charsets.UTF_8).replace("\r\n", "\n"), err.toString(Charsets.UTF_8).replace("\r\n", "\n"))
    }

    private fun sheet(text: String): String = dir.resolve("beam.bbm").also { it.writeText(text) }.toString()

    private val beam = "# Beam\n\n```calc\nL := 6 m\nq := 12 kN/m\nM := q*L^2/8 = ? kN*m\nq*L/2 = ?\n```\n"

    @Test
    fun `eval prints results`() {
        val r = run("eval", sheet(beam))
        assertEquals(0, r.code)
        assertEquals("6: M := q*L^2/8 = ? kN*m   #= 54 kN*m\n7: q*L/2 = ?               #= 36 kN\n", r.out)
    }

    @Test
    fun `eval as JSON`() {
        val r = run("eval", sheet("```calc\nx = ?\n\"a\\\"b\" = ?\n```\n"), "--json")
        assertEquals(1, r.code)
        val file = Json.string(dir.resolve("beam.bbm").toString())
        assertEquals(
            "{\"file\": $file, \"lines\": [{\"line\": 2, \"statement\": \"x = ?\", \"error\": \"x is not defined\"}, " +
                "{\"line\": 3, \"statement\": \"\\\"a\\\\\\\"b\\\" = ?\", \"result\": \"\\\"a\\\\\\\"b\\\"\"}]}\n",
            r.out,
        )
    }

    @Test
    fun `eval --write then check`() {
        val file = sheet(beam)
        assertEquals("ok\n", run("check", file).out, "no cache: nothing stale")
        assertEquals(0, run("eval", file, "--write").code)
        val text = Path.of(file).readText()
        assertTrue(text.startsWith("---\nresults: "), text)
        assertTrue("M := q*L^2/8 = ? kN*m   #= 54 kN*m\n" in text, text)
        assertEquals(0, run("check", file).code)

        Path.of(file).writeText(text.replace("6 m", "8 m"))
        val stale = run("check", file)
        assertEquals(1, stale.code)
        assertTrue("cached results are stale" in stale.out, stale.out)
    }

    @Test
    fun export() {
        val file = sheet(beam)
        val md = run("export", file)
        assertEquals(0, md.code)
        assertTrue("$$\nM := \\frac{q \\cdot L^{2}}{8} = 54\\ \\mathrm{kN} \\cdot \\mathrm{m}\n$$" in md.out, md.out)
        val out = dir.resolve("beam.tex").toString()
        assertEquals(0, run("export", file, "--to=latex", "--out", out).code)
        assertTrue(Path.of(out).readText().startsWith("\\documentclass{article}"))
        assertEquals(2, run("export", file, "--to", "pdf").code)
        assertEquals(2, run("export", file, "--to").code)
    }

    @Test
    fun `usage errors`() {
        assertEquals(2, run().code)
        assertEquals(2, run("frobnicate").code)
        assertEquals(2, run("eval", "--fast", "x.bbm").code)
        assertEquals(2, run("check", dir.resolve("missing.bbm").toString()).code)
        assertEquals(0, run("help").code)
    }
}
