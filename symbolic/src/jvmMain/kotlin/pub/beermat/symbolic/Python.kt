package pub.beermat.symbolic

import java.io.File

/**
 * The bundled Python with SymPy (docs/symbolic.md#bundled-sympy), prepared by the `preparePython`
 * Gradle task. Started only when something needs it, never at the app's start.
 */
object Python {
    /**
     * Python's folder: the `beermat.python` property (tests and development), `python/` in the
     * app's resources (Compose's `compose.application.resources.dir`), or `python/` beside the `lib/`
     * folder this code's jar is in (the command line's distribution). Null when this build has none,
     * as on the web.
     */
    fun home(): File? {
        val candidates = listOfNotNull(
            System.getProperty("beermat.python")?.let(::File),
            System.getProperty("compose.application.resources.dir")?.let { File(it, "python") },
            besideJar(),
        )
        return candidates.firstOrNull { executable(it).isFile }
    }

    private fun besideJar(): File? = runCatching {
        val jar = File(Python::class.java.protectionDomain.codeSource.location.toURI())
        if (jar.isFile) File(jar.parentFile.parentFile, "python") else null
    }.getOrNull()

    /** The interpreter inside a [home]. */
    fun executable(home: File): File =
        if (File(home, "python.exe").isFile) File(home, "python.exe") else File(home, "bin/python3")

    /**
     * A process builder for the interpreter, isolated from any Python installed on the machine:
     * `-I` (no user site-packages, no `PYTHON*` variables, no current folder on the path) and
     * `PYTHONHOME` set to the bundled folder. `BEERMAT_PACKAGES` tells the bridge where the
     * user's own packages are ([Packages]).
     */
    fun process(home: File, vararg args: String): ProcessBuilder =
        ProcessBuilder(executable(home).path, "-I", *args).also {
            it.environment()["PYTHONHOME"] = home.path
            it.environment()["BEERMAT_PACKAGES"] = Packages.root.path
        }
}
