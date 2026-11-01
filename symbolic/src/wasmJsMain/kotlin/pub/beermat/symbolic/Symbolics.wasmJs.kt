package pub.beermat.symbolic

import pub.beermat.engine.HostFile
import pub.beermat.engine.eval.Modules
import pub.beermat.engine.eval.SymbolicBackend

// No Python in the browser (docs/web.md): what our own engine can't do says it needs the desktop version.
internal actual val sympy: SymbolicBackend? = null

internal actual fun pythonModules(allowed: (HostFile) -> Boolean): Modules? = null
