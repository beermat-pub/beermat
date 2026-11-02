package pub.beermat.engine.display

import pub.beermat.engine.eval.ArrayValue
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.eval.RangeValue
import pub.beermat.engine.eval.FunctionValue
import pub.beermat.engine.eval.Module
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Shown
import pub.beermat.engine.eval.Text
import pub.beermat.engine.eval.Value
import pub.beermat.engine.eval.quantity
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.units.BaseDimension
import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity
import pub.beermat.engine.units.Rational
import kotlin.math.pow

/**
 * The text form of a result (docs/results.md): the number, a space, and the unit in linear
 * syntax, e.g. `54 kN*m`. Used by the CLI and by the file's result cache.
 */
object ResultText {

    /**
     * A result split into its [number] (a number, a complex number in parentheses, a matrix, a
     * string…) and its [unit], both in linear syntax; [unit] is null for a pure number, or when
     * each element of an array carries its own.
     */
    data class Parts(val number: String, val unit: String?) {
        val text: String get() = unit?.let { attach(number, it) } ?: number
    }

    fun format(shown: Shown, f: ResultFormat = ResultFormat()): String = parts(shown, f).text

    /** The unit a result of dimension [d] is shown in without a placeholder, such as `N*m` or `m/s^2`. */
    fun unitText(d: pub.beermat.engine.units.Dimension): String = AutoUnit.text(d)

    fun parts(shown: Shown, f: ResultFormat = ResultFormat()): Parts = when (val v = shown.value) {
        is Scalar -> quantity(v.q, shown, f)
        is ArrayValue -> array(v, shown, f)
        is RangeValue -> array(ArrayValue.column(v.values), shown, f)
        is Text -> Parts(Printer.print(Str(v.value)), null)
        is Module -> Parts(v.toString(), null)
        is FunctionValue -> Parts(v.toString(), null)
    }

    /**
     * `[1, 2; 3, 4] m` when every element has the same unit (or the sticky unit applies);
     * otherwise each element with its own: `[3 kN; 2 kN*m]`.
     */
    private fun array(a: ArrayValue, shown: Shown, f: ResultFormat): Parts {
        shown.unit?.let { unit ->
            return Parts(matrix(a) { NumberFormat.format((it.quantity("a result").value - Complex(shown.zero)) / unit.value, f) }, Printer.print(shown.unitExpr!!))
        }
        val dims = a.elements.map { (it as? Scalar)?.q?.dimension }.distinct()
        val dim = dims.singleOrNull() ?: return Parts(matrix(a) { format(Shown(it), f) }, null)
        if (dim.isNone) return Parts(matrix(a) { NumberFormat.format((it as Scalar).q.value, f) }, null)
        val largest = a.elements.maxOf { (it as Scalar).q.value.abs() }
        val (unit, factor) = Prefixes.unit(AutoUnit.text(dim), largest, f)
        return Parts(matrix(a) { NumberFormat.format((it as Scalar).q.value / Complex(factor), f) }, unit)
    }

    /**
     * A number and its unit, written so that it parses back: `54 kN*m`, but `5/s` and `2/(N*m)`
     * rather than `5 1/s`, and `(3 + 4i)*m` or `[1; 2]*m`, since only a plain number can be followed by a unit.
     */
    private fun attach(number: String, unit: String) = when {
        unit.startsWith("1/") -> number + unit.substring(1)
        number.startsWith("(") || number.startsWith("[") -> "$number*$unit"
        else -> "$number $unit"
    }

    private fun matrix(a: ArrayValue, element: (Value) -> String): String =
        (0 until a.rows).joinToString("; ", "[", "]") { r -> (0 until a.cols).joinToString(", ") { c -> element(a[r, c]) } }

    private fun quantity(q: Quantity, shown: Shown, f: ResultFormat): Parts {
        val unit = shown.unit
        val (number, unitText) = when {
            unit != null -> ((q.value - Complex(shown.zero)) / unit.value) to Printer.print(shown.unitExpr ?: error("a unit needs its expression"))
            q.dimension.isNone -> q.value to null
            else -> Prefixes.unit(AutoUnit.text(q.dimension), q.value.abs(), f).let { (text, factor) -> q.value / Complex(factor) to text }
        }
        val n = NumberFormat.format(number, f)
        return Parts(if (unitText != null && ' ' in n) "($n)" else n, unitText)
    }
}

/**
 * Engineering prefixes on an automatic unit (docs/units.md#display, rule 3): its first unit gets
 * the prefix that puts the number between 1 and 1000, from the prefixed units in the built-in
 * table only, in steps of a thousand. Mass goes from mg to t. Time, temperatures, and units that
 * start with a reciprocal or a fractional power, never get one.
 */
internal object Prefixes {
    private val steps = listOf("p" to -12, "n" to -9, "μ" to -6, "m" to -3, "k" to 3, "M" to 6, "G" to 9, "T" to 12)

    private val mass = listOf("mg" to 1e-6, "\\rm g" to 1e-3, "kg" to 1.0, "t" to 1e3)

    private val never = setOf("s", "K")

    private val forms = mutableMapOf<String, List<Pair<String, Double>>>()

    /** The unit [unit] with a prefix for a number of size [magnitude], and what the number is divided by. */
    fun unit(unit: String, magnitude: Double, f: ResultFormat): Pair<String, Double> {
        if (!f.prefixes || magnitude == 0.0 || !magnitude.isFinite()) return unit to 1.0
        val first = unit.takeWhile { it != '*' && it != '/' && it != '^' }
        val rest = unit.substring(first.length)
        val power = if (rest.startsWith("^")) rest.drop(1).takeWhile { it.isDigit() }.toIntOrNull() ?: return unit to 1.0 else 1
        val candidates = forms.getOrPut(first) { candidates(first) }
        if (candidates.size < 2) return unit to 1.0
        val scaled = candidates.map { (name, factor) -> Triple(name, factor, magnitude / factor.pow(power)) }
        // The largest unit that leaves at least 1; for a number smaller than every one, the smallest unit.
        val (name, factor) = scaled.filter { it.third >= 1 - 1e-9 }.maxByOrNull { it.second }?.let { it.first to it.second }
            ?: scaled.minBy { it.second }.let { it.first to it.second }
        return name + rest to factor.pow(power)
    }

    /** The unit itself and its prefixed forms in the table, with their factors. */
    private fun candidates(symbol: String): List<Pair<String, Double>> {
        if (symbol == "kg") return mass
        if (symbol in never || symbol.isEmpty() || symbol[0].isDigit()) return emptyList()
        val one = (Builtins.units[Id(symbol)] as? Scalar)?.q ?: return emptyList()
        return listOf(symbol to 1.0) + steps.mapNotNull { (prefix, exponent) ->
            val q = (Builtins.units[Id(prefix + symbol)] as? Scalar)?.q ?: return@mapNotNull null
            val factor = 10.0.pow(exponent)
            (prefix + symbol to factor).takeIf { q.dimension == one.dimension && kotlin.math.abs(q.value.re / one.value.re / factor - 1) < 1e-12 }
        }
    }
}

/**
 * Chooses the unit for a result with no unit placeholder, in SI (docs/units.md#display). All
 * choices are coherent (factor 1). In order of preference:
 *
 * 1. a named derived unit whose dimension matches exactly, except that N·m is shown as `N*m`
 *    rather than `J`, because moments are far more common than energies in engineering;
 * 2. a derived unit squared or cubed, such as `N^2`;
 * 3. a derived unit times or over `m` or `s`, squared at most, such as `N/m`, `N*m^2`, `Pa*s`,
 *    `W/m^2`;
 * 4. the reciprocal of one of those: `1/N`, `m/N`, `1/(N*m)`;
 * 5. a product of base units, such as `m/s^2`.
 *
 * Rules 2 to 4 apply only when the base-unit form has at least two factors.
 */
internal object AutoUnit {
    /** Derived units with a dimension of their own; not Hz, Bq, Gy or Sv, which share one. */
    private val names = listOf("N", "Pa", "J", "W", "C", "V", "Ω", "F", "H", "T", "Wb", "S")

    private val dimensions: Map<String, Dimension> = names.associateWith { name ->
        val q = (Builtins.units.getValue(Id(name)) as Scalar).q
        check(q.value.re == 1.0) { "$name is not coherent" }
        q.dimension
    }

    private val derived: Map<Dimension, String> =
        dimensions.entries.associate { (name, dim) -> dim to if (name == "J") "N*m" else name }

    /** Bases a derived unit may be combined with in rule 2. */
    private val residualBases = listOf(BaseDimension.LENGTH to "m", BaseDimension.TIME to "s")

    private val baseSymbols = listOf(
        BaseDimension.MASS to "kg",
        BaseDimension.LENGTH to "m",
        BaseDimension.TIME to "s",
        BaseDimension.CURRENT to "A",
        BaseDimension.TEMPERATURE to "K",
        BaseDimension.AMOUNT to "mol",
        BaseDimension.LUMINOSITY to "cd",
    )

    fun text(d: Dimension): String {
        derived[d]?.let { return it }
        if (BaseDimension.entries.count { !d[it].isZero } >= 2) {
            named(d)?.let { return it.text }
            // Rule 4: the reciprocal of a named form: 1/N, 1/(N*m), m/N.
            named(Dimension.NONE / d)?.let { return it.inverse().text }
        }
        return baseProduct(d)
    }

    /** A unit as [num] over [den]. */
    private data class Fraction(val num: String, val den: String? = null) {
        val text: String get() = if (den == null) num else "$num/${group(den)}"

        fun inverse() = if (den == null) Fraction("1", num) else Fraction(den, num)

        private fun group(s: String) = if ('*' in s || '/' in s) "($s)" else s
    }

    /** Rules 1 to 3 as a fraction, or null. */
    private fun named(d: Dimension): Fraction? {
        derived[d]?.let { return Fraction(it) }
        // Rule 2: a derived unit squared or cubed (N^2, the determinant of a stiffness matrix).
        for (name in names) {
            if (name == "J") continue
            for (n in 2..3) if (dimensions.getValue(name).pow(n) == d) return Fraction("$name^$n")
        }
        return combined(d)
    }

    /** Rule 3: the first derived unit (other than J, which `N*m` covers) that leaves one power of m or s. */
    private fun combined(d: Dimension): Fraction? {
        for (name in names) {
            if (name == "J") continue
            val residual = d / dimensions.getValue(name)
            for ((base, symbol) in residualBases) {
                val e = residual[base]
                // Powers up to 2 only, or kg*m^2 would become W*s^3.
                if (e.isZero || !e.isInteger || kotlin.math.abs(e.num) > 2 || residual != Dimension.of(base to e.num)) continue
                return if (e > Rational.ZERO) Fraction("$name*${power(symbol, e)}") else Fraction(name, power(symbol, -e))
            }
        }
        return null
    }

    private fun baseProduct(d: Dimension): String {
        val num = baseSymbols.filter { d[it.first] > Rational.ZERO }.map { power(it.second, d[it.first]) }
        val den = baseSymbols.filter { d[it.first] < Rational.ZERO }.map { power(it.second, -d[it.first]) }
        val top = if (num.isEmpty()) "1" else num.joinToString("*")
        return when (den.size) {
            0 -> top
            1 -> "$top/${den[0]}"
            else -> "$top/(${den.joinToString("*")})"
        }
    }

    private fun power(symbol: String, e: Rational) = when {
        e == Rational.ONE -> symbol
        e.isInteger -> "$symbol^$e"
        else -> "$symbol^($e)"
    }
}
