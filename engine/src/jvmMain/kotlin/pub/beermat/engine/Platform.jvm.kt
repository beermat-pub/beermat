package pub.beermat.engine

import java.io.File

actual typealias HostFile = File

actual fun hostFile(folder: HostFile?, path: String): HostFile = File(path).takeIf { it.isAbsolute } ?: File(folder ?: File("."), path)

actual fun isAbsolutePath(path: String): Boolean = File(path).isAbsolute

actual fun isHostFile(file: HostFile): Boolean = file.isFile

internal actual fun hostLines(file: HostFile): List<String> = file.readLines()

actual fun hostBytes(file: HostFile): ByteArray? = runCatching { file.readBytes() }.getOrNull()

internal actual fun stopRequested(): Boolean = Thread.currentThread().isInterrupted

internal actual fun epochSeconds(): Double = System.currentTimeMillis() / 1000.0

internal actual val maxCallDepth: Int = 1000

internal actual fun isStackOverflow(t: Throwable): Boolean = t is StackOverflowError

internal actual abstract class Jump actual constructor() : RuntimeException(null, null, false, false)
