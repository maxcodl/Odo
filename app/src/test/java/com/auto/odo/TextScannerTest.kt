package com.auto.odo

import com.auto.odo.core.ReceiptValues
import com.auto.odo.core.TextScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextScannerTest {

    @Test
    fun parsesTypicalPumpReceipt() {
        val lines = listOf(
            "INDIAN OIL", "Date: 12/09/2026", "Nozzle No: 2", "Density: 745.2",
            "Rate(Rs/L): 102.50", "Volume(L)", "20.35", "Amount(Rs): 2,085.88"
        )
        assertEquals(ReceiptValues(20.35, 102.5, 2085.88), TextScanner.parseReceipt(lines))
    }

    @Test
    fun parsesUnlabelledPumpDisplayByProduct() {
        // 7-segment digits with no labels recognised: 20.35 L x 102.50 = 2085.88
        val lines = listOf("HP", "2085.88", "20.35", "102.50", "Pump 3")
        assertEquals(ReceiptValues(20.35, 102.5, 2085.88), TextScanner.parseReceipt(lines))
    }

    @Test
    fun parsesIndianOilPumpPhotoIgnoringStickerText() {
        // Shaped like the real pump photo: sticker text with "Totaliser"/"Calibrated",
        // keypad digits, and 7-segment gaps ("8 10.39", "1 13.5O")
        val lines = listOf(
            "1. Sealing Points- Meter with Pulsar, Electronic Totaliser with Electronic Calibration.",
            "2. Electronically Calibrated DU - No Calibration on Meter.",
            "Amount (₹)", "8 10.39", "7.14", "Volume (L)", "7 51.5", "1 13.5O",
            "Density (Kg/m³)", "Rate (₹/L)", "17", "1 2 3 4 5 6 7 8 9 0",
            "3. Press AMT for amount and LTR for volume"
        )
        assertEquals(ReceiptValues(7.14, 113.5, 810.39), TextScanner.parseReceipt(lines))
    }

    @Test
    fun recoversDroppedDecimalPoints() {
        // Real OCR output from the pump photo lost the dots: "8 1039" is 810.39
        val lines = listOf("Amount ()", "8 1039", "714", "Volume L)", "7515", "11350")
        assertEquals(ReceiptValues(7.14, 113.5, 810.39), TextScanner.parseReceipt(lines))
    }

    @Test
    fun parsesOdometerIgnoringTripAndClock() {
        assertEquals("48213", TextScanner.parseOdometer(listOf("10:45", "TRIP A 123.4", "ODO 048213 km")))
    }

    @Test
    fun parsesBikeClusterOdometerNotClock() {
        // Shaped like the real cluster photo: clock "09 33", rpm scale, speed, km/L
        val lines = listOf("QS TCS", "09 33", "x1000r/min", "0", "km/h", "ODO", "7348 km", "CRNT FUEL", "km/L")
        assertEquals("7348", TextScanner.parseOdometer(lines))
        assertEquals("7348", TextScanner.parseOdometer(lines, lastKnown = 7200.0))
    }

    @Test
    fun rejectsOdometerBelowLastKnown() {
        // Only the clock-like 933 survives OCR: better no value than a wrong one
        assertNull(TextScanner.parseOdometer(listOf("0933", "ODO"), lastKnown = 7200.0))
    }
}
