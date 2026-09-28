package com.auto.odo.core

import kotlin.math.roundToInt

/** One OCR text line with its box in image pixels. */
data class OcrLine(val text: String, val box: Box)

/**
 * Layout half of the two-pass odometer read (the OCR calls live in [TextScanner]):
 * pass 1 finds the "ODO" label or "km" unit, [valueRegion] turns that into the box where the
 * reading sits, and [prepare] enlarges and cleans that box for pass 2.
 */
object OdometerReader {

    private val odoWord = Regex("""[oa0q][d0o][o0d]""")
    private val unit = Regex("""\b(km|kms|mi|miles)\b""")
    private val rateUnit = Regex("""km\s*/\s*(h|l)|r\s*/\s*min""")

    /**
     * Where the odometer value is. "km" line: extend left to catch digits OCR missed ("148 km"
     * for 7348). "ODO" label: the value sits below/right of it. Neither: bottom-left corner,
     * where bike and car clusters usually put it. Candidates nearest bottom-left win.
     */
    fun valueRegion(lines: List<OcrLine>, width: Int, height: Int): Box {
        fun bottomLeftness(l: OcrLine) = l.box.top - l.box.left
        val km = lines
            .filter { val t = it.text.lowercase(); unit.containsMatchIn(t) && !rateUnit.containsMatchIn(t) }
            .maxByOrNull(::bottomLeftness)
        val odo = lines
            .filter { odoWord.matches(it.text.lowercase().filter(Char::isLetterOrDigit)) }
            .maxByOrNull(::bottomLeftness)
        val roi = when {
            km != null -> km.box.let { b ->
                val lh = b.height
                Box(b.left - 8 * lh, b.top - lh, b.right + lh / 2, b.bottom + lh / 2)
            }
            odo != null -> odo.box.let { b ->
                val lh = b.height
                Box(b.left - lh / 2, b.top, b.left + 12 * lh, b.bottom + 4 * lh)
            }
            else -> Box(0, (height * 0.55f).toInt(), (width * 0.55f).toInt(), height)
        }
        return roi.clampTo(width, height)
    }

    /**
     * Crops [roi], scales it so text is ~[targetHeight]px tall-ish (bilinear, up to 4x),
     * stretches contrast between the 5th/95th percentiles, and inverts light-on-dark screens
     * so OCR always sees dark text on a light background.
     */
    fun prepare(img: GrayImage, roi: Box, targetHeight: Int = 160): GrayImage {
        if (roi.width < 2 || roi.height < 2) return img
        val scale = (targetHeight.toFloat() / roi.height).coerceIn(1f, 4f)
        val w = (roi.width * scale).roundToInt()
        val h = (roi.height * scale).roundToInt()

        val values = IntArray(roi.area)
        var i = 0
        for (y in roi.top until roi.bottom) for (x in roi.left until roi.right) values[i++] = img[x, y]
        values.sort()
        val lo = values[values.size / 20]
        val hi = values[values.size * 19 / 20]
        val range = maxOf(1, hi - lo)
        val t = PumpDisplayReader.otsu(img, roi)
        // Mostly-dark region = dark screen with light text
        val invert = values.count { it > t } < values.size / 2

        val out = IntArray(w * h)
        for (y in 0 until h) {
            val sy = (roi.top + (y + 0.5f) / scale - 0.5f).coerceIn(roi.top.toFloat(), roi.bottom - 1f)
            val y0 = sy.toInt(); val y1 = minOf(y0 + 1, roi.bottom - 1); val fy = sy - y0
            for (x in 0 until w) {
                val sx = (roi.left + (x + 0.5f) / scale - 0.5f).coerceIn(roi.left.toFloat(), roi.right - 1f)
                val x0 = sx.toInt(); val x1 = minOf(x0 + 1, roi.right - 1); val fx = sx - x0
                val v = (img[x0, y0] * (1 - fx) + img[x1, y0] * fx) * (1 - fy) + (img[x0, y1] * (1 - fx) + img[x1, y1] * fx) * fy
                val stretched = ((v - lo) * 255 / range).toInt().coerceIn(0, 255)
                out[y * w + x] = if (invert) 255 - stretched else stretched
            }
        }
        return GrayImage(w, h, out)
    }
}
