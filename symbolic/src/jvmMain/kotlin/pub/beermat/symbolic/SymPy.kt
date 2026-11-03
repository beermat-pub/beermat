package pub.beermat.symbolic

import pub.beermat.engine.eval.EvalException
import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The bundled Python running SymPy (docs/symbolic.md#bundled-sympy): one process, started by the
 * first request and kept until the app closes, talking JSON lines (`bridge.py`). A request that takes
 * longer than [timeoutSeconds] stops the process; the next request starts a new one.
 */
class SymPy(
    private val command: (Array<String>) -> ProcessBuilder,
    private val name: String = "SymPy",
    private val timeoutSeconds: Long = 30,
) {
    /** The bundled Python at [home]. */
    constructor(home: File, timeoutSeconds: Long = 30) : this({ args -> Python.process(home, *args) }, "SymPy", timeoutSeconds)

    companion object {
        /** The user's own [interpreter], for the sheets' Python functions only (docs/symbolic.md#the-users-own-python). */
        fun own(interpreter: File) = SymPy({ args -> OwnPython.process(interpreter, *args) }, "Your Python (${interpreter.path})")

        private const val END = "\u0000end"

        /** `bridge.py`, copied out of the jar once, into a folder of its own: Python imports from a script's folder. */
        private val script: File by lazy {
            val dir = Files.createTempDirectory("beermat-bridge").toFile()
            val file = File(dir, "bridge.py")
            dir.deleteOnExit()
            file.deleteOnExit()
            SymPy::class.java.getResourceAsStream("bridge.py")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        }
    }

    private var process: Process? = null
    private var input: Writer? = null
    // A new queue for each process, so a stopped one can't answer for the next.
    private var lines = LinkedBlockingQueue<String>()
    private val errors = ArrayDeque<String>()

    /** Recent answers, by request: a sheet is evaluated again at every edit. */
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 500
    }

    /** The answer to a [request] (one JSON line): the line `bridge.py` wrote back. */
    @Synchronized
    fun ask(request: String): String {
        cache[request]?.let { return it }
        val writer = input ?: start()
        try {
            writer.write(request)
            writer.write("\n")
            writer.flush()
        } catch (x: java.io.IOException) {
            stop()
            throw EvalException("$name stopped${lastError()}")
        }
        val reply = next(timeoutSeconds, "$name took longer than $timeoutSeconds s and was stopped")
        cache[request] = reply
        return reply
    }

    private fun next(seconds: Long, late: String): String {
        val line = try {
            lines.poll(seconds, TimeUnit.SECONDS)
        } catch (x: InterruptedException) {
            // The evaluation was stopped while SymPy worked: its answer would come later and be taken
            // for the next request's, so this process goes, and the next request starts a fresh one.
            stop()
            Thread.currentThread().interrupt()
            throw EvalException("the evaluation was stopped")
        }
        if (line == null) {
            stop()
            throw EvalException(late)
        }
        if (line == END) {
            stop()
            throw EvalException("$name stopped${lastError()}")
        }
        return line
    }

    private fun lastError() = synchronized(errors) { errors.lastOrNull()?.let { ": $it" } ?: "" }

    private fun start(): Writer {
        val queue = LinkedBlockingQueue<String>().also { lines = it }
        val p = try {
            command(arrayOf("-X", "utf8", "-u", script.path)).start()
        } catch (x: java.io.IOException) {
            throw EvalException("$name couldn't start: ${x.message}")
        }
        process = p
        Runtime.getRuntime().addShutdownHook(Thread { p.destroyForcibly() })
        thread("SymPy output") { p.inputStream.bufferedReader().useLines { all -> all.forEach(queue::put) }; queue.put(END) }
        thread("SymPy errors") {
            p.errorStream.bufferedReader().useLines { all ->
                all.forEach { synchronized(errors) { errors.addLast(it); if (errors.size > 20) errors.removeFirst() } }
            }
        }
        val writer = p.outputStream.bufferedWriter(Charsets.UTF_8)
        input = writer
        // The first line says the bridge is ready.
        next(60, "$name took too long to start")
        return writer
    }

    /** Stops the process, if it runs. */
    @Synchronized
    fun stop() {
        process?.destroyForcibly()
        process = null
        input = null
    }

    private fun thread(name: String, body: () -> Unit) = Thread(body, name).apply { isDaemon = true }.start()
}
