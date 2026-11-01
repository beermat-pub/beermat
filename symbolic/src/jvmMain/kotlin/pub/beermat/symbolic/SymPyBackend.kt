package pub.beermat.symbolic

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

/**
 * Symbolic evaluation by SymPy (docs/symbolic.md#bundled-sympy): the expression and keywords go to
 * `bridge.py` as trees, with every name replaced by its number in a table, and the answer comes
 * back the same way. Names the sheet defines are put in as [Cas] does: numbers without units by
 * their value, functions by their body.
 */
class SymPyBackend(private val sympy: SymPy) : SymbolicBackend {

    override fun evaluate(expr: Expr, keywords: List<Expr>, defined: (Id) -> Value?): Expr {
        val request = Request(defined)
        val tree = request.tree(expr)
        var digits: Int? = null
        val kws = keywords.map { k ->
            val name = ((k as? Call)?.fn ?: k) as? Id ?: throw EvalException("unknown symbolic keyword", k)
            val args = (k as? Call)?.args ?: emptyList()
            if (name.name !in KEYWORDS || name.subscript != null || name.style != Id.Style.PLAIN) {
                throw EvalException("${name.text} is not a symbolic keyword", k, listOf(name))
            }
            if (name.name == "float") digits = (args.firstOrNull() as? Num)?.text?.toIntOrNull() ?: 10
            listOf(name.name) + request.keywordArgs(name.name, args)
        }
        val words = request.ids.withIndex()
            .filter { (_, id) -> id.subscript == null && id.style == Id.Style.PLAIN }
            .associate { (k, id) -> k.toString() to id.name }
        val reply = Json.read(sympy.ask(Json.write(mapOf("expr" to tree, "keywords" to kws, "words" to words)))) as Map<*, *>
        reply["error"]?.let { throw EvalException(it.toString()) }
        return Answer(request.ids, digits).expr(reply["ok"]!!)
    }

    /** Our tree to the bridge's: lists headed by a tag (see `bridge.py`). */
    private class Request(val defined: (Id) -> Value?) {
        val ids = mutableListOf<Id>()
        private var depth = 0

        fun index(id: Id): Int = ids.indexOf(id).takeIf { it >= 0 } ?: ids.size.also { ids += id }

        /**
         * [e] as a tree. [env] holds what a name stands for (a function's parameters while it is
         * inlined, an integral's variable); [substituting] puts in the sheet's definitions.
         */
        fun tree(e: Expr, env: Map<Id, Any> = emptyMap(), substituting: Boolean = true): Any = when (e) {
            is Num -> number(e.text)
            is Id -> env[e] ?: (if (substituting) value(e) else null) ?: name(e)
            is Parens -> tree(e.inner, env, substituting)
            is Apply -> apply(e, env, substituting)
            is Call -> call(e, env, substituting)
            is Matrix -> listOf("m", e.rows.size, e.rows[0].size) + e.rows.flatten().map { tree(it, env, substituting) }
            is Str -> listOf("str", e.value)
            Placeholder -> throw EvalException("this expression is incomplete", e)
            else -> throw EvalException("symbolic evaluation can't do this yet", e)
        }

        private fun name(id: Id): Any = when {
            id == Sym.PI -> listOf("pi")
            id == Sym.E -> listOf("e")
            id.name == Id.INFINITY && id.subscript == null -> listOf("oo")
            else -> listOf("v", index(id))
        }

        /** A name defined above as a number without units: its value, exactly as typed (†). */
        private fun value(id: Id): Any? {
            val v = defined(id) as? Scalar ?: return null
            if (!v.q.dimension.isNone || v.q.value.im != 0.0 || !v.q.value.re.isFinite()) return null
            return rational(Q.parse(Decimal.of(v.q.value.re).toPlainString()))
        }

        private fun rational(q: Q) = listOf("q", q.num.toString(), q.den.toString())

        private fun number(text: String): Any {
            val imaginary = text.endsWith("i") || text.endsWith("j")
            val q = rational(Q.parse(if (imaginary) text.dropLast(1) else Num(text).decimal))
            return if (imaginary) listOf("*", q, listOf("I")) else q
        }

        private fun apply(e: Apply, env: Map<Id, Any>, substituting: Boolean): Any {
            val a = e.args.map { tree(it, env, substituting) }
            return when (e.op) {
                Op.ADD -> listOf("+", a[0], a[1])
                Op.SUB -> listOf("+", a[0], listOf("*", MINUS_ONE, a[1]))
                Op.MUL -> listOf("*", a[0], a[1])
                Op.DIV -> listOf("*", a[0], listOf("^", a[1], MINUS_ONE))
                Op.POW -> listOf("^", a[0], a[1])
                Op.NEG -> listOf("*", MINUS_ONE, a[0])
                Op.POS -> a[0]
                Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE -> listOf(e.op.symbol, a[0], a[1])
                Op.AND, Op.OR -> listOf(e.op.symbol, a[0], a[1])
                Op.ABS -> listOf("f", "abs", a[0])
                Op.FACTORIAL -> listOf("f", "factorial", a[0])
                else -> throw EvalException("symbolic evaluation can't do ${e.op.symbol} yet", e)
            }
        }

        private fun call(e: Call, env: Map<Id, Any>, substituting: Boolean): Any {
            val fn = e.fn as? Id ?: throw EvalException("symbolic evaluation can't call this", e.fn)
            val plain = fn.subscript == null && fn.style == Id.Style.PLAIN && fn !in env
            val args = e.args
            if (plain && fn.name in BOUND && args.size >= 2) {
                // The variable of an integral, a derivative, a limit or a sum is its own: never the sheet's value.
                val x = args[1] as? Id ?: throw EvalException("${fn.name} needs the variable as its second argument", args[1])
                val inner = env + (x to name(x))
                return listOf("f", fn.name, tree(args[0], inner, substituting), name(x)) + args.drop(2).map { tree(it, env, substituting) }
            }
            if (substituting) inline(fn, args, env)?.let { return it }
            val trees = args.map { tree(it, env, substituting) }
            return when {
                plain && fn.name == "sqrt" && args.size == 1 -> listOf("^", trees[0], listOf("q", "1", "2"))
                plain && fn.name in FUNCTIONS -> listOf("f", fn.name) + trees
                else -> listOf("u", index(fn)) + trees
            }
        }

        /** A function the sheet defines, its body with the arguments put in. */
        private fun inline(fn: Id, args: List<Expr>, env: Map<Id, Any>): Any? {
            val f = defined(fn) as? UserFunction ?: return null
            if (f.params.size != args.size || depth > 50) return null
            depth++
            try {
                return tree(f.body, f.params.zip(args.map { tree(it, env) }).toMap())
            } finally {
                depth--
            }
        }

        /**
         * A keyword's arguments. Its variables (`solve(x)`, `series(x == 0)`) stay names even if the
         * sheet gives them a value; values (`substitute(x == 2)`, the point of a series) put it in.
         */
        fun keywordArgs(keyword: String, args: List<Expr>): List<Any> = args.mapIndexed { k, a ->
            val values = when (keyword) {
                "substitute" -> k % 2 == 1
                "float", "series" -> k == 1
                else -> false
            }
            if (a is Apply && a.op in RELATIONS) {
                listOf(a.op.symbol, tree(a.args[0], substituting = false), tree(a.args[1], substituting = keyword != "assume"))
            } else {
                tree(a, substituting = values)
            }
        }
    }

    /** The bridge's tree back to ours, read as [Cas] writes its results: `x^2 - 1`, `(a + b)/(2*c)`. */
    private class Answer(val ids: List<Id>, val digits: Int?) {

        fun expr(t: Any): Expr {
            val list = t as List<*>
            return when (val tag = list[0]) {
                "m" -> {
                    val rows = (list[1] as Long).toInt()
                    val cols = (list[2] as Long).toInt()
                    val elements = list.drop(3).map { expr(it!!) }
                    if (rows * cols == 1) elements[0] else Matrix(elements.chunked(cols))
                }
                in RELATIONS.map { it.symbol } -> Apply(RELATIONS.first { it.symbol == tag }, expr(list[1]!!), expr(list[2]!!))
                else -> finish(Cas.Back(digits).expr(Sym.simplify(sym(t))))
            }
        }

        private fun sym(t: Any?): Sym {
            val list = t as List<*>
            val args = { list.drop(1).map(::sym) }
            return when (list[0]) {
                "q" -> Sym.Num(Q.of(BigInt.parse(list[1] as String), BigInt.parse(list[2] as String)))
                "r" -> Sym.Real((list[1] as String).toDouble())
                "v" -> Sym.Var(ids[(list[1] as Long).toInt()])
                "s" -> Sym.Var(Id(list[1] as String))
                "pi" -> Sym.Var(Sym.PI)
                "e" -> Sym.Var(Sym.E)
                "I" -> Sym.Var(IMAGINARY)
                "oo" -> Sym.Var(Id(Id.INFINITY))
                "+" -> Sym.Add(args())
                "*" -> Sym.Mul(args())
                "^" -> Sym.Pow(sym(list[1]), sym(list[2]))
                "f" -> Sym.Fn(list[1] as String, list.drop(2).map(::sym))
                "u" -> Sym.Fn(FUNCTION + (list[1] as Long), list.drop(2).map(::sym))
                else -> throw EvalException("can't show this result yet (${list[0]})")
            }
        }

        /**
         * The stand-ins put back: the imaginary unit as `1i` (`2*1i` as `2i`), functions by their
         * names; `abs(x)` drawn `|x|`, and a complex number with its real part first (`-0.7 - 1.2i`).
         */
        private fun finish(e: Expr): Expr = when (e) {
            IMAGINARY -> Num("1i")
            is Apply -> {
                val a = e.args.map(::finish)
                val n = a.getOrNull(0) as? Num
                when {
                    e.op == Op.MUL && n != null && !imaginary(n) && a[1] == Num("1i") -> Num(n.text + "i")
                    (e.op == Op.ADD || e.op == Op.SUB) && imaginary(a[0]) && !imaginary(a[1]) && a[1].let { it is Num || (it is Apply && it.op == Op.NEG && it.args[0] is Num) } -> {
                        // im ± re, as re ± im: `-1.2i - 0.7` reads `-0.7 - 1.2i`.
                        val re = if (e.op == Op.SUB) negate(a[1]) else a[1]
                        val im = a[0]
                        if (im is Apply && im.op == Op.NEG) Apply(Op.SUB, re, im.args[0]) else Apply(Op.ADD, re, im)
                    }
                    else -> e.copy(args = a)
                }
            }
            is Call -> {
                val name = (e.fn as? Id)?.name
                val fn = if (name != null && name.startsWith(FUNCTION)) ids[name.removePrefix(FUNCTION).toInt()] else e.fn
                if (fn == Id("abs") && e.args.size == 1) Apply(Op.ABS, finish(e.args[0])) else Call(fn, e.args.map(::finish))
            }
            is Matrix -> Matrix(e.rows.map { r -> r.map(::finish) })
            else -> e
        }

        private fun imaginary(e: Expr): Boolean = when (e) {
            is Num -> e.text.endsWith("i")
            is Apply -> e.op == Op.NEG && imaginary(e.args[0])
            else -> false
        }

        private fun negate(e: Expr) = if (e is Apply && e.op == Op.NEG) e.args[0] else Apply(Op.NEG, e)
    }

    private companion object {
        val MINUS_ONE = listOf("q", "-1", "1")
        val RELATIONS = listOf(Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE)
        val IMAGINARY = Id("1i")
        const val FUNCTION = "\u0000fn"

        /** Calls whose second argument is a variable of their own. */
        val BOUND = setOf("int", "diff", "lim", "sum", "prod")

        /** The functions `bridge.py` knows; any other call is a function SymPy keeps unknown, `f(x)`. */
        val FUNCTIONS = setOf(
            "sin", "cos", "tan", "cot", "sec", "csc", "asin", "acos", "atan", "acot", "sinh", "cosh", "tanh", "coth",
            "asinh", "acosh", "atanh", "exp", "ln", "log", "abs", "sign", "floor", "ceil", "Re", "Im", "atan2", "gamma",
            "erf", "erfc", "Heaviside", "Φ", "Dirac", "δ", "nthroot", "max", "min", "factorial",
        )

        val KEYWORDS = Cas.SYMPY_KEYWORDS + setOf("simplify", "expand", "float", "solve", "substitute")
    }
}
