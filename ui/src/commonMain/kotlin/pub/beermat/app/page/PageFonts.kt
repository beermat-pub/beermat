package pub.beermat.app.page

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.paragraph.TypefaceFontProvider

/**
 * Fonts the shell gives Skia, where the platform has none of its own (the browser): text regions and
 * tables find them by family name, after the system's. Registered before the first page is drawn.
 */
object PageFonts {
    internal val provider = TypefaceFontProvider()

    /** A font file's [bytes]; false when Skia can't read them. */
    fun register(bytes: ByteArray): Boolean {
        val typeface = FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) ?: return false
        provider.registerTypeface(typeface)
        return true
    }
}
