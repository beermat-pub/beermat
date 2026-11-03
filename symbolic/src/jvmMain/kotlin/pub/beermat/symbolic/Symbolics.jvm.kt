package pub.beermat.symbolic

import pub.beermat.engine.HostFile
import pub.beermat.engine.eval.Modules
import pub.beermat.engine.eval.SymbolicBackend
import java.io.File

/** The bundled Python, one process for SymPy and the sheet's own Python functions; started when first needed. */
private val python: SymPy? by lazy { Python.home()?.let { SymPy(it) } }

internal actual val sympy: SymbolicBackend? by lazy { python?.let { SymPyBackend(it) } }

/** The user's own Python, when one is set, and the process running it (docs/symbolic.md#the-users-own-python). */
private var own: Pair<File, SymPy>? = null

private val lock = Any()

/** Where the sheets' Python functions run: the user's own Python, when one is set, else the bundled one. */
private fun functions(): SymPy? = synchronized(lock) {
    val interpreter = OwnPython.interpreter()
    own?.let { (file, process) ->
        if (file == interpreter) return process
        process.stop()
        own = null
    }
    if (interpreter == null) return python
    SymPy.own(interpreter).also { own = interpreter to it }
}

internal actual fun pythonModules(allowed: (HostFile) -> Boolean): Modules? = functions()?.let { PythonModules(it, allowed) }
