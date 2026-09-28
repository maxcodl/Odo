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
        val (values, validated) = PumpDisplayReader.interpret(listOf("81039", "714"), "11350", 0.0)
        assertEquals(ReceiptValues(7.14, 113.5, 810.39), values)
        assertEquals(true, validated)
    }

    @Test
    fun unreadableRateFallsBackToLastFillUpPrice() {
        val (values, validated) = PumpDisplayReader.interpret(listOf("810.39", "7.14"), null, lastRate = 112.0)
        assertEquals(ReceiptValues(7.14, null, 810.39), values)
        assertEquals(true, validated)
    }

    @Test
    fun rateThatDisagreesRejectsTheReading() {
        val (values, _) = PumpDisplayReader.interpret(listOf("810.39", "9.14"), "113.50", 0.0)
        assertEquals(null, values)
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
    fun readsRealPumpPhoto() {
        val file = File("../pump.pgm")
        assumeTrue("pump.pgm not present", file.exists())
        val scan = PumpDisplayReader.read(readPgm(file))
        assertEquals("scan: $scan", ReceiptValues(7.14, 113.5, 810.39), scan.values)
    }
}
