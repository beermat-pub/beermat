package pub.beermat.engine.display

import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultTextTest {

    private val plain = ResultFormat(exponentialThreshold = 99)

    /** The text of the last statement's result. */
    private fun text(vararg lines: String, f: ResultFormat = plain): String {
        val shown = when (val o = Evaluator().run(lines.map(Parser::parseStatement)).last()) {
            is Outcome.Evaluated -> o.shown
            is Outcome.Defined -> o.shown!!
            else -> error("no result: $o")
        }
        return ResultText.format(shown, f)
    }

    @Test
    fun `sticky units are written as typed`() {
        assertEquals("54 kN*m", text("q := 12 kN/m", "L := 6 m", "M := q*L^2/8 = ? kN*m"))
        assertEquals("3000 mm", text("x := 3 m", "x = ? mm"))
        assertEquals("30 deg", text("α := π/6", "α = ? deg"))
    }

    @Test
    fun `powers and reciprocals of derived units`() {
        assertEquals("4 N^2", text("(2 N)^2 = ?"))
        assertEquals("0.5/N", text("1/(2 N) = ?"))
        assertEquals("0.5 m/N", text("1 m/(2 N) = ?"))
        assertEquals("0.5/(N*m)", text("1/(2 N*m) = ?"))
        assertEquals("5/s", text("5 Hz = ?"))
        assertEquals("[1; 2]/N", text("[1; 2]/(1 N) = ?"))
        assertEquals("1/kg", text("1/(1 kg) = ?"))
    }

    @Test
    fun `results parse back`() {
        for (s in listOf("0.5/N", "0.5 m/N", "(3 + 4i)*m", "[1, 2; 3, 4]*m", "[3000 N; 2000 N*m]", "5/s", "2 m^(1/2)", "1.2e7 Pa")) {
            Parser.parseExpression(s)
        }
    }

    @Test
    fun `automatic units`() {
        assertEquals("54000 N*m", text("q := 12 kN/m", "L := 6 m", "M := q*L^2/8 = ?"))
        assertEquals("12000 N/m", text("q := 12 kN/m = ?"))
        assertEquals("N*m^2", text("EI := 210 GPa*8356 cm^4 = ?").substringAfter(' '))
        assertEquals("Pa*s", text("μ := 1 Pa*1 s = ?").substringAfter(' '))
        assertEquals("W/m^2", text("1 W/(1 m^2) = ?").substringAfter(' '))
        assertEquals("N*s", text("1 kg*1 m/(1 s) = ?").substringAfter(' '))
        assertEquals("V/m", text("1 V/(1 m) = ?").substringAfter(' '))
        assertEquals("kg/m^3", text("1 kg/(1 m^3) = ?").substringAfter(' '))
        assertEquals("kg*m^2", text("1 kg*(1 m)^2 = ?").substringAfter(' '))
        assertEquals("2.1e11 Pa", text("E := 210 GPa = ?", f = ResultFormat()))
        assertEquals("9.807 m/s^2", text("g = ?"))
        assertEquals("1/s", text("1/(1 s) = ?").substringAfter(' '))
        assertEquals("Pa*s", text("1 kg/(1 m*1 s) = ?").substringAfter(' '))
        assertEquals("1/(m*s)", text("1/(1 m*1 s) = ?").substringAfter(' '))
        assertEquals("m^(1/2)", text("sqrt(4 m) = ?").substringAfter(' '))
        assertEquals("2 m^(1/2)", text("sqrt(4 m) = ?"))
        assertEquals("0.5", text("sin(30 deg) = ?"))
    }

    @Test
    fun `significant figures, end to end`() {
        val sig3 = plain.copy(rounding = Rounding.Significant(3))
        assertEquals("11.5 mm", text("δ := 5*12 kN/m*(6 m)^4/(384*210 GPa*8356 cm^4) = ? mm", f = sig3))
        assertEquals("1230 kN", text("F := 1234.567 kN = ? kN", f = sig3.copy(rounding = Rounding.Significant(3, strict = true))))
    }

    @Test
    fun `other values`() {
        assertEquals("(3 + 4i)*m", text("(3 + 4i)*1 m = ?"))
        assertEquals("\"OK\"", text("\"OK\" = ?"))
        assertEquals("1", text("1 < 2 = ?"))
    }
}
