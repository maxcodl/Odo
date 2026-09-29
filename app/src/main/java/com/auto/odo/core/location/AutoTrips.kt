package com.auto.odo.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.auto.odo.R
import com.auto.odo.core.UserSessionManager
import com.auto.odo.data.AppDatabase
import com.auto.odo.data.entity.TripLogEntity
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Automatic trip logging: Activity Recognition reports "in vehicle" enter/exit using the low-power
 * motion sensors; only while driving does [TripRecorderService] run GPS.
 */
object AutoTrips {
    private const val TAG = "AutoTrips"

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasFineLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    // Before Android 10 foreground location covers background use
    fun hasBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    // Before Android 10 this is an install-time permission
    fun hasActivityRecognition(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(context, Manifest.permission.ACTIVITY_RECOGNITION)

    fun ignoresBatteryOptimizations(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun canRun(context: Context) =
        hasFineLocation(context) && hasBackgroundLocation(context) && hasActivityRecognition(context)

    private fun transitionsIntent(context: Context): PendingIntent {
        // Mutable: Play Services fills in the transition result extras
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 0, Intent(context, TripReceiver::class.java), flags)
    }

    /** Registers or removes driving detection to match the setting and granted permissions. */
    @SuppressLint("MissingPermission")
    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val client = ActivityRecognition.getClient(app)
        if (UserSessionManager(app).autoTripsEnabled.first() && canRun(app)) {
            val request = ActivityTransitionRequest(
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                    ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(it)
                        .build()
                }
            )
            client.requestActivityTransitionUpdates(request, transitionsIntent(app))
                .addOnFailureListener { Log.w(TAG, "Driving detection not registered", it) }
        } else {
            runCatching { client.removeActivityTransitionUpdates(transitionsIntent(app)) }
            TripRecorderService.send(app, TripRecorderService.ACTION_DISCARD)
        }
    }
}

/** Receives in-vehicle enter/exit events from Play Services. */
class TripReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val event = ActivityTransitionResult.extractResult(intent)?.transitionEvents?.lastOrNull() ?: return
        TripRecorderService.send(
            context,
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) TripRecorderService.ACTION_DRIVING
            else TripRecorderService.ACTION_STOPPED
        )
    }
}

/** Detection registrations are dropped on reboot and app update. */
class TripBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { AutoTrips.sync(context) } finally { pending.finish() }
        }
    }
}

/**
 * Records one drive: GPS every 5 s / 20 m while driving, then saves a trip once driving has
 * stopped for [EXIT_GRACE_MS] (so a red light doesn't split it) or nothing moved for [IDLE_TIMEOUT_MS].
 * All state lives on the main thread.
 */
class TripRecorderService : Service() {
    companion object {
        const val ACTION_DRIVING = "com.auto.odo.trip.DRIVING"
        const val ACTION_STOPPED = "com.auto.odo.trip.STOPPED"
        const val ACTION_SAVE = "com.auto.odo.trip.SAVE"
        const val ACTION_DISCARD = "com.auto.odo.trip.DISCARD"

        private const val TAG = "TripRecorder"
        private const val CHANNEL_ID = "trip_recording"
        private const val RECORDING_ID = 4201
        private const val SAVED_ID = 4202
        private const val EXIT_GRACE_MS = 3 * 60_000L
        private const val IDLE_TIMEOUT_MS = 10 * 60_000L
        private const val MIN_TRIP_M = 500.0
        private const val MAX_ACCURACY_M = 50f
        private const val MIN_STEP_M = 20f

        @Volatile
        private var running = false

        fun send(context: Context, action: String) {
            val intent = Intent(context, TripRecorderService::class.java).setAction(action)
            try {
                when {
                    action == ACTION_DRIVING -> ContextCompat.startForegroundService(context, intent)
                    running -> context.startService(intent)
                }
            } catch (e: Exception) {
                // e.g. background-start restrictions when permissions were revoked
                Log.w(TAG, "Couldn't deliver $action", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val points = mutableListOf<Location>()
    private var distanceM = 0.0
    private var startedAt = 0L
    private var lastMoveAt = 0L
    private var drivingStoppedAt: Long? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val stopped = drivingStoppedAt?.let { now - it >= EXIT_GRACE_MS } == true
            if (stopped || now - lastMoveAt >= IDLE_TIMEOUT_MS) finish(save = true)
            else handler.postDelayed(this, 30_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DRIVING -> if (running) drivingStoppedAt = null else start()
            ACTION_STOPPED -> if (running && drivingStoppedAt == null) drivingStoppedAt = SystemClock.elapsedRealtime()
            ACTION_SAVE -> finish(save = true)
            ACTION_DISCARD -> finish(save = false)
            else -> if (!running) stopSelf() // restarted without an action: the recorded points are gone
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        try {
            ServiceCompat.startForeground(
                this, RECORDING_ID, recordingNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "Can't start location foreground service", e)
            stopSelf()
            return
        }
        running = true
        points.clear()
        distanceM = 0.0
        startedAt = System.currentTimeMillis()
        lastMoveAt = SystemClock.elapsedRealtime()
        drivingStoppedAt = null
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000)
            .setMinUpdateDistanceMeters(MIN_STEP_M)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            finish(save = false)
            return
        }
        handler.postDelayed(watchdog, 30_000)
    }

    private fun onLocation(loc: Location) {
        if (!running || (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M)) return
        val last = points.lastOrNull()
        if (last != null) {
            val step = last.distanceTo(loc)
            if (step < MIN_STEP_M) return // GPS jitter while standing still
            distanceM += step
        }
        points += loc
        lastMoveAt = SystemClock.elapsedRealtime()
        notify(RECORDING_ID, recordingNotification())
    }

    private fun finish(save: Boolean) {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        handler.removeCallbacks(watchdog)
        fused.removeLocationUpdates(callback)
        val route = points.map { LatLon(it.latitude, it.longitude) }
        val km = distanceM / 1000
        val start = startedAt
        if (!save || distanceM < MIN_TRIP_M) {
            stop()
            return
        }
        scope.launch {
            runCatching { saveTrip(start, km, route) }.onFailure { Log.e(TAG, "Trip not saved", it) }
            stop()
        }
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun saveTrip(date: Long, km: Double, route: List<LatLon>) {
        val db = AppDatabase.getDatabase(this)
        val vehicleId = UserSessionManager(this).currentVehicleId.first() ?: return
        val vehicle = db.vehicleDao().getVehicleById(vehicleId) ?: return
        // Odometer isn't readable from GPS: continue from the latest known reading
        val startOdo = db.tripLogDao().getLatestOdometer(vehicleId) ?: 0.0
        val from = route.firstOrNull()?.let { placeName(this, it) }
        val to = route.lastOrNull()?.let { placeName(this, it) }
        db.tripLogDao().insertTripLog(
            TripLogEntity(
                vehicleId = vehicleId,
                date = date,
                startOdo = startOdo,
                endOdo = startOdo + km,
                purpose = "Personal",
                notes = null,
                route = Route.encode(route),
                startPlace = from,
                endPlace = to
            )
        )
        val dist = if (vehicle.distanceUnit == "miles") "%.1f mi".format(Locale.US, km * 0.621371) else "%.1f km".format(Locale.US, km)
        val places = listOfNotNull(from, to).distinct().joinToString(" → ")
        notify(SAVED_ID, baseNotification()
            .setContentTitle("Trip saved · $dist")
            .setContentText(places.ifEmpty { "Tap to review it in Odo" })
            .setAutoCancel(true)
            .build())
    }

    private fun recordingNotification() = baseNotification()
        .setContentTitle("Recording trip")
        .setContentText(if (distanceM > 0) "%.1f km so far".format(Locale.US, distanceM / 1000) else "Waiting for GPS…")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(0, "Save now", serviceIntent(ACTION_SAVE))
        .addAction(0, "Discard", serviceIntent(ACTION_DISCARD))
        .build()

    private fun baseNotification(): NotificationCompat.Builder {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Trip recording", NotificationManager.IMPORTANCE_LOW))
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setContentIntent(open)
            .setSilent(true)
    }

    private fun serviceIntent(action: String) = PendingIntent.getService(
        this, action.hashCode(), Intent(this, TripRecorderService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE
    )

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    override fun onDestroy() {
        if (running) {
            running = false
            handler.removeCallbacks(watchdog)
            fused.removeLocationUpdates(callback)
        }
        scope.cancel()
        super.onDestroy()
    }
}
