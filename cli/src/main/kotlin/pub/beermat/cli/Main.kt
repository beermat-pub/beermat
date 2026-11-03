package pub.beermat.cli

import pub.beermat.export.SheetExport
import pub.beermat.format.Annotation
import pub.beermat.format.Calculation
import pub.beermat.format.Check
import pub.beermat.format.Worksheet
import pub.beermat.format.WorksheetText
import pub.beermat.xmcd.XmcdImport
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** The `beermat` command (docs/cli.md). */
fun main(args: Array<String>) {
    val out = PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)
    val err = PrintStream(FileOutputStream(FileDescriptor.err), true, Charsets.UTF_8)
    exitProcess(Cli(out, err).run(args.toList()))
}

class Cli(private val out: PrintStream, private val err: PrintStream) {
    private companion object {
        /** Options that take a value: `--to md` or `--to=md`. */
        val VALUED = setOf("--to", "--out")
    }


    fun run(args: List<String>): Int {
        val command = args.firstOrNull() ?: return usage()
        val options = mutableSetOf<String>()
        val values = mutableMapOf<String, String>()
        val files = mutableListOf<String>()
        val rest = args.drop(1)
        var k = 0
        while (k < rest.size) {
            val a = rest[k++]
            when {
                a in VALUED -> values[a] = rest.getOrNull(k++) ?: return usage("$a needs a value")
                a.startsWith("--") && '=' in a -> values[a.substringBefore('=')] = a.substringAfter('=')
                a.startsWith("--") -> options += a
                else -> files += a
            }
        }
        val allowed = when (command) {
            "eval" -> setOf("--write", "--json", "--allow-python")
            "check" -> setOf("--allow-python")
            "export" -> setOf("--to", "--out", "--allow-python")
            "import" -> setOf("--out")
            "mcp" -> {
                pub.beermat.mcp.Mcp(System.`in`.bufferedReader(Charsets.UTF_8), out).serve()
                return 0
            }
            "packages" -> return packages(args.drop(1))
            "python" -> return python(args.drop(1))
            "bridge" -> return bridge()
            "help", "--help", "-h" -> { usage(out); return 0 }
            "version", "--version", "-v" -> { out.println("beermat ${pub.beermat.engine.Version.current}"); return 0 }
            else -> return usage("unknown command '$command'")
        }
        (options + values.keys - allowed).firstOrNull()?.let { return usage("unknown option '$it' for $command") }
        val file = files.singleOrNull() ?: return usage("$command takes one worksheet file")
        // A sheet's Python files (use "file.py") run only when asked.
        if ("--allow-python" in options) pub.beermat.format.PythonConsent.allowed = { true }

        if (command == "import") return import(file, values["--out"])
        val path = Path.of(file)
        val text = try {
            Files.readString(path)
        } catch (e: IOException) {
            err.println("beermat: cannot read $file: ${e.message}")
            return 2
        }
        val sheet = WorksheetText.read(text)
        return when (command) {
            "eval" -> eval(file, path, sheet, write = "--write" in options, json = "--json" in options)
            "export" -> export(sheet, values["--to"] ?: "md", values["--out"])
            else -> check(file, path, sheet)
        }
    }

    private fun eval(file: String, path: Path, sheet: Worksheet, write: Boolean, json: Boolean): Int {
        val calc = Calculation.of(sheet, path.toAbsolutePath().parent?.toFile())
        val shown = calc.lines.filter { it.annotation != null }
        if (json) {
            out.println(Json.obj("file" to file, "lines" to shown.map { outcome ->
                val (line, a) = outcome
                Json.obj(
                    "line" to line.number,
                    "statement" to outcome.statement,
                    if (a is Annotation.Error) "error" to a.message else "result" to (a as Annotation.Result).text,
                )
            }))
        } else {
            val width = shown.maxOfOrNull { "${it.line.number}: ${it.line.source.trim()}".length } ?: 0
            for ((line, a) in shown) {
                val left = "${line.number}: ${line.source.trim()}".padEnd(width)
                out.println(if (a is Annotation.Error) "$left   #! ${a.message}" else "$left   #= ${(a as Annotation.Result).text}")
            }
        }
        if (write) {
            try {
                Files.writeString(path, WorksheetText.write(calc.written()))
            } catch (e: IOException) {
                err.println("beermat: cannot write $file: ${e.message}")
                return 2
            }
        }
        calc.settingProblems.forEach { err.println("$file: $it") }
        return if (calc.hasErrors) 1 else 0
    }

    /** Converts a `.xmcd` (or gzipped `.xmcdz`) file to `.bbm`, next to it unless `--out` says where. */
    private fun import(file: String, outFile: String?): Int {
        val source = Path.of(file)
        val target = Path.of(outFile ?: file.removeSuffix(".xmcdz").removeSuffix(".xmcd") + ".bbm")
        val imported = try {
            Files.newInputStream(source).use { XmcdImport.import(it, source.fileName.toString().removeSuffix(".xmcdz").removeSuffix(".xmcd")) }
        } catch (e: IOException) {
            err.println("beermat: cannot read $file: ${e.message}")
            return 2
        } catch (e: org.xml.sax.SAXException) {
            err.println("beermat: $file is not a valid .xmcd file: ${e.message}")
            return 2
        }
        try {
            Files.writeString(target, imported.text)
        } catch (e: IOException) {
            err.println("beermat: cannot write $target: ${e.message}")
            return 2
        }
        imported.warnings.forEach { err.println("$file: $it") }
        out.println("wrote $target")
        return 0
    }

    private fun export(sheet: Worksheet, to: String, outFile: String?): Int {
        // A Word document is a zip, so it goes to a file (docs/export.md#word).
        if (to == "docx" || to == "word") {
            outFile ?: return usage("export --to docx needs --out <file.docx>")
            return try {
                Files.write(Path.of(outFile), pub.beermat.export.Docx.write(sheet))
                0
            } catch (e: IOException) {
                err.println("beermat: cannot write $outFile: ${e.message}")
                2
            }
        }
        val text = when (to) {
            "md", "markdown" -> SheetExport.markdown(sheet)
            "latex", "tex" -> SheetExport.latex(sheet)
            else -> return usage("export --to takes md, latex or docx, not '$to'")
        }
        if (outFile == null) {
            out.print(text)
            return 0
        }
        return try {
            Files.writeString(Path.of(outFile), text)
            0
        } catch (e: IOException) {
            err.println("beermat: cannot write $outFile: ${e.message}")
            2
        }
    }

    private fun check(file: String, path: Path, sheet: Worksheet): Int {
        val problems = Check.of(sheet, path.toAbsolutePath().parent?.toFile())
        if (problems.isEmpty()) {
            out.println("ok")
            return 0
        }
        problems.forEach { out.println(if (it.line != null) "$file:${it.line}: ${it.message}" else "$file: ${it.message}") }
        return 1
    }

    /** `beermat packages`: the user's own Python packages, for Python functions in a sheet (docs/cli.md#packages). */
    private fun packages(args: List<String>): Int {
        val home = pub.beermat.symbolic.Python.home() ?: run {
            err.println("beermat: this copy of Beermat has no Python")
            return 2
        }
        val P = pub.beermat.symbolic.Packages
        pub.beermat.symbolic.OwnPython.interpreter()?.let {
            out.println("Python functions run in your own Python, $it, with its own packages (its pip or conda installs them); these are for Beermat's Python.")
        }
        fun done(o: pub.beermat.symbolic.Packages.Outcome): Int {
            (if (o.ok) out else err).print(o.output)
            return if (o.ok) 0 else 1
        }
        return when (args.firstOrNull() ?: "list") {
            "list" -> {
                out.println("Packages in ${P.folder(home)}:")
                P.installed(home).forEach { out.println("  ${it.name} ${it.version}") }
                P.missing(home).takeIf { it.isNotEmpty() }?.let { out.println("Asked for but not installed (beermat packages reinstall): ${it.joinToString(", ")}") }
                0
            }
            "install" -> args.drop(1).takeIf { it.isNotEmpty() }?.let { done(P.install(home, it)) } ?: usage("packages install needs a package name")
            "remove" -> args.drop(1).takeIf { it.isNotEmpty() }?.let { names -> names.map { done(P.remove(home, it)) }.max() } ?: usage("packages remove needs a package name")
            "reinstall" -> P.missing(home).takeIf { it.isNotEmpty() }?.let { done(P.install(home, it)) } ?: 0.also { out.println("Every package asked for is installed.") }
            else -> usage("packages takes list, install, remove or reinstall")
        }
    }

    /**
     * `beermat bridge`: SymPy for another program, one JSON request per line on standard input and one
     * answer per line on standard output, as `bridge.py` takes and gives them: the VS Code extension's
     * helper (docs/vscode.md#step-3-in-detail). Symbolic requests only: Python functions (`op`) are not
     * run, since nothing here has asked the user.
     */
    private fun bridge(): Int {
        val home = pub.beermat.symbolic.Python.home() ?: run {
            err.println("beermat: this copy of Beermat has no Python")
            return 2
        }
        val sympy = pub.beermat.symbolic.SymPy(home)
        val writer = System.out.bufferedWriter(Charsets.UTF_8)
        for (line in System.`in`.bufferedReader(Charsets.UTF_8).lineSequence()) {
            if (line.isBlank()) continue
            val reply = when {
                Regex(""""op"\s*:""").containsMatchIn(line) -> "{\"error\": \"Python functions in VS Code need the desktop app for now\"}"
                else -> try {
                    sympy.ask(line)
                } catch (x: pub.beermat.engine.eval.EvalException) {
                    "{\"error\": ${Json.string(x.message ?: "SymPy stopped")}}"
                }
            }
            writer.write(reply)
            writer.newLine()
            writer.flush()
        }
        sympy.stop()
        return 0
    }

    /** `beermat python`: which Python runs the sheets' Python functions (docs/cli.md#python). */
    private fun python(args: List<String>): Int {
        val own = pub.beermat.symbolic.OwnPython
        return when (val what = args.firstOrNull()) {
            null -> {
                out.println(own.interpreter()?.let { "Your own Python: $it" } ?: "Beermat's own Python (bundled)")
                0
            }
            "bundled" -> {
                own.set(null)
                out.println("Python functions run in Beermat's own Python again.")
                0
            }
            else -> {
                val interpreter = own.resolve(java.io.File(what).absoluteFile)
                own.check(interpreter).fold(
                    { version ->
                        own.set(interpreter)
                        out.println("Python functions now run in Python $version, $interpreter. SymPy stays in Beermat's own Python.")
                        0
                    },
                    { x -> err.println("beermat: ${x.message}"); 1 },
                )
            }
        }
    }

    private fun usage(problem: String? = null): Int {
        problem?.let { err.println("beermat: $it") }
        usage(err)
        return 2
    }

    private fun usage(stream: PrintStream) {
        stream.println(
            """
            |Usage:
            |  beermat eval <file> [--write] [--json]   evaluate a worksheet and print its results
            |  beermat check <file>                     report errors and stale cached results
            |  beermat export <file> [--to md|latex|docx] [--out <file>]
            |                                           write the worksheet as Markdown, LaTeX or Word
            |  beermat import <file.xmcd> [--out <file.bbm>]
            |                                           convert an .xmcd worksheet
            |  beermat mcp                              serve worksheets to MCP clients (Claude Code) on stdin/stdout
            |  beermat packages [list|install <name>…|remove <name>…|reinstall]
            |                                           Python packages for the Python files sheets use
            |  beermat python [<python>|bundled]        run those files in your own Python (a venv, Conda), or the bundled one
            |  beermat --version                        print the version
            |
            |  --allow-python   let the worksheet run the Python files it uses (use "file.py")
            |
            |Beermat is free and open source. If it saves you time: https://beermat.pub/beer
            """.trimMargin(),
        )
    }
}

/** Just enough JSON writing for `eval --json`. */
internal object Json {
    class Raw(val text: String) {
        override fun toString() = text
    }

    fun obj(vararg entries: Pair<String, Any?>): Raw = Raw(
        entries.joinToString(", ", "{", "}") { (k, v) -> "${string(k)}: ${value(v)}" },
    )

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        is Raw -> v.text
        is String -> string(v)
        is List<*> -> v.joinToString(", ", "[", "]") { value(it) }
        else -> string(v.toString())
    }

    fun string(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
