package pub.beermat.symbolic

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The user's own Python packages (docs/symbolic.md#the-users-own-packages), for Python functions
 * in a sheet: installed with the bundled pip into a folder of the user's, outside the app, so an
 * update never touches them. One folder per Python minor version, `<root>/3.13/site-packages`,
 * since compiled packages fit only the version they were built for; the bridge puts it on Python's
 * path. [wanted] lists what the user asked for, for every version, so that a new Python can
 * install the same again.
 */
object Packages {

    /** `~/.beermat/python`, or the `beermat.packages` property (tests). */
    val root: File get() = System.getProperty("beermat.packages")?.let(::File) ?: File(System.getProperty("user.home"), ".beermat/python")

    private val wantedFile get() = File(root, "packages.txt")

    /** A package in the folder: its name and version, as pip lists them. */
    data class Installed(val name: String, val version: String)

    /** What pip said, and whether it worked. */
    data class Outcome(val ok: Boolean, val output: String)

    /** The bundled Python's minor version, `3.13`. */
    fun version(home: File): String = run(home, "-c", "import sys; print('%d.%d' % sys.version_info[:2])").output.trim()

    /** The folder the packages of [home]'s Python go in. */
    fun folder(home: File): File = File(root, "${version(home)}/site-packages")

    /** The packages the user asked for, by the names they gave (`numpy`, `scipy==1.14`). */
    fun wanted(): List<String> = runCatching { wantedFile.readLines().map(String::trim).filter(String::isNotEmpty) }.getOrDefault(emptyList())

    private fun want(names: List<String>) {
        root.mkdirs()
        wantedFile.writeText(names.distinctBy(::key).joinToString("\n", postfix = "\n"))
    }

    /** A requirement's package name, lower case: `SciPy==1.14` is `scipy`. */
    fun key(requirement: String): String = requirement.split('=', '<', '>', '!', '~', '[', ';', ' ').first().lowercase().replace('_', '-')

    /** What is in the folder now. */
    fun installed(home: File): List<Installed> {
        val dir = folder(home)
        if (!dir.isDirectory) return emptyList()
        val out = pip(home, "list", "--path", dir.path, "--format", "freeze")
        return out.output.lines().mapNotNull { line -> line.split("==").takeIf { it.size == 2 }?.let { (n, v) -> Installed(n.trim(), v.trim()) } }
    }

    /** Installs [requirements] (and what they need) into the folder, and remembers them; [options] go to pip too. */
    fun install(home: File, requirements: List<String>, options: List<String> = emptyList()): Outcome {
        val dir = folder(home).apply { mkdirs() }
        // --upgrade replaces what is there; without it pip --target refuses to.
        val out = pip(home, "install", "--target", dir.path, "--upgrade", "--no-warn-script-location", *options.toTypedArray(), *requirements.toTypedArray())
        if (out.ok) want(wanted().filter { w -> requirements.none { key(it) == key(w) } } + requirements)
        return out
    }

    /** Removes [name] from the folder and from the list. Packages it brought along stay, as with pip. */
    fun remove(home: File, name: String): Outcome {
        // pip uninstall finds a package on Python's path, so the folder goes on it first.
        val code = "import sys; sys.path.insert(0, sys.argv[1]); from pip._internal.cli.main import main; sys.exit(main(['uninstall', '-y', '--disable-pip-version-check', sys.argv[2]]))"
        val out = run(home, "-c", code, folder(home).path, name)
        want(wanted().filter { key(it) != key(name) })
        return out
    }

    /** What the list has that the folder lacks: after an update brought a new Python, say. */
    fun missing(home: File): List<String> {
        val have = installed(home).map { key(it.name) }.toSet()
        return wanted().filter { key(it) !in have }
    }

    private fun pip(home: File, vararg args: String) = run(home, "-m", "pip", "--disable-pip-version-check", *args)

    private fun run(home: File, vararg args: String): Outcome {
        val p = Python.process(home, *args).redirectErrorStream(true).start()
        p.outputStream.close()
        val text = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(30, TimeUnit.MINUTES)) p.destroyForcibly()
        return Outcome(p.exitValue() == 0, text)
    }
}
