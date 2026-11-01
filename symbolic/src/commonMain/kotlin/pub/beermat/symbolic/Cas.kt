package pub.beermat.symbolic

import kotlin.math.abs
import kotlin.math.pow
import pub.beermat.engine.display.Decimal
import pub.beermat.engine.eval.EvalException
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.SymbolicBackend
import pub.beermat.engine.eval.UserFunction
import pub.beermat.engine.eval.Value
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Str
import pub.beermat.symbolic.Sym.Companion.ONE
import pub.beermat.symbolic.Sym.Companion.ZERO
import pub.beermat.symbolic.Sym.Companion.add
import pub.beermat.symbolic.Sym.Companion.mul
import pub.beermat.symbolic.Sym.Companion.neg
import pub.beermat.symbolic.Sym.Companion.pow
import pub.beermat.symbolic.Sym.Companion.simplify

/**
 * Beermat's own small symbolic engine (docs/symbolic.md): simplification with exact rationals,
 * `expand`, derivatives (`diff`), `substitute`, `solve` for equations of degree 1 and 2, and
 * `float`. Names the sheet defines above are substituted (functions inlined, numbers without
 * units put in) †; other names stay symbols.
 *
 * What it can't do, it throws as [Beyond], for SymPy to do instead ([Symbolics]).
 */
object Cas : SymbolicBackend {

    /** Something only SymPy can do: [what] it is, as the message says where there's no SymPy. */
    class Beyond(what: String, node: Expr? = null) : EvalException("$what needs SymPy, which this version doesn't include", node)

    override fun evaluate(expr: Expr, keywords: List<Expr>, defined: (Id) -> Value?): Expr {
        var s = simplify(Convert(defined).sym(expr))
        var matrix: List<Sym>? = null
        for (k in keywords) {
            val name = ((k as? Call)?.fn ?: k) as? Id ?: throw EvalException("unknown symbolic keyword", k)
            val args = (k as? Call)?.args ?: emptyList()
            when (name.name) {
                "simplify" -> s = simplify(s)
                "expand" -> s = expand(s)
                "float" -> {
                    val digits = (args.firstOrNull() as? Num)?.text?.toIntOrNull() ?: 10
                    s = float(s)
                    matrix = matrix?.map(::float)
                    return toExpr(matrix, s, digits)
                }
                "substitute" -> s = substitute(s, args, defined)
                "solve" -> {
                    if (args.size > 1 || args.firstOrNull() is Matrix) throw Beyond("solving a system", k)
                    matrix = solve(s, args.singleOrNull() as? Id ?: throw EvalException("solve needs the variable: solve(x)", k))
                }
                in SYMPY_KEYWORDS -> throw Beyond(name.name, k)
                else -> throw EvalException("${name.text} is not a symbolic keyword", k, listOf(name))
            }
        }
        return toExpr(matrix, s, null)
    }

    /** The keywords only SymPy does (docs/symbolic.md). */
    val SYMPY_KEYWORDS = setOf(
        "factor", "collect", "series", "parfrac", "laplace", "invlaplace", "fourier", "invfourier", "ztrans", "assume", "coeffs",
    )

    private fun toExpr(matrix: List<Sym>?, s: Sym, digits: Int?): Expr =
        if (matrix != null) (if (matrix.size == 1) Back(digits).expr(matrix[0]) else Matrix(matrix.map { listOf(Back(digits).expr(it)) }))
        else Back(digits).expr(s)

    // Expanding.

    fun expand(s: Sym): Sym = simplify(
        when (s) {
            is Sym.Add -> Sym.Add(s.terms.map(::expand))
            is Sym.Mul -> s.factors.map(::expand).fold<Sym, Sym>(ONE) { acc, f -> distribute(acc, f) }
            is Sym.Pow -> {
                val b = expand(s.base)
                val n = (s.exp as? Sym.Num)?.q
                if (b is Sym.Add && n != null && n.isInteger && n.sign > 0 && n.num.toInt() <= 12) {
                    (1..n.num.toInt()).fold<Int, Sym>(ONE) { acc, _ -> distribute(acc, b) }
                } else Sym.Pow(b, expand(s.exp))
            }
            is Sym.Fn -> Sym.Fn(s.name, s.args.map(::expand))
            else -> s
        },
    )

    private fun distribute(a: Sym, b: Sym): Sym {
        val ta = if (a is Sym.Add) a.terms else listOf(a)
        val tb = if (b is Sym.Add) b.terms else listOf(b)
        return simplify(Sym.Add(ta.flatMap { x -> tb.map { y -> simplify(Sym.Mul(listOf(x, y))) } }))
    }

    // Derivatives.

    fun diff(s: Sym, x: Id): Sym = simplify(
        when (s) {
            is Sym.Num, is Sym.Real -> ZERO
            is Sym.Var -> if (s.id == x) ONE else ZERO
            is Sym.Add -> Sym.Add(s.terms.map { diff(it, x) })
            is Sym.Mul -> Sym.Add(s.factors.indices.map { i -> Sym.Mul(s.factors.mapIndexed { j, f -> if (i == j) diff(f, x) else f }) })
            is Sym.Pow -> when {
                !Sym.has(s.exp, x) -> mul(s.exp, pow(s.base, add(s.exp, Sym.MINUS_ONE)), diff(s.base, x))
                !Sym.has(s.base, x) -> mul(s, ln(s.base), diff(s.exp, x))
                else -> mul(s, add(mul(diff(s.exp, x), ln(s.base)), mul(s.exp, diff(s.base, x), pow(s.base, Sym.MINUS_ONE))))
            }
            is Sym.Fn -> {
                val u = s.args.singleOrNull() ?: throw Beyond("differentiating ${s.name}")
                val du = diff(u, x)
                when (s.name) {
                    "sin" -> mul(Sym.Fn("cos", listOf(u)), du)
                    "cos" -> neg(mul(Sym.Fn("sin", listOf(u)), du))
                    "tan" -> mul(pow(Sym.Fn("cos", listOf(u)), Sym.num(-2)), du)
                    "ln" -> mul(du, pow(u, Sym.MINUS_ONE))
                    "asin" -> mul(du, pow(add(ONE, neg(pow(u, Sym.num(2)))), Sym.num(-1, 2)))
                    "acos" -> neg(mul(du, pow(add(ONE, neg(pow(u, Sym.num(2)))), Sym.num(-1, 2))))
                    "atan" -> mul(du, pow(add(ONE, pow(u, Sym.num(2))), Sym.MINUS_ONE))
                    "sinh" -> mul(Sym.Fn("cosh", listOf(u)), du)
                    "cosh" -> mul(Sym.Fn("sinh", listOf(u)), du)
                    else -> if (Sym.has(u, x)) throw Beyond("differentiating ${s.name}") else ZERO
                }
            }
        },
    )

    private fun ln(s: Sym): Sym = simplify(Sym.Fn("ln", listOf(s)))

    // Substituting.

    /** `substitute(x == 2)` or `substitute(x, 2)`, several pairs allowed. */
    private fun substitute(s: Sym, args: List<Expr>, defined: (Id) -> Value?): Sym {
        val pairs = mutableListOf<Pair<Id, Expr>>()
        var k = 0
        while (k < args.size) {
            val a = args[k]
            if (a is Apply && a.op == Op.EQ && a.args[0] is Id) {
                pairs += (a.args[0] as Id) to a.args[1]
                k++
            } else if (a is Id && k + 1 < args.size) {
                pairs += a to args[k + 1]
                k += 2
            } else {
                throw EvalException("substitute takes x == value (or x, value)", a)
            }
        }
        val convert = Convert(defined)
        return pairs.fold(s) { acc, (x, v) -> simplify(Sym.substitute(acc, x, convert.sym(v))) }
    }

    // Solving.

    /** The roots of [s] = 0 in [x], for degree 1 and 2. */
    fun solve(s: Sym, x: Id): List<Sym> {
        val e = expand(s)
        val coefficients = mutableMapOf<Int, MutableList<Sym>>()
        for (t in if (e is Sym.Add) e.terms else listOf(e)) {
            val (degree, rest) = degreeIn(t, x) ?: throw Beyond("solving this equation")
            coefficients.getOrPut(degree) { mutableListOf() } += rest
        }
        fun c(d: Int) = simplify(Sym.Add(coefficients[d] ?: listOf(ZERO)))
        return when (coefficients.keys.maxOrNull() ?: 0) {
            0 -> throw EvalException("${x.text} does not appear in the equation", names = listOf(x))
            1 -> listOf(simplify(neg(mul(c(0), pow(c(1), Sym.MINUS_ONE)))))
            2 -> {
                val (a, b, cc) = Triple(c(2), c(1), c(0))
                val disc = simplify(add(pow(b, Sym.num(2)), neg(mul(Sym.num(4), a, cc))))
                if (disc is Sym.Num && disc.q.sign < 0) throw Beyond("solving with complex roots")
                val root = pow(disc, Sym.num(1, 2))
                val twoA = pow(mul(Sym.num(2), a), Sym.MINUS_ONE)
                if (disc == ZERO) listOf(simplify(neg(mul(b, twoA))))
                else listOf(simplify(mul(add(neg(b), neg(root)), twoA)), simplify(mul(add(neg(b), root), twoA)))
            }
            else -> throw Beyond("solving beyond degree 2")
        }
    }

    /** A term as `rest * x^degree`, or null if [x] appears other than as a whole-number power. */
    private fun degreeIn(t: Sym, x: Id): Pair<Int, Sym>? {
        val factors = if (t is Sym.Mul) t.factors else listOf(t)
        var degree = 0
        val rest = mutableListOf<Sym>()
        for (f in factors) {
            when {
                f is Sym.Var && f.id == x -> degree += 1
                f is Sym.Pow && f.base == Sym.Var(x) && f.exp is Sym.Num && f.exp.q.isInteger && f.exp.q.sign > 0 -> degree += f.exp.q.num.toInt()
                Sym.has(f, x) -> return null
                else -> rest += f
            }
        }
        return degree to simplify(Sym.Mul(rest.ifEmpty { listOf(ONE) }))
    }

    // Numbers.

    /** Every number (and π, e) as a decimal, computed where it can be. */
    fun float(s: Sym): Sym = simplify(
        when (s) {
            is Sym.Num -> Sym.Real(s.q.toDouble())
            is Sym.Var -> when (s.id) {
                Sym.PI -> Sym.Real(kotlin.math.PI)
                Sym.E -> Sym.Real(kotlin.math.E)
                else -> s
            }
            is Sym.Add -> Sym.Add(s.terms.map(::float))
            is Sym.Mul -> Sym.Mul(s.factors.map(::float))
            is Sym.Pow -> {
                val b = float(s.base)
                val e = float(s.exp)
                if (b is Sym.Real && e is Sym.Real) Sym.Real(b.d.pow(e.d)) else Sym.Pow(b, e)
            }
            is Sym.Fn -> Sym.Fn(s.name, s.args.map(::float))
            is Sym.Real -> s
        },
    )

    /** From the syntax tree: `a/b` is `a*b^-1`, `a - b` is `a + (-1)*b`, `sqrt(x)` is `x^(1/2)`; `diff` is done here. */
    private class Convert(val defined: (Id) -> Value?) {
        private var depth = 0

        fun sym(e: Expr): Sym = when (e) {
            is Num -> {
                if (e.text.endsWith("i") || e.text.endsWith("j")) throw Beyond("a complex number", e)
                Sym.Num(Q.parse(e.decimal))
            }
            is Id -> substituted(e) ?: if (e.name == Id.INFINITY) throw Beyond("∞", e) else Sym.Var(e)
            is Parens -> sym(e.inner)
            is Apply -> apply(e)
            is Call -> call(e)
            Placeholder -> throw EvalException("this expression is incomplete", e)
            is Str -> throw EvalException("a string can't be evaluated symbolically", e)
            else -> throw Beyond("this expression", e)
        }

        /** A name defined above as a number without units: its value (†). */
        private fun substituted(id: Id): Sym? {
            val v = defined(id) as? Scalar ?: return null
            if (!v.q.dimension.isNone || v.q.value.im != 0.0) return null
            val d = v.q.value.re
            return Sym.Num(Q.parse(Decimal.of(d).toPlainString()))
        }

        private fun apply(e: Apply): Sym {
            val a = e.args.map { sym(it) }
            return when (e.op) {
                Op.ADD -> Sym.Add(a)
                Op.SUB -> Sym.Add(listOf(a[0], Sym.Mul(listOf(Sym.MINUS_ONE, a[1]))))
                Op.MUL -> Sym.Mul(a)
                Op.DIV -> Sym.Mul(listOf(a[0], Sym.Pow(a[1], Sym.MINUS_ONE)))
                Op.POW -> Sym.Pow(a[0], a[1])
                Op.NEG -> Sym.Mul(listOf(Sym.MINUS_ONE, a[0]))
                Op.POS -> a[0]
                Op.EQ -> Sym.Add(listOf(a[0], Sym.Mul(listOf(Sym.MINUS_ONE, a[1]))))
                else -> throw Beyond(e.op.symbol, e)
            }
        }

        private fun call(e: Call): Sym {
            val name = (e.fn as? Id)?.takeIf { it.subscript == null && it.style == Id.Style.PLAIN }?.name
            val args = e.args
            return when {
                name == "sqrt" && args.size == 1 -> Sym.Pow(sym(args[0]), Sym.num(1, 2))
                name == "nthroot" && args.size == 2 -> Sym.Pow(sym(args[0]), Sym.Pow(sym(args[1]), Sym.MINUS_ONE))
                name == "exp" && args.size == 1 -> Sym.Pow(Sym.Var(Sym.E), sym(args[0]))
                (name == "ln" || name == "log") && args.size == 1 -> Sym.Fn("ln", listOf(sym(args[0])))
                name == "diff" && args.size !in 2..3 -> throw EvalException("diff takes the expression and the variable: diff(f, x), or diff(f, x, n)", e)
                name == "diff" -> {
                    val x = args[1] as? Id ?: throw EvalException("diff needs the variable: diff(f, x)", args[1])
                    val n = (args.getOrNull(2) as? Num)?.text?.toIntOrNull() ?: 1
                    (1..n).fold(simplify(sym(args[0]))) { f, _ -> diff(f, x) }
                }
                name in SYMPY_CALLS -> throw Beyond(SYMPY_CALLS.getValue(name!!), e)
                name in FUNCTIONS && args.size == 1 -> Sym.Fn(name!!, listOf(sym(args[0])))
                else -> inline(e) ?: Sym.Fn((e.fn as? Id)?.text ?: "f", args.map { sym(it) })
            }
        }

        /** A function the sheet defines, its body with the arguments put in. */
        private fun inline(e: Call): Sym? {
            val f = (e.fn as? Id)?.let(defined) as? UserFunction ?: return null
            if (f.params.size != e.args.size || ++depth > 50) return null
            try {
                var s = sym(f.body)
                f.params.zip(e.args).forEach { (p, a) -> s = Sym.substitute(s, p, sym(a)) }
                return s
            } finally {
                depth--
            }
        }
    }

    private val FUNCTIONS = setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "abs")

    private val SYMPY_CALLS = mapOf("int" to "integration", "lim" to "a limit", "sum" to "a symbolic sum", "prod" to "a symbolic product")

    /** Back to the syntax tree, readably: `3*x^2 - 2*x + 1`, `(a + b)/(2*c)`, `sqrt(2)`. */
    internal class Back(val digits: Int?) {
        fun expr(s: Sym): Expr = when (s) {
            is Sym.Num -> number(s.q)
            is Sym.Real -> real(s.d)
            is Sym.Var -> s.id
            is Sym.Add -> positiveFirst(s.terms).let { terms ->
                terms.drop(1).fold(expr(terms[0])) { acc, t ->
                    if (negative(t)) Apply(Op.SUB, acc, expr(simplify(neg(t)))) else Apply(Op.ADD, acc, expr(t))
                }
            }
            is Sym.Mul -> product(s.factors)
            is Sym.Pow -> power(s)
            is Sym.Fn -> Call(Id(s.name), s.args.map(::expr))
        }

        private fun negative(t: Sym) = Sym.split(t).first.sign < 0 || (t is Sym.Num && t.q.sign < 0) || (t is Sym.Real && t.d < 0)

        /** A sum reads better starting with a positive term: `b^2 - 4*a*c`, not `-4*a*c + b^2`. */
        private fun positiveFirst(terms: List<Sym>): List<Sym> {
            if (!negative(terms[0])) return terms
            val k = terms.indexOfFirst { !negative(it) }
            return if (k < 0) terms else listOf(terms[k]) + terms.filterIndexed { i, _ -> i != k }
        }

        private fun number(q: Q): Expr {
            val n = Num(q.num.abs().toString())
            val e = if (q.isInteger) n else Apply(Op.DIV, n, Num(q.den.toString()))
            return if (q.sign < 0) Apply(Op.NEG, e) else e
        }

        private fun real(d: Double): Expr {
            val text = Decimal.exactOf(abs(d)).round(digits ?: 10).stripTrailingZeros().let {
                if (it.scale < -15 || it.toDouble().let { v -> v != 0.0 && v < 1e-6 }) it.toScientificString().replace("E+", "e").replace("E", "e") else it.toPlainString()
            }
            val n = Num(if (text.contains('.') || text.contains('e')) text else text)
            return if (d < 0) Apply(Op.NEG, n) else n
        }

        private fun power(p: Sym.Pow): Expr {
            val e = p.exp
            if (e is Sym.Num && e.q.sign < 0) return Apply(Op.DIV, Num("1"), expr(simplify(Sym.Pow(p.base, Sym.Num(-e.q)))))
            if (e is Sym.Num && e.q == Q.of(1, 2)) return Call(Id("sqrt"), expr(p.base))
            if (e is Sym.Num && e.q.num == BigInt.ONE) return Call(Id("nthroot"), expr(p.base), Num(e.q.den.toString()))
            return Apply(Op.POW, expr(p.base), expr(e))
        }

        /** A product as a fraction: factors with negative exponents go below. */
        private fun product(factors: List<Sym>): Expr {
            var c = Q.ONE
            var decimal: Double? = null
            val up = mutableListOf<Sym>()
            val down = mutableListOf<Sym>()
            for (f in factors) {
                when {
                    f is Sym.Num -> c *= f.q
                    f is Sym.Real -> decimal = (decimal ?: 1.0) * f.d
                    f is Sym.Pow && f.exp is Sym.Num && f.exp.q.sign < 0 -> down += simplify(Sym.Pow(f.base, Sym.Num(-f.exp.q)))
                    else -> up += f
                }
            }
            val negative = c.sign < 0 || (decimal != null && decimal < 0)
            val top = mutableListOf<Expr>()
            if (decimal != null) top += real(abs(decimal * c.toDouble()))
            else if (c.num.abs() != BigInt.ONE) top += Num(c.num.abs().toString())
            top += up.map(::expr)
            val num = if (top.isEmpty()) Num("1") else top.drop(1).fold(top[0]) { a, b -> Apply(Op.MUL, a, b) }
            val bottom = (if (decimal == null && c.den != BigInt.ONE) listOf<Expr>(Num(c.den.toString())) else emptyList()) + down.map(::expr)
            val frac = if (bottom.isEmpty()) num else Apply(Op.DIV, num, bottom.drop(1).fold(bottom[0]) { a, b -> Apply(Op.MUL, a, b) })
            return if (negative) Apply(Op.NEG, frac) else frac
        }
    }
}
