package com.auto.odo

import android.app.Application
import com.auto.odo.core.location.AutoTrips
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

@HiltAndroidApp
class OdoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // OSM's tile servers block osmdroid's default user agent; tile cache stays in app storage
        Configuration.getInstance().apply {
            load(this@OdoApplication, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = cacheDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
        }
        // Permissions may have been revoked (or granted) since the last run
        CoroutineScope(Dispatchers.IO).launch { AutoTrips.sync(this@OdoApplication) }
    }
}
