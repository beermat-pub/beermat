package pub.beermat.format

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.ArrayValue
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.eval.EvalException
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Outcome
import pub.beermat.engine.eval.RangeValue
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Scope
import pub.beermat.engine.eval.Shown
import pub.beermat.engine.eval.Value
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity

/** One curve, in display units: [xs] and [ys] have the same length; NaN marks a gap. */
data class Series(
    val label: String,
    val xs: List<Double>,
    val ys: List<Double>,
    val unit: String?,
    val right: Boolean = false,
    val color: String? = null,
    val line: String = "solid",
    val marker: String = "none",
)

/** One axis's settings, in display units; null limits are automatic. [log]: a logarithmic axis; [step]: the tick spacing. */
data class Axis(
    val label: String,
    val unit: String?,
    val min: Double? = null,
    val max: Double? = null,
    val grid: Boolean = true,
    val log: Boolean = false,
    val step: Double? = null,
)

/** A plot ready to draw: numbers only, no units left to convert. */
data class PlotData(val x: Axis, val y: Axis, val y2: Axis?, val series: List<Series>, val title: String? = null)

/** A plot's data, or why it couldn't be drawn; [problems] also lists curves that failed. */
data class PlotOutcome(val block: PlotBlock, val data: PlotData?, val problems: List<String>)

/**
 * Evaluates a `plot` fence (docs/plots.md) in the scope of the sheet where it sits. The x axis
 * is either a range of a variable, sampled smoothly (`x: 0 m .. L`), a stepped range
 * (`x: 0, 0.5..3`), or a vector of data (`x: d`); each trace's `y` is evaluated at every x.
 */
object Plots {
    private const val DEFAULT_SAMPLES = 200
    private const val MAX_SAMPLES = 100_000

    fun evaluate(block: PlotBlock, scope: Scope, evaluator: Evaluator): PlotOutcome {
        val problems = mutableListOf<String>()
        fun fail(message: String) = PlotOutcome(block, null, problems + message)
        val yaml = try {
            Load(LoadSettings.builder().build()).loadFromString(block.body.joinToString("\n"))
        } catch (e: YamlEngineException) {
            return fail("the plot is not valid YAML: ${e.message?.lineSequence()?.first()}")
        } as? Map<*, *> ?: return fail("the plot needs settings such as 'x: 0 .. 10' and 'traces'")

        val xSpec = yaml["x"]?.toString() ?: return fail("the plot needs an x axis, such as 'x: 0 m .. L'")
        val (variable, xText) = Regex("""\s*([\p{L}][\p{L}\p{N}'_]*)\s*=(?!=)\s*(.*)""").matchEntire(xSpec)
            ?.let { Id(it.groupValues[1]) to it.groupValues[2] } ?: (Id("x") to xSpec)
        val samples = (yaml["samples"] as? Number)?.toInt()?.coerceIn(2, MAX_SAMPLES) ?: DEFAULT_SAMPLES
        val logX = ((yaml["axes"] as? Map<*, *>)?.get("x") as? Map<*, *>)?.get("log").let { it == true || it == "true" }

        // The x values, in SI.
        val xs: List<Quantity> = try {
            when (val e = parse("${variable.name} := $xText").let { (it as Define).value }) {
                is Range -> if (e.second == null) sampled(e, scope, evaluator, samples, logX) else points(rangeValue(Define(variable, e), scope))
                else -> points(evaluator.eval(e, scope))
            }
        } catch (ex: EvalException) {
            return fail("x: ${ex.message}")
        } catch (ex: ParseException) {
            return fail("x: ${ex.message}")
        }
        if (xs.isEmpty()) return fail("x: there are no values to plot")

        val axes = yaml["axes"] as? Map<*, *>
        val traces = (yaml["traces"] as? List<*>) ?: return fail("the plot needs a list of traces, each with a 'y'")
        val series = mutableListOf<Series>()
        // The x axis's unit comes from the first trace's x values: the variable's, or a parametric trace's own x.
        var xUnit: Unit? = null
        var xLabel = variable.name
        var leftUnit: Unit? = null
        var rightUnit: Unit? = null
        for ((k, t) in traces.withIndex()) {
            val trace = t as? Map<*, *>
            val yText = trace?.get("y")?.toString() ?: (t as? String)
            if (yText == null) {
                problems += "trace ${k + 1}: it needs a 'y'"
                continue
            }
            try {
                val y = expression(yText, "y")
                val xText = trace?.get("x")?.toString()
                // A parametric curve: its own x, at each value of the plot's variable.
                val xq: List<Quantity?> = xText?.let { traceValues(expression(it, "x"), variable, xs, scope, evaluator) } ?: xs
                val ys = traceValues(y, variable, xs, scope, evaluator)
                val xDim = xq.firstOrNull { it != null }?.dimension ?: Dimension.NONE
                if (xUnit == null) {
                    xUnit = unit(yaml["x_unit"]?.toString(), xDim, evaluator, scope) { problems += "x_unit: $it" }
                    if (xText != null) xLabel = xText
                }
                val xu = xUnit!!
                if (xDim != xu.dimension) problems += "trace ${k + 1}: its x has other units than the first trace's"
                val dim = ys.firstOrNull { it != null }?.dimension ?: Dimension.NONE
                val right = trace?.get("axis")?.toString() == "right"
                val unit = unit(trace?.get("unit")?.toString(), dim, evaluator, scope) { problems += "trace ${k + 1}: $it" }
                if (right) rightUnit = rightUnit ?: unit else leftUnit = leftUnit ?: unit
                series += Series(
                    label = trace?.get("label")?.toString() ?: yText,
                    xs = xq.map { q -> if (q == null || q.dimension != xu.dimension || !q.isReal) Double.NaN else xu.number(q.value.re) },
                    ys = ys.map { q -> if (q == null || q.dimension != unit.dimension || !q.isReal) Double.NaN else unit.number(q.value.re) },
                    unit = unit.text,
                    right = right,
                    color = (trace?.get("style") as? Map<*, *>)?.get("color")?.toString()?.takeIf { it != "auto" },
                    line = (trace?.get("style") as? Map<*, *>)?.get("line")?.toString() ?: "solid",
                    marker = (trace?.get("style") as? Map<*, *>)?.get("marker")?.toString() ?: "none",
                )
                if (ys.all { it == null }) problems += "trace ${k + 1} ($yText): no point could be evaluated"
            } catch (ex: EvalException) {
                problems += "trace ${k + 1} ($yText): ${ex.message}"
            } catch (ex: ParseException) {
                problems += "trace ${k + 1} ($yText): ${ex.message}"
            }
        }
        val xu = xUnit ?: unit(yaml["x_unit"]?.toString(), xs.first().dimension, evaluator, scope) { problems += "x_unit: $it" }
        val xAxis = axis(axes?.get("x"), xLabel + xu.label(), xu.text) { problems += it }
        val yLabel = series.filter { !it.right }.map { it.label }.distinct().joinToString(", ")
        val y2Label = series.filter { it.right }.map { it.label }.distinct().joinToString(", ")
        val yAxis = axis(axes?.get("y"), yLabel + (leftUnit?.label() ?: ""), leftUnit?.text) { problems += it }
        val y2Axis = rightUnit?.let { axis(axes?.get("y2"), y2Label + it.label(), it.text) { p -> problems += p } }
        return PlotOutcome(block, PlotData(xAxis, yAxis, y2Axis, series, yaml["title"]?.toString()), problems)
    }

    private fun expression(text: String, what: String): Expr =
        (parse(text) as? pub.beermat.engine.syntax.ExprStatement)?.expr ?: throw ParseException("$what must be an expression", 0)

    /** A display unit: its text (null for none), the SI value of one of it and of its zero (only °C and °F have one). */
    private data class Unit(val text: String?, val scale: Double, val dimension: Dimension, val zero: Double = 0.0) {
        fun label() = text?.let { " [$it]" } ?: ""

        fun number(si: Double) = (si - zero) / scale
    }

    private fun unit(given: String?, dim: Dimension, evaluator: Evaluator, scope: Scope, problem: (String) -> kotlin.Unit): Unit {
        if (given != null) {
            try {
                val (q, zero) = evaluator.displayUnit(Parser.parseExpression(given), scope)
                if (q.dimension == dim && q.isReal && q.value.re != 0.0) return Unit(given, q.value.re, dim, zero)
                problem("'$given' is not a unit of $dim")
            } catch (ex: Exception) {
                problem("'$given': ${ex.message}")
            }
        }
        val auto = if (dim.isNone) null else ResultText.parts(Shown(Scalar(Quantity(1.0, dim)))).unit
        return Unit(auto, 1.0, dim)
    }

    private fun axis(spec: Any?, defaultLabel: String, unit: String?, problem: (String) -> kotlin.Unit): Axis {
        val m = spec as? Map<*, *>
        fun number(key: String) = (m?.get(key) as? Number)?.toDouble() ?: m?.get(key)?.toString()?.toDoubleOrNull()
        fun flag(key: String) = m?.get(key)?.let { it == true || it == "true" }
        val log = flag("log") ?: false
        var min = number("min")
        var max = number("max")
        if (log && ((min ?: 1.0) <= 0 || (max ?: 1.0) <= 0)) {
            problem("a log axis needs limits above 0")
            min = min?.takeIf { it > 0 }
            max = max?.takeIf { it > 0 }
        }
        val step = number("step")?.takeIf { it > 0 }
        if (m?.get("step") != null && step == null) problem("an axis step must be a number above 0")
        return Axis(
            label = m?.get("label")?.toString() ?: defaultLabel,
            unit = unit,
            min = min,
            max = max,
            grid = flag("grid") ?: true,
            log = log,
            step = step,
        )
    }

    /** `a .. b` sampled evenly with [n] points (evenly in log with [log]). */
    private fun sampled(r: Range, scope: Scope, evaluator: Evaluator, n: Int, log: Boolean): List<Quantity> {
        val a = (evaluator.eval(r.first, scope) as? Scalar)?.q ?: throw EvalException("the range must start at a number")
        val b = (evaluator.eval(r.last, scope) as? Scalar)?.q ?: throw EvalException("the range must end at a number")
        if (a.dimension != b.dimension) throw EvalException("units do not match: ${a.dimension} and ${b.dimension}")
        if (!a.isReal || !b.isReal) throw EvalException("the range needs real numbers")
        val (x0, x1) = a.value.re to b.value.re
        // On a log axis, evenly spaced in log, so every decade gets its share of the points.
        if (log && x0 > 0 && x1 > 0) return List(n) { k -> Quantity(x0 * Math.pow(x1 / x0, k.toDouble() / (n - 1)), a.dimension) }
        return List(n) { k -> Quantity(x0 + (x1 - x0) * k / (n - 1), a.dimension) }
    }

    /** A stepped range `a, b..c`, by the evaluator's own range rules. */
    private fun rangeValue(d: Define, scope: Scope): Value {
        val (outcomes, scopes) = Evaluator(scope).runWithScopes(listOf(d))
        (outcomes.single() as? Outcome.Failed)?.let { throw it.error }
        return (scopes.last()[d.target as Id] as Scope.Binding.Bound).value
    }

    private fun points(value: Value): List<Quantity> {
        return when (value) {
            is RangeValue -> List(value.count) { value[it] }
            is ArrayValue -> value.elements.map { (it as? Scalar)?.q ?: throw EvalException("x must be numbers") }
            is Scalar -> listOf(value.q)
            else -> throw EvalException("x must be a range or a vector of numbers")
        }
    }

    /**
     * The y value at each x. An expression that doesn't use the variable and gives a vector as
     * long as x is data, taken element by element; anything else is evaluated at every x.
     */
    private fun traceValues(y: Expr, variable: Id, xs: List<Quantity>, scope: Scope, evaluator: Evaluator): List<Quantity?> {
        if (!uses(y, variable)) {
            val v = evaluator.eval(y, scope)
            if (v is ArrayValue && v.elements.size == xs.size) return v.elements.map { (it as? Scalar)?.q }
        }
        var firstError: EvalException? = null
        val ys = xs.map { x ->
            try {
                (evaluator.eval(y, scope.with(variable, Scalar(x))) as? Scalar)?.q
            } catch (ex: EvalException) {
                firstError = firstError ?: ex
                null
            }
        }
        if (ys.all { it == null }) firstError?.let { throw it }
        return ys
    }

    private fun uses(e: Expr, id: Id): Boolean = when (e) {
        is Id -> e == id
        is Apply -> e.args.any { uses(it, id) }
        is Call -> uses(e.fn, id) || e.args.any { uses(it, id) }
        is Index -> uses(e.target, id) || e.indices.any { uses(it, id) }
        is Parens -> uses(e.inner, id)
        is Matrix -> e.rows.flatten().any { uses(it, id) }
        else -> false
    }

    private fun parse(s: String) = Parser.parseStatement(s)
}
