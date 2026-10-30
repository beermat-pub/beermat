package pub.beermat.engine.eval

import pub.beermat.engine.syntax.Id

/**
 * An immutable set of name bindings. Each definition makes a new scope, so a function
 * definition can keep the scope it was defined in.
 */
class Scope private constructor(private val bindings: Map<Id, Binding>) {

    sealed interface Binding {
        data class Bound(val value: Value) : Binding
        /** The definition of this name failed; uses of it report why instead of "undefined". */
        data class Broken(val message: String, val names: List<Id> = emptyList()) : Binding
    }

    operator fun get(id: Id): Binding? = bindings[id]

    fun with(id: Id, binding: Binding) = Scope(bindings + (id to binding))

    fun with(id: Id, value: Value) = with(id, Binding.Bound(value))

    fun withAll(values: Map<Id, Value>) = Scope(bindings + values.mapValues { Binding.Bound(it.value) })

    companion object {
        val EMPTY = Scope(emptyMap())
    }
}
