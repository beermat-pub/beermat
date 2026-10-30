package pub.beermat.app.page

import org.jetbrains.skia.Surface
import pub.beermat.app.editor.Editor
import pub.beermat.app.editor.Key
import pub.beermat.app.math.MathLayout
import pub.beermat.app.math.SkiaMath
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Random keystrokes, clicks and toolbar commands on real sheets: whatever the order, nothing
 * throws, the sheet still draws, and its text still reads back. (Found the freezes Dan hit.)
 */
class FuzzTest {

    private val math = MathLayout(SkiaMath, 12f)

    private val chars = "abfsxyz0123.+-*/^<>!=:~()'[]{},;|\\\$#?&\" "

    private val specials = listOf(
        Key.Backspace, Key.Delete, Key.Left, Key.Right, Key.Space, Key.Insert, Key.Tab, Key.BackTab, Key.Home, Key.End,
        Key.Ctrl('='), Key.Ctrl('3'), Key.Ctrl('9'), Key.Ctrl('0'), Key.Ctrl('1'), Key.Ctrl('6'), Key.Ctrl('-'), Key.Ctrl('\\'),
        Key.Ctrl('.'), Key.Ctrl('.', shift = true), Key.Ctrl('g'), Key.Ctrl('b'), Key.Ctrl('i'), Key.Ctrl('k', shift = true), Key.Ctrl('k', shift = true), Key.Ctrl('4', shift = true),
    )

    private val words = listOf("if", "for", "while", "otherwise", "return", "break", "continue", "try", "given")

    @Test
    fun `random editing never breaks the sheet`() {
        val beam = File("../examples/beam.bbm").readText()
        // The first failure of each kind (where it was thrown), with the shortest way found to it.
        val failures = mutableMapOf<String, String>()
        // `BEERMAT_FUZZ_RUNS=3000 ./gradlew :app:test` for a longer hunt.
        val runs = System.getenv("BEERMAT_FUZZ_RUNS")?.toIntOrNull() ?: 300
        for (seed in 0 until runs) {
            val rnd = Random(seed)
            val s = Session(if (seed % 2 == 0) beam else "", math)
            val log = mutableListOf<String>()
            try {
                repeat(120) {
                    val step = step(s, rnd)
                    log += step
                    paint(s)
                    s.text()
                }
            } catch (e: Throwable) {
                val where = "$e @ " + e.stackTrace.take(3).joinToString(" < ")
                val report = "seed $seed, last steps: ${log.takeLast(12).joinToString(" ")}\n  ${e.stackTrace.take(10).joinToString("\n  ")}"
                if (where !in failures || failures[where]!!.length > report.length) failures[where] = report
            }
        }
        if (failures.isNotEmpty()) fail(failures.entries.joinToString("\n\n") { (k, v) -> "$k\n$v" })
    }

    /** One random action on [s]; returns what it was, for the failure message. */
    private fun step(s: Session, rnd: Random): String {
        val r = rnd.nextInt(100)
        return when {
            r < 55 -> chars[rnd.nextInt(chars.length)].let { s.key(Key.Type(it)); "'$it'" }
            r < 75 -> specials[rnd.nextInt(specials.size)].let { s.key(it); "$it" }
            r < 79 -> words[rnd.nextInt(words.size)].let { w -> s.edit { Editor.keyword(it, w) }; "[$w]" }
            r < 84 -> {
                val x = 40f + rnd.nextFloat() * 300f
                val y = 40f + rnd.nextFloat() * 300f
                s.click(x, y); "click($x,$y)"
            }
            r < 86 -> s.enter().let { "Enter" }
            r < 87 -> s.leave().let { "Esc" }
            r < 89 -> rnd.nextBoolean().let { up -> s.vertical(up); if (up) "Up" else "Down" }
            r < 90 -> s.undo().let { "Undo" }
            r < 91 -> s.redo().let { "Redo" }
            r < 93 -> {
                val x0 = 30f + rnd.nextFloat() * 300f
                val y0 = 30f + rnd.nextFloat() * 300f
                val x1 = 30f + rnd.nextFloat() * 300f
                val y1 = 30f + rnd.nextFloat() * 300f
                s.selectIn(x0, y0, x1, y1); "select($x0,$y0,$x1,$y1)"
            }
            r < 95 -> s.move(s.selected, rnd.nextFloat() * 100f - 30f, rnd.nextFloat() * 100f - 30f).let { "moveSelected" }
            r < 96 -> s.deleteSelected().let { "deleteSelected" }
            r < 97 -> s.matrix(1 + rnd.nextInt(3), 1 + rnd.nextInt(3)).let { "matrix" }
            r < 98 -> s.layout.views.randomOrNull(rnd)?.let { v -> s.toggle(v.index); "toggle(${v.index})" } ?: "toggle-none"
            else -> s.layout.views.randomOrNull(rnd)?.let { v -> s.move(v.index, rnd.nextFloat() * 60f - 20f, rnd.nextFloat() * 60f - 20f); "move(${v.index})" } ?: "move-none"
        }
    }

    private val surface = Surface.makeRasterN32Premul(800, 1200)

    private fun paint(s: Session) {
        PagePainter.paint(surface.canvas, s.layout, Overlay(s.editing, s.crosshair, s.textEditing?.first, null, s.selected, null))
    }
}
