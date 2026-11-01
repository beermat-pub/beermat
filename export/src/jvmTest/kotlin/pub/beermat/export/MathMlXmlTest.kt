package pub.beermat.export

import pub.beermat.engine.syntax.Parser
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** The MathML parses as XML (with the JDK's parser, so on the JVM only). */
class MathMlXmlTest {

    @Test
    fun `MathML is well formed`() {
        val samples = listOf(
            "M_max := q*L^2/8", "x := (-b + sqrt(b^2 - 4*a*c))/(2*a)", "sum(k^2, k, 1, 10) = ?", "[1, 2; 3, 4]",
            "a < b and c >= d", "1.2e-3 Pa", "\"a & b\"", "f(x) :=\n  -x if x < 0\n  x otherwise", "vec(v)^T", "nthroot(27, 3)",
        )
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        for (s in samples) {
            val doc = builder.parse(MathMl().math(Parser.parseStatement(s)).byteInputStream())
            assertEquals("math", doc.documentElement.localName, s)
        }
    }
}
