package pub.beermat.symbolic

import pub.beermat.engine.HostFile

// What PythonModules needs to know of a file, on each platform.

/** The file's name, without its folder: `beam_tools.py`. */
internal expect fun fileName(file: HostFile): String

/** The file's full path, the same however the sheet named it. */
internal expect fun canonicalPath(file: HostFile): String

/** Changes when the file does, so a changed file isn't answered from the cache. */
internal expect fun fileVersion(file: HostFile): String
