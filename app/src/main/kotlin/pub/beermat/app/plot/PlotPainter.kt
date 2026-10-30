package pub.beermat.app.plot

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathEffect
import org.jetbrains.skia.Rect
import pub.beermat.app.math.SkiaMath
import pub.beermat.engine.display.NumberFormat
import pub.beermat.engine.display.ResultFormat
import pub.beermat.engine.display.Rounding
import pub.beermat.format.Axis
import pub.beermat.format.PlotData
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Draws an X-Y plot with Skia: title, frame, nice ticks (or decades on a log axis), grid,
 * curves, legend, and an optional right axis.
 */
object PlotPainter {

    private val palette = listOf(0xFF1F5FA8, 0xFFC0392B, 0xFF2E8B57, 0xFFD68910, 0xFF7D3C98, 0xFF117A65).map { it.toInt() }
    private val named = mapOf(
        "blue" to 0xFF1F5FA8, "red" to 0xFFC0392B, "green" to 0xFF2E8B57, "orange" to 0xFFD68910,
        "purple" to 0xFF7D3C98, "black" to 0xFF1A1A1A, "gray" to 0xFF808080, "grey" to 0xFF808080,
    ).mapValues { it.value.toInt() }

    private fun display(label: String) = label.replace("*", "·")

    /**
     * One axis's mapping from data to position, through log10 on a log axis. [lo] and [hi] are
     * the ends in that transformed space.
     */
    internal class Scale(val axis: Axis, values: List<Double>) {
        val lo: Double
        val hi: Double

        init {
            val (a, b) = if (axis.log) logLimits(axis, values.filter { it > 0 }) else limits(axis, values)
            lo = a
            hi = b
        }

        /** Where [v] falls, 0 at [lo] and 1 at [hi]; NaN where it can't be drawn (0 or less on a log axis). */
        fun fraction(v: Double): Double {
            val t = if (axis.log) (if (v > 0) log10(v) else Double.NaN) else v
            return (t - lo) / (hi - lo)
        }

        /** The labelled ticks, in data units. */
        fun ticks(): List<Double> = if (axis.log) logTicks() else linearTicks(lo, hi, axis.step)

        /** Grid lines without labels: 2 … 9 in each decade of a log axis that spans few enough. */
        fun minor(): List<Double> {
            if (!axis.log || hi - lo > 6) return emptyList()
            val labelled = ticks().toSet()
            return (floor(lo).toInt()..ceil(hi).toInt()).flatMap { k -> (2..9).map { m -> m * 10.0.pow(k) } }
                .filter { log10(it) in lo..hi && it !in labelled }
        }

        fun label(v: Double): String {
            if (axis.log) return NumberFormat.format(v, ResultFormat(rounding = Rounding.Decimals(maxOf(0, -floor(log10(v) + 1e-9).toInt())), exponentialThreshold = 6))
            // Just enough decimals for the step: 0.2 and 0.4, not 0.2000; 2.5 keeps its .5.
            val step = axis.step ?: step(lo, hi)
            val places = (0..10).first { p -> abs(step * 10.0.pow(p) - Math.rint(step * 10.0.pow(p))) < 1e-6 * step * 10.0.pow(p) }
            return NumberFormat.format(v, ResultFormat(rounding = Rounding.Decimals(places), exponentialThreshold = 6))
        }

        /** Decades; with two decades or less, also 2 and 5 times them. */
        private fun logTicks(): List<Double> {
            val mantissas = if (hi - lo <= 2) listOf(1.0, 2.0, 5.0) else listOf(1.0)
            val every = maxOf(1, ceil((hi - lo) / 8).toInt())
            return (floor(lo).toInt()..ceil(hi).toInt()).filter { it % every == 0 }
                .flatMap { k -> mantissas.map { m -> m * 10.0.pow(k) } }
                .filter { log10(it) in (lo - 1e-9)..(hi + 1e-9) }
        }
    }

    fun paint(canvas: Canvas, data: PlotData, x: Float, y: Float, width: Float, height: Float, textSize: Float = 12f) {
        val font = SkiaMath.font(textSize)
        val text = Paint().apply { color = 0xFF1A1A1A.toInt(); isAntiAlias = true }
        val left = x + 4.2f * textSize
        val right = x + width - (if (data.y2 != null) 4.2f * textSize else 0.8f * textSize)
        val top = y + (if (data.title != null) 2.2f else 0.8f) * textSize
        val bottom = y + height - 3.2f * textSize
        val area = Rect(left, top, right, bottom)

        data.title?.let { t ->
            val titleFont = SkiaMath.font(textSize * 1.15f, bold = true)
            canvas.drawString(display(t), (left + right) / 2 - titleFont.measureTextWidth(display(t)) / 2, y + 1.3f * textSize, titleFont, text)
        }

        val sx = Scale(data.x, data.series.flatMap { it.xs }.filter { it.isFinite() })
        val sy = Scale(data.y, data.series.filter { !it.right }.flatMap { it.ys }.filter { it.isFinite() })
        val sr = data.y2?.let { Scale(it, data.series.filter { s -> s.right }.flatMap { s -> s.ys }.filter { v -> v.isFinite() }) }
        fun px(v: Double) = (left + sx.fraction(v) * (right - left)).toFloat()
        fun py(v: Double, onRight: Boolean) = (bottom - (if (onRight) sr!! else sy).fraction(v) * (bottom - top)).toFloat()

        val grid = Paint().apply { color = 0xFFE3E3E3.toInt(); strokeWidth = 1f; mode = PaintMode.STROKE }
        val minorGrid = Paint().apply { color = 0xFFF0F0F0.toInt(); strokeWidth = 1f; mode = PaintMode.STROKE }
        val frame = Paint().apply { color = 0xFF555555.toInt(); strokeWidth = 1f; mode = PaintMode.STROKE; isAntiAlias = true }

        if (data.x.grid) for (t in sx.minor()) px(t).let { canvas.drawLine(it, top, it, bottom, minorGrid) }
        if (data.y.grid) for (t in sy.minor()) py(t, false).let { canvas.drawLine(left, it, right, it, minorGrid) }
        for (t in sx.ticks()) {
            val p = px(t)
            if (data.x.grid) canvas.drawLine(p, top, p, bottom, grid)
            canvas.drawLine(p, bottom, p, bottom + 4f, frame)
            val label = sx.label(t)
            canvas.drawString(label, p - font.measureTextWidth(label) / 2, bottom + 1.3f * textSize, font, text)
        }
        for (t in sy.ticks()) {
            val p = py(t, false)
            if (data.y.grid) canvas.drawLine(left, p, right, p, grid)
            canvas.drawLine(left - 4f, p, left, p, frame)
            val label = sy.label(t)
            canvas.drawString(label, left - 6f - font.measureTextWidth(label), p + textSize * 0.35f, font, text)
        }
        sr?.let { s ->
            for (t in s.ticks()) {
                val p = py(t, true)
                canvas.drawLine(right, p, right + 4f, p, frame)
                canvas.drawString(s.label(t), right + 6f, p + textSize * 0.35f, font, text)
            }
        }
        canvas.drawRect(area, frame)

        // Axis labels: x below, y rotated on the left (and the right axis on the right).
        val xl = display(data.x.label)
        canvas.drawString(xl, (left + right) / 2 - font.measureTextWidth(xl) / 2, bottom + 2.7f * textSize, font, text)
        fun vertical(label: String, at: Float) {
            canvas.save()
            canvas.translate(at, (top + bottom) / 2 + font.measureTextWidth(label) / 2)
            canvas.rotate(-90f)
            canvas.drawString(label, 0f, 0f, font, text)
            canvas.restore()
        }
        vertical(display(data.y.label), x + 0.9f * textSize)
        data.y2?.let { vertical(display(it.label), x + width - 0.2f * textSize) }

        // Curves, clipped to the frame, broken where a point is missing (or can't be drawn on a log axis).
        canvas.save()
        canvas.clipRect(area)
        data.series.forEachIndexed { k, s ->
            val color = s.color?.let(::color) ?: palette[k % palette.size]
            val stroke = Paint().apply {
                this.color = color
                strokeWidth = 1.8f
                mode = PaintMode.STROKE
                isAntiAlias = true
                if (s.line == "dash" || s.line == "dashed") pathEffect = PathEffect.makeDash(floatArrayOf(6f, 4f), 0f)
                if (s.line == "dot" || s.line == "dotted") pathEffect = PathEffect.makeDash(floatArrayOf(1.5f, 3f), 0f)
            }
            val points = s.xs.indices.map { i -> px(s.xs[i]) to py(s.ys[i], s.right) }
            fun drawable(p: Pair<Float, Float>) = p.first.isFinite() && p.second.isFinite()
            if (s.line != "none") {
                val path = PathBuilder()
                var pen = false
                for (p in points) {
                    if (!drawable(p)) {
                        pen = false
                        continue
                    }
                    if (pen) path.lineTo(p.first, p.second) else path.moveTo(p.first, p.second)
                    pen = true
                }
                canvas.drawPath(path.detach(), stroke)
            }
            if (s.marker != "none") {
                val fill = Paint().apply { this.color = color; isAntiAlias = true }
                for ((cx, cy) in points.filter(::drawable)) {
                    if (s.marker == "square") canvas.drawRect(Rect(cx - 3f, cy - 3f, cx + 3f, cy + 3f), fill) else canvas.drawCircle(cx, cy, 3f, fill)
                }
            }
        }
        canvas.restore()

        // A legend when there is more than one curve.
        if (data.series.size > 1) {
            val lineHeight = 1.3f * textSize
            val w = data.series.maxOf { font.measureTextWidth(it.label) } + 3f * textSize
            val box = Rect(right - w - 6f, top + 6f, right - 6f, top + 6f + lineHeight * data.series.size + 6f)
            canvas.drawRect(box, Paint().apply { color = 0xEEFFFFFF.toInt() })
            canvas.drawRect(box, grid)
            data.series.forEachIndexed { k, s ->
                val cy = box.top + 3f + lineHeight * (k + 0.5f)
                val color = s.color?.let(::color) ?: palette[k % palette.size]
                val swatch = Paint().apply { this.color = color; strokeWidth = 2f; isAntiAlias = true }
                if (s.line != "none") canvas.drawLine(box.left + 6f, cy, box.left + 2f * textSize, cy, swatch)
                if (s.marker != "none") canvas.drawCircle(box.left + 6f + textSize * 0.6f, cy, 3f, swatch)
                canvas.drawString(s.label, box.left + 2.4f * textSize, cy + 0.35f * textSize, font, text)
            }
        }
    }

    /** The axis range: given limits, or the data's, widened a little and never empty. */
    private fun limits(axis: Axis, values: List<Double>): Pair<Double, Double> {
        var lo = axis.min ?: values.minOrNull() ?: 0.0
        var hi = axis.max ?: values.maxOrNull() ?: 1.0
        if (hi - lo <= 1e-12 * maxOf(abs(lo), abs(hi), 1.0)) {
            lo -= maxOf(abs(lo) * 0.1, 1.0)
            hi += maxOf(abs(hi) * 0.1, 1.0)
        } else if (axis.min == null || axis.max == null) {
            // Round the automatic ends outwards to the ticks, so the curve doesn't touch the frame by accident.
            val step = axis.step ?: step(lo, hi)
            if (axis.min == null) lo = floor(lo / step + 1e-9) * step
            if (axis.max == null) hi = ceil(hi / step - 1e-9) * step
        }
        return lo to hi
    }

    /** A log axis's ends, as powers of ten: given limits, or the data's rounded out to whole decades. */
    private fun logLimits(axis: Axis, values: List<Double>): Pair<Double, Double> {
        var lo = axis.min?.let(::log10) ?: values.minOrNull()?.let { floor(log10(it) + 1e-9) } ?: 0.0
        var hi = axis.max?.let(::log10) ?: values.maxOrNull()?.let { ceil(log10(it) - 1e-9) } ?: 1.0
        if (hi - lo < 1e-9) {
            lo = floor(lo) - (if (axis.min == null) 1 else 0)
            hi = maxOf(ceil(hi), lo + 1)
        }
        return lo to hi
    }

    /** A 1-2-5 tick step giving about five ticks. */
    private fun step(lo: Double, hi: Double): Double {
        val raw = (hi - lo) / 5
        val magnitude = 10.0.pow(floor(log10(raw)))
        val f = raw / magnitude
        return magnitude * when {
            f < 1.5 -> 1.0
            f < 3.5 -> 2.0
            f < 7.5 -> 5.0
            else -> 10.0
        }
    }

    /** Ticks from [lo] to [hi], [given] apart or a 1-2-5 step; never more than 200. */
    internal fun linearTicks(lo: Double, hi: Double, given: Double? = null): List<Double> {
        val step = given?.takeIf { (hi - lo) / it <= 200 } ?: step(lo, hi)
        val first = ceil(lo / step - 1e-9) * step
        return generateSequence(first) { it + step }.takeWhile { it <= hi + step * 1e-9 }.map { if (abs(it) < step * 1e-9) 0.0 else it }.toList()
    }

    internal fun ticks(lo: Double, hi: Double) = linearTicks(lo, hi)

    private fun color(s: String): Int? = named[s.lowercase()] ?: s.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}
