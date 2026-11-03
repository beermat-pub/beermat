package pub.beermat.symbolic

import pub.beermat.engine.HostFile

internal actual fun fileName(file: HostFile): String = file.name

internal actual fun canonicalPath(file: HostFile): String = file.canonicalPath

internal actual fun fileVersion(file: HostFile): String = "${file.lastModified()}:${file.length()}"
