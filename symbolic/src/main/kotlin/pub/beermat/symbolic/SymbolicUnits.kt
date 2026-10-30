package pub.beermat.symbolic

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.EvalException
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Value
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.units.BaseDimension
import pub.beermat.engine.units.Dimension
import pub.beermat.engine.units.Rational

/**
 * Units through symbolic evaluation (docs/symbolic.md#units), around either engine: names defined
 * with units go in as SI values times base-unit symbols ([put]), the dimensions that are known are
 * checked as the numeric engine does ([check]), and the result's units come back as one unit ([take]).
 */
internal object SymbolicUnits {

    /** The base units as upright names, so they never meet a variable called `m` or `s`. */
    private val bases: Map<BaseDimension, Id> = mapOf(
        BaseDimension.LENGTH to "m", BaseDimension.MASS to "kg", BaseDimension.TIME to "s", BaseDimension.CURRENT to "A",
        BaseDimension.TEMPERATURE to "K", BaseDimension.AMOUNT to "mol", BaseDimension.LUMINOSITY to "cd",
    ).mapValues { Id(it.value, style = Id.Style.UPRIGHT) }

    private val baseOf: Map<Id, BaseDimension> = bases.entries.associate { (b, id) -> id to b }

    /** Functions that take and give plain numbers. */
    private val DIMENSIONLESS = setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "exp", "ln", "log", "erf", "Γ", "gamma")

    /** Where a function's second argument is its own variable, never the sheet's value. */
    private val BOUND = setOf("int", "diff", "lim", "sum", "prod")

    /** [e] with every name defined above as a real number with units replaced by its SI value times base units. */
    fun put(e: Expr, defined: (Id) -> Value?, bound: Set<Id> = emptySet()): Expr = when (e) {
        is Id -> if (e in bound) e else quantity(e, defined) ?: e
        is Parens -> Parens(put(e.inner, defined, bound))
        is Apply -> e.copy(args = e.args.map { put(it, defined, bound) })
        is Matrix -> Matrix(e.rows.map { r -> r.map { put(it, defined, bound) } })
        is Call -> {
            val name = (e.fn as? Id)?.takeIf { it.subscript == null && it.style == Id.Style.PLAIN }?.name
            val own = if (name in BOUND) (e.args.getOrNull(1) as? Id)?.let { bound + it } ?: bound else bound
            Call(e.fn, e.args.mapIndexed { k, a -> if (name in BOUND && k == 1) a else put(a, defined, if (k == 0) own else bound) })
        }
        else -> e
    }

    private fun quantity(id: Id, defined: (Id) -> Value?): Expr? {
        val q = (defined(id) as? Scalar)?.q ?: return null
        if (q.dimension.isNone || q.value.im != 0.0 || !q.value.re.isFinite()) return null
        val number = Num(q.value.re.toBigDecimal().stripTrailingZeros().abs().toPlainString())
        val product = Apply(Op.MUL, listOf(number, unitExpr(q.dimension)))
        return Parens(if (q.value.re < 0) Apply(Op.NEG, listOf(product)) else product)
    }

    /** Base units with their powers, as a product. */
    private fun unitExpr(d: Dimension): Expr = BaseDimension.entries.filter { !d[it].isZero }.map { b ->
        val e = d[b]
        if (e == Rational.ONE) bases.getValue(b) else Apply(Op.POW, listOf(bases.getValue(b), exponent(e)))
    }.reduce { a, b -> Apply(Op.MUL, listOf(a, b)) }

    private fun exponent(r: Rational): Expr {
        val n = if (r.isInteger) Num(kotlin.math.abs(r.num).toString()) else Parens(Apply(Op.DIV, listOf(Num(kotlin.math.abs(r.num).toString()), Num(r.den.toString()))))
        return if (r.num < 0) Apply(Op.NEG, listOf(n)) else n
    }

    /**
     * The numeric engine's unit rules where the dimensions are known: sums and comparisons of
     * matching units, plain numbers into functions such as `sin`. Throws [EvalException] otherwise.
     */
    fun check(e: Expr) {
        dimension(e)
    }

    /** [e]'s dimension when it is known (null when a name not defined is in it), checking as it goes. */
    private fun dimension(e: Expr): Dimension? = when (e) {
        is Num -> Dimension.NONE
        is Id -> baseOf[e]?.let { Dimension.of(it to 1) }
        is Parens -> dimension(e.inner)
        is Apply -> apply(e)
        is Call -> {
            val name = (e.fn as? Id)?.takeIf { it.subscript == null && it.style == Id.Style.PLAIN }?.name
            val dims = e.args.map { dimension(it) }
            when {
                name in DIMENSIONLESS -> {
                    dims.forEach { d -> if (d != null && !d.isNone) throw EvalException("$name needs a number without units, not one in ${ResultText.unitText(d)}", e) }
                    Dimension.NONE
                }
                name == "sqrt" && e.args.size == 1 -> dims[0]?.pow(Rational.of(1, 2))
                name == "abs" && e.args.size == 1 -> dims[0]
                else -> null
            }
        }
        else -> null
    }

    private fun apply(e: Apply): Dimension? {
        val d = e.args.map { dimension(it) }
        return when (e.op) {
            Op.ADD, Op.SUB, Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE -> {
                val (a, b) = d[0] to d[1]
                if (a != null && b != null && a != b && !isZero(e.args[0]) && !isZero(e.args[1])) throw EvalException("units do not match: $a vs $b", e)
                if (e.op == Op.ADD || e.op == Op.SUB) a ?: b else Dimension.NONE
            }
            Op.MUL -> if (d[0] != null && d[1] != null) d[0]!! * d[1]!! else null
            Op.DIV -> if (d[0] != null && d[1] != null) d[0]!! / d[1]!! else null
            Op.POW -> {
                val base = d[0] ?: return null
                if (base.isNone) return Dimension.NONE
                val n = number(e.args[1])?.let { Rational.approximate(it) } ?: return null
                base.pow(n)
            }
            Op.NEG, Op.POS, Op.ABS -> d[0]
            else -> null
        }
    }

    private fun isZero(e: Expr) = number(e) == 0.0

    /** A constant exponent's value, such as 2, -1 or (1/2). */
    private fun number(e: Expr): Double? = when (e) {
        is Num -> e.decimal.toDoubleOrNull()
        is Parens -> number(e.inner)
        is Apply -> when (e.op) {
            Op.NEG -> number(e.args[0])?.let { -it }
            Op.DIV -> number(e.args[0])?.let { a -> number(e.args[1])?.let { b -> a / b } }
            else -> null
        }
        else -> null
    }

    /**
     * The result with its units as one unit: a product holding base-unit symbols becomes the rest
     * times the unit `=` would show (`54000 N*m`); units that cancel disappear. Where the units sit
     * inside a sum, the result is left as it is.
     */
    fun take(e: Expr): Expr {
        if (!mentions(e)) return e
        val numerator = mutableListOf<Expr>()
        val denominator = mutableListOf<Expr>()
        var dim = Dimension.NONE
        var negative = false
        fun factor(x: Expr, up: Boolean): Boolean {
            when {
                x is Parens -> return factor(x.inner, up)
                x is Apply && x.op == Op.MUL -> return factor(x.args[0], up) && factor(x.args[1], up)
                x is Apply && x.op == Op.DIV -> return factor(x.args[0], up) && factor(x.args[1], !up)
                x is Apply && x.op == Op.NEG -> { negative = !negative; return factor(x.args[0], up) }
                x is Id && x in baseOf -> dim = if (up) dim * Dimension.of(baseOf.getValue(x) to 1) else dim / Dimension.of(baseOf.getValue(x) to 1)
                x is Apply && x.op == Op.POW && (x.args[0] as? Id)?.let { it in baseOf } == true -> {
                    val n = number(x.args[1])?.let { Rational.approximate(it) } ?: return false
                    val d = Dimension.of(baseOf.getValue(x.args[0] as Id) to 1).pow(n)
                    dim = if (up) dim * d else dim / d
                }
                mentions(x) -> return false
                else -> if (up) numerator += x else denominator += x
            }
            return true
        }
        if (!factor(e, true)) return e
        fun product(xs: List<Expr>): Expr = xs.reduceOrNull { a, b -> Apply(Op.MUL, listOf(a, b)) } ?: Num("1")
        var rest: Expr = if (denominator.isEmpty()) product(numerator) else Apply(Op.DIV, listOf(product(numerator), product(denominator)))
        if (negative) rest = Apply(Op.NEG, listOf(rest))
        if (dim.isNone) return rest
        // The unit `=` would choose, its names upright: `N*m`, or `1/s` written as a division.
        val unit = upright(Parser.parseExpression(ResultText.unitText(dim)))
        return if (unit is Apply && unit.op == Op.DIV && unit.args[0] == Num("1")) Apply(Op.DIV, listOf(rest, unit.args[1])) else attach(rest, unit)
    }

    /** [x] times [unit], left to right as it would be typed, the unit beside the number as in a result: `54000 N*m`, `3 m/s^2`. */
    private fun attach(x: Expr, unit: Expr, first: Boolean = true): Expr = when {
        unit is Apply && unit.op == Op.MUL -> attach(attach(x, unit.args[0], first), unit.args[1], false)
        unit is Apply && unit.op == Op.DIV -> Apply(Op.DIV, listOf(attach(x, unit.args[0], first), unit.args[1]))
        else -> Apply(Op.MUL, listOf(x, unit), implicit = first)
    }

    private fun mentions(e: Expr): Boolean = when (e) {
        is Id -> e in baseOf
        is Parens -> mentions(e.inner)
        is Apply -> e.args.any(::mentions)
        is Call -> e.args.any(::mentions)
        is Matrix -> e.rows.flatten().any(::mentions)
        else -> false
    }

    private fun upright(e: Expr): Expr = when (e) {
        is Id -> e.copy(style = Id.Style.UPRIGHT)
        is Parens -> Parens(upright(e.inner))
        is Apply -> e.copy(args = e.args.map(::upright))
        else -> e
    }
}
