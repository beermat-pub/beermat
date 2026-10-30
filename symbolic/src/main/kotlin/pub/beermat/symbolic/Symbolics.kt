package pub.beermat.symbolic

import pub.beermat.engine.eval.SymbolicBackend
import pub.beermat.engine.eval.Value
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Id

/**
 * Symbolic evaluation as the app, the CLI and the MCP server do it (docs/symbolic.md): our own
 * engine ([Cas]) first, instant and in the same process; what it can't do goes to SymPy, when this
 * version bundles it. Python starts only then, once, and stays.
 */
object Symbolics : SymbolicBackend {
    /** The bundled Python, one process for SymPy and the sheet's own Python functions. */
    private val python: SymPy? by lazy { Python.home()?.let { SymPy(it) } }

    private val sympy: SymPyBackend? by lazy { python?.let { SymPyBackend(it) } }

    /** What runs `use "file.py"`, asking [allowed] before a file runs; null without Python (the web version). */
    fun modules(allowed: (java.io.File) -> Boolean): pub.beermat.engine.eval.Modules? = python?.let { PythonModules(it, allowed) }

    /** Whether SymPy is there (the desktop versions; not the web, nor a build without Python). */
    val hasSymPy: Boolean get() = sympy != null

    override fun evaluate(expr: Expr, keywords: List<Expr>, defined: (Id) -> Value?): Expr {
        // Units go through as symbols, checked where they are known (docs/symbolic.md#units).
        val withUnits = SymbolicUnits.put(expr, defined)
        SymbolicUnits.check(withUnits)
        val result = try {
            Cas.evaluate(withUnits, keywords, defined)
        } catch (beyond: Cas.Beyond) {
            (sympy ?: throw beyond).evaluate(withUnits, keywords, defined)
        }
        return SymbolicUnits.take(result)
    }
}
