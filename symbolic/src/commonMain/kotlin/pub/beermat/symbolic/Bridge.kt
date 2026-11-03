package pub.beermat.symbolic

/**
 * The way to `bridge.py` (docs/symbolic.md#bundled-sympy): one JSON request, one JSON answer. On the
 * desktop, the bundled Python in a process of its own ([SymPy]); in VS Code's web view, the installed
 * `beermat bridge` through the extension ([Helper], docs/vscode.md#step-3-in-detail).
 */
fun interface Bridge {
    /** The answer to [request] (one JSON line), as `bridge.py` wrote it. */
    fun ask(request: String): String
}

/**
 * A bridge a host provides from outside, where the page itself has no Python: VS Code's web view sets
 * [bridge] when the extension finds `beermat`. Null everywhere else.
 */
object Helper {
    var bridge: Bridge? = null
}
