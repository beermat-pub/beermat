package pub.beermat.engine.eval

import pub.beermat.engine.syntax.Expr

/**
 * What evaluates `expr -> keyword -> … -> ?` (milestone 10, docs/symbolic.md). The engine only
 * defines the interface; the `symbolic` module has a small engine of its own, and a fuller
 * backend could plug in behind the same interface.
 */
fun interface SymbolicBackend {
    /**
     * [expr] transformed by each of [keywords] in turn (with none, simplified), as an expression.
     * [defined] tells which names the sheet has defined above: a backend may substitute them.
     * Throws [EvalException] when a keyword is unknown or can't be done.
     */
    fun evaluate(expr: Expr, keywords: List<Expr>, defined: (pub.beermat.engine.syntax.Id) -> Value?): Expr
}
