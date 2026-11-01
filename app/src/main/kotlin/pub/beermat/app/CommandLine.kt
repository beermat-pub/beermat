package pub.beermat.app

import java.io.File

/**
 * Tools → Install the beermat command (docs/cli.md#installing), on macOS and Linux: a link on the PATH to
 * the `beermat` script in the app's own folder, which runs the command line with the app's Java and Python.
 * On Windows the installer adds the app's `bin` folder to the PATH instead.
 */
object CommandLine {

    private val os = System.getProperty("os.name").lowercase()
    val mac = "mac" in os

    /** Whether there is anything to install: macOS or Linux, from an installed app (not a development run). */
    val available: Boolean get() = !os.startsWith("windows") && script() != null

    /** The script in the app's folder: Contents/app/bin/beermat on macOS, bin/beermat on Linux. */
    private fun script(): File? {
        val app = System.getProperty("compose.application.resources.dir")?.let { File(it).parentFile } ?: return null
        return listOf(File(app, "bin/beermat"), File(app, "../../bin/beermat")).map { it.canonicalFile }.firstOrNull { it.canExecute() }
    }

    /** Puts the link in place; the message to show (where it went, or what went wrong). */
    fun install(): String {
        val script = script() ?: return "The beermat command comes with an installed Beermat; this one has none."
        return try {
            if (mac) installMac(script) else installLinux(script)
        } catch (e: Exception) {
            "The beermat command couldn't be installed: ${e.message}"
        }
    }

    /** /usr/local/bin, which needs an administrator: macOS asks for the password. */
    private fun installMac(script: File): String {
        val command = "mkdir -p /usr/local/bin && ln -sf ${quoted(script.path)} /usr/local/bin/beermat"
        val apple = "do shell script \"${command.replace("\\", "\\\\").replace("\"", "\\\"")}\" with administrator privileges"
        val process = ProcessBuilder("osascript", "-e", apple).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0) return if ("-128" in output) "Not installed (cancelled)." else "The beermat command couldn't be installed: $output"
        return "Installed: /usr/local/bin/beermat. Open a new terminal and type beermat."
    }

    /** ~/.local/bin, the user's own, which most Linux systems put on the PATH: no password needed. */
    private fun installLinux(script: File): String {
        val bin = File(System.getProperty("user.home"), ".local/bin").apply { mkdirs() }
        val link = File(bin, "beermat").toPath()
        java.nio.file.Files.deleteIfExists(link)
        java.nio.file.Files.createSymbolicLink(link, script.toPath())
        val onPath = System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it).canonicalFile == bin.canonicalFile }
        return "Installed: ~/.local/bin/beermat." + if (onPath) " Open a new terminal and type beermat." else " Add ~/.local/bin to your PATH to use it from any folder."
    }

    private fun quoted(path: String) = "'" + path.replace("'", "'\\''") + "'"
}
