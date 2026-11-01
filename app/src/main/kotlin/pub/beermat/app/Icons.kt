package pub.beermat.app

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Makes the app's icons and installer images from the logo (branding/logo.svg), so that
 * one drawing is the source of every icon (docs/release.md#branding). Run with
 * `./gradlew :app:icons`; the output in `packaging/icons` is committed.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "packaging/icons").apply { mkdirs() }
    val svg = Icons::class.java.getResourceAsStream("/brand/logo.svg")!!.readBytes()
    // 32 px and below use the simpler logo, without the dashed ring (branding/README.md).
    val small = Icons::class.java.getResourceAsStream("/brand/logo-small.svg")!!.readBytes()
    fun logo(size: Int) = Icons.png(if (size < 48) small else svg, size)

    for (size in listOf(16, 32, 48, 64, 128, 256, 512, 1024)) File(out, "beermat-$size.png").writeBytes(logo(size))
    File(out, "beermat.png").writeBytes(logo(512))
    File(out, "beermat.ico").writeBytes(Icons.ico(listOf(16, 24, 32, 48, 64, 128, 256).map { it to logo(it) }))
    File(out, "beermat.icns").writeBytes(
        Icons.icns(listOf("icp4" to 16, "icp5" to 32, "icp6" to 64, "ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024).map { (t, s) -> t to logo(s) }),
    )
    // The Windows installer's pictures (Inno Setup, modern style): cream, with the logo; no red anywhere.
    File(out, "installer-side.bmp").writeBytes(Icons.bmp(Icons.panel(svg, 164, 314, logoSize = 96, logoY = 92)))
    File(out, "installer-side-2x.bmp").writeBytes(Icons.bmp(Icons.panel(svg, 328, 628, logoSize = 192, logoY = 184)))
    File(out, "installer-small.bmp").writeBytes(Icons.bmp(Icons.panel(svg, 55, 55, logoSize = 47, logoY = 4, background = Icons.WHITE)))
    File(out, "installer-small-2x.bmp").writeBytes(Icons.bmp(Icons.panel(svg, 110, 110, logoSize = 94, logoY = 8, background = Icons.WHITE)))
    // The Microsoft Store package's logos (packaging/windows/msix.ps1): the taskbar and Start menu
    // pick a target size, plated or not; the tiles have room around the logo.
    val msix = File(out, "msix").apply { mkdirs() }
    File(msix, "Square44x44Logo.png").writeBytes(Icons.padded(small, 44, 44, 36))
    for (size in listOf(16, 24, 32, 48, 256)) {
        File(msix, "Square44x44Logo.targetsize-$size.png").writeBytes(logo(size))
        File(msix, "Square44x44Logo.targetsize-${size}_altform-unplated.png").writeBytes(logo(size))
    }
    File(msix, "Square150x150Logo.png").writeBytes(Icons.padded(svg, 150, 150, 96))
    File(msix, "Wide310x150Logo.png").writeBytes(Icons.padded(svg, 310, 150, 96))
    File(msix, "StoreLogo.png").writeBytes(logo(50))
    println("icons written to ${out.path}")
}

object Icons {

    private const val CREAM = 0xFFF6EFE3.toInt()

    private fun draw(svg: ByteArray, canvas: Canvas, x: Float, y: Float, size: Float) {
        val dom = SVGDOM(Data.makeFromBytes(svg))
        dom.setContainerSize(size, size)
        canvas.save()
        canvas.translate(x, y)
        dom.render(canvas)
        canvas.restore()
    }

    /** The logo as a transparent PNG, [size] pixels square. */
    fun png(svg: ByteArray, size: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(size, size)
        draw(svg, surface.canvas, 0f, 0f, size.toFloat())
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    /** A transparent PNG with the logo, [logoSize] pixels square, in its middle. */
    fun padded(svg: ByteArray, width: Int, height: Int, logoSize: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        draw(svg, surface.canvas, (width - logoSize) / 2f, (height - logoSize) / 2f, logoSize.toFloat())
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    /** The wizard's white, behind the small picture in its header, so only the round logo shows there. */
    const val WHITE = 0xFFFFFFFF.toInt()

    /** A panel (cream unless [background] says otherwise) with the logo centred across, its top at [logoY]. */
    fun panel(svg: ByteArray, width: Int, height: Int, logoSize: Int, logoY: Int, background: Int = CREAM): Image {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(background)
        draw(svg, surface.canvas, (width - logoSize) / 2f, logoY.toFloat(), logoSize.toFloat())
        return surface.makeImageSnapshot()
    }

    /** A Windows icon with PNG entries (Vista and later read those). */
    fun ico(images: List<Pair<Int, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(6 + 16 * images.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(images.size.toShort())
        var offset = 6 + 16 * images.size
        for ((size, png) in images) {
            header.put((if (size >= 256) 0 else size).toByte()).put((if (size >= 256) 0 else size).toByte())
            header.put(0).put(0).putShort(1).putShort(32).putInt(png.size).putInt(offset)
            offset += png.size
        }
        out.write(header.array())
        images.forEach { out.write(it.second) }
        return out.toByteArray()
    }

    /** A macOS icon with PNG entries. */
    fun icns(images: List<Pair<String, ByteArray>>): ByteArray {
        val body = ByteArrayOutputStream()
        val d = DataOutputStream(body)
        for ((type, png) in images) {
            d.writeBytes(type)
            d.writeInt(png.size + 8)
            d.write(png)
        }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeBytes("icns")
            writeInt(body.size() + 8)
            write(body.toByteArray())
        }
        return out.toByteArray()
    }

    /** A 24-bit BMP, as Inno Setup wants its wizard images. */
    fun bmp(image: Image): ByteArray {
        val w = image.width
        val h = image.height
        val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(w, h)) }
        image.readPixels(bitmap)
        val pixels = bitmap.readPixels()!! // BGRA or RGBA by platform: read through getColor instead
        val row = (w * 3 + 3) / 4 * 4
        val buf = ByteBuffer.allocate(54 + row * h).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('B'.code.toByte()).put('M'.code.toByte()).putInt(54 + row * h).putInt(0).putInt(54)
        buf.putInt(40).putInt(w).putInt(h).putShort(1).putShort(24).putInt(0).putInt(row * h).putInt(2835).putInt(2835).putInt(0).putInt(0)
        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val c = bitmap.getColor(x, y)
                buf.put((c and 0xFF).toByte()).put((c shr 8 and 0xFF).toByte()).put((c shr 16 and 0xFF).toByte())
            }
            repeat(row - w * 3) { buf.put(0) }
        }
        check(pixels.isNotEmpty())
        return buf.array()
    }
}
