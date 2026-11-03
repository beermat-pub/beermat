package pub.beermat.symbolic

import pub.beermat.engine.HostFile
import pub.beermat.engine.hostBytes

internal actual fun fileName(file: HostFile): String = file.path.substringAfterLast('/').substringAfterLast('\\')

internal actual fun canonicalPath(file: HostFile): String = file.path

// The file as the host read it (RemoteFiles, read again when the sheet comes again): its contents' hash.
internal actual fun fileVersion(file: HostFile): String = hostBytes(file)?.contentHashCode()?.toString() ?: "?"
