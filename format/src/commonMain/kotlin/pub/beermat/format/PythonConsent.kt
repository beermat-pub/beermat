package pub.beermat.format

import kotlin.concurrent.Volatile
import pub.beermat.engine.HostFile


/**
 * Whether a sheet may run a Python file with `use "file.py"` (docs/symbolic.md#python-functions-in-a-sheet).
 * Nothing runs until allowed: the command line allows every file with `--allow-python`, the app asks
 * once per file and its content; the MCP server never changes this.
 */
object PythonConsent {
    @Volatile
    var allowed: (HostFile) -> Boolean = { false }

    /**
     * What evaluation uses: the Python functions, asking [allowed]; null without Python. Looked up at
     * each evaluation, since the Python can change (the user's own, set while the app runs; VS Code's
     * helper, found after the page starts).
     */
    internal val modules get() = pub.beermat.symbolic.Symbolics.modules { allowed(it) }
}
