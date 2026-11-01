package pub.beermat.format

import pub.beermat.engine.eval.Builtin

actual fun <K : Any, V> identityMap(): MutableMap<K, V> = java.util.IdentityHashMap()

internal actual fun today(): String = java.time.LocalDate.now().toString()

internal actual val platformFunctions: List<Builtin> get() = ExcelFiles.functions
