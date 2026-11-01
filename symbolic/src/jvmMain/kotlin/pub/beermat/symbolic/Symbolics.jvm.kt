package pub.beermat.symbolic

import pub.beermat.engine.HostFile
import pub.beermat.engine.eval.Modules
import pub.beermat.engine.eval.SymbolicBackend

/** The bundled Python, one process for SymPy and the sheet's own Python functions; started when first needed. */
private val python: SymPy? by lazy { Python.home()?.let { SymPy(it) } }

internal actual val sympy: SymbolicBackend? by lazy { python?.let { SymPyBackend(it) } }

internal actual fun pythonModules(allowed: (HostFile) -> Boolean): Modules? = python?.let { PythonModules(it, allowed) }
