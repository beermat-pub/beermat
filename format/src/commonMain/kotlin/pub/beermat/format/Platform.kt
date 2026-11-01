package pub.beermat.format

import pub.beermat.engine.eval.Builtin

// What the format module needs from its platform: the JVM on the desktop, WebAssembly in the browser
// (docs/web.md), each with an `actual` in jvmMain and wasmJsMain.

/** A map whose keys are told apart by identity, not equality: two equal lines are still two lines. */
expect fun <K : Any, V> identityMap(): MutableMap<K, V>

/** Today's date, as `2026-10-29`, for `{date}` in headers and footers. */
internal expect fun today(): String

/** Built-ins only some platforms have: `READEXCEL` on the desktop. */
internal expect val platformFunctions: List<Builtin>
