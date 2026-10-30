package pub.beermat.app

import java.util.prefs.Preferences

/**
 * The window's settings, remembered between runs (Java preferences, node `pub/beermat`): the zoom
 * and the toolbar (docs/editor.md#zoom, #toolbar). Never in the worksheet: they are the user's, not the sheet's.
 */
object Settings {
    /** The zoom levels offered, in percent. */
    val ZOOMS = listOf(50, 75, 100, 125, 150, 200)

    var zoom: Int
        get() = read { getInt("zoom", 100) }?.takeIf { it in ZOOMS } ?: 100
        set(value) = write { putInt("zoom", value) }

    var toolbar: Boolean
        get() = read { getBoolean("toolbar", true) } ?: true
        set(value) = write { putBoolean("toolbar", value) }

    /** The toolbar's palettes the user turned off, by name. */
    var hiddenPalettes: Set<String>
        get() = read { get("hiddenPalettes", "") }?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        set(value) = write { put("hiddenPalettes", value.joinToString(",")) }

    /** The next zoom level up ([step] 1) or down (-1), staying within [ZOOMS]. */
    fun step(zoom: Int, step: Int): Int {
        val i = ZOOMS.indexOf(zoom).takeIf { it >= 0 } ?: ZOOMS.indexOf(100)
        return ZOOMS[(i + step).coerceIn(0, ZOOMS.lastIndex)]
    }

    private fun prefs() = Preferences.userRoot().node("pub/beermat")

    private fun <T> read(get: Preferences.() -> T): T? = runCatching { prefs().get() }.getOrNull()

    private fun write(put: Preferences.() -> Unit) {
        runCatching { prefs().put() }
    }
}
