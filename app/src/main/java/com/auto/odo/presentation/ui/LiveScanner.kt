package com.auto.odo.presentation.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.auto.odo.core.Box
import com.auto.odo.core.GrayImage
import com.auto.odo.core.PumpDisplayReader
import com.auto.odo.core.ReceiptValues
import com.auto.odo.core.TextScanner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.abs

enum class ScanTarget(val hint: String, val frameHeightFraction: Float, val framesToConfirm: Int) {
    ODOMETER("Fit the whole instrument display inside the frame", 0.32f, 3),
    // Validated pump results (litres x rate = amount) need fewer frames; see requiredFrames()
    PUMP("Fit the whole pump display, including the small Rate box, inside the frame", 0.5f, 2)
}

sealed interface LiveScanResult {
    data class Odometer(val reading: String) : LiveScanResult
    /** [validated]: cross-checked against the rate (read or last fill-up's), not just read. */
    data class Fuel(val values: ReceiptValues, val validated: Boolean = true) : LiveScanResult
}

/** What one frame produced: regions to outline (in frame-crop pixels) and the parsed result. */
private class FrameResult(val regions: List<Box>, val result: LiveScanResult?)

// Unvalidated pump readings must repeat for longer before they're offered
private fun requiredFrames(target: ScanTarget, result: LiveScanResult?) =
    if (result is LiveScanResult.Fuel && !result.validated) 4 else target.framesToConfirm

private fun parsePumpText(lines: List<String>): LiveScanResult? {
    val r = TextScanner.parseReceipt(lines)
    val (q, p, t) = Triple(r.quantity ?: return null, r.pricePerUnit ?: return null, r.totalCost ?: return null)
    return if (abs(q * p - t) <= t * 0.01) LiveScanResult.Fuel(r) else null
}

/**
 * Full-screen live camera. Odometer: two-pass OCR (find ODO/km, re-read that region enlarged).
 * Pump: find the LCD windows and decode the 7-segment digits directly.
 * A result is offered only once it has been read identically in consecutive frames.
 */
@Composable
fun LiveScannerDialog(
    target: ScanTarget,
    odometerFloor: Double,
    lastRate: Double,
    onResult: (LiveScanResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onDismiss()
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (hasPermission) {
                ScannerContent(target, odometerFloor, lastRate, onResult)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
            }
        }
    }
}

@Composable
private fun BoxScope.ScannerContent(
    target: ScanTarget,
    odometerFloor: Double,
    lastRate: Double,
    onResult: (LiveScanResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var frame by remember { mutableStateOf(Rect.Zero) }
    var regionBoxes by remember { mutableStateOf<List<RectF>>(emptyList()) }
    var latest by remember { mutableStateOf<LiveScanResult?>(null) }
    var confirmed by remember { mutableStateOf<LiveScanResult?>(null) }
    val recent = remember { ArrayDeque<LiveScanResult?>() }

    fun onParsed(parsed: LiveScanResult?) {
        latest = parsed
        recent.addLast(parsed)
        val needed = requiredFrames(target, parsed)
        while (recent.size > needed) recent.removeFirst()
        if (parsed != null && recent.size == needed && recent.all { it == parsed }) confirmed = parsed
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // Default analysis resolution (640x480) is too small for display digits
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val worker = Executors.newSingleThreadExecutor()
        // Runs on the worker thread; OCR calls block there
        val process: (GrayImage) -> FrameResult = when (target) {
            ScanTarget.ODOMETER -> { gray ->
                val (reading, roi) = TextScanner.readOdometerBlocking(recognizer, gray, odometerFloor)
                FrameResult(listOf(roi), reading?.let { LiveScanResult.Odometer(it) })
            }
            ScanTarget.PUMP -> { gray ->
                val scan = PumpDisplayReader.read(gray, lastRate)
                Log.d("OdoOcr", "pump windows=${scan.windows.size} rows=${scan.rows} rate=${scan.rate} -> ${scan.values} validated=${scan.validated}")
                val result = scan.values?.let { LiveScanResult.Fuel(it, scan.validated) } ?: run {
                    // Fallback: ML Kit on binarised, thickened digits (segment gaps closed)
                    val clean = PumpDisplayReader.preprocessForOcr(gray, scan.windows)
                    val lines = TextScanner.recognizeBlocking(recognizer, clean).map { it.text }
                    Log.d("OdoOcr", "pump fallback: ${lines.joinToString(" | ")}")
                    parsePumpText(lines)
                }
                FrameResult(scan.windows, result)
            }
        }
        cameraController.setImageAnalysisAnalyzer(
            worker,
            FrameAnalyzer(context, { frame }, process) { regions, parsed ->
                regionBoxes = regions
                onParsed(parsed)
            }
        )
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            worker.shutdown()
            recognizer.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    val accent = Color(0xFF4CAF50)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val w = size.width * 0.9f
                val h = size.height * target.frameHeightFraction
                frame = Rect(Offset((size.width - w) / 2, (size.height - h) / 2 - size.height * 0.06f), GeoSize(w, h))
            }
    ) {
        val corner = CornerRadius(16.dp.toPx())
        val dim = Path().apply {
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(frame, corner))
            fillType = PathFillType.EvenOdd
        }
        drawPath(dim, Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = if (confirmed != null) accent else Color.White,
            topLeft = frame.topLeft,
            size = frame.size,
            cornerRadius = corner,
            style = Stroke(3.dp.toPx())
        )
        // Regions being read (pump LCD windows / odometer value area), green once they decode
        regionBoxes.forEach { box ->
            drawRoundRect(
                color = if (latest != null) accent else Color(0xFFFFC107),
                topLeft = Offset(box.left, box.top),
                size = GeoSize(box.width(), box.height()),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(3.dp.toPx())
            )
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = when {
                confirmed != null -> "Found it — check the values below"
                latest != null -> "Hold steady…"
                regionBoxes.isNotEmpty() -> "Reading… move closer or reduce glare"
                else -> target.hint
            },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        confirmed?.let { result ->
            Button(onClick = { onResult(result) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(
                    text = when (result) {
                        is LiveScanResult.Odometer -> "Use odometer: ${result.reading}"
                        is LiveScanResult.Fuel -> with(result.values) {
                            listOfNotNull(
                                "$quantity L",
                                pricePerUnit?.let { "rate $it" },
                                "total $totalCost"
                            ).joinToString(" · ", prefix = "Use ")
                        }
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Runs on a worker thread: copies the guide-frame region of the Y (luma) plane upright,
 * hands it to [process], and maps the returned regions back to preview coordinates.
 * Frames arriving while [process] runs are dropped (KEEP_ONLY_LATEST).
 */
private class FrameAnalyzer(
    context: Context,
    private val frameInView: () -> Rect,
    private val process: (GrayImage) -> FrameResult,
    private val onFrame: (regions: List<RectF>, result: LiveScanResult?) -> Unit
) : ImageAnalysis.Analyzer {
    private val main = ContextCompat.getMainExecutor(context)
    @Volatile private var sensorToView: Matrix? = null

    override fun getTargetCoordinateSystem() = ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED

    override fun updateTransform(matrix: Matrix?) {
        sensorToView = matrix?.let { Matrix(it) }
    }

    override fun analyze(image: ImageProxy) {
        val s2v = sensorToView
        val frame = frameInView()
        if (s2v == null || frame.isEmpty) { image.close(); return }

        val rotation = image.imageInfo.rotationDegrees
        val bw = image.width
        val bh = image.height
        val bufferToView = Matrix().apply {
            image.imageInfo.sensorToBufferTransformMatrix.invert(this)
            postConcat(s2v)
        }
        val bufferToUpright = Matrix().apply {
            postRotate(rotation.toFloat())
            val r = RectF(0f, 0f, bw.toFloat(), bh.toFloat())
            mapRect(r)
            postTranslate(-r.left, -r.top)
        }
        val uprightToView = Matrix().apply { bufferToUpright.invert(this); postConcat(bufferToView) }
        val viewToUpright = Matrix().apply { uprightToView.invert(this) }

        val uw = if (rotation % 180 == 0) bw else bh
        val uh = if (rotation % 180 == 0) bh else bw
        val crop = RectF(frame.left, frame.top, frame.right, frame.bottom).also { viewToUpright.mapRect(it) }
        val x0 = crop.left.toInt().coerceIn(0, uw)
        val y0 = crop.top.toInt().coerceIn(0, uh)
        val cw = crop.right.toInt().coerceIn(0, uw) - x0
        val ch = crop.bottom.toInt().coerceIn(0, uh) - y0
        if (cw < 50 || ch < 50) { image.close(); return }

        val gray = copyUprightLuma(image, rotation, x0, y0, cw, ch)
        image.close()

        // OCR throws once the recognizer is closed on dismiss; that frame is just dropped
        val out = try { process(gray) } catch (e: Exception) { FrameResult(emptyList(), null) }
        val regionsInView = out.regions.map { b ->
            RectF((b.left + x0).toFloat(), (b.top + y0).toFloat(), (b.right + x0).toFloat(), (b.bottom + y0).toFloat())
                .also { uprightToView.mapRect(it) }
        }
        main.execute { onFrame(regionsInView, out.result) }
    }

    /** Luma of the upright-image region (x0, y0, w, h), rotating from sensor buffer orientation. */
    private fun copyUprightLuma(image: ImageProxy, rotation: Int, x0: Int, y0: Int, w: Int, h: Int): GrayImage {
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bw = image.width
        val bh = image.height
        val out = IntArray(w * h)
        for (v in 0 until h) {
            val uy = y0 + v
            for (u in 0 until w) {
                val ux = x0 + u
                val bx = when (rotation) { 90 -> uy; 180 -> bw - 1 - ux; 270 -> bw - 1 - uy; else -> ux }
                val by = when (rotation) { 90 -> bh - 1 - ux; 180 -> bh - 1 - uy; 270 -> ux; else -> uy }
                out[v * w + u] = buf.get(by * rowStride + bx * pixelStride).toInt() and 0xFF
            }
        }
        return GrayImage(w, h, out)
    }
}
