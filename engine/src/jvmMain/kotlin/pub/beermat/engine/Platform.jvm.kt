package pub.beermat.engine

import java.io.File

actual typealias HostFile = File

internal actual fun hostFile(folder: HostFile?, path: String): HostFile = File(path).takeIf { it.isAbsolute } ?: File(folder ?: File("."), path)

internal actual fun isAbsolutePath(path: String): Boolean = File(path).isAbsolute

internal actual fun isHostFile(file: HostFile): Boolean = file.isFile

internal actual fun hostLines(file: HostFile): List<String> = file.readLines()

internal actual fun stopRequested(): Boolean = Thread.currentThread().isInterrupted

internal actual fun epochSeconds(): Double = System.currentTimeMillis() / 1000.0

internal actual val maxCallDepth: Int = 1000

internal actual fun isStackOverflow(t: Throwable): Boolean = t is StackOverflowError

internal actual abstract class Jump actual constructor() : RuntimeException(null, null, false, false)
