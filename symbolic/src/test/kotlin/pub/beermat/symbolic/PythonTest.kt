package pub.beermat.symbolic

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The bundled Python, as `preparePython` leaves it: SymPy there, nothing unused. */
class PythonTest {

    private val home = assertNotNull(Python.home(), "run through Gradle, which prepares the bundled Python")

    private fun run(code: String): String {
        val p = Python.process(home, "-c", code).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "Python took too long")
        assertEquals(0, p.exitValue(), out)
        return out.trim()
    }

    @Test
    fun `SymPy integrates`() {
        assertEquals("-x*cos(x) + sin(x)", run("import sympy as s; x = s.Symbol('x'); print(s.integrate(x*s.sin(x), x))"))
    }

    @Test
    fun `it is the bundled Python, with the unused parts left out`() {
        val prefix = run("import sys; print(sys.prefix)")
        assertEquals(home.canonicalFile, java.io.File(prefix).canonicalFile)
        assertEquals("False False", run("import importlib.util as u; print(u.find_spec('tkinter') is not None, u.find_spec('pip') is not None)"))
    }

    @Test
    fun `SymPy is compiled ahead, so it imports quickly`() {
        // Without its .pyc files, writing nothing, the import takes about 5 s; with them, about 0.5 s.
        assertEquals("True", run("import sys; sys.dont_write_bytecode = True; import time; t = time.time(); import sympy; print(time.time() - t < 3)"))
    }
}
