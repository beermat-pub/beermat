package pub.beermat.symbolic

import pub.beermat.engine.HostFile
import pub.beermat.engine.eval.Modules
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
    /** What runs `use "file.py"`, asking [allowed] before a file runs; null without Python (the web version). */
    fun modules(allowed: (HostFile) -> Boolean): Modules? = pythonModules(allowed)

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

/** SymPy, in the bundled Python: the desktop versions; null in the browser, or a build without Python. */
internal expect val sympy: SymbolicBackend?

/** The sheet's own Python functions (`use "file.py"`), where there is Python. */
internal expect fun pythonModules(allowed: (HostFile) -> Boolean): Modules?
