package pub.beermat.format

import pub.beermat.engine.eval.Builtin
import pub.beermat.engine.eval.ArrayValue
import pub.beermat.engine.eval.EvalException
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Text
import pub.beermat.engine.eval.Value
import pub.beermat.engine.units.Quantity
import pub.beermat.symbolic.Json

/** Keys wrapped so they compare by identity; their own hash code still spreads them (equal keys hash alike). */
private class Identity(val key: Any) {
    override fun equals(other: Any?) = other is Identity && other.key === key
    override fun hashCode() = key.hashCode()
}

actual fun <K : Any, V> identityMap(): MutableMap<K, V> = object : AbstractMutableMap<K, V>() {
    private val map = LinkedHashMap<Identity, Pair<K, V>>()

    override fun put(key: K, value: V): V? = map.put(Identity(key), key to value)?.second

    override fun get(key: K): V? = map[Identity(key)]?.second

    override fun containsKey(key: K) = Identity(key) in map

    override fun remove(key: K): V? = map.remove(Identity(key))?.second

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = map.values.mapTo(LinkedHashSet()) { (k, v) ->
            object : MutableMap.MutableEntry<K, V> {
                override val key = k
                override val value = v
                override fun setValue(newValue: V): V = put(k, newValue)!!
            }
        }
}

// The local date, as the desktop gives it (toISOString would be the date in UTC).
internal actual fun today(): String =
    js("(() => { const d = new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); })()")

// Greyed out in the browser, as everything the web version lacks (docs/web.md). In VS Code, the installed
// beermat reads the workbook (docs/vscode.md#step-3-in-detail), over the bridge SymPy uses.
internal actual val platformFunctions: List<Builtin> = listOf(
    Builtin("READEXCEL", 1..2) { a ->
        val bridge = pub.beermat.symbolic.Helper.bridge ?: throw EvalException("READEXCEL needs the desktop version of Beermat")
        val path = (a[0]() as? Text)?.value ?: throw EvalException("READEXCEL needs the file's name as a string: READEXCEL(\"loads.xlsx\")")
        val range = a.getOrNull(1)?.invoke()?.let { (it as? Text)?.value ?: throw EvalException("READEXCEL: the range is a string, such as \"Sheet1!A1:C10\"") }
        if (folder == null && !pub.beermat.engine.isAbsolutePath(path)) throw EvalException("READEXCEL: save the sheet first, so '$path' can be found beside it")
        val file = pub.beermat.engine.hostFile(folder, path).path
        val reply = Json.read(bridge.ask(Json.write(mapOf("excel" to file, "range" to range)))) as Map<*, *>
        reply["error"]?.let { throw EvalException(it.toString()) }
        excelValue(reply["ok"])
    },
)

/** A cell, or the cells, as the helper sends them: a number, `{"s": text}`, or `{"m": rows}`. */
private fun excelValue(v: Any?): Value = when {
    v is Number -> Scalar(Quantity(v.toDouble()))
    v is Map<*, *> && "s" in v -> Text(v["s"].toString())
    v is Map<*, *> && "m" in v -> {
        val rows = (v["m"] as List<*>).map { it as List<*> }
        ArrayValue.of(rows.size, rows[0].size) { r, c -> excelValue(rows[r][c]) }
    }
    else -> throw EvalException("READEXCEL: the workbook gave something a sheet can't hold")
}
