package pub.beermat.app.page

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthContext
import pub.beermat.app.math.SkiaMath
import pub.beermat.format.ImageRegion
import java.io.File

/**
 * The pictures of image regions (docs/file-format.md#images), decoded once and kept by their
 * source, since the sheet is laid out again at every keystroke: PNG, JPEG, GIF, WebP and SVG.
 */
object Pictures {

    /** A decoded picture and its natural size in points (pixels at 96 per inch). */
    sealed class Picture(val width: Float, val height: Float) {
        class Raster(val image: Image) : Picture(image.width * PT_PER_PX, image.height * PT_PER_PX)
        class Vector(val dom: SVGDOM, width: Float, height: Float) : Picture(width, height)
    }

    private const val PT_PER_PX = 0.75f
    private val cache = object : LinkedHashMap<String, Picture?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Picture?>) = size > 64
    }

    /** The picture of [r]; a file is found relative to [base], the sheet's folder. Null when it can't be read. */
    @Synchronized
    fun of(r: ImageRegion, base: File?): Picture? {
        val key = if (r.embedded) r.source else File(base, r.source).path
        return cache.getOrPut(key) { decode(bytes(r, base) ?: return@getOrPut null) }
    }

    private fun bytes(r: ImageRegion, base: File?): ByteArray? =
        r.bytes() ?: runCatching { File(r.source).takeIf { it.isAbsolute } ?: File(base ?: return null, r.source) }.getOrNull()
            ?.takeIf { !r.embedded && it.isFile }?.readBytes()

    /** [bytes] as a picture: SVG when it reads as XML with an `<svg>`, otherwise any format Skia knows. */
    fun decode(bytes: ByteArray): Picture? {
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8)
        if ("<svg" in head || head.trimStart().startsWith("<?xml")) {
            return runCatching {
                val dom = SVGDOM(Data.makeFromBytes(bytes))
                val root = dom.root ?: return null
                val size = root.getIntrinsicSize(SVGLengthContext(300f, 150f))
                val (w, h) = if (size.x > 0 && size.y > 0) size.x to size.y else root.viewBox?.let { it.width to it.height } ?: (300f to 150f)
                Picture.Vector(dom, w * PT_PER_PX, h * PT_PER_PX)
            }.getOrNull()
        }
        return runCatching { Picture.Raster(Image.makeFromEncoded(bytes)) }.getOrNull()
    }

    /** The size to place a new picture at: its natural size, scaled down to fit [maxWidth]. */
    fun fit(p: Picture, maxWidth: Float): Pair<Float, Float> {
        val k = minOf(1f, maxWidth / p.width)
        return p.width * k to p.height * k
    }

    /** Draws [p] filling [width] × [height] at the origin; a grey box with the alt text when there is no picture. */
    fun paint(canvas: Canvas, p: Picture?, width: Float, height: Float, alt: String) {
        when (p) {
            is Picture.Raster -> canvas.drawImageRect(p.image, Rect.makeWH(width, height))
            is Picture.Vector -> {
                canvas.save()
                canvas.clipRect(Rect.makeWH(width, height))
                canvas.scale(width / p.width * PT_PER_PX, height / p.height * PT_PER_PX)
                p.dom.setContainerSize(p.width / PT_PER_PX, p.height / PT_PER_PX)
                p.dom.render(canvas)
                canvas.restore()
            }
            null -> {
                canvas.drawRect(Rect.makeWH(width, height), Paint().apply { color = 0xFFF2F2F2.toInt() })
                canvas.drawRect(Rect.makeWH(width, height), Paint().apply { color = 0xFFBBBBBB.toInt(); mode = PaintMode.STROKE; strokeWidth = 0.8f })
                val font: Font = SkiaMath.font(10f)
                canvas.drawString(alt.ifEmpty { "picture not found" }, 6f, 14f, font, Paint().apply { color = 0xFF777777.toInt(); isAntiAlias = true })
            }
        }
    }
}
