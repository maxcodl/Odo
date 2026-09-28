package com.auto.odo

import com.auto.odo.core.Box
import com.auto.odo.core.GrayImage
import com.auto.odo.core.OcrLine
import com.auto.odo.core.OdometerReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerReaderTest {

    // Boxes shaped like the real cluster photo (1500x1300 crop): value "7348 km" bottom-left
    private val trueValue = Box(390, 1140, 575, 1190)
    private fun Box.contains(b: Box) = left <= b.left && top <= b.top && right >= b.right && bottom >= b.bottom

    @Test
    fun kmAnchorExtendsLeftToCoverMissedDigits() {
        val lines = listOf(
            OcrLine("09 33", Box(1100, 640, 1250, 690)),
            OcrLine("km/h", Box(925, 1030, 1040, 1065)),  // speed unit, must be ignored
            OcrLine("148 km", Box(440, 1140, 575, 1190))  // OCR lost "73" to a water drop
        )
        val roi = OdometerReader.valueRegion(lines, 1500, 1300)
        assertTrue("roi $roi", roi.contains(trueValue))
    }

    @Test
    fun odoLabelAnchorLooksBelowRight() {
        val lines = listOf(OcrLine("^ADD", Box(260, 1090, 380, 1120))) // "ODO" misread, as in the real log
        val roi = OdometerReader.valueRegion(lines, 1500, 1300)
        assertTrue("roi $roi", roi.contains(trueValue))
    }

    @Test
    fun noAnchorFallsBackToBottomLeft() {
        val roi = OdometerReader.valueRegion(listOf(OcrLine("QS TCS", Box(420, 630, 610, 680))), 1500, 1300)
        assertTrue("roi $roi", roi.contains(trueValue))
    }

    @Test
    fun prepareInvertsLightTextOnDarkScreen() {
        // Dark screen (30) with a light glyph block (230)
        val px = IntArray(200 * 50) { 30 }
        for (y in 15 until 35) for (x in 80 until 120) px[y * 200 + x] = 230
        val out = OdometerReader.prepare(GrayImage(200, 50, px), Box(0, 0, 200, 50))
        assertEquals("upscaled to ~160px tall", 160, out.height)
        assertTrue("background now light", out[5, 5] > 200)
        assertTrue("glyph now dark", out[out.width / 2, out.height / 2] < 50)
    }
}
