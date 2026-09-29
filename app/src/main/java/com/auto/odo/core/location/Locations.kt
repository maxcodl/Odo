package com.auto.odo.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.auto.odo.data.entity.FuelLogEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Great-circle distance in metres. */
fun distanceMeters(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * 6_371_000.0 * asin(sqrt(h))
}

/**
 * Whether a GPS step is real travel rather than drift. Parked, fixes wander tens of metres but
 * the GPS's own (Doppler) speed stays near zero, so trust that when present; otherwise require the
 * step to beat both fixes' combined error. Rejected fixes aren't recorded, so the next step is
 * measured from the last accepted point and slow real movement still adds up.
 */
fun isRealMovement(stepM: Float, seconds: Double, speedMps: Float?, combinedAccuracyM: Float): Boolean {
    if (speedMps != null && speedMps < 2f) return false // under ~7 km/h: parked or crawling
    if (stepM < maxOf(20f, combinedAccuracyM)) return false
    return seconds <= 0 || stepM / seconds < 70 // over ~250 km/h: a bad fix, not a drive
}

/** Trip routes are stored as "lat,lon;lat,lon;…" with 5 decimals (~1 m). */
object Route {
    fun encode(points: List<LatLon>): String =
        points.joinToString(";") { String.format(Locale.US, "%.5f,%.5f", it.lat, it.lon) }

    fun decode(route: String?): List<LatLon> =
        route.orEmpty().split(';').mapNotNull { p ->
            val parts = p.split(',')
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lon = parts.getOrNull(1)?.toDoubleOrNull()
            if (parts.size == 2 && lat != null && lon != null) LatLon(lat, lon) else null
        }
}

fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t -> cont.resume(if (t.isSuccessful) t.result else null) }
}

/** One fresh GPS fix, or null without permission / with location switched off / after 10 s. */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LatLon? {
    if (!hasLocationPermission(context)) return null
    val cts = CancellationTokenSource()
    return try {
        withTimeoutOrNull(10_000) {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .awaitOrNull()
        }?.let { LatLon(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    } finally {
        cts.cancel()
    }
}

/** Area name ("Andheri West", "Pune") via Android's built-in Geocoder; free, no key. */
suspend fun placeName(context: Context, p: LatLon): String? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault()).getFromLocation(p.lat, p.lon, 1)?.firstOrNull()
    }.getOrNull()?.let { it.subLocality ?: it.locality ?: it.subAdminArea }
}

/** Station name from your own history: the closest logged fill within [radiusM]. Works offline. */
fun nearestSavedStation(logs: List<FuelLogEntity>, here: LatLon, radiusM: Double = 150.0): String? =
    logs.asSequence()
        .filter { !it.stationName.isNullOrBlank() && it.latitude != null && it.longitude != null }
        .map { it to distanceMeters(here, LatLon(it.latitude!!, it.longitude!!)) }
        .filter { it.second <= radiusM }
        .minByOrNull { it.second }
        ?.first?.stationName?.trim()

/** Nearest fuel station from OpenStreetMap's public Overpass API. Free, no key; sends [here] to OSM. */
object OsmStations {
    private val MIRRORS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )
    // OSM services block generic user agents
    private const val USER_AGENT = "Odo/1.0 (+https://github.com/maxcodl/Odo)"

    suspend fun nearest(here: LatLon, radiusM: Int = 300): String? = withContext(Dispatchers.IO) {
        val query = "[out:json][timeout:5];nwr(around:$radiusM,${here.lat},${here.lon})[amenity=fuel];out center tags;"
        for (mirror in MIRRORS) {
            // 429 / 504 / timeout on one mirror → try the next
            val body = runCatching { post(mirror, "data=" + URLEncoder.encode(query, "UTF-8")) }.getOrNull() ?: continue
            return@withContext runCatching { closestNamed(body, here) }.getOrNull()
        }
        null
    }

    private fun post(url: String, form: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(form.toByteArray()) }
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun closestNamed(json: String, here: LatLon): String? {
        val elements = JSONObject(json).optJSONArray("elements") ?: return null
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val name = listOf("name", "brand", "operator")
                .map { tags.optString(it) }.firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
            // Nodes carry lat/lon; ways and relations carry a computed "center"
            val pos = e.optJSONObject("center") ?: e
            if (!pos.has("lat")) return@mapNotNull null
            name to distanceMeters(here, LatLon(pos.getDouble("lat"), pos.getDouble("lon")))
        }.minByOrNull { it.second }?.first
    }
}
