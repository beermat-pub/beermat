package pub.beermat.symbolic

import pub.beermat.engine.syntax.Id

/**
 * The symbolic engine's own tree: sums, products and powers, kept in a canonical form by
 * [Sym.simplify] (flattened, constants folded exactly, like terms and like factors collected,
 * in a fixed order) so that equal expressions are equal trees.
 */
sealed interface Sym {
    data class Num(val q: Q) : Sym
    /** A decimal, once `float` has been applied. */
    data class Real(val d: Double) : Sym
    data class Var(val id: Id) : Sym
    data class Add(val terms: List<Sym>) : Sym
    data class Mul(val factors: List<Sym>) : Sym
    data class Pow(val base: Sym, val exp: Sym) : Sym
    data class Fn(val name: String, val args: List<Sym>) : Sym

    companion object {
        val ZERO = Num(Q.ZERO)
        val ONE = Num(Q.ONE)
        val MINUS_ONE = Num(-Q.ONE)
        fun num(n: Long, d: Long = 1) = Num(Q.of(n, d))

        fun add(vararg t: Sym) = simplify(Add(t.toList()))
        fun mul(vararg f: Sym) = simplify(Mul(f.toList()))
        fun pow(b: Sym, e: Sym) = simplify(Pow(b, e))
        fun neg(s: Sym) = mul(MINUS_ONE, s)

        /** The canonical form of [s] (bottom-up). */
        fun simplify(s: Sym): Sym = when (s) {
            is Num, is Real, is Var -> s
            is Add -> simplifyAdd(s.terms.map(::simplify))
            is Mul -> simplifyMul(s.factors.map(::simplify))
            is Pow -> simplifyPow(simplify(s.base), simplify(s.exp))
            is Fn -> simplifyFn(s.name, s.args.map(::simplify))
        }

        private fun simplifyAdd(terms: List<Sym>): Sym {
            val flat = terms.flatMap { if (it is Add) it.terms else listOf(it) }
            var constant = Q.ZERO
            var real = 0.0
            var hasReal = false
            // Like terms: the same monomial, coefficients added.
            val coefficients = LinkedHashMap<Sym, Q>()
            for (t in flat) {
                when (t) {
                    is Num -> constant += t.q
                    is Real -> { real += t.d; hasReal = true }
                    else -> {
                        val (c, m) = split(t)
                        coefficients[m] = (coefficients[m] ?: Q.ZERO) + c
                    }
                }
            }
            val out = coefficients.filterValues { !it.isZero }.map { (m, c) -> withCoefficient(c, m) }.sortedWith(ORDER).toMutableList()
            if (hasReal) out += Real(real + constant.toDouble()) else if (!constant.isZero) out += Num(constant)
            return when (out.size) {
                0 -> ZERO
                1 -> out[0]
                else -> Add(out)
            }
        }

        /** A term as its numeric coefficient and the rest: `3*x^2` is 3 and `x^2`. */
        fun split(t: Sym): Pair<Q, Sym> = when {
            t is Mul && t.factors.first() is Num -> (t.factors.first() as Num).q to (t.factors.drop(1).let { if (it.size == 1) it[0] else Mul(it) })
            else -> Q.ONE to t
        }

        private fun withCoefficient(c: Q, m: Sym): Sym = when {
            c.isOne -> m
            m is Mul -> Mul(listOf(Num(c)) + m.factors)
            else -> Mul(listOf(Num(c), m))
        }

        private fun simplifyMul(factors: List<Sym>): Sym {
            val flat = factors.flatMap { if (it is Mul) it.factors else listOf(it) }
            var c = Q.ONE
            var real = 1.0
            var hasReal = false
            // Like factors: the same base, exponents added.
            val exponents = LinkedHashMap<Sym, MutableList<Sym>>()
            for (f in flat) {
                when (f) {
                    is Num -> c *= f.q
                    is Real -> { real *= f.d; hasReal = true }
                    is Pow -> exponents.getOrPut(f.base) { mutableListOf() } += f.exp
                    else -> exponents.getOrPut(f) { mutableListOf() } += ONE
                }
            }
            if (c.isZero || (hasReal && real == 0.0)) return ZERO
            val rest = mutableListOf<Sym>()
            for ((base, exps) in exponents) {
                val p = simplifyPow(base, simplifyAdd(exps))
                when (p) {
                    is Num -> c *= p.q
                    is Mul -> p.factors.forEach { if (it is Num) c *= it.q else rest += it }
                    else -> if (p != ONE) rest += p
                }
            }
            rest.sortWith(FACTORS)
            val coefficient: Sym? = when {
                hasReal -> Real(real * c.toDouble())
                c.isOne -> null
                else -> Num(c)
            }
            val all = listOfNotNull(coefficient) + rest
            return when (all.size) {
                0 -> ONE
                1 -> all[0]
                else -> Mul(all)
            }
        }

        private fun simplifyPow(b: Sym, e: Sym): Sym {
            if (e == ZERO) return ONE
            if (e == ONE) return b
            if (b == ONE) return ONE
            if (b == ZERO && e is Num && e.q.sign > 0) return ZERO
            if (b is Num && e is Num) numericPow(b.q, e.q)?.let { return it }
            if (b is Real && e is Num) return Real(Math.pow(b.d, e.q.toDouble()))
            // (a^m)^n = a^(m n) when n is an integer.
            if (b is Pow && e is Num && e.q.isInteger) return simplifyPow(b.base, simplifyMul(listOf(b.exp, e)))
            // (a b)^n = a^n b^n when n is an integer.
            if (b is Mul && e is Num && e.q.isInteger) return simplifyMul(b.factors.map { simplifyPow(it, e) })
            if (b is Var && b.id == E && e is Fn && e.name == "ln") return e.args[0]
            return Pow(b, e)
        }

        /** An exact power of numbers: 2^3 = 8, 4^(1/2) = 2, 8^(-2/3) = 1/4; null when not exact (2^(1/2) stays). */
        private fun numericPow(b: Q, e: Q): Sym? {
            if (e.isInteger) return e.num.toInt().let { b.pow(it) }?.let(::Num)
            val n = e.num.toInt()
            val d = e.den.toInt()
            val r = b.root(d) ?: return extract(b, n, d)
            return r.pow(n)?.let(::Num)
        }

        /** `8^(1/2)` = `2*2^(1/2)`: a whole number's largest perfect [d]-th power factor, taken out of the root. */
        private fun extract(b: Q, n: Int, d: Int): Sym? {
            if (Math.abs(n) != 1 || !b.isInteger || b.sign <= 0 || b.num.bitLength() > 62) return null
            val v = b.num.toLong()
            var k = 1L
            var f = 2L
            var rest = v
            while (Math.pow(f.toDouble(), d.toDouble()) <= rest && f < 1_000_000) {
                val p = Math.pow(f.toDouble(), d.toDouble()).toLong()
                if (rest % p == 0L) {
                    k *= f
                    rest /= p
                } else {
                    f++
                }
            }
            if (k == 1L) return null
            val outside = Q.of(k).pow(n) ?: return null
            return Mul(listOf(Num(outside), Pow(Num(Q.of(rest)), Num(Q.of(n.toLong(), d.toLong())))))
        }

        private fun simplifyFn(name: String, args: List<Sym>): Sym {
            val a = args.singleOrNull()
            return when {
                name == "ln" && a == ONE -> ZERO
                name == "ln" && a == Var(E) -> ONE
                name == "ln" && a is Pow && a.base == Var(E) -> a.exp
                name == "exp" && a != null -> simplifyPow(Var(E), a)
                (name == "sin" || name == "tan") && a == ZERO -> ZERO
                name == "cos" && a == ZERO -> ONE
                a is Real -> when (name) {
                    "sin" -> Real(Math.sin(a.d))
                    "cos" -> Real(Math.cos(a.d))
                    "tan" -> Real(Math.tan(a.d))
                    "ln" -> Real(Math.log(a.d))
                    else -> Fn(name, args)
                }
                else -> Fn(name, args)
            }
        }

        val E = Id("e")
        val PI = Id("π")

        /** How factors sort in a product: by name (`w*x^2`), functions after. */
        val FACTORS: Comparator<Sym> = compareBy<Sym>({ rank(it) }, { key(if (it is Pow) it.base else it) }, { key(it) })

        /** How terms sort in a sum: higher powers first, then by name; numbers and functions after. */
        val ORDER: Comparator<Sym> = compareBy<Sym>({ -degree(it) }, { rank(it) }, { key(it) })

        fun degree(s: Sym): Double = when (s) {
            is Var -> 1.0
            is Pow -> if (s.exp is Num) degree(s.base) * s.exp.q.toDouble() else 0.5
            is Mul -> s.factors.sumOf(::degree)
            else -> 0.0
        }

        private fun rank(s: Sym): Int = when (s) {
            is Num, is Real -> 0
            is Var, is Pow, is Mul -> 1
            is Fn -> 2
            is Add -> 3
        }

        fun key(s: Sym): String = when (s) {
            is Num -> s.q.toString()
            is Real -> s.d.toString()
            is Var -> s.id.text
            is Add -> s.terms.joinToString("+", "(", ")") { key(it) }
            is Mul -> s.factors.joinToString("*") { key(it) }
            is Pow -> key(s.base) + "^" + key(s.exp)
            is Fn -> s.name + s.args.joinToString(",", "(", ")") { key(it) }
        }

        /** Whether [s] contains the variable [x]. */
        fun has(s: Sym, x: Id): Boolean = when (s) {
            is Var -> s.id == x
            is Num, is Real -> false
            is Add -> s.terms.any { has(it, x) }
            is Mul -> s.factors.any { has(it, x) }
            is Pow -> has(s.base, x) || has(s.exp, x)
            is Fn -> s.args.any { has(it, x) }
        }

        /** [s] with [x] replaced by [v]. */
        fun substitute(s: Sym, x: Id, v: Sym): Sym = when (s) {
            is Var -> if (s.id == x) v else s
            is Num, is Real -> s
            is Add -> Add(s.terms.map { substitute(it, x, v) })
            is Mul -> Mul(s.factors.map { substitute(it, x, v) })
            is Pow -> Pow(substitute(s.base, x, v), substitute(s.exp, x, v))
            is Fn -> Fn(s.name, s.args.map { substitute(it, x, v) })
        }
    }
}
