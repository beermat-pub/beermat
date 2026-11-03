package pub.beermat.symbolic

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The user's own Python (docs/symbolic.md#the-users-own-python). The test's "own" Python is the
 * bundled interpreter run as the user's would be, not isolated, which a Python function can tell
 * from `sys.flags.isolated`.
 */
class OwnPythonTest {

    private val home = Python.home()!!
    private val interpreter = Python.executable(home)
    private val folder: File = Files.createTempDirectory("beermat-own").toFile().apply { deleteOnExit() }

    private fun isolated(): String {
        File(folder, "flags.py").writeText("import sys\n\ndef isolated():\n    return sys.flags.isolated\n")
        val o = Evaluator(folder = folder, modules = Symbolics.modules { true }!!)
            .run(listOf("p := use \"flags.py\"", "p.isolated() = ?").map(Parser::parseStatement)).last()
        return (o as? Outcome.Evaluated)?.let { ResultText.format(it.shown) } ?: o.toString()
    }

    private fun <T> owning(python: File?, body: () -> T): T {
        python?.let { System.setProperty("beermat.ownPython", it.path) }
        try {
            return body()
        } finally {
            System.clearProperty("beermat.ownPython")
        }
    }

    @Test
    fun `Python functions run in the user's own Python when one is set, and in the bundled one again after`() {
        assertEquals("1", isolated())
        assertEquals("0", owning(interpreter) { isolated() })
        assertEquals("1", isolated())
    }

    @Test
    fun `a missing interpreter is an error on the statement, not a crash`() {
        val gone = File(folder, "no-python/python.exe")
        val result = owning(gone) { isolated() }
        assertTrue("couldn't start" in result, result)
    }

    @Test
    fun `checking an interpreter gives its version, or says why not`() {
        assertTrue(OwnPython.check(interpreter).getOrThrow().matches(Regex("""3\.\d+\.\d+""")))
        assertTrue(OwnPython.check(File(folder, "nothing")).isFailure)
        // A folder, as a venv's, is resolved to the interpreter in it.
        assertEquals(interpreter.canonicalFile, OwnPython.resolve(home).canonicalFile)
    }
}
