package pub.beermat.app.math

/** Where a box sits: its left edge [x], its baseline [y], and its extent. */
data class Rect(val x: Float, val y: Float, val width: Float, val ascent: Float, val descent: Float) {
    val top get() = y - ascent
    val bottom get() = y + descent
    val right get() = x + width

    fun contains(px: Float, py: Float) = px in x..right && py in top..bottom
}

/**
 * Finding nodes in a tagged layout (see [MathLayout.editing]): a node's box from its path, and
 * the path of the node under a point. Paths are those of `editor.Nodes`.
 */
object BoxPaths {

    /** The box of the node at [path] in [root], a statement laid out with tags; null if it isn't drawn. */
    fun rect(root: Box, path: List<Int>, x: Float = 0f, y: Float = 0f): Rect? {
        if (path.isEmpty()) return Rect(x, y, root.width, root.ascent, root.descent)
        val (g, gx, gy) = tagged(root, x, y).firstOrNull { it.first.tag == path[0] } ?: return null
        return rect(g, path.drop(1), gx, gy)
    }

    /** The path of the deepest node under ([px], [py]), or null if the point is outside every node. */
    fun at(root: Box, px: Float, py: Float, x: Float = 0f, y: Float = 0f): List<Int>? {
        val hit = tagged(root, x, y).filter { (g, gx, gy) -> Rect(gx, gy, g.width, g.ascent, g.descent).contains(px, py) }
            // Of overlapping boxes (a fraction's whole and its parts), the smallest.
            .minByOrNull { (g, _, _) -> g.width * g.height } ?: return null
        val (g, gx, gy) = hit
        return listOf(g.tag!!) + (at(g, px, py, gx, gy) ?: emptyList())
    }

    /** The nearest tagged groups inside [b] (not inside each other), with their origins. */
    private fun tagged(b: Box, x: Float, y: Float): List<Triple<Group, Float, Float>> {
        if (b !is Group) return emptyList()
        return b.children.flatMap { p ->
            val c = p.box
            if (c is Group && c.tag != null) listOf(Triple(c, x + p.x, y + p.y)) else tagged(c, x + p.x, y + p.y)
        }
    }
}
