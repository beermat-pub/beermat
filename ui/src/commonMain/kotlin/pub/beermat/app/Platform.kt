package pub.beermat.app

// What the shared UI needs from its platform (docs/web.md#step-2-in-detail), with an `actual` in jvmMain and wasmJsMain.

/** Notes a bug that was caught and undone, for a report (the desktop keeps `~/.beermat/errors.log`). */
internal expect fun logError(e: Throwable)

/** The math font's bytes where the platform has them at hand (the desktop's resources); null otherwise. */
internal expect fun defaultFontBytes(): ByteArray?

/** [block] run holding [lock], so threads take turns (the desktop's MCP server lays out the live sheet too); the browser has one thread. */
internal expect inline fun <T> locked(lock: Any, block: () -> T): T

/** [content] with a tip ([tip], drawn by the caller) shown while the mouse rests on it: the toolbar's buttons. */
@androidx.compose.runtime.Composable
internal expect fun WithTip(tip: @androidx.compose.runtime.Composable () -> Unit, content: @androidx.compose.runtime.Composable () -> Unit)

/** Whether a typed character comes with its key-down event (the browser) rather than as an event of its own (the desktop). */
internal expect val typedOnKeyDown: Boolean
