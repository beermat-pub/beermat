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
        val text = when (to) {
            "md", "markdown" -> SheetExport.markdown(sheet)
            "latex", "tex" -> SheetExport.latex(sheet)
            else -> return usage("export --to takes md or latex, not '$to'")
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
            |  beermat export <file> [--to md|latex] [--out <file>]
            |                                           write the worksheet as Markdown or LaTeX
            |  beermat import <file.xmcd> [--out <file.bbm>]
            |                                           convert an .xmcd worksheet
            |  beermat mcp                              serve worksheets to MCP clients (Claude Code) on stdin/stdout
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
