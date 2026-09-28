package com.auto.odo.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReceiptValues(val quantity: Double?, val pricePerUnit: Double?, val totalCost: Double?)

/** On-device OCR (ML Kit, bundled model — works offline) plus parsers for fuel receipts and odometers. */
object TextScanner {

    suspend fun recognize(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = Tasks.await(recognizer.process(InputImage.fromFilePath(context, uri)))
            result.textBlocks.flatMap { block -> block.lines.map { it.text } }
                .also { Log.d("OdoOcr", it.joinToString(" | ")) } // adb logcat -s OdoOcr
        } finally {
            recognizer.close()
        }
    }

    fun GrayImage.toBitmap(): Bitmap {
        val argb = IntArray(pixels.size) { i -> val v = pixels[i]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    /** OCR lines with boxes. Blocks until done, so call it off the main thread. */
    fun recognizeBlocking(recognizer: TextRecognizer, img: GrayImage): List<OcrLine> {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(img.toBitmap(), 0)))
        return result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line -> line.boundingBox?.let { OcrLine(line.text, Box(it.left, it.top, it.right, it.bottom)) } }
        }
    }

    /**
     * Two-pass odometer read (blocking): pass 1 over the whole image only locates the ODO/km
     * anchors, pass 2 re-reads the value region enlarged and cleaned by [OdometerReader.prepare].
     * Returns the reading (null if nothing plausible) and the region that was read.
     */
    fun readOdometerBlocking(recognizer: TextRecognizer, img: GrayImage, floor: Double): Pair<String?, Box> {
        val pass1 = recognizeBlocking(recognizer, img)
        val roi = OdometerReader.valueRegion(pass1, img.width, img.height)
        val pass2 = recognizeBlocking(recognizer, OdometerReader.prepare(img, roi))
        Log.d("OdoOcr", "odo pass1: ${pass1.joinToString(" | ") { it.text }} || roi=$roi || pass2: ${pass2.joinToString(" | ") { it.text }}")
        val reading = parseOdometer(pass2.map { it.text }, floor) ?: parseOdometer(pass1.map { it.text }, floor)
        return reading to roi
    }

    /** Decodes an image (downscaled to [maxSide]) to grayscale for [PumpDisplayReader]. */
    suspend fun loadGray(context: Context, uri: Uri, maxSide: Int = 2048): GrayImage = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder also applies the EXIF rotation
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = maxSide.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Unreadable image")
        }
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        GrayImage.fromArgb(bitmap.width, bitmap.height, argb)
    }

    private val number = Regex("""\d+(?:[.,]\d+)?""")

    /** Fixes common 7-segment / LCD misreads inside mostly-numeric tokens: O→0, l→1, S→5, B→8... */
    private fun fixDigits(line: String): String = line.split(' ').joinToString(" ") { token ->
        val digits = token.count { it.isDigit() }
        if (digits == 0 || digits * 2 < token.length) token
        else token.map { c ->
            when (c) {
                'O', 'o', 'D', 'Q' -> '0'; 'I', 'l', 'i', '|', '!' -> '1'; 'Z', 'z' -> '2'
                'S', 's' -> '5'; 'B' -> '8'; 'G', 'b' -> '6'; 'T' -> '7'; 'A', 'q' -> '4'
                else -> c
            }
        }.joinToString("")
    }

    /**
     * All numbers on a line, plus the variant with spaces between digits removed:
     * 7-segment displays leave gaps that OCR reads as spaces ("8 10.39" is 810.39).
     */
    private fun numbersIn(line: String): List<Double> {
        val clean = fixDigits(line).replace(Regex("""(?<=\d),(?=\d{3}\b)"""), "")
        val joined = clean.replace(Regex("""(?<=\d) +(?=\d)"""), "")
        return (number.findAll(clean) + number.findAll(joined))
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .distinct()
            .toList()
    }

    // Whole-word matches only: "Totaliser" / "Calibrated" on pump stickers must not count.
    private val priceKey = Regex("""\b(rate|price)\b|/\s*(l|ltr|litre|liter)\b""")
    private val qtyKey = Regex("""\b(volume|vol|qty|quantity|litres?|liters?|ltrs?)\b""")
    private val totalKey = Regex("""\b(amount|amt|total)\b""")
    private val listIndex = Regex("""^\s*\d{1,2}[.)]\s+""")

    /**
     * Pump displays: any three numbers where quantity x price = total (within 0.5%) are
     * trusted first, since that check is self-validating. Printed receipts fall back to
     * "label: value" pairs; ML Kit often splits a label and its value onto separate lines,
     * so a label with no number takes the next line's number.
     */
    fun parseReceipt(lines: List<String>): ReceiptValues {
        findProductTriple(lines.flatMap(::numbersIn))?.let { return it }

        var qty: Double? = null
        var price: Double? = null
        var total: Double? = null
        lines.forEachIndexed { i, raw ->
            val line = raw.lowercase().replace(listIndex, "")
            val value = numbersIn(line).lastOrNull()
                ?: lines.getOrNull(i + 1)?.let { numbersIn(it).firstOrNull() }
                ?: return@forEachIndexed
            when {
                priceKey.containsMatchIn(line) -> price = price ?: value
                qtyKey.containsMatchIn(line) -> qty = qty ?: value
                totalKey.containsMatchIn(line) -> total = total ?: value
            }
        }
        return ReceiptValues(qty, price, total)
    }

    /**
     * Smallest total first: a display that dropped its decimal dots also yields 100x-scaled
     * matches (7.14 x 11350 = 81039), and the real reading is always the smallest. The factor
     * in the typical per-litre price range (50-200) is taken as price; otherwise the smaller one
     * is quantity. Factors below 0.5 are ignored so list numbers can't form trivial matches.
     */
    private fun findProductTriple(nums: List<Double>): ReceiptValues? {
        // Displays often lose the decimal dot ("8 1039"), so whole numbers >= 100 also try 2 decimals;
        // the product check keeps the wrong guesses out.
        val candidates = (nums + nums.filter { it >= 100 && it % 1.0 == 0.0 }.map { it / 100 })
            .filter { it >= 0.5 }.distinct()
        for (t in candidates.sorted()) for (a in candidates) for (b in candidates) {
            // Fuel quantity/price are practically never both whole numbers; this rules out
            // keypad digits and pump numbers (2 x 4 = 8) forming a false match.
            if (a > b || a == t || b == t || (a % 1.0 == 0.0 && b % 1.0 == 0.0)) continue
            if (kotlin.math.abs(a * b - t) <= t * 0.005) {
                val (q, p) = if (a in 50.0..200.0 && b !in 50.0..200.0) b to a else a to b
                // No vehicle takes 250+ L/gal; rejects mixed-scale matches like 714 x 113.5 = 81039
                if (q > 250) continue
                return ReceiptValues(q, p, t)
            }
        }
        return null
    }

    private val clock = Regex("""\b\d{1,2}\s*[:.]?\s+\d{2}\b|\b\d{1,2}:\d{2}\b""")
    private val odoLabel = Regex("""\b(odo|km|kms|mi|miles)\b""")
    private val rateUnit = Regex("""km\s*/\s*(h|l)|r\s*/\s*min""")

    /**
     * Odometer candidates are 3-7 digit numbers, skipping clock readings ("09 33") and
     * speed / economy / rpm lines. Numbers on a line labelled ODO/km (or right under "ODO")
     * win. When the last known reading is available, only values at or above it (and
     * within 20,000) count; nothing plausible returns null rather than a wrong reading.
     */
    fun parseOdometer(lines: List<String>, lastKnown: Double = 0.0): String? {
        data class Candidate(val value: Long, val labelled: Boolean)
        val candidates = lines.flatMapIndexed { i, raw ->
            val line = fixDigits(raw).lowercase()
            if (rateUnit.containsMatchIn(line)) return@flatMapIndexed emptyList()
            val labelled = odoLabel.containsMatchIn(line) ||
                lines.getOrNull(i - 1)?.lowercase()?.contains("odo") == true
            Regex("""\d{3,7}""").findAll(line.replace(clock, " ").replace(Regex("""(?<=\d)[ ,](?=\d{3}\b)"""), ""))
                .map { Candidate(it.value.toLong(), labelled) }
                .toList()
        }
        val plausible = if (lastKnown > 0) {
            candidates.filter { it.value >= lastKnown && it.value - lastKnown <= 20_000 }
        } else candidates
        return plausible
            .maxWithOrNull(compareBy<Candidate> { it.labelled }.thenBy { it.value.toString().length }.thenBy { it.value })
            ?.value?.toString()
    }
}
