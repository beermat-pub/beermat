package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Parser
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class DataFilesTest {

    private val dir = Files.createTempDirectory("beermat-data").toFile().apply { deleteOnExit() }

    private fun run(vararg lines: String, folder: java.io.File? = dir) = Evaluator(folder = folder).run(lines.map(Parser::parseStatement)).last()

    private fun text(vararg lines: String): String = when (val o = run(*lines)) {
        is Outcome.Evaluated -> ResultText.format(o.shown)
        is Outcome.Failed -> fail("failed: ${o.error.message}")
        else -> fail("$o")
    }

    @Test
    fun `numbers in columns, beside the sheet`() {
        dir.resolve("loads.txt").writeText("x  q\n0  1.5\n2, 3.5\n\n4\t5\n")
        assertEquals("[0, 1.5; 2, 3.5; 4, 5]", text("READPRN(\"loads.txt\") = ?"))
        assertEquals("[1.5; 3.5; 5]*kN/m", text("q := READPRN(\"loads.txt\")^<1>*1 kN/m", "q = ? kN/m"))
    }

    @Test
    fun `csv and delimited files keep text apart from numbers`() {
        dir.resolve("cases.csv").writeText("case,q\n\"Dead, permanent\",4.5\nImposed,7.5\n")
        assertEquals("[\"case\", \"q\"; \"Dead, permanent\", 4.5; \"Imposed\", 7.5]", text("READCSV(\"cases.csv\") = ?"))
        assertEquals("7.5", text("READFILE(\"cases.csv\", \"csv\")[2, 1] = ?"))
    }

    @Test
    fun `a file that isn't there, or a sheet not saved yet`() {
        assertTrue("not found beside the sheet" in assertIs<Outcome.Failed>(run("READPRN(\"nope.txt\") = ?")).error.message!!)
        assertTrue("save the sheet first" in assertIs<Outcome.Failed>(run("READPRN(\"nope.txt\") = ?", folder = null)).error.message!!)
    }
}
