package pub.beermat.app.math

/**
 * A laid-out piece of math, TeX style: a [width], and an [ascent] above and a [descent] below
 * its baseline. Coordinates are in pixels with y growing downwards; a box's baseline is at y = 0.
 *
 * There are only three kinds: text, strokes (rules, radical signs, stretched delimiters) and
 * groups of placed boxes. Everything the layout builds is made of these, so drawing is simple.
 */
sealed interface Box {
    val width: Float
    val ascent: Float
    val descent: Float
    val height: Float get() = ascent + descent
}

/** How a piece of text is drawn. */
enum class Ink { Normal, Keyword, Error, Warning, Faint }

/** A run of text in the math font at [size] pixels. [bold] thickens it (for `==`). */
data class TextBox(
    val text: String,
    val size: Float,
    override val width: Float,
    override val ascent: Float,
    override val descent: Float,
    val ink: Ink = Ink.Normal,
    val bold: Boolean = false,
) : Box

/** One step of a stroked path, in the box's coordinates. */
sealed interface PathOp {
    data class Move(val x: Float, val y: Float) : PathOp
    data class Line(val x: Float, val y: Float) : PathOp
    data class Quad(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathOp
}

/** Lines and curves stroked with [thickness]: fraction rules, radical signs, big brackets, program bars. */
data class StrokeBox(
    override val width: Float,
    override val ascent: Float,
    override val descent: Float,
    val path: List<PathOp>,
    val thickness: Float,
) : Box

/** A box placed with its baseline origin at ([x], [y]) inside a [Group]. */
data class Placed(val box: Box, val x: Float, val y: Float)

/**
 * Boxes placed together. A [tag] marks the box of a node of the tree being edited: the index of
 * that node among its parent's children (`editor.Nodes`), so nested tags spell out its path.
 */
data class Group(
    override val width: Float,
    override val ascent: Float,
    override val descent: Float,
    val children: List<Placed>,
    val tag: Int? = null,
) : Box {
    companion object {
        /** Boxes side by side on a common baseline. */
        fun row(boxes: List<Box>): Group {
            var x = 0f
            val placed = boxes.map { b -> Placed(b, x, 0f).also { x += b.width } }
            return Group(x, boxes.maxOfOrNull { it.ascent } ?: 0f, boxes.maxOfOrNull { it.descent } ?: 0f, placed)
        }

        /** Empty horizontal space. */
        fun space(width: Float) = Group(width, 0f, 0f, emptyList())

        /** [children] placed freely; the extent is computed from them. */
        fun of(children: List<Placed>, minWidth: Float = 0f): Group {
            val width = maxOf(minWidth, children.maxOfOrNull { it.x + it.box.width } ?: 0f)
            val ascent = children.maxOfOrNull { it.box.ascent - it.y } ?: 0f
            val descent = children.maxOfOrNull { it.box.descent + it.y } ?: 0f
            return Group(width, maxOf(ascent, 0f), maxOf(descent, 0f), children)
        }
    }
}
