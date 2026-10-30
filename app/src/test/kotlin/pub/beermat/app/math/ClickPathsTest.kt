package pub.beermat.app.math

import pub.beermat.app.editor.Nodes
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

/** Wherever a statement is clicked, the path found leads to one of its nodes (a wrong one once froze the window). */
class ClickPathsTest {

    private val layout = MathLayout(SkiaMath, 12f).editing(emptyMap())

    @Test
    fun `every point of every kind of statement leads to a node`() {
        val statements = listOf(
            "a + b", "sqrt(?)", "sum(f, b)", "x/int(s!, ?*?, ?, ?)", "vec(a >= ?)", "nthroot(2, ?)", "f(s)",
            "y := diff(x^2, x)", "M^T = ?", "v[1, 2] = ? mm", "M^<1>", "|x - 1|", "(a + b)/(c - d)",
            "x := [1, 2; 3, 4]", "i := 1..10", "try 1/0 on error 2", "(x + 1)^2 -> expand -> ?", "given", "x < 3",
            "f(x) :=\n  1 if x > 0\n  0 otherwise", "g :=\n  s <- 0\n  for k in 1..3\n    s <- s + k\n  s",
            "? := 2", "\"text\"",
        )
        for (src in statements) {
            val st = Parser.parseStatement(src)
            val box = layout.statement(st)
            val bad = mutableSetOf<List<Int>>()
            var y = -box.ascent
            while (y < box.descent) {
                var x = 0f
                while (x < box.width) {
                    BoxPaths.at(box, x, y)?.let { p -> if (runCatching { Nodes.at(st, p) }.isFailure) bad += p }
                    x += 0.5f
                }
                y += 0.5f
            }
            assertEquals(emptySet(), bad, src)
        }
    }
}
