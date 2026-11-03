package pub.beermat.engine.eval

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every built-in has its entry in the manual's reference, and every example there evaluates (docs/manual.md#what-generates-itself). */
class FunctionDocsTest {

    @Test
    fun `every built-in function has a doc entry, and every entry a function`() {
        // READEXCEL is defined in the format module, which reads the files.
        val elsewhere = setOf("READEXCEL")
        val names = Builtins.functions.keys.map(Id::name).toSet()
        assertEquals(emptyList(), names.filter { it !in FunctionDocs.byName }.sorted(), "built-ins without a doc entry")
        assertEquals(emptyList(), FunctionDocs.all.map { it.name }.filter { it !in names && it !in elsewhere }, "entries for no built-in")
        assertEquals(FunctionDocs.all.size, FunctionDocs.byName.size, "a function listed twice")
    }

    @Test
    fun `every example evaluates`() {
        for (doc in FunctionDocs.all) {
            if (doc.example.isEmpty()) continue
            val outcomes = Evaluator().run(doc.example.map(Parser::parseStatement))
            outcomes.forEachIndexed { i, o -> if (o is Outcome.Failed) fail("${doc.name}: '${doc.example[i]}': ${o.error.message}") }
            val last = outcomes.last()
            assertTrue(last is Outcome.Evaluated, "${doc.name}: the example ends in a result")
            println("${doc.name}: ${doc.example.last()} → ${ResultText.format(last.shown)}")
        }
    }
}
