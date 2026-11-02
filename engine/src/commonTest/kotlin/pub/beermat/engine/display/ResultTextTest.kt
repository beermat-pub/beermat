package pub.beermat.engine.display

import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Shown
import pub.beermat.engine.syntax.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultTextTest {

    // Without prefixes, so the tests of the automatic unit see its plain form.
    private val plain = ResultFormat(exponentialThreshold = 99, prefixes = false)

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
        assertEquals("2.1e11 Pa", text("E := 210 GPa = ?", f = ResultFormat(prefixes = false)))
        assertEquals("9.807 m/s^2", text("g = ?"))
        assertEquals("1/s", text("1/(1 s) = ?").substringAfter(' '))
        assertEquals("Pa*s", text("1 kg/(1 m*1 s) = ?").substringAfter(' '))
        assertEquals("1/(m*s)", text("1/(1 m*1 s) = ?").substringAfter(' '))
        assertEquals("m^(1/2)", text("sqrt(4 m) = ?").substringAfter(' '))
        assertEquals("2 m^(1/2)", text("sqrt(4 m) = ?"))
        assertEquals("0.5", text("sin(30 deg) = ?"))
    }

    @Test
    fun `engineering prefixes`() {
        val on = plain.copy(prefixes = true)
        fun shown(vararg lines: String) = text(*lines, f = on)
        assertEquals("54 kN*m", shown("q := 12 kN/m", "L := 6 m", "M := q*L^2/8 = ?"))
        assertEquals("12 kN/m", shown("q := 12 kN/m = ?"))
        assertEquals("210 GPa", shown("E := 210 GPa = ?"))
        assertEquals("12 mm", shown("0.012 m = ?"))
        assertEquals("3.5 km", shown("3500 m = ?"))
        assertEquals("5 μm", shown("0.000005 m = ?"))
        assertEquals("250 N", shown("250 N = ?"))
        // No mN in the table: the plain unit.
        assertEquals("0.5 N", shown("0.5 N = ?"))
        // Past the largest prefix in the table, the largest.
        assertEquals("5000 GPa", shown("5e12 Pa = ?"))
        // A squared unit takes the prefix squared.
        assertEquals("1500 mm^2", shown("0.0015 m^2 = ?"))
        assertEquals("[3 kN; 2 kN*m]", shown("[3000 N; 2000 N*m] = ?"))
        assertEquals("[0.5; 2.5]*kN", shown("[500; 2500]*N = ?"))
        // Mass: grams up to kg, then tonnes.
        assertEquals("250 \\rm g", shown("0.25 kg = ?"))
        assertEquals("75 kg", shown("75 kg = ?"))
        assertEquals("7.85 t/m^3", shown("7850 kg/m^3 = ?"))
        // Never on time, temperature, reciprocals or plain numbers, nor on zero.
        assertEquals("7200 s", shown("2 hr = ?"))
        assertEquals("300 K", shown("300 K = ?"))
        assertEquals("5000/s", shown("5 kHz = ?"))
        assertEquals("12000", shown("12000 = ?"))
        assertEquals("0 N", shown("0 kN = ?"))
        // A unit asked for wins; off, the plain unit.
        assertEquals("54000 N*m", shown("M := 54 kN*m = ? N*m"))
        assertEquals("54000 N*m", text("M := 54 kN*m = ?"))
        // What it writes reads back as the same value.
        for (s in listOf("250 \\rm g", "7.85 t/m^3", "[0.5; 2.5]*kN", "1500 mm^2", "5 μm")) Parser.parseExpression(s)
    }

    @Test
    fun `a big array shows a window, in the unit of the whole`() {
        val shown = when (val o = Evaluator().run(listOf("i := 0..99", "x[i] := i*1 mm", "x = ?").map(Parser::parseStatement)).last()) {
            is Outcome.Evaluated -> o.shown
            else -> error("no result: $o")
        }
        val big = ResultText.table(shown)!!
        assertEquals(100 to 1, big.rows to big.cols)
        // The largest element (99 mm) picks the unit for every window.
        val first = ResultText.parts(shown, plain.copy(prefixes = true), ResultText.Window())
        assertEquals("[0; 1; 2; 3; 4; 5; 6; 7; 8; 9; 10; 11; 12; 13; 14; 15; 16; 17; 18; 19]" to "mm", first.number to first.unit)
        // A window past the end stops at the last 20 rows.
        val last = ResultText.parts(shown, plain.copy(prefixes = true), ResultText.Window(row = 95))
        assertEquals("80", last.number.removePrefix("[").substringBefore(";"))
        assertEquals(80 until 100, ResultText.Window(row = 95).rows(100))
        // Small arrays aren't tables.
        assertEquals(null, ResultText.table(Shown(pub.beermat.engine.eval.ArrayValue.column(List(20) { Scalar(pub.beermat.engine.units.Quantity(1.0)) }))))
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
