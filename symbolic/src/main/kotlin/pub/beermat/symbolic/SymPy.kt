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
class SymPy(private val home: File, private val timeoutSeconds: Long = 30) {
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
            throw EvalException("SymPy stopped${lastError()}")
        }
        val reply = next(timeoutSeconds, "SymPy took longer than $timeoutSeconds s and was stopped")
        cache[request] = reply
        return reply
    }

    private fun next(seconds: Long, late: String): String {
        val line = lines.poll(seconds, TimeUnit.SECONDS)
        if (line == null) {
            stop()
            throw EvalException(late)
        }
        if (line == END) {
            stop()
            throw EvalException("SymPy stopped${lastError()}")
        }
        return line
    }

    private fun lastError() = synchronized(errors) { errors.lastOrNull()?.let { ": $it" } ?: "" }

    private fun start(): Writer {
        val queue = LinkedBlockingQueue<String>().also { lines = it }
        val p = Python.process(home, "-X", "utf8", "-u", script.path).start()
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
        // The first line says SymPy is loaded.
        next(60, "SymPy took too long to start")
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

    private companion object {
        const val END = "\u0000end"

        /** `bridge.py`, copied out of the jar once. */
        val script: File by lazy {
            val file = Files.createTempFile("beermat-bridge", ".py").toFile()
            file.deleteOnExit()
            SymPy::class.java.getResourceAsStream("bridge.py")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        }
    }
}
