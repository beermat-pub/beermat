package pub.beermat.symbolic

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The user's own Python (docs/symbolic.md#the-users-own-python): an interpreter of theirs (a venv,
 * Anaconda, the system's) that runs the sheets' Python functions in place of the bundled one, with
 * whatever packages it has. SymPy stays in the bundled Python. A setting of the machine, kept in
 * `~/.beermat/python/own-python.txt`, never in a sheet, so sheets stay portable.
 */
object OwnPython {

    private val file get() = File(Packages.root, "own-python.txt")

    /** The interpreter set, or null for the bundled Python. The `beermat.ownPython` property comes first (tests). */
    fun interpreter(): File? {
        val path = System.getProperty("beermat.ownPython") ?: runCatching { file.readText() }.getOrNull()
        return path?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
    }

    /** Sets the interpreter, or goes back to the bundled Python with null. */
    fun set(interpreter: File?) {
        if (interpreter == null) {
            file.delete()
            return
        }
        file.parentFile.mkdirs()
        file.writeText(interpreter.absolutePath + "\n")
    }

    /** What the user picked: the interpreter itself, or the folder of a venv or a Conda environment. */
    fun resolve(picked: File): File {
        if (picked.isFile) return picked
        val inside = listOf("python.exe", "Scripts/python.exe", "bin/python3", "bin/python").map { File(picked, it) }
        return inside.firstOrNull(File::isFile) ?: picked
    }

    /** The interpreter's version (`3.12.4`), or a message saying why it can't be used. */
    fun check(interpreter: File): Result<String> = runCatching {
        if (!interpreter.isFile) error("${interpreter.path} isn't there")
        val p = process(interpreter, "-c", "import sys; print('%d.%d.%d' % sys.version_info[:3])").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            error("${interpreter.path} didn't answer")
        }
        if (p.exitValue() != 0) error("${interpreter.path} didn't run: $out")
        val version = out.lines().last()
        val (major, minor) = version.split('.').map { it.toIntOrNull() ?: error("${interpreter.path} isn't a Python: $out") }
        if (major < 3 || major == 3 && minor < MINIMUM) error("Python $version is too old; Beermat needs 3.$MINIMUM or newer")
        version
    }

    /**
     * A process builder for the user's interpreter, as they have it set up: not isolated (their
     * `PYTHONPATH`, user site-packages and `.pth` files count), with no `PYTHONHOME` and none of the
     * bundled Python's packages. A script runs from a folder holding only itself, so nothing beside it
     * is imported by mistake.
     */
    fun process(interpreter: File, vararg args: String): ProcessBuilder =
        ProcessBuilder(interpreter.path, *args).also {
            it.environment().remove("PYTHONHOME")
            it.environment().remove("BEERMAT_PACKAGES")
        }

    /** The oldest Python 3 minor version the bridge runs on. */
    const val MINIMUM = 9
}
