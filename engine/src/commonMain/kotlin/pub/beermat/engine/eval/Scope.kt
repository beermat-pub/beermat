package pub.beermat.engine.eval

import pub.beermat.engine.syntax.Id

/**
 * An immutable set of name bindings. Each definition makes a new scope, so a function
 * definition can keep the scope it was defined in.
 *
 * A scope is a short chain of maps, newest first, each shadowing those below it. Adding a name
 * merges the top maps while the one below is no bigger than what is being merged (like carrying
 * in a binary counter), so binding a program's local or a function's parameter doesn't copy the
 * sheet's names and the unit table, and the chain stays about log n maps long.
 */
class Scope private constructor(private val local: Map<Id, Binding>, private val parent: Scope?) {

    sealed interface Binding {
        data class Bound(val value: Value) : Binding
        /** The definition of this name failed; uses of it report why instead of "undefined". */
        data class Broken(val message: String, val names: List<Id> = emptyList()) : Binding
    }

    operator fun get(id: Id): Binding? {
        var s: Scope? = this
        while (s != null) {
            s.local[id]?.let { return it }
            s = s.parent
        }
        return null
    }

    fun with(id: Id, binding: Binding) = merged(mapOf(id to binding))

    fun with(id: Id, value: Value) = with(id, Binding.Bound(value))

    fun withAll(values: Map<Id, Value>) = if (values.isEmpty()) this else merged(values.mapValues { Binding.Bound(it.value) })

    private fun merged(top: Map<Id, Binding>): Scope {
        var map = top
        var below: Scope? = this
        while (below != null && below.local.size <= map.size) {
            // `put`, not `putAll`: in WebAssembly `putAll` keeps a binding equal to the new one, and a sheet's
            // `°C := K` must replace the built-in `°C` (equal in value, told apart as another object).
            val upper = map
            map = HashMap<Id, Binding>(below.local.size + upper.size).apply {
                putAll(below.local)
                for ((id, binding) in upper) put(id, binding)
            }
            below = below.parent
        }
        return Scope(map, below)
    }

    companion object {
        val EMPTY = Scope(emptyMap(), null)
    }
}
