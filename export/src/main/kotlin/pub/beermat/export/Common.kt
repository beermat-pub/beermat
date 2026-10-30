package pub.beermat.export

import pub.beermat.engine.display.ResultText
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Otherwise
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.ProgramLine
import pub.beermat.engine.syntax.While

/**
 * Which names are units, to be set upright: the built-in units, except names the sheet
 * defines for itself (a sheet that defines `L := 6 m` means a length by `L`, not litres).
 */
class Units private constructor(private val names: Set<Id>, private val all: Boolean) {

    fun isUnit(id: Id) = id !in CONSTANTS && (all || id in names)

    companion object {
        /**
         * Built-in "units" that are physical constants, so drawn italic like variables: gravity
         * `g` is italic and the gram `\rm g` upright, so the two can't be confused.
         */
        private val CONSTANTS = setOf(Id("g"))

        val BUILT_IN = Units(Builtins.units.keys, false)

        /** Every name is a unit: for the unit part of a result. */
        val ALL = Units(emptySet(), true)

        /**
         * No name is a unit: for symbolic statements, which carry no units (docs/symbolic.md), so that
         * `t` and `s` in `laplace(t)` are drawn as the variables they are, not as tonne and second.
         */
        val NONE = Units(emptySet(), false)

        /** The units of a sheet: every name it defines anywhere (a function's parameters and a program's locals too) is not a unit. */
        fun of(statements: List<Statement>): Units {
            val defined = mutableSetOf<Id>()
            fun target(e: Expr) {
                when (e) {
                    is Id -> defined += e
                    is Call -> {
                        (e.fn as? Id)?.let { defined += it }
                        e.args.filterIsInstance<Id>().forEach { defined += it }
                    }
                    is Index -> (e.target as? Id)?.let { defined += it }
                    // `M^<j> := …` and `[a; b] := …`.
                    is Apply -> (e.args.firstOrNull() as? Id)?.let { defined += it }
                    is Matrix -> e.rows.flatten().filterIsInstance<Id>().forEach { defined += it }
                    else -> {}
                }
            }
            fun lines(ls: List<ProgramLine>) {
                for (l in ls) {
                    when (l) {
                        is LocalDefine -> target(l.target)
                        is For -> {
                            (l.variable as? Id)?.let { defined += it }
                            lines(l.body)
                        }
                        is IfThen -> lines(l.body)
                        is Otherwise -> lines(l.body)
                        is While -> lines(l.body)
                        else -> {}
                    }
                }
            }
            for (st in statements.filterIsInstance<Define>()) {
                target(st.target)
                (st.value as? Program)?.let { lines(it.lines) }
            }
            return Units(Builtins.units.keys - defined, false)
        }
    }
}

/** A result to write: its number and unit as expressions, parsed back from [ResultText.Parts]. */
data class Result(val numberText: String, val number: Expr?, val unit: Expr?) {
    companion object {
        fun of(parts: ResultText.Parts): Result =
            Result(parts.number, parse(parts.number), parts.unit?.let(::parse))

        private fun parse(s: String): Expr? = try {
            Parser.parseExpression(s)
        } catch (_: ParseException) {
            null
        }
    }
}
