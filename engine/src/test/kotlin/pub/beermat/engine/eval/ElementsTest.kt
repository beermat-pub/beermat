package pub.beermat.engine.eval

import pub.beermat.engine.units.Quantity
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The persistent vector behind arrays set element by element: the same as a list copied at each change. */
class ElementsTest {

    private fun n(x: Int) = Scalar(Quantity(x.toDouble()))

    @Test
    fun `sets and additions match a copied list, and every version stays as it was`() {
        val random = Random(1)
        val versions = mutableListOf(Elements.of(emptyList()) to emptyList<Value>())
        repeat(5000) { k ->
            val (e, l) = versions[random.nextInt(versions.size)].takeIf { random.nextInt(4) == 0 } ?: versions.last()
            val index = if (l.isEmpty() || random.nextBoolean()) l.size else random.nextInt(l.size)
            val copy = l.toMutableList().apply { if (index == size) add(n(k)) else set(index, n(k)) }
            versions += e.with(index, n(k)) to copy
        }
        for ((e, l) in versions) assertEquals<List<Value>>(l, e)
        // Past 32 and 1024 elements, where the tree gains levels.
        assertEquals<List<Value>>(List(2000, ::n), Elements.of(List(2000, ::n)))
    }
}
