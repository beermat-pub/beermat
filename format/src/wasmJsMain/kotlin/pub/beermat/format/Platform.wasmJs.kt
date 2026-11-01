package pub.beermat.format

import pub.beermat.engine.eval.Builtin
import pub.beermat.engine.eval.EvalException

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

// Greyed out in the browser, as everything the web version lacks (docs/web.md).
internal actual val platformFunctions: List<Builtin> = listOf(
    Builtin("READEXCEL", 1..2) { throw EvalException("READEXCEL needs the desktop version of Beermat") },
)
