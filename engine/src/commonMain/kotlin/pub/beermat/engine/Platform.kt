package pub.beermat.engine

import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.ulp

// What the engine needs from the platform it runs on: the JVM on the desktop, WebAssembly in the
// browser (docs/web.md). Each is one declaration here, with an `actual` in jvmMain and wasmJsMain.

/**
 * A file on the computer: a `java.io.File` on the desktop. In the browser there are no files, so
 * data files and Python modules say they need the desktop version.
 */
expect class HostFile

/** [path] itself when it is absolute, otherwise in [folder] (the current folder when null). */
internal expect fun hostFile(folder: HostFile?, path: String): HostFile

internal expect fun isAbsolutePath(path: String): Boolean

/** Whether [file] exists and is a file, not a folder. */
internal expect fun isHostFile(file: HostFile): Boolean

/** The lines of [file]; throws if it can't be read. */
internal expect fun hostLines(file: HostFile): List<String>

/** Whether the evaluation was asked to stop (the desktop app interrupts its evaluation thread). */
internal expect fun stopRequested(): Boolean

/** Seconds since 1970-01-01, for `time`. */
internal expect fun epochSeconds(): Double

/** How deep user functions may call each other before "too many nested calls": as deep as the platform's stack allows. */
internal expect val maxCallDepth: Int

/** Whether [t] is the platform's stack overflow, from endless recursion. */
internal expect fun isStackOverflow(t: Throwable): Boolean

/** An exception used for control flow (`return`, `break`): without a stack trace, which costs time and is never shown. */
internal expect abstract class Jump() : RuntimeException

/**
 * The few `java.lang.Math` functions the engine uses, the same on every platform. Files import
 * this one, which then stands in for `java.lang.Math` on the JVM too.
 */
internal object Math {
    const val PI: Double = kotlin.math.PI

    /** The nearest whole number, halves to the even one. */
    fun rint(x: Double): Double = kotlin.math.round(x)

    /** The nearest whole number, halves up; 0 for NaN. */
    fun round(x: Double): Long = if (x.isNaN()) 0 else x.roundToLong()

    fun abs(x: Double): Double = kotlin.math.abs(x)

    fun pow(a: Double, b: Double): Double = a.pow(b)

    fun ulp(x: Double): Double = x.ulp

    fun addExact(a: Int, b: Int): Int = exact(a.toLong() + b)

    fun multiplyExact(a: Int, b: Int): Int = exact(a.toLong() * b)

    fun negateExact(a: Int): Int = exact(-a.toLong())

    fun toIntExact(a: Long): Int = exact(a)

    private fun exact(x: Long): Int = if (x in Int.MIN_VALUE..Int.MAX_VALUE) x.toInt() else throw ArithmeticException("integer overflow")
}
