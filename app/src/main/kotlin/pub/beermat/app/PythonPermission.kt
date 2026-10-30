package pub.beermat.app

import pub.beermat.format.PythonConsent
import java.io.File
import java.security.MessageDigest
import java.util.prefs.Preferences
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * The app's consent to run a sheet's Python files (docs/symbolic.md#python-functions-in-a-sheet): it
 * asks once per file, showing its path, as Excel asks for macros. A yes is kept for that file and its
 * content, so a changed file asks again; a no lasts until the app closes, so editing doesn't ask at
 * every keystroke.
 */
object PythonPermission {
    private val saved: Preferences = Preferences.userRoot().node("pub/beermat/python")
    private val refused = mutableSetOf<String>()

    fun install() {
        PythonConsent.allowed = ::allowed
    }

    @Synchronized
    private fun allowed(file: File): Boolean {
        val key = key(file)
        if (saved.getBoolean(key, false)) return true
        if (key in refused) return false
        val yes = ask(file)
        if (yes) saved.putBoolean(key, true) else refused += key
        return yes
    }

    private fun ask(file: File): Boolean {
        var answer = false
        val question = {
            answer = JOptionPane.showConfirmDialog(
                null,
                "This sheet runs Python code from\n${file.path}\n\nRun it? Only say yes if you trust where the file came from.",
                "Run Python code?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
            ) == JOptionPane.YES_OPTION
        }
        if (SwingUtilities.isEventDispatchThread()) question() else SwingUtilities.invokeAndWait(question)
        return answer
    }

    /** The file's path and content as one key (preferences keys are at most 80 characters). */
    private fun key(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(file.canonicalPath.toByteArray())
        digest.update(0)
        digest.update(file.readBytes())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
