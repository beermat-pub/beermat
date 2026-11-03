package pub.beermat.symbolic

import pub.beermat.engine.HostFile
import pub.beermat.engine.eval.Modules
import pub.beermat.engine.eval.SymbolicBackend

// No Python in the browser (docs/web.md): what our own engine can't do says it needs the desktop version.
// In VS Code's web view, SymPy is the installed `beermat`'s, through the extension, once it sets Helper.bridge.
private var backend: Pair<Bridge, SymbolicBackend>? = null

internal actual val sympy: SymbolicBackend?
    get() {
        val bridge = Helper.bridge ?: return null
        backend?.let { (b, s) -> if (b === bridge) return s }
        return SymPyBackend(bridge).also { backend = bridge to it }
    }

// In VS Code, the installed beermat's Python too, over the same bridge; the extension checks the user's consent again.
internal actual fun pythonModules(allowed: (HostFile) -> Boolean): Modules? = Helper.bridge?.let { PythonModules(it, allowed) }
