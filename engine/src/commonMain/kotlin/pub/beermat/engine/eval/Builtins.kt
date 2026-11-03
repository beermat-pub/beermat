package pub.beermat.engine.eval

import kotlin.math.E
import kotlin.math.PI
import pub.beermat.engine.Math
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Quantity

/** The built-in scope: constants, units and the core function library. */
object Builtins {

    /** The SI base units. The rest of the unit library ([units]) is defined in terms of these. */
    val baseUnits: Map<Id, Value> = mapOf(
        "m" to Dimension.LENGTH,
        "kg" to Dimension.MASS,
        "s" to Dimension.TIME,
        "A" to Dimension.CURRENT,
        "K" to Dimension.TEMPERATURE,
        "mol" to Dimension.AMOUNT,
        "cd" to Dimension.LUMINOSITY,
    ).entries.associate { (name, dim) -> Id(name) to Scalar(Quantity(1.0, dim)) }

    /** Constants and built-in variables. `TOL` is the comparison (and later solver) tolerance; `ORIGIN` the first index. */
    val constants: Map<Id, Value> = mapOf(
        Id("π") to Scalar(Quantity(PI)),
        Id("e") to Scalar(Quantity(E)),
        Id(Id.INFINITY) to Scalar(Quantity(Double.POSITIVE_INFINITY)),
        Id("TOL") to Scalar(Quantity(1e-3)),
        Id("ORIGIN") to Scalar(Quantity(0.0)),
    )

    /**
     * The temperature scales, `°C` and `°F` (docs/units.md#temperature): the SI value of their
     * zero. One degree of each is in the unit table.
     */
    val temperatureZeros: Map<Id, Double> = mapOf(Id("°C") to 273.15, Id("°F") to 273.15 - 32.0 * 5 / 9)

    val functions: Map<Id, Value> = listOf(
        unary("sqrt") { it.sqrt() },
        Builtin("nthroot", 2..2) { a ->
            val n = a[1]().real("nthroot", dimensionless = true)
            if (n != Math.rint(n) || n == 0.0) throw EvalException("root: the index must be a non-zero integer")
            val x = a[0]().quantity("nthroot")
            // An odd root of a negative number is the real one: nthroot(-8, 3) is -2.
            val negative = x.isReal && x.value.re < 0 && n.toInt() % 2 != 0
            Scalar(if (negative) -(-x).root(n.toInt()) else x.root(n.toInt()))
        },
        unary("abs") { Quantity(it.value.abs(), it.dimension) },
        unary("Re") { Quantity(it.value.re, it.dimension) },
        unary("Im") { Quantity(it.value.im, it.dimension) },
        unary("arg") { Quantity(it.value.arg()) },
        dimensionless("exp") { it.exp() },
        dimensionless("ln") { it.ln() },
        Builtin("log", 1..2) { a ->
            val x = a[0]().quantity("log").requireNone("log").value
            val base = if (a.size == 2) a[1]().quantity("log").requireNone("log").value else Complex(10.0)
            Scalar(Quantity(if (base == Complex(10.0) && x.isReal && x.re >= 0) Complex(kotlin.math.log10(x.re)) else x.ln() / base.ln()))
        },
        angular("sin") { it.sin() },
        angular("cos") { it.cos() },
        angular("tan") { it.sin() / it.cos() },
        realFunction("asin") { kotlin.math.asin(it) },
        realFunction("acos") { kotlin.math.acos(it) },
        realFunction("atan") { kotlin.math.atan(it) },
        realFunction("sinh") { kotlin.math.sinh(it) },
        realFunction("cosh") { kotlin.math.cosh(it) },
        realFunction("tanh") { kotlin.math.tanh(it) },
        realFunction("floor") { kotlin.math.floor(it) },
        realFunction("ceil") { kotlin.math.ceil(it) },
        Builtin("atan2", 2..2) { a ->
            // The traditional argument order: atan2(x, y) is the angle of the point (x, y). *To verify.*
            val x = a[0]().quantity("atan2")
            val y = a[1]().quantity("atan2")
            if (x.dimension != y.dimension) throw EvalException("atan2: units do not match")
            Scalar(Quantity(kotlin.math.atan2(y.realValue("atan2"), x.realValue("atan2"))))
        },
        Builtin("round", 1..2) { a ->
            val x = a[0]().real("round", dimensionless = true)
            val n = if (a.size == 2) a[1]().real("round", dimensionless = true).toInt() else 0
            val scale = Math.pow(10.0, n.toDouble())
            Scalar(Quantity(Math.round(x * scale) / scale))
        },
        Builtin("mod", 2..2) { a ->
            val x = a[0]().quantity("mod")
            val y = a[1]().quantity("mod")
            if (x.dimension != y.dimension) throw EvalException("mod: units do not match")
            Scalar(Quantity(x.realValue("mod").rem(y.realValue("mod")), x.dimension))
        },
        extremum("max") { a, b -> a > b },
        extremum("min") { a, b -> a < b },
        // Arrays. Sizes count elements; positions count from ORIGIN.
        Builtin("rows", 1..1) { a -> count((a[0]() as? ArrayValue)?.rows ?: 0) },
        Builtin("cols", 1..1) { a -> count((a[0]() as? ArrayValue)?.cols ?: 0) },
        Builtin("length", 1..1) { a -> count(vector(a[0](), "length").elements.size) },
        Builtin("last", 1..1) { a -> count(origin + vector(a[0](), "last").elements.size - 1) },
        // matrix(m, n, f): element (i, j) is f(i, j), counting from 0 whatever ORIGIN is (decided with Dan).
        Builtin("matrix", 3..3) { a ->
            val m = size(a[0](), "matrix")
            val n = size(a[1](), "matrix")
            val f = a[2]() as? FunctionValue ?: throw EvalException("matrix: the third argument must be a function f(i, j)")
            if (m < 1 || n < 1) throw EvalException("matrix needs at least one row and one column")
            ArrayValue.of(m, n) { i, j -> call(f, listOf(Scalar(Quantity(i.toDouble())), Scalar(Quantity(j.toDouble())))) }
        },
        Builtin("identity", 1..1) { a ->
            val n = size(a[0](), "identity")
            if (n < 1) throw EvalException("identity needs a size of at least 1")
            MatrixMath.identity(n)
        },
        Builtin("augment", 1..Int.MAX_VALUE) { a ->
            val ms = a.map { asArray(it()) }
            if (ms.any { it.rows != ms[0].rows }) throw EvalException("augment: the matrices need the same number of rows")
            val cols = ms.flatMap { m -> (0 until m.cols).map { c -> m to c } }
            ArrayValue.of(ms[0].rows, cols.size) { r, c -> cols[c].let { (m, k) -> m[r, k] } }
        },
        Builtin("stack", 1..Int.MAX_VALUE) { a ->
            val ms = a.map { asArray(it()) }
            if (ms.any { it.cols != ms[0].cols }) throw EvalException("stack: the matrices need the same number of columns")
            val rows = ms.flatMap { m -> (0 until m.rows).map { r -> m to r } }
            ArrayValue.of(rows.size, ms[0].cols) { r, c -> rows[r].let { (m, k) -> m[k, c] } }
        },
        Builtin("submatrix", 5..5) { a ->
            val m = MatrixMath.array(a[0](), "submatrix")
            val (r0, r1, c0, c1) = (1..4).map { size(a[it](), "submatrix") - origin }
            if (r0 < 0 || c0 < 0 || r1 >= m.rows || c1 >= m.cols || r0 > r1 || c0 > c1) {
                throw EvalException("submatrix: rows ${r0 + origin}..${r1 + origin} and columns ${c0 + origin}..${c1 + origin} are not inside the ${m.size} matrix (ORIGIN = $origin)")
            }
            ArrayValue.of(r1 - r0 + 1, c1 - c0 + 1) { r, c -> m[r0 + r, c0 + c] }
        },
        Builtin("tr", 1..1) { a ->
            val m = MatrixMath.array(a[0](), "tr")
            if (m.rows != m.cols) throw EvalException("tr needs a square matrix, not ${m.size}")
            Scalar((0 until m.rows).map { m[it, it].quantity("tr") }.reduce(Quantity::plus))
        },
        // Special forms: sum(expr, i[, a, b]) and prod(…) bind i; the one-argument forms are the vector sum and product.
        Builtin("sum", 1..4, special = true) { a -> Scalar(numbers(a, "sum").reduce(Quantity::plus)) },
        Builtin("prod", 1..4, special = true) { a -> Scalar(numbers(a, "prod").reduce(Quantity::times)) },
        Builtin("vec", 1..1, special = true) { a -> a[0]() },
        // diff(f, x[, n]) and int(f, x, a, b) bind x too; the evaluator does them (Calculus).
        Builtin("diff", 2..3, special = true) { _ -> throw EvalException("diff is evaluated by the evaluator") },
        Builtin("int", 4..4, special = true) { _ -> throw EvalException("int is evaluated by the evaluator") },
        Builtin("root", 2..4, special = true) { _ -> throw EvalException("root is evaluated by the evaluator") },
        // Solve blocks: find(x, y, …) and minerr(…) use the constraints below the last `given`.
        Builtin("find", 1..Int.MAX_VALUE, special = true) { _ -> throw EvalException("find is evaluated by the evaluator") },
        Builtin("minerr", 1..Int.MAX_VALUE, special = true) { _ -> throw EvalException("minerr is evaluated by the evaluator") },
        // Minimize(f, x, …) and Maximize(…): the x where f is least or greatest, within the solve block's constraints if there is one.
        Builtin("Minimize", 2..Int.MAX_VALUE, special = true) { _ -> throw EvalException("Minimize is evaluated by the evaluator") },
        Builtin("Maximize", 2..Int.MAX_VALUE, special = true) { _ -> throw EvalException("Maximize is evaluated by the evaluator") },
        // Odesolve(t, b[, steps]) or Odesolve([x; y], t, b[, steps]): the solve block's differential equations, solved (OdeSolve).
        Builtin("Odesolve", 2..4, special = true) { _ -> throw EvalException("Odesolve is evaluated by the evaluator") },
        // use "file.py": a module of Python functions (docs/symbolic.md#python-functions-in-a-sheet).
        Builtin("use", 1..1, special = true) { _ -> throw EvalException("use is evaluated by the evaluator") },
        Builtin("mean", 1..Int.MAX_VALUE) { a -> numbers(a, "mean").let { qs -> Scalar(qs.reduce(Quantity::plus) / Quantity(qs.size.toDouble())) } },
        // if(cond, a, b): only the chosen branch is evaluated, so recursion terminates.
        Builtin("if", 3..3) { a -> if (a[0]().truth("if")) a[1]() else a[2]() },
    ).plus(Fitting.functions).plus(Library.functions).plus(Numerics.functions).plus(DataFiles.functions).associateBy { Id(it.name) }

    /**
     * The built-ins for a sheet with [options] (docs/units.md#angle-and-currency-as-dimensions):
     * with angles as a dimension, `rad` is its unit and the inverse trigonometric functions return
     * angles; with a currency, its name is a unit of a currency dimension. The scope, and the units.
     */
    fun forSheet(options: UnitOptions): Pair<Scope, Map<Id, Value>> = sheets.getOrPut(options) {
        var extra = mapOf<Id, Value>()
        var fns = mapOf<Id, Value>()
        if (options.angle) {
            extra = extra + ANGLE_UNITS.associate { name ->
                val q = (units.getValue(Id(name)) as Scalar).q
                Id(name) to Scalar(Quantity(q.value, Dimension.ANGLE))
            }
            fns = ANGLE_RESULTS.associate { name ->
                val f = functions.getValue(Id(name)) as Builtin
                Id(name) to Builtin(name, f.arity) { a ->
                    val q = (f.impl(this, a) as Scalar).q
                    Scalar(Quantity(q.value, Dimension.ANGLE))
                }
            }
        }
        options.currency?.let { extra = extra + (Id(it) to Scalar(Quantity(1.0, Dimension.CURRENCY))) }
        val all = units + extra
        scope.withAll(extra).withAll(fns) to all
    }

    private val sheets = mutableMapOf<UnitOptions, Pair<Scope, Map<Id, Value>>>()

    /** The units of an angle, from `units.bbm`. */
    private val ANGLE_UNITS = listOf("rad", "deg", "rev", "gon")

    /** The functions that return an angle. */
    private val ANGLE_RESULTS = listOf("asin", "acos", "atan", "atan2", "arg", "acot", "asec", "acsc")

    /** Everything but the unit table: the scope the unit table is evaluated in. */
    private val core: Scope = Scope.EMPTY.withAll(baseUnits).withAll(constants).withAll(functions)

    /** All built-in units: the base units and the table in `units.bbm`. */
    val units: Map<Id, Value> = baseUnits + UnitTable.load(core, baseUnits)

    val scope: Scope = core.withAll(units)

    private fun unary(name: String, f: (Quantity) -> Quantity) =
        Builtin(name, 1..1) { a -> Scalar(f(a[0]().quantity(name))) }

    /** A trigonometric function: of a plain number, read as radians, or of an angle. */
    private fun angular(name: String, f: (Complex) -> Complex) =
        unary(name) { Quantity(f(it.requireAngle(name).value)) }

    private fun dimensionless(name: String, f: (Complex) -> Complex) =
        unary(name) { Quantity(f(it.requireNone(name).value)) }

    private fun realFunction(name: String, f: (Double) -> Double) = Builtin(name, 1..1) { a ->
        val y = f(a[0]().real(name, dimensionless = true))
        if (y.isNaN()) throw EvalException("$name: argument out of range")
        Scalar(Quantity(y))
    }

    private fun extremum(name: String, better: (Quantity, Quantity) -> Boolean) = Builtin(name, 1..Int.MAX_VALUE) { a ->
        // A bare 0 takes the others' unit: `max(0, x)` with x a length.
        val qs = numbers(a, name).let { sameUnits(it, data = true) ?: it }
        // With complex values, the real and imaginary parts are taken separately †.
        if (qs.any { !it.isReal }) {
            fun part(f: (Quantity) -> Double) = qs.map { Quantity(f(it), it.dimension) }.reduce { best, q -> if (better(q, best)) q else best }
            val (re, im) = part { it.value.re } to part { it.value.im }
            return@Builtin Scalar(Quantity(Complex(re.value.re, im.value.re), re.dimension))
        }
        Scalar(qs.reduce { best, q -> if (better(q, best)) q else best })
    }
}

/** Every number in the arguments, arrays flattened (as max, min, mean take them). */
internal fun numbers(args: List<() -> Value>, context: String): List<Quantity> {
    fun flat(v: Value): List<Quantity> = if (v is ArrayValue) v.elements.flatMap(::flat) else listOf(v.quantity(context))
    return args.flatMap { flat(it()) }
}

private fun count(n: Int) = Scalar(Quantity(n.toDouble()))

private fun asArray(v: Value): ArrayValue = v as? ArrayValue ?: ArrayValue(1, 1, listOf(v))

internal fun vector(v: Value, context: String): ArrayValue {
    val m = MatrixMath.array(v, context)
    if (!m.isVector) throw EvalException("$context needs a vector, not a ${m.size} matrix")
    return m
}

private fun size(v: Value, context: String): Int {
    val x = v.real(context, dimensionless = true)
    if (x != Math.rint(x)) throw EvalException("$context: expected a whole number, not $x")
    return x.toInt()
}

internal fun Value.quantity(context: String): Quantity = when (this) {
    is Scalar -> q
    is Text -> throw EvalException("$context: expected a number, not a string")
    is Module -> throw EvalException("$context: expected a number, not a module")
    is ArrayValue -> throw EvalException("$context: expected a number, not a $size matrix")
    is RangeValue -> throw EvalException(RangeValue.MISUSE)
    is FunctionValue -> throw EvalException("$context: expected a number, not the function $name")
}

internal fun Value.real(context: String, dimensionless: Boolean): Double {
    val q = quantity(context)
    if (dimensionless) q.requireNone(context)
    return q.realValue(context)
}

internal fun Quantity.realValue(context: String): Double {
    if (!isReal) throw EvalException("$context: expected a real number, not $value")
    return value.re
}

internal fun Quantity.requireNone(context: String): Quantity {
    if (!dimension.isNone) throw EvalException("$context: the argument must be dimensionless, not $dimension")
    return this
}

/** A plain number or an angle (docs/units.md#angle-and-currency-as-dimensions): what sin, cos and tan take. */
internal fun Quantity.requireAngle(context: String): Quantity {
    if (!dimension.isNone && dimension != Dimension.ANGLE) throw EvalException("$context: the argument must be dimensionless or an angle, not $dimension")
    return this
}

/** Truth: any non-zero number is true. */
internal fun Value.truth(context: String): Boolean = quantity(context).requireNone(context).value != Complex.ZERO

/** The dimensions a sheet can add to the seven (docs/units.md#angle-and-currency-as-dimensions): frontmatter `angle: dimension`, `currency: AUD`. */
data class UnitOptions(val angle: Boolean = false, val currency: String? = null)
