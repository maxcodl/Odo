package com.auto.odo.core

import kotlin.math.abs
import kotlin.math.pow

/** Grayscale image, 0 = black .. 255 = white, row-major. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    operator fun get(x: Int, y: Int) = pixels[y * width + x]

    companion object {
        fun fromArgb(width: Int, height: Int, argb: IntArray) = GrayImage(width, height, IntArray(argb.size) { i ->
            val c = argb[i]
            ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        })
    }
}

/** Pixel box; right/bottom exclusive. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
    val centerX get() = (left + right) / 2

    fun clampTo(w: Int, h: Int) = Box(left.coerceIn(0, w), top.coerceIn(0, h), right.coerceIn(0, w), bottom.coerceIn(0, h))

    fun inset(fx: Float, fy: Float) = Box(
        left + (width * fx).toInt(), top + (height * fy).toInt(),
        right - (width * fx).toInt(), bottom - (height * fy).toInt()
    )
}

/** What one frame produced; [values] is null when the digits couldn't be read. */
data class PumpScan(
    val windows: List<Box>,
    val rows: List<String>,
    val rate: String?,
    val values: ReceiptValues?,
    val validated: Boolean
)

/**
 * Reads Indian fuel-pump displays (Tokheim / Gilbarco / Midco style): a bright LCD window
 * holding the Amount row above the Volume row, with two small windows underneath
 * (Density left, Rate right), all set in a dark bezel.
 *
 * 1. Find bright rectangles fully enclosed by the dark bezel, and prefer the big-window +
 *    two-small-windows-below layout.
 * 2. Split each window into text rows, deskew the italic digits, split into digit cells.
 * 3. Decode each cell by checking which of its 7 segments are dark.
 *
 * ML Kit can't do step 3: it reads segment gaps as separate strokes.
 */
object PumpDisplayReader {

    fun read(img: GrayImage, lastRate: Double = 0.0): PumpScan {
        val windows = findWindows(img)
        val main = windows.firstOrNull() ?: return PumpScan(emptyList(), emptyList(), null, null, false)
        val rows = decodeRows(img, main)
        val rateWindow = windows.drop(1).filter { it.centerX > main.centerX }.maxByOrNull { it.centerX }
        val rate = rateWindow?.let { decodeRows(img, it).firstOrNull() }
        val (values, validated) = interpret(rows, rate, lastRate)
        return PumpScan(windows, rows, rate, values, validated)
    }

    // ── Values ──────────────────────────────────────────────────────────────

    private fun value(s: String, decimals: Int): Double? =
        if ('.' in s) s.toDoubleOrNull() else s.toLongOrNull()?.let { it / 10.0.pow(decimals) }

    /**
     * Amount = first row, Volume = second row. A missing decimal dot means 2 decimals
     * (volume may have 3). Validated when volume x rate = amount, or failing that when
     * amount / volume is within 3% of the last fill-up's price.
     */
    internal fun interpret(rows: List<String>, rateText: String?, lastRate: Double): Pair<ReceiptValues?, Boolean> {
        if (rows.size < 2) return null to false
        val amount = value(rows[0], 2)?.takeIf { it > 0 } ?: return null to false
        val volumes = (if ('.' in rows[1]) listOf(0) else listOf(2, 3)).mapNotNull { value(rows[1], it) }.filter { it > 0 }
        if (volumes.isEmpty()) return null to false
        val rate = rateText?.let { value(it, 2) }?.takeIf { it > 0 }

        if (rate != null) {
            volumes.firstOrNull { abs(it * rate - amount) <= amount * 0.01 }
                ?.let { return ReceiptValues(it, rate, amount) to true }
            // A rate was read but disagrees: one of the rows is misread, offer nothing
            return null to false
        }
        if (lastRate > 0) {
            volumes.firstOrNull { abs(amount / it - lastRate) <= lastRate * 0.03 }
                ?.let { return ReceiptValues(it, null, amount) to true }
        }
        return ReceiptValues(volumes.first(), null, amount) to false
    }

    // ── Window detection ────────────────────────────────────────────────────

    internal fun otsu(img: GrayImage, box: Box = Box(0, 0, img.width, img.height)): Int {
        val hist = IntArray(256)
        val step = maxOf(1, (box.area / 200_000.0).pow(0.5).toInt())
        var n = 0
        for (y in box.top until box.bottom step step) for (x in box.left until box.right step step) {
            hist[img[x, y]]++; n++
        }
        val total = hist.withIndex().sumOf { (i, c) -> i.toLong() * c }
        var sumB = 0L; var wB = 0; var best = 0.0; var threshold = 128
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = n - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (total - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = t }
        }
        return threshold
    }

    /** Bright, rectangular blobs not touching the image edge; main window first, then small ones left→right. */
    internal fun findWindows(img: GrayImage): List<Box> {
        // Block-average downscale: thin label text blurs away instead of bridging windows together
        val f = maxOf(1, img.width / 400)
        val w = img.width / f
        val h = img.height / f
        val small = IntArray(w * h)
        for (sy in 0 until h) for (sx in 0 until w) {
            var sum = 0
            for (dy in 0 until f) for (dx in 0 until f) sum += img[sx * f + dx, sy * f + dy]
            small[sy * w + sx] = sum / (f * f)
        }
        val smallImg = GrayImage(w, h, small)
        val t = otsu(smallImg)

        val label = IntArray(w * h)
        val queue = IntArray(w * h)
        val candidates = mutableListOf<Box>()
        var next = 0
        for (start in 0 until w * h) {
            if (label[start] != 0 || small[start] <= t) continue
            next++
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = next
            var minX = w; var minY = h; var maxX = 0; var maxY = 0; var count = 0
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                count++
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (x > 0) { val q = p - 1; if (label[q] == 0 && small[q] > t) { label[q] = next; queue[tail++] = q } }
                if (x < w - 1) { val q = p + 1; if (label[q] == 0 && small[q] > t) { label[q] = next; queue[tail++] = q } }
                if (y > 0) { val q = p - w; if (label[q] == 0 && small[q] > t) { label[q] = next; queue[tail++] = q } }
                if (y < h - 1) { val q = p + w; if (label[q] == 0 && small[q] > t) { label[q] = next; queue[tail++] = q } }
            }
            val box = Box(minX, minY, maxX + 1, maxY + 1)
            val touchesEdge = minX == 0 || minY == 0 || maxX == w - 1 || maxY == h - 1
            val aspect = box.width.toFloat() / box.height
            if (!touchesEdge && box.area >= w * h / 500 && count >= box.area * 0.5 && aspect in 0.8f..6f) {
                candidates += box
            }
        }

        // Pump layout: main window with 1-2 smaller windows directly below it
        fun below(main: Box) = candidates.filter { s ->
            s !== main &&
                s.top >= main.bottom - main.height * 0.05 &&
                s.top - main.bottom <= main.height * 0.8 &&
                s.centerX in (main.left - main.width / 10)..(main.right + main.width / 10) &&
                s.width <= main.width * 0.75 && s.height <= main.height * 0.6
        }.sortedBy { it.left }.take(2)

        val main = candidates.maxWithOrNull(compareBy<Box> { below(it).size }.thenBy { it.area }) ?: return emptyList()
        return (listOf(main) + below(main)).map { Box(it.left * f, it.top * f, it.right * f, it.bottom * f) }
    }

    // ── Digit decoding ──────────────────────────────────────────────────────

    // Segment bits: a=top b=upper-right c=lower-right d=bottom e=lower-left f=upper-left g=middle
    private fun bits(s: String) = s.fold(0) { acc, c -> acc or (1 shl (c - 'a')) }
    private val digitPatterns = listOf(
        "abcdef" to '0', "bc" to '1', "abdeg" to '2', "abcdg" to '3', "bcfg" to '4',
        "acdfg" to '5', "acdefg" to '6', "cdefg" to '6', "abc" to '7', "abcf" to '7',
        "abcdefg" to '8', "abcdfg" to '9', "abcfg" to '9'
    ).map { (segs, d) -> bits(segs) to d }

    private fun matchDigit(pattern: Int): Char? {
        digitPatterns.firstOrNull { it.first == pattern }?.let { return it.second }
        // Allow one wrong segment only when the nearest digit is unambiguous
        val scored = digitPatterns.map { Integer.bitCount(it.first xor pattern) to it.second }
        val best = scored.minOf { it.first }
        if (best > 1) return null
        return scored.filter { it.first == best }.map { it.second }.distinct().singleOrNull()
    }

    /** Decoded text rows of a window, top to bottom; rows that aren't clean digits are dropped. */
    internal fun decodeRows(img: GrayImage, window: Box): List<String> {
        val inner = window.inset(0.03f, 0.05f)
        if (inner.width < 10 || inner.height < 10) return emptyList()
        val t = otsu(img, inner)
        val ink = { x: Int, y: Int -> img[x, y] <= t }

        val rowInk = IntArray(inner.height) { dy -> (inner.left until inner.right).count { ink(it, inner.top + dy) } }
        val minRowInk = maxOf(1, inner.width / 50)
        val maxGap = maxOf(1, inner.height / 25) // bridges the gap between upper and lower segments
        val bands = mutableListOf<IntRange>()
        var start = -1; var lastOn = -1
        for (dy in 0 until inner.height) {
            if (rowInk[dy] < minRowInk) continue
            if (start < 0) start = dy
            else if (dy - lastOn - 1 > maxGap) { bands += (inner.top + start)..(inner.top + lastOn); start = dy }
            lastOn = dy
        }
        if (start >= 0) bands += (inner.top + start)..(inner.top + lastOn)
        return bands
            .filter { it.last - it.first + 1 >= inner.height * 0.15 }
            .mapNotNull { decodeBand(ink, inner.left, inner.right, it.first, it.last + 1) }
    }

    private fun decodeBand(ink: (Int, Int) -> Boolean, x0: Int, x1: Int, y0: Int, y1: Int): String? {
        val h = y1 - y0
        val mid = (y0 + y1) / 2f
        val pad = h // room for shifted columns

        fun profile(shear: Float): IntArray {
            val cols = IntArray(x1 - x0 + 2 * pad)
            for (y in y0 until y1) {
                val shift = (shear * (y - mid)).toInt()
                for (x in x0 until x1) if (ink(x, y)) {
                    val c = x - x0 + pad + shift
                    if (c in cols.indices) cols[c]++
                }
            }
            return cols
        }
        // Italic digits: pick the shear that opens up the most empty columns between digits
        val shear = (-6..6).map { it * 0.05f }.maxBy { s ->
            val cols = profile(s)
            val first = cols.indexOfFirst { it > 0 }
            val last = cols.indexOfLast { it > 0 }
            if (first < 0) 0 else (first..last).count { cols[it] == 0 }
        }
        val cols = profile(shear)
        val minColInk = maxOf(1, h / 25)

        // Cells in deskewed coordinates
        val cells = mutableListOf<IntRange>()
        var cs = -1
        for (c in 0..cols.size) {
            val on = c < cols.size && cols[c] >= minColInk
            if (on && cs < 0) cs = c
            if (!on && cs >= 0) { cells += cs until c; cs = -1 }
        }

        // Inverse of the deskew: original x for a deskewed column at row y
        fun origX(c: Int, y: Int) = c - pad - (shear * (y - mid)).toInt() + x0

        fun fraction(c0: Int, c1: Int, ya: Int, yb: Int): Float {
            var hit = 0; var n = 0
            for (y in ya until yb) for (c in c0 until c1) {
                val x = origX(c, y)
                if (x in x0 until x1) { n++; if (ink(x, y)) hit++ }
            }
            return if (n == 0) 0f else hit.toFloat() / n
        }

        // Vertical extent (top, bottom) of a cell's ink
        fun extent(c0: Int, c1: Int): Pair<Int, Int> {
            var top = y1; var bottom = y0
            for (y in y0 until y1) for (c in c0 until c1) {
                val x = origX(c, y)
                if (x in x0 until x1 && ink(x, y)) { if (y < top) top = y; if (y + 1 > bottom) bottom = y + 1 }
            }
            return top to bottom
        }
        fun isDot(e: Pair<Int, Int>) = e.second - e.first in 1 until (h * 0.3f).toInt() && e.first > y0 + h * 0.6f

        // Segments of one digit can be split by thin gaps (bar ends don't touch): rejoin cells
        // closer than 8% of the digit height. The decimal dot sits in such a gap, so it's kept apart.
        // A merge may not grow a digit past the typical width of the row's intact digits, so an
        // edge speck next to the last digit isn't absorbed (it shifts where segments are sampled).
        val intactWidths = cells.filter { c ->
            val (top, bottom) = extent(c.first, c.last + 1)
            c.last - c.first + 1 >= h * 0.28f && bottom - top >= h * 0.6f
        }.map { it.last - it.first + 1 }.sorted()
        val maxDigitWidth = if (intactWidths.size >= 2) intactWidths[intactWidths.size / 2] * 1.15f else h * 0.7f
        val merged = mutableListOf<IntRange>()
        for (cell in cells) {
            val prev = merged.lastOrNull()
            if (prev != null && cell.first - prev.last - 1 < h * 0.08f &&
                cell.last - prev.first + 1 <= maxDigitWidth &&
                !isDot(extent(prev.first, prev.last + 1)) && !isDot(extent(cell.first, cell.last + 1))
            ) {
                merged[merged.size - 1] = prev.first..cell.last
            } else {
                merged += cell
            }
        }

        val out = StringBuilder()
        for (cell in merged) {
            val c0 = cell.first; val c1 = cell.last + 1
            val (top, bottom) = extent(c0, c1)
            val ch = bottom - top
            val cw = c1 - c0
            when {
                ch <= 0 -> continue
                ch < h * 0.3f && top > y0 + h * 0.6f -> { if ('.' !in out && out.isNotEmpty()) out.append('.') }
                ch < h * 0.6f -> continue // dirt / reflections
                cw < h * 0.28f -> out.append('1')
                else -> {
                    fun seg(fx0: Float, fx1: Float, fy0: Float, fy1: Float) =
                        fraction(c0 + (cw * fx0).toInt(), c0 + maxOf((cw * fx1).toInt(), (cw * fx0).toInt() + 1),
                            top + (ch * fy0).toInt(), top + maxOf((ch * fy1).toInt(), (ch * fy0).toInt() + 1)) >= 0.3f
                    var pattern = 0
                    if (seg(0.3f, 0.7f, 0.0f, 0.15f)) pattern = pattern or bits("a")
                    if (seg(0.7f, 1.0f, 0.15f, 0.42f)) pattern = pattern or bits("b")
                    if (seg(0.7f, 1.0f, 0.58f, 0.85f)) pattern = pattern or bits("c")
                    if (seg(0.3f, 0.7f, 0.85f, 1.0f)) pattern = pattern or bits("d")
                    if (seg(0.0f, 0.3f, 0.58f, 0.85f)) pattern = pattern or bits("e")
                    if (seg(0.0f, 0.3f, 0.15f, 0.42f)) pattern = pattern or bits("f")
                    if (seg(0.3f, 0.7f, 0.42f, 0.58f)) pattern = pattern or bits("g")
                    out.append(matchDigit(pattern) ?: return null)
                }
            }
        }
        return out.toString().trimEnd('.').takeIf { s -> s.any { it.isDigit() } }
    }

    /**
     * ML Kit fallback input: per-window binarised (dark digits → black, rest white) and the
     * digits thickened so segment gaps close up and each digit reads as one stroke shape.
     */
    fun preprocessForOcr(img: GrayImage, windows: List<Box>): GrayImage {
        val regions = windows.ifEmpty { listOf(Box(0, 0, img.width, img.height)) }
        val dark = BooleanArray(img.width * img.height)
        for (r in regions) {
            val inner = r.inset(0.03f, 0.05f)
            val t = otsu(img, inner)
            for (y in inner.top until inner.bottom) for (x in inner.left until inner.right) {
                if (img[x, y] <= t) dark[y * img.width + x] = true
            }
        }
        val radius = maxOf(1, img.height / 250)
        // Separable max filter (dilation) on the dark mask
        val tmp = BooleanArray(dark.size)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            tmp[y * img.width + x] = (maxOf(0, x - radius)..minOf(img.width - 1, x + radius)).any { dark[y * img.width + it] }
        }
        val out = IntArray(dark.size)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val on = (maxOf(0, y - radius)..minOf(img.height - 1, y + radius)).any { tmp[it * img.width + x] }
            out[y * img.width + x] = if (on) 0 else 255
        }
        return GrayImage(img.width, img.height, out)
    }
}
