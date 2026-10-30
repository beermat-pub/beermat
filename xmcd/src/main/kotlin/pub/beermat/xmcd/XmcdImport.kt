package pub.beermat.xmcd

import org.w3c.dom.Element
import org.w3c.dom.Node
import pub.beermat.engine.syntax.Apply
import pub.beermat.engine.syntax.Break
import pub.beermat.engine.syntax.Call
import pub.beermat.engine.syntax.Continue
import pub.beermat.engine.syntax.Define
import pub.beermat.engine.syntax.Evaluate
import pub.beermat.engine.syntax.Expr
import pub.beermat.engine.syntax.ExprStatement
import pub.beermat.engine.syntax.For
import pub.beermat.engine.syntax.Given
import pub.beermat.engine.syntax.Id
import pub.beermat.engine.syntax.IfThen
import pub.beermat.engine.syntax.Index
import pub.beermat.engine.syntax.Line
import pub.beermat.engine.syntax.LocalDefine
import pub.beermat.engine.syntax.Matrix
import pub.beermat.engine.syntax.Num
import pub.beermat.engine.syntax.Op
import pub.beermat.engine.syntax.Otherwise
import pub.beermat.engine.syntax.Parens
import pub.beermat.engine.syntax.Placeholder
import pub.beermat.engine.syntax.Printer
import pub.beermat.engine.syntax.Program
import pub.beermat.engine.syntax.Symbolic
import pub.beermat.engine.syntax.ProgramLine
import pub.beermat.engine.syntax.Range
import pub.beermat.engine.syntax.Return
import pub.beermat.engine.syntax.ResultSlot
import pub.beermat.engine.syntax.Statement
import pub.beermat.engine.syntax.Str
import pub.beermat.engine.syntax.While
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Imports a `.xmcd` worksheet (schema `worksheet30`; `worksheet20` and `worksheet10` in older files) as `.bbm` text
 * (docs/xmcd.md). The math XML maps onto the expression tree almost element for element.
 * Anything that can't be mapped yet becomes a `?` placeholder and is listed in [Imported.warnings].
 */
object XmcdImport {

    /** The `.bbm` text, and what could not be imported faithfully. */
    /**
     * The `.bbm` text, and what could not be imported faithfully. [expected] holds, for each statement
     * that shows a result (in the order written), the result saved in the file, in SI; null where it had none.
     */
    data class Imported(val text: String, val warnings: List<String>, val expected: List<Expected> = emptyList())

    /**
     * The result saved in the file for the statement whose first line is [statement]: [values] in SI, or,
     * when [displayed], the numbers as shown in the statement's own unit (a unit placeholder without units, such as `%`,
     * or a temperature scale, which the file names `degC` even where the sheet made `°C` a plain degree).
     * [values] is empty where the file kept no result we can read: the entry only keeps the order.
     */
    class Expected(val statement: String, val values: DoubleArray, val displayed: Boolean, /** Whether the saved result had a unit. */ val united: Boolean = false)

    /** The warning for a sheet that references another worksheet (Insert Reference): what it brings is missing. */
    const val REFERENCE = "a reference to another worksheet is not imported: the definitions it brings are missing"

    /** The math namespace: `math30`, or `math20` and `math10` in older `.xmcd` files. */
    private val ML =Regex("""http://schemas\.mathsoft\.com/math\d+""")

    private val Element.isMath: Boolean get() = namespaceURI?.let(ML::matches) == true

    /** What `ml:provenance` (where a region was copied from) holds besides the math. */
    private val PROVENANCE = setOf("originRef", "parentRef", "comment", "contentHash", "hash")

    fun import(input: InputStream, title: String): Imported {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // A worksheet is data: no external entities or DTDs.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        // `.xmcdz` is the same XML, gzipped.
        val buffered = input.buffered().apply { mark(2) }
        val gzipped = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val doc = factory.newDocumentBuilder().parse(if (gzipped) java.util.zip.GZIPInputStream(buffered) else buffered)
        return Importer(title).run(doc.documentElement)
    }

    private class Importer(val title: String) {
        val warnings = linkedSetOf<String>()
        var mathCount = 0
        var textCount = 0

        fun run(root: Element): Imported {
            // Every region, areas' too, placed on one page with the area lines as markers (place()), then in
            // The original worksheet's order: by the region's baseline (`align-y`; the top in files without it), then
            // left to right, so a fraction reaching up beside a definition comes after it.
            val entries = mutableListOf<Entry>()
            root.elements().firstOrNull { it.localName == "regions" }?.let { place(it.elements().filter { r -> r.localName == "region" }, 0f, entries) }
            val ordered = entries.withIndex().sortedWith(compareBy({ it.value.key }, { it.value.left }, { it.index })).map { it.value }

            // The sheet's multiplication style, `<operators multiplication="…">`: thin or no space draws `*` with a space.
            val operators = root.getElementsByTagNameNS("*", "operators").list().filterIsInstance<Element>().firstOrNull()
            val spaced = operators?.attr("multiplication") in setOf("thin-space", "no-space")
            // The worksheet's own ORIGIN and TOL (Tools → Worksheet Options), when they aren't the defaults.
            val builtIn = root.getElementsByTagNameNS("*", "builtInVariables").list().filterIsInstance<Element>().firstOrNull()
            val origin = builtIn?.attr("array-origin")?.toIntOrNull()?.takeIf { it != 0 }?.let { listOf("origin: $it") }.orEmpty()
            val tolerance = builtIn?.attr("convergence-tolerance")?.toDoubleOrNull()?.takeIf { it != 1e-3 }?.let { listOf("tolerance: $it") }.orEmpty()
            val out = (listOf("---", "beermat: 1", "title: ${yaml(title)}") + (if (spaced) listOf("multiplication: space") else emptyList()) + origin + tolerance + listOf("---", "")).toMutableList()
            // The worksheet's binary items: region images (PNG) and the program's own data, base64, sometimes gzipped.
            root.getElementsByTagNameNS("*", "item").list().filterIsInstance<Element>().forEach { items[it.attr("item-id")] = it }
            for (e in ordered) out += e.marker?.let { listOf(it, "") } ?: region(e.region!!)
            // Pictures' data goes last, as Beermat writes it (docs/file-format.md#images).
            if (images.isNotEmpty()) out += listOf("") + images.map { (ref, uri) -> "[$ref]: $uri" }
            return Imported(out.joinToString("\n").trimEnd() + "\n", warnings.toList(), expected)
        }

        /** A region, or an area's line (a [marker]), at the place where it is evaluated. */
        class Entry(val key: Float, val left: Float, val region: Element? = null, val marker: String? = null)

        var areaCount = 0
        var excelCount = 0

        private fun Element.top() = attr("top").toFloatOrNull() ?: 0f

        /**
         * Places [regions] (siblings: the sheet's, or an area's) [dy] lower, on one page, adding
         * them to [out]. The file stores a **collapsed** area's regions relative to the area and
         * shifts what follows up over them; an expanded area's regions are where they are. So a
         * collapsed area's regions go below its line, and everything after it moves down by
         * their height: Beermat's own collapsed area then shows the page.
         * Positions are written back into the elements, which [region] reads.
         */
        fun place(regions: List<Element>, dy: Float, out: MutableList<Entry>) {
            var shift = dy
            for (r in regions.sortedBy { it.attr("align-y").toFloatOrNull() ?: it.top() }) {
                val top = r.top() + shift
                val alignY = r.attr("align-y").toFloatOrNull()?.plus(shift)
                r.setAttribute("top", top.toString())
                alignY?.let { r.setAttribute("align-y", it.toString()) }
                val area = r.elements().firstOrNull { it.localName == "area" }
                if (area == null) {
                    out += Entry(alignY ?: top, r.attr("left").toFloatOrNull() ?: 0f, r)
                    continue
                }
                val inner = area.elements().filter { it.localName == "region" }
                val collapsed = area.attr("is-collapsed") == "true"
                val title = area.attr("name").ifBlank { "Area" }.replace("\"", "'")
                val id = "a${++areaCount}"
                out += Entry(top, 0f, marker = "<!-- #$id area y=${number(top.toString())} title=\"$title\"${if (collapsed) " collapsed" else ""} -->")
                val before = out.size
                place(inner, if (collapsed) top + AREA_LINE else shift, out)
                val bottom = out.drop(before).mapNotNull { e -> e.region?.let { it.top() + (it.attr("height").toFloatOrNull() ?: 0f) } }.maxOrNull() ?: (top + AREA_LINE)
                out += Entry(bottom + 1, 0f, marker = "<!-- /area y=${number((bottom + 1).toString())} -->")
                if (collapsed) shift += bottom + AREA_LINE - top
            }
        }

        val expected = mutableListOf<Expected>()
        val items = mutableMapOf<String, Element>()
        val images = mutableListOf<Pair<String, String>>()
        var imageCount = 0

        /** A binary item's bytes, unzipped. */
        fun item(id: String): ByteArray? {
            val e = items[id] ?: return null
            val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(e.textContent.trim()) }.getOrNull() ?: return null
            return if (e.attr("content-encoding") == "gzip") runCatching { java.util.zip.GZIPInputStream(bytes.inputStream()).readBytes() }.getOrNull() else bytes
        }

        /**
         * The picture last drawn of region [r] (its `rendering`, a PNG), as a picture region
         * at its place and size, with [alt] as its text; null if it has none.
         */
        fun rendering(r: Element, alt: String): List<String>? {
            val id = r.elements().firstOrNull { it.localName == "rendering" }?.attr("item-idref") ?: return null
            val png = item(id)?.takeIf { it.size > 8 && it[0] == 0x89.toByte() && it[1] == 'P'.code.toByte() } ?: return null
            val ref = "i${++imageCount}"
            images += ref to "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png)
            val w = number(r.attr("width"))
            val h = number(r.attr("height"))
            return listOf("<!-- #$ref x=${number(r.attr("left"))} y=${number(r.attr("top"))} w=$w h=$h -->", "![$alt][$ref]", "")
        }

        fun region(r: Element): List<String> {
            val x = number(r.attr("left"))
            val y = number(r.attr("top"))
            // A reference to another worksheet (Insert Reference) brings that sheet's definitions; not imported (Later, if ever: SPEC).
            if (r.elements().any { it.localName == "reference" }) {
                warnings += REFERENCE
                return emptyList()
            }
            val content = r.elements().firstOrNull { it.localName in setOf("math", "text", "area", "plot", "picture", "component") } ?: return emptyList()
            if (content.localName == "component") {
                // Excel and other components define variables (their outputs) that the sheet below uses.
                // Outputs written as a matrix of names are one output each, in order († as for inputs: one range each).
                val outputs = content.elements().firstOrNull { it.localName == "outputs" }?.elements()
                    ?.flatMap { if (it.localName == "matrix") it.elements() else listOf(it) }?.map { Printer.print(id(it)) }.orEmpty()
                val kind = if (content.attr("clsid-buddy").lowercase() == EXCEL) "an Excel component" else "a component"
                val defines = if (outputs.isEmpty()) "" else ", defining " + outputs.joinToString(", ") { "`$it`" }
                val bottom = number(((r.attr("top").toFloatOrNull() ?: 0f) + (r.attr("height").toFloatOrNull() ?: 0f) + 2).toString())
                // An Excel component's outputs, from the workbook's last calculated values (not live: its formulas aren't run).
                // An Excel component becomes a Beermat table (docs/tables.md#import-from-mcdx-and-xmcd),
                // its inputs taking the sheet's values and its outputs defined beside it, from the table where they can be.
                if (kind == "an Excel component") {
                    val inputs = content.elements().firstOrNull { it.localName == "inputs" }?.elements()?.map { expr(it) }.orEmpty()
                    val name = "Excel${++excelCount}"
                    ExcelComponent.convert(content, item(content.attr("item-idref")), inputs, outputs, name)?.let { t ->
                        warnings += "an Excel component is imported as a table$defines"
                        warnings += t.warnings
                        val right = number(((r.attr("left").toFloatOrNull() ?: 0f) + (r.attr("width").toFloatOrNull() ?: 0f) + 12).toString())
                        val definitions = if (t.definitions.isEmpty()) emptyList() else listOf("```calc {#r${++mathCount} x=$right y=$y}") + t.definitions + listOf("```", "")
                        return listOf("```table {#tb$excelCount x=$x y=$y name=$name}") + t.table + listOf("```", "") + definitions
                    }
                }
                // A control (list box, check box, radio buttons, text box): its picture, and its current value defined beside it.
                if (kind != "an Excel component" && outputs.size == 1) {
                    val inputs = content.elements().firstOrNull { it.localName == "inputs" }?.elements()?.map { expr(it) }.orEmpty()
                    ControlComponent.read(item(content.attr("item-idref")), inputs, content.attr("clsid-buddy"))?.let { control ->
                        warnings += "a ${control.kind} is imported as its current value: not live"
                        val right = number(((r.attr("left").toFloatOrNull() ?: 0f) + (r.attr("width").toFloatOrNull() ?: 0f) + 12).toString())
                        val note = listOf("<!-- #t${++textCount} x=$x y=$bottom -->", "*(A ${control.kind} from the original worksheet: its current value, defined beside it; not live.)*", "")
                        return (rendering(r, "A ${control.kind}") ?: emptyList()) + listOf("```calc {#r${++mathCount} x=$right y=$y}", "${outputs[0]} := ${control.value}", "```", "") + note
                    }
                }
                val values = if (kind == "an Excel component") ExcelComponent.outputs(content, item(content.attr("item-idref")), outputs.size) else null
                if (values != null) {
                    warnings += "an Excel component is imported as the values it last calculated$defines"
                    val right = number(((r.attr("left").toFloatOrNull() ?: 0f) + (r.attr("width").toFloatOrNull() ?: 0f) + 12).toString())
                    val statements = outputs.zip(values).map { (name, v) -> "$name := $v" }
                    val note = listOf("<!-- #t${++textCount} x=$x y=$bottom -->", "*(An Excel component from the original worksheet: its values as last calculated, defined beside it; its formulas are not live yet.)*", "")
                    return (rendering(r, "An Excel component") ?: emptyList()) + listOf("```calc {#r${++mathCount} x=$right y=$y}") + statements + listOf("```", "") + note
                }
                warnings += "$kind is not imported yet$defines"
                // Its picture, so its values can at least be read, and a note just below it.
                val note = listOf("<!-- #t${++textCount} x=$x y=$bottom -->", "*(${kind.replaceFirstChar(Char::uppercaseChar)} from the original worksheet$defines: shown as its picture; not live yet.)*", "")
                return (rendering(r, kind.replaceFirstChar(Char::uppercaseChar)) ?: emptyList()) + note
            }
            if (content.localName == "plot" || content.localName == "picture") {
                // The file keeps a plot's settings in its own binary format: for now, the plot as it was last drawn.
                val what = if (content.localName == "plot") "A plot from the original worksheet, as it was last drawn" else "A picture from the original worksheet"
                rendering(r, what)?.let { picture ->
                    if (content.localName == "plot") warnings += "a plot is imported as its picture: not live yet"
                    return picture
                }
            }
            return when (content.localName) {
                "math" -> {
                    val st = statementOf(content) ?: return emptyList()
                    val printed = try {
                        Printer.print(st)
                    } catch (ex: IllegalArgumentException) {
                        warnings += "a region could not be written: ${ex.message}"
                        return emptyList()
                    }
                    // The result saved in the file, to check the import against (SavedResults).
                    if (st is Evaluate || (st is Define && st.result != null)) {
                        val top = content.elements().firstOrNull()?.let { if (it.localName == "provenance") it.elements().lastOrNull { e -> e.localName !in PROVENANCE } else it }
                        val eval = if (top?.localName == "eval") top else top?.elements()?.getOrNull(1)?.takeIf { it.localName == "eval" }
                        val result = eval?.elements()?.firstOrNull { it.localName == "result" }
                        // On a temperature scale, as shown: the sheet may have made °C a plain degree, which the file still names degC.
                        val scale = result != null && SavedResults.onScale(result)
                        val values = result?.let { SavedResults.read(it, raw = scale) }
                        if (values != null) {
                            val override = eval.elements().any { it.localName == "unitOverride" }
                            val united = result.elements().firstOrNull()?.takeIf { it.localName == "unitedValue" }?.elements()?.any { it.localName == "unitMonomial" } == true
                            expected += Expected(printed.lineSequence().first().trim(), values, scale || (override && !united), united)
                        } else {
                            // No result kept (or none we read): still listed, so the results after it pair up in order.
                            expected += Expected(printed.lineSequence().first().trim(), DoubleArray(0), false)
                        }
                    }
                    val id = "r${++mathCount}"
                    val disabled = if (content.attr("disable-calc") == "true") " disabled" else ""
                    listOf("```calc {#$id x=$x y=$y$disabled}", printed, "```", "")
                }
                "text" -> {
                    val lines = text(content)
                    if (lines.isEmpty()) emptyList() else listOf("<!-- #t${++textCount} x=$x y=$y -->") + lines + ""
                }
                "area" -> emptyList()
                else -> {
                    warnings += "a ${content.localName} region is not imported yet"
                    listOf("<!-- #t${++textCount} x=$x y=$y -->", "*(A ${content.localName} from the original worksheet: not imported yet.)*", "")
                }
            }
        }

        // Text regions.

        /** Paragraphs as Markdown; inline math as `code` in linear syntax; headings by style. */
        fun text(t: Element): List<String> {
            val out = mutableListOf<String>()
            for (p in t.elements().filter { it.localName == "p" }) {
                val body = inline(p).trim()
                val style = p.attr("style")
                out += when {
                    body.isEmpty() -> ""
                    style.startsWith("Heading 1") -> "# $body"
                    style.startsWith("Heading 2") -> "## $body"
                    style.startsWith("Heading 3") -> "### $body"
                    else -> body
                }
            }
            return out.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        }

        fun inline(e: Element): String = buildString {
            for (n in e.childNodes.list()) {
                when {
                    n.nodeType == Node.TEXT_NODE -> append(n.textContent.replace(Regex("\\s+"), " "))
                    n is Element && n.localName == "region" -> {
                        val math = n.elements().firstOrNull { it.localName == "math" }
                        val st = math?.let(::statementOf)
                        if (st != null) append("`${Printer.print(st)}`")
                    }
                    n is Element && n.localName == "b" -> inline(n).trim().takeIf { it.isNotEmpty() }?.let { append("**$it**") }
                    n is Element && n.localName == "i" -> inline(n).trim().takeIf { it.isNotEmpty() }?.let { append("*$it*") }
                    n is Element && n.localName != "inlineAttr" -> append(inline(n))
                }
            }
        }

        // Math regions.

        fun statementOf(math: Element): Statement? {
            var e = math.elements().firstOrNull() ?: return null
            if (e.localName == "provenance") e = e.elements().lastOrNull { it.isMath && it.localName !in PROVENANCE } ?: return null
            return try {
                statement(e)
            } catch (ex: IllegalArgumentException) {
                warnings += "a region could not be imported: ${ex.message}"
                null
            }
        }

        fun statement(e: Element): Statement = when (e.localName) {
            "define", "globalDefine" -> {
                val (target, value) = e.elements().let { it[0] to it[1] }
                // `x := expr = ?`: a definition that also shows its value.
                if (value.localName == "eval") {
                    val (shown, slot) = evaluated(value)
                    return Define(target(target), shown, global = e.localName == "globalDefine", result = slot)
                }
                val v = if (value.localName == "program") Program(value.elements().map(::programLine)) else expr(value)
                Define(target(target), v, global = e.localName == "globalDefine")
            }
            "eval" -> evaluated(e).let { (shown, slot) -> Evaluate(shown, slot) }
            // `expr → keyword → result`: each `command` is a keyword, alone (`simplify`) or with its arguments (`solve, x`).
            "symEval" -> {
                val parts = e.elements()
                val keywords = parts.filter { it.localName == "command" }.mapNotNull { c ->
                    val k = c.elements().firstOrNull() ?: return@mapNotNull null
                    if (k.localName == "sequence") k.elements().let { s -> Call(Id(id(s[0]).name), s.drop(1).map(::expr)) } else Id(id(k).name)
                }
                Symbolic(expr(parts.first { it.localName != "command" && it.localName != "symResult" }), keywords)
            }
            // `Given` alone in a region starts a solve block.
            "id" -> if (e.textContent.trim() == "Given" && !e.hasAttribute("subscript")) Given else ExprStatement(expr(e))
            else -> ExprStatement(expr(e))
        }

        fun target(e: Element): Expr = when (e.localName) {
            "function" -> {
                val (name, vars) = e.elements().let { it[0] to it[1] }
                Call(id(name), vars.elements().map { id(it) })
            }
            else -> expr(e).let(::unwrapped).let { if (Define.isDefinitionTarget(it)) it else Id("unknown").also { _ -> warnings += "a definition target could not be imported: ${describe(e)}" } }
        }

        fun expr(e: Element): Expr = when (e.localName) {
            "id" -> id(e)
            // The file's `1Fh`, `17o`, `101b` come with a base: written with Beermat's prefixes, `0x1F`, `0o17`, `0b101`.
            "real" -> real(e.textContent.trim().let { t -> when (e.attr("base")) { "16" -> based("0x", t); "8" -> based("0o", t); "2" -> based("0b", t); else -> t } })
            "imag" -> real(e.textContent.trim() + "i")
            "str" -> Str(e.textContent)
            "parens" -> Parens(expr(e.elements().single()))
            // `return x` outside a program, which an `.xmcd` file accepts as a definition's value: just x.
            "return" -> expr(e.elements().single())
            "apply" -> apply(e)
            "matrix" -> matrix(e)
            "range" -> range(e)
            // A program anywhere: written with the word `program` in its place (docs/syntax.md#programs).
            "program" -> Program(e.elements().map(::programLine))
            "tryCatch" -> e.elements().let { Apply(Op.ON_ERROR, expr(it[0]), expr(it[1])) }
            "sequence" -> e.elements().singleOrNull()?.let(::expr) ?: unsupported("a sequence")
            "placeholder" -> Placeholder
            // Solvers are elements of their own: `ml:Find`; the ones Beermat lacks keep their name, so the error names them.
            in SOLVERS -> Id(SOLVERS.getValue(e.localName))
            // Where the expression was copied from: only the expression matters.
            "provenance" -> e.elements().lastOrNull { it.localName !in PROVENANCE }?.let(::expr) ?: unsupported("an empty provenance")
            else -> unsupported(e.localName)
        }

        /** An `ml:eval`: the expression, and the result slot with the unit from its placeholder (`ml:unitOverride`), if any. */
        fun evaluated(e: Element): Pair<Expr, ResultSlot> {
            val parts = e.elements()
            val shown = parts.first { it.localName != "result" && it.localName != "unitOverride" }
            val unit = parts.firstOrNull { it.localName == "unitOverride" }?.elements()?.firstOrNull()?.let(::expr)
            // `Area*height → explicit = 12 m³`: a symbolic evaluation evaluated numerically. The keywords only
            // change how it is shown; its number is the expression's, so that is what is evaluated.
            if (shown.localName == "symEval") {
                val inner = shown.elements().first { it.localName != "command" && it.localName != "symResult" }
                return expr(inner) to ResultSlot(unit)
            }
            return expr(shown) to ResultSlot(unit)
        }

        /** `(M)[i, j]` as a target is `M[i, j]`: the file keeps the parentheses a user typed around the name. */
        fun unwrapped(e: Expr): Expr = if (e is Index && e.target is Parens) e.copy(target = (e.target as Parens).inner) else e

        /** An element's shape, for a warning: `apply(indexer, id, sequence)`. */
        fun describe(e: Element): String = e.localName + e.elements().takeIf { it.isNotEmpty() }?.joinToString(", ", "(", ")") { it.localName }.orEmpty()

        fun unsupported(what: String): Expr {
            warnings += "'$what' is not supported yet: imported as ?"
            return Placeholder
        }

        /** [t] with a base prefix, after its sign. */
        fun based(prefix: String, t: String) = if (t.startsWith("-")) "-$prefix${t.drop(1)}" else prefix + t

        fun real(t: String): Expr = when {
            t.startsWith("-") -> Apply(Op.NEG, real(t.drop(1)))
            // `2.` is 2.
            t.endsWith(".") && t.length > 1 -> real(t.dropLast(1))
            else -> runCatching { Num(t) }.getOrElse { unsupported("the number $t") }
        }

        fun apply(e: Element): Expr {
            val children = e.elements()
            val head = children.first()
            val args = children.drop(1)
            // A function call: the name (or an expression) then the arguments, in a sequence when several.
            if (head.isMath && head.localName !in OPERATORS) {
                val a = args.singleOrNull()?.takeIf { it.localName == "sequence" }?.elements() ?: args
                val fn = expr(head)
                // A function written as an operator (`a op(5) b`, fixity infix, prefix or postfix): the file has
                // no partial calls, so the operator's own arguments come first, `op(5, a, b)`.
                if (e.hasAttribute("fixity") && fn is Call) return Call(fn.fn, fn.args + a.map(::expr))
                return Call(fn, a.map(::expr))
            }
            fun x(k: Int) = expr(args[k])
            fun indices(k: Int) = args[k].let { if (it.localName == "sequence") it.elements().map(::expr) else listOf(expr(it)) }
            return when (head.localName) {
                "plus" -> Apply(Op.ADD, x(0), x(1))
                "minus" -> if (args.size == 1) Apply(Op.NEG, x(0)) else Apply(Op.SUB, x(0), x(1))
                "neg" -> Apply(Op.NEG, x(0))
                // The file's styles: `dot`, and `x` (a cross, which reads as a cross product here), become a dot;
                // `no-space` a thin space (`qL` touching would read as one name); `auto-select` follows the sheet.
                "mult" -> when (head.attr("style")) {
                    "dot", "x" -> Apply(Op.MUL, listOf(x(0), x(1)), dot = true)
                    "no-space" -> Apply(Op.MUL, listOf(x(0), x(1)), implicit = true)
                    else -> Apply(Op.MUL, listOf(x(0), x(1)))
                }
                "div" -> Apply(Op.DIV, x(0), x(1))
                "pow" -> Apply(Op.POW, x(0), x(1))
                "equal" -> Apply(Op.EQ, x(0), x(1))
                "notEqual" -> Apply(Op.NE, x(0), x(1))
                "lessThan" -> Apply(Op.LT, x(0), x(1))
                "lessOrEqual" -> Apply(Op.LE, x(0), x(1))
                "greaterThan" -> Apply(Op.GT, x(0), x(1))
                "greaterOrEqual" -> Apply(Op.GE, x(0), x(1))
                "and" -> Apply(Op.AND, x(0), x(1))
                "or" -> Apply(Op.OR, x(0), x(1))
                "xor" -> Apply(Op.XOR, x(0), x(1))
                "not" -> Apply(Op.NOT, x(0))
                "absval" -> Apply(Op.ABS, x(0))
                "factorial" -> Apply(Op.FACTORIAL, x(0))
                "transpose" -> Apply(Op.TRANSPOSE, x(0))
                "matcol" -> Apply(Op.COLUMN, x(0), x(1))
                "sqrt" -> Call(Id("sqrt"), x(0))
                "nthRoot" -> Call(Id("nthroot"), x(1), x(0))
                "indexer" -> Index(x(0), indices(1))
                "vectorize" -> Call(Id("vec"), x(0))
                "vectorSum" -> Call(Id("sum"), x(0))
                "crossProduct" -> Call(Id("cross"), x(0), x(1))
                "conjugate" -> Call(Id("conj"), x(0))
                "determinant" -> Apply(Op.ABS, x(0))
                "summation", "product", "integral", "derivative" -> iteration(head, args)
                else -> unsupported(head.localName)
            }
        }

        /** Σ, Π, ∫ and d/dx: a lambda for the variable and body, and bounds when there are limits. */
        fun iteration(head: Element, args: List<Element>): Expr {
            val name = when (head.localName) {
                "summation" -> "sum"
                "product" -> "prod"
                "integral" -> "int"
                else -> "diff"
            }
            val lambda = args.firstOrNull { it.localName == "lambda" } ?: return unsupported("a ${head.localName} without a lambda")
            val (vars, body) = lambda.elements().let { it[0] to it[1] }
            val variable = id(vars.elements().first())
            val bounds = args.firstOrNull { it.localName == "bounds" }?.elements()?.map(::expr) ?: emptyList()
            return Call(Id(name), listOf(expr(body), variable) + bounds)
        }

        fun matrix(e: Element): Expr {
            val rows = e.attr("rows").toInt()
            val cols = e.attr("cols").toInt()
            val cells = e.elements().map(::expr)
            // Stored column by column.
            return Matrix(List(rows) { r -> List(cols) { c -> cells[c * rows + r] } })
        }

        fun range(e: Element): Expr {
            val (first, last) = e.elements().let { it[0] to it[1] }
            return if (first.localName == "sequence") {
                val (a, b) = first.elements().let { it[0] to it[1] }
                Range(expr(a), expr(b), expr(last))
            } else {
                Range(expr(first), null, expr(last))
            }
        }

        fun programLine(e: Element): ProgramLine = when (e.localName) {
            "localDefine" -> e.elements().let { LocalDefine(target(it[0]).let { t -> if (t is Call && !Define.isFunctionTarget(t)) Id("unknown") else t }, expr(it[1])) }
            "ifThen" -> e.elements().let { IfThen(expr(it[0]), body(it[1])) }
            "otherwise" -> Otherwise(body(e.elements().single()))
            // The file keeps parentheses typed around the range: `for k ∈ (1..n)`.
            "for" -> e.elements().let { For(id(it[0]), expr(it[1]).let { r -> if (r is Parens && r.inner is Range) r.inner else r }, body(it[2])) }
            "while" -> e.elements().let { While(expr(it[0]), body(it[1])) }
            "return" -> Return(expr(e.elements().single()))
            "break" -> Break
            "continue" -> Continue
            "parens" -> e.elements().single().let { if (it.localName in LINES) programLine(it) else Line(expr(e)) }
            else -> Line(expr(e))
        }

        /** The body of a block: a nested program's lines, or one line. */
        fun body(e: Element): List<ProgramLine> = when {
            e.localName == "program" -> e.elements().map(::programLine)
            e.localName == "parens" && e.elements().single().localName == "program" -> e.elements().single().elements().map(::programLine)
            else -> listOf(programLine(e))
        }

        /** An identifier, kept as the file has it: any character goes (the printer quotes what needs it). */
        fun id(e: Element): Id {
            fun keep(s: String) = s.filter { it != '`' && it != '\n' && it != '\r' }
            val name = keep(e.textContent.trim()).ifEmpty { "x".also { warnings += "a name with no characters imported as 'x'" } }
            // `subscript=""` is an empty subscript: a different name from no subscript, drawn the same (Dan, 2026-10-28).
            val sub = if (e.hasAttribute("subscript")) keep(e.attr("subscript")) else null
            return Id(name, sub)
        }

        fun number(s: String) = s.toFloatOrNull()?.let { if (it == Math.rint(it.toDouble()).toFloat()) it.toInt().toString() else it.toString() } ?: "0"

        fun yaml(s: String) = if (s.any { it in ":#\"'" }) "\"${s.replace("\"", "\\\"")}\"" else s
    }

    /** The space an area's line takes above its regions, in points (Beermat draws it 16 high). */
    private const val AREA_LINE = 18f

    /** The class id an Excel component is embedded with (Excel's worksheet). */
    private const val EXCEL = "00020820-0000-0000-c000-000000000046"

    /** The file's solver elements, and the names they have here. */
    private val SOLVERS = mapOf(
        "Find" to "find", "Minerr" to "minerr", "Minimize" to "Minimize", "Maximize" to "Maximize",
        "Odesolve" to "Odesolve", "Pdesolve" to "Pdesolve", "polyroots" to "polyroots", "Polyroots" to "polyroots", "genfit" to "genfit",
    )

    /** Operator elements that come first in an `ml:apply`. */
    private val OPERATORS = setOf(
        "plus", "minus", "neg", "mult", "div", "pow", "equal", "notEqual", "lessThan", "lessOrEqual",
        "greaterThan", "greaterOrEqual", "and", "or", "xor", "not", "absval", "factorial", "transpose",
        "matcol", "sqrt", "nthRoot", "indexer", "vectorize", "summation", "product", "integral", "derivative",
        "rangeSum", "rangeProduct", "limit", "scale", "vectorSum", "determinant", "crossProduct", "conjugate",
    )

    private val LINES = setOf("localDefine", "ifThen", "otherwise", "for", "while", "return", "break", "continue", "program")

    private fun Element.attr(name: String): String = getAttribute(name)

    private fun Element.elements(): List<Element> = childNodes.list().filterIsInstance<Element>()

    private fun org.w3c.dom.NodeList.list(): List<Node> = (0 until length).map { item(it) }
}
