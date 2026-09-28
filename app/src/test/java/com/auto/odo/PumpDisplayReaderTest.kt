package com.auto.odo

import com.auto.odo.core.GrayImage
import com.auto.odo.core.PumpDisplayReader
import com.auto.odo.core.ReceiptValues
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PumpDisplayReaderTest {

    // ── Synthetic display ───────────────────────────────────────────────────

    private val segmentsOf = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg"
    )

    private class Canvas(val w: Int, val h: Int, bg: Int) {
        val px = IntArray(w * h) { bg }
        /** Fills a rect given in un-slanted coords, slanting it italic around [midY]. */
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, value: Int, shear: Float = 0f, midY: Int = 0) {
            for (y in y0 until y1) {
                val shift = (-shear * (y - midY)).toInt()
                for (x in x0 until x1) {
                    val sx = x + shift
                    if (sx in 0 until w && y in 0 until h) px[y * w + sx] = value
                }
            }
        }
    }

    /** Right-aligned 7-segment text ending at [right]; '.' is drawn in the gap before the next digit. */
    private fun Canvas.text(s: String, right: Int, top: Int, dh: Int, shear: Float = 0.15f) {
        val dw = dh * 6 / 11
        val t = dh / 8
        val gap = 2
        val spacing = dw * 2 / 5
        val mid = top + dh / 2
        val digits = s.count { it.isDigit() }
        var x = right - digits * dw - (digits - 1) * spacing
        for (c in s) {
            if (c == '.') {
                val dot = t
                fill(x - spacing / 2 - dot / 2, top + dh - dot, x - spacing / 2 + dot / 2 + 1, top + dh, 40, shear, mid)
                continue
            }
            val segs = segmentsOf.getValue(c)
            val m = top + dh / 2
            fun seg(n: Char, x0: Int, y0: Int, x1: Int, y1: Int) { if (n in segs) fill(x + x0, y0, x + x1, y1, 40, shear, mid) }
            seg('a', t + gap, top, dw - t - gap, top + t)
            seg('b', dw - t, top + t + gap, dw, m - gap)
            seg('c', dw - t, m + gap, dw, top + dh - t - gap)
            seg('d', t + gap, top + dh - t, dw - t - gap, top + dh)
            seg('e', 0, m + gap, t, top + dh - t - gap)
            seg('f', 0, top + t + gap, t, m - gap)
            seg('g', t + gap, m - t / 2, dw - t - gap, m + t / 2)
            x += dw + spacing
        }
    }

    private fun syntheticPump(amount: String, volume: String, density: String, rate: String): GrayImage {
        val c = Canvas(900, 700, bg = 30) // dark bezel
        c.fill(100, 100, 800, 400, 225)   // main window
        c.fill(100, 440, 420, 560, 225)   // density window
        c.fill(480, 440, 800, 560, 225)   // rate window
        c.text(amount, right = 740, top = 130, dh = 110)
        c.text(volume, right = 740, top = 270, dh = 110)
        c.text(density, right = 400, top = 460, dh = 80)
        c.text(rate, right = 770, top = 460, dh = 80)
        return GrayImage(c.w, c.h, c.px)
    }

    @Test
    fun findsLayoutAndDecodesSyntheticDisplay() {
        val scan = PumpDisplayReader.read(syntheticPump("810.39", "7.14", "751.5", "113.50"))
        assertEquals("windows: ${scan.windows}", 3, scan.windows.size)
        assertEquals(listOf("810.39", "7.14"), scan.rows)
        assertEquals("113.50", scan.rate)
        assertEquals(ReceiptValues(7.14, 113.5, 810.39), scan.values)
        assertEquals(true, scan.validated)
    }

    @Test
    fun decodesEveryDigit() {
        val scan = PumpDisplayReader.read(syntheticPump("1234.56", "78.90", "751.5", "102.34"))
        assertEquals(listOf("1234.56", "78.90"), scan.rows)
    }

    // ── Interpretation ──────────────────────────────────────────────────────

    @Test
    fun missingDecimalPointsAssumeTwoPlaces() {
        val (values, validated) = PumpDisplayReader.interpret(listOf("81039", "714"), listOf("11350"), 0.0)
        assertEquals(ReceiptValues(7.14, 113.5, 810.39), values)
        assertEquals(true, validated)
    }

    @Test
    fun unreadableRateFallsBackToLastFillUpPrice() {
        val (values, validated) = PumpDisplayReader.interpret(listOf("810.39", "7.14"), emptyList(), lastRate = 112.0)
        assertEquals(ReceiptValues(7.14, null, 810.39), values)
        assertEquals(true, validated)
    }

    @Test
    fun noAgreeingRateLeavesReadingUnvalidated() {
        val (values, validated) = PumpDisplayReader.interpret(listOf("810.39", "9.14"), listOf("751.5", "113.50"), 0.0)
        assertEquals(ReceiptValues(9.14, null, 810.39), values)
        assertEquals(false, validated)
    }

    // ── Real photo ──────────────────────────────────────────────────────────
    // Android unit tests can't decode JPEGs (no javax.imageio), so pump.jpg is pre-converted
    // to binary PGM, downscaled to 2048px like gallery photos in the app:
    //   python -c "from PIL import Image,ImageOps; im=ImageOps.exif_transpose(Image.open('pump.jpg')).convert('L'); s=min(1,2048/max(im.size)); im.resize((int(im.width*s),int(im.height*s))).save('pump.pgm')"

    /** Reads a binary (P5) 8-bit PGM. */
    private fun readPgm(file: File): GrayImage {
        val bytes = file.readBytes()
        var pos = 0
        fun token(): String {
            while (bytes[pos].toInt().toChar().isWhitespace()) pos++
            val start = pos
            while (!bytes[pos].toInt().toChar().isWhitespace()) pos++
            return String(bytes, start, pos - start)
        }
        check(token() == "P5")
        val w = token().toInt()
        val h = token().toInt()
        token() // max value, 255
        pos++   // single whitespace before pixel data
        return GrayImage(w, h, IntArray(w * h) { bytes[pos + it].toInt() and 0xFF })
    }

    @Test
    fun readsRealPumpPhoto() = assertPhoto("pump", ReceiptValues(7.14, 113.5, 810.39))

    // One photo per layout seen at Indian pumps. PGMs are made by the python line above.

    @Test // Tokheim, two displays side by side: the one nearest the centre is read
    fun tokheimTwinDisplays() = assertPhoto("IMG_20260110_192839848", ReceiptValues(6.74, 105.49, 711.0))

    @Test // BPCL blue backlight, Price/Litre on the left and blank: validated by last rate
    @org.junit.Ignore("photo taken from a distance: digits too small and thin")
    fun bpclBlueDisplayBlankRate() =
        assertPhoto("IMG_20260223_191953100", ReceiptValues(7.72, null, 814.54), lastRate = 105.5)

    @Test // Tokheim with the neighbouring display cut off at the edge
    @org.junit.Ignore("steep angle: last digits hidden by the bezel, segments blurred to outlines")
    fun tokheimPartlyCutNeighbour() = assertPhoto("IMG_20260601_215429287", ReceiptValues(7.05, 113.5, 800.18))

    @Test // Accuefill: density inside the main bezel, rate in its own bezel further down
    @org.junit.Ignore("sky reflection across the glass hides the LCD edges")
    fun accuefillSeparateRateBezel() = assertPhoto("IMG_20260712_155540789", ReceiptValues(7.38, 113.48, 837.48))

    @Test // Gilbarco: density and rate stacked on the left under a big main window
    fun gilbarcoStackedSmallWindows() = assertPhoto("IMG_20260807_091809161", ReceiptValues(6.98, 113.5, 792.23))

    @Test // Newer Tokheim, glare line across the volume row
    fun tokheimNewWithGlare() = assertPhoto("IMG_20260831_222531923", ReceiptValues(6.01, 113.61, 682.8))

    private fun assertPhoto(name: String, expected: ReceiptValues, lastRate: Double = 0.0) {
        val file = File("../$name.pgm")
        assumeTrue("$name.pgm not present", file.exists())
        val scan = PumpDisplayReader.read(readPgm(file), lastRate)
        assertEquals("scan: $scan", expected, scan.values)
    }
}
