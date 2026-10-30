package pub.beermat.format

import java.io.File

/**
 * Whether a sheet may run a Python file with `use "file.py"` (docs/symbolic.md#python-functions-in-a-sheet).
 * Nothing runs until allowed: the command line allows every file with `--allow-python`, the app asks
 * once per file and its content; the MCP server never changes this.
 */
object PythonConsent {
    @Volatile
    var allowed: (File) -> Boolean = { false }

    /** What evaluation uses: the bundled Python's functions, asking [allowed]; null without Python. */
    internal val modules by lazy { pub.beermat.symbolic.Symbolics.modules { allowed(it) } }
}
