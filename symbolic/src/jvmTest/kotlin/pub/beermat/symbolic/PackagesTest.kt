package pub.beermat.symbolic

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The user's own packages (docs/symbolic.md#the-users-own-packages): installed with the bundled pip, found by a sheet's Python. */
class PackagesTest {

    private val home = Python.home()!!

    private val folder: File = Files.createTempDirectory("beermat-pkg").toFile().apply { deleteOnExit() }

    /** A small wheel, made here so the test needs no network: the package `beermat_demo` with `twice(x)`. */
    private fun wheel(): File {
        val name = "beermat_demo-1.0-py3-none-any.whl"
        val files = mapOf(
            "beermat_demo/__init__.py" to "def twice(x):\n    return 2 * x\n",
            "beermat_demo-1.0.dist-info/METADATA" to "Metadata-Version: 2.1\nName: beermat-demo\nVersion: 1.0\n",
            "beermat_demo-1.0.dist-info/WHEEL" to "Wheel-Version: 1.0\nGenerator: test\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
        )
        val record = files.keys.joinToString("") { "$it,,\n" } + "beermat_demo-1.0.dist-info/RECORD,,\n"
        val out = File(folder, name)
        ZipOutputStream(out.outputStream()).use { zip ->
            (files + ("beermat_demo-1.0.dist-info/RECORD" to record)).forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out
    }

    @Test
    fun `a package installed into the user's folder is there for a sheet's Python, until removed`() {
        Packages.remove(home, "beermat-demo")
        wheel()
        val out = Packages.install(home, listOf("beermat-demo"), listOf("--no-index", "--find-links", folder.path))
        assertTrue(out.ok, out.output)
        assertTrue(Packages.installed(home).any { it.name == "beermat-demo" && it.version == "1.0" }, Packages.installed(home).toString())
        assertEquals(listOf("beermat-demo"), Packages.wanted().filter { it == "beermat-demo" })
        assertEquals(emptyList(), Packages.missing(home))
        // The folder is per Python minor version, outside the app.
        assertTrue(Packages.folder(home).path.replace('\\', '/').endsWith("/${Packages.version(home)}/site-packages"))

        // A sheet's Python file imports it, though Python was already running.
        File(folder, "uses.py").writeText("import beermat_demo\n\ndef f(x):\n    return beermat_demo.twice(x)\n")
        val modules = Symbolics.modules { true }!!
        val o = Evaluator(folder = folder, modules = modules).run(listOf("u := use \"uses.py\"", "u.f(21) = ?").map(Parser::parseStatement)).last()
        assertEquals("42", (o as? Outcome.Evaluated)?.let { ResultText.format(it.shown) } ?: o.toString())

        assertTrue(Packages.remove(home, "beermat-demo").ok)
        assertTrue(Packages.installed(home).none { it.name == "beermat-demo" })
        assertTrue("beermat-demo" !in Packages.wanted())
    }

    @Test
    fun `a requirement's name`() {
        assertEquals("scipy", Packages.key("SciPy==1.14"))
        assertEquals("beermat-demo", Packages.key("beermat_demo"))
        assertEquals("numpy", Packages.key("numpy[extra]>=2"))
    }
}
