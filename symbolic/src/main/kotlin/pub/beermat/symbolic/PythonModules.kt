package pub.beermat.symbolic

import pub.beermat.engine.eval.ArrayValue
import pub.beermat.engine.eval.Builtin
import pub.beermat.engine.eval.Builtins
import pub.beermat.engine.eval.DisplayUnit
import pub.beermat.engine.eval.EvalException
import pub.beermat.engine.eval.Evaluator
import pub.beermat.engine.eval.Module
import pub.beermat.engine.eval.Modules
import pub.beermat.engine.eval.Scalar
import pub.beermat.engine.eval.Text
import pub.beermat.engine.eval.Value
import pub.beermat.engine.syntax.ParseException
import pub.beermat.engine.syntax.Parser
import pub.beermat.engine.units.Complex
import pub.beermat.engine.units.Quantity
import java.io.File

/**
 * `use "file.py"` (docs/symbolic.md#python-functions-in-a-sheet): the file's public functions as
 * built-ins that call into the bundled Python, in the same process as SymPy. An argument or result
 * annotated with a unit (`q: "kN/m"`, `-> "kN*m"`) goes as a plain number in that unit; without one,
 * it must be a plain number. [allowed] asks whether a file may run (the user's consent).
 */
class PythonModules(private val python: SymPy, private val allowed: (File) -> Boolean) : Modules {

    /** A function's description, as `bridge.py` reads it with `inspect`. */
    class Function(val name: String, val params: List<Param>, val returns: String?, val signature: String, val doc: String)

    class Param(val name: String, val unit: String?, val optional: Boolean)

    override fun load(file: File): Module {
        if (!allowed(file)) throw EvalException("${file.name} runs Python code, which hasn't been allowed yet (the app asks; the command line takes --allow-python)")
        val path = file.canonicalPath
        val functions = describe(file)
        return Module(file.name, functions.associate { f -> f.name to builtin(file, path, f) }, functions.associate { f -> f.name to Module.Doc(f.signature, f.doc) })
    }

    /** The module's public functions (for completion and hover too). */
    fun describe(file: File): List<Function> {
        val reply = ask(mapOf("op" to "describe", "path" to file.canonicalPath, "version" to version(file)))
        return (reply as List<*>).map { d ->
            d as Map<*, *>
            Function(
                d["name"] as String,
                (d["params"] as List<*>).map { p -> (p as Map<*, *>).let { Param(it["name"] as String, it["unit"] as String?, it["optional"] == true) } },
                d["returns"] as String?, d["signature"] as String, d["doc"] as String,
            )
        }
    }

    private fun builtin(file: File, path: String, f: Function): Builtin {
        val required = f.params.count { !it.optional }
        return Builtin(f.name, required..f.params.size) { args ->
            val values = args.mapIndexed { k, arg -> argument(arg(), f.params[k], f) }
            val reply = ask(mapOf("op" to "call", "path" to path, "version" to version(file), "name" to f.name, "args" to values))
            result(reply, f)
        }
    }

    /** An argument as the plain number Python gets: in the parameter's unit, if it has one. */
    private fun argument(v: Value, p: Param, f: Function): Any = when (v) {
        is Scalar -> {
            val unit = unit(p.unit, f)
            if (unit != null) {
                if (v.q.dimension != unit.one.dimension) throw EvalException("${f.name}: ${p.name} is in ${p.unit}, and this value isn't (its units are ${v.q.dimension})")
                number(unit.number(v.q))
            } else {
                if (!v.q.dimension.isNone) throw EvalException("${f.name}: ${p.name} takes a plain number; give it a unit in the .py file (${p.name}: \"m\"), or divide by one at the call")
                number(v.q.value)
            }
        }
        is ArrayValue -> mapOf("m" to (0 until v.rows).map { r -> (0 until v.cols).map { c -> argument(v[r, c], p, f) } })
        is Text -> v.value
        else -> throw EvalException("${f.name}: ${p.name} can't be passed to Python (numbers, matrices and strings can)")
    }

    private fun number(c: Complex): Any = if (c.isReal) c.re else mapOf("c" to listOf(c.re, c.im))

    /** Python's answer as a value, in the result's unit if it has one. */
    private fun result(v: Any?, f: Function): Value {
        val unit = unit(f.returns, f)
        fun quantity(x: Complex) = unit?.quantity(x) ?: Quantity(x)
        return when {
            v is Double -> Scalar(quantity(Complex(v)))
            v is Long -> Scalar(quantity(Complex(v.toDouble())))
            v is Map<*, *> && "c" in v -> (v["c"] as List<*>).let { Scalar(quantity(Complex((it[0] as Number).toDouble(), (it[1] as Number).toDouble()))) }
            v is Map<*, *> && "s" in v -> Text(v["s"] as String)
            v is Map<*, *> && "m" in v -> {
                val rows = (v["m"] as List<*>).map { it as List<*> }
                if (rows.isEmpty() || rows[0].isEmpty()) throw EvalException("${f.name} returned an empty list")
                ArrayValue.of(rows.size, rows[0].size) { r, c -> result(rows[r].getOrNull(c) ?: throw EvalException("${f.name} returned rows of different lengths"), f) }
            }
            else -> throw EvalException("${f.name} returned something a sheet can't hold")
        }
    }

    /** The unit an annotation names; null for none, or for a Python type such as `float`. */
    private fun unit(annotation: String?, f: Function): DisplayUnit? {
        val text = annotation?.trim()?.takeUnless { it.isEmpty() || it in PYTHON_TYPES } ?: return null
        return units.getOrPut(text) {
            try {
                Evaluator().displayUnit(Parser.parseExpression(text), Builtins.scope)
            } catch (x: ParseException) {
                throw EvalException("${f.name}: '$text' is not a unit")
            } catch (x: EvalException) {
                throw EvalException("${f.name}: '$text' is not a unit (${x.message})")
            }
        }
    }

    private val units = mutableMapOf<String, DisplayUnit>()

    private fun ask(request: Map<String, Any?>): Any? {
        val reply = Json.read(python.ask(Json.write(request))) as Map<*, *>
        reply["error"]?.let { throw EvalException(it.toString()) }
        return reply["ok"]
    }

    /** The file's version, so a changed file isn't answered from the cache. */
    private fun version(file: File) = "${file.lastModified()}:${file.length()}"

    private companion object {
        val PYTHON_TYPES = setOf("float", "int", "complex", "str", "bool", "list", "tuple", "object", "Any", "None")
    }
}
