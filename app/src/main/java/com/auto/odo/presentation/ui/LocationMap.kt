package com.auto.odo.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.auto.odo.core.location.LatLon
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Static OpenStreetMap preview: a route (2+ points) fitted to the view, or a single spot.
 * Not interactive, so it can sit inside scrolling forms.
 */
@Composable
fun LocationMap(points: List<LatLon>, modifier: Modifier = Modifier) {
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                }
            },
            update = { map ->
                val geo = points.map { GeoPoint(it.lat, it.lon) }
                map.overlays.clear()
                if (geo.size >= 2) {
                    map.overlays += Polyline(map).apply {
                        setPoints(geo)
                        outlinePaint.color = routeColor
                        outlinePaint.strokeWidth = 10f
                    }
                }
                listOfNotNull(geo.firstOrNull(), geo.lastOrNull().takeIf { geo.size >= 2 }).forEach { p ->
                    map.overlays += Marker(map).apply {
                        position = p
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setInfoWindow(null)
                    }
                }
                val fit = {
                    if (geo.size >= 2) map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(geo), false, 64)
                    else if (geo.isNotEmpty()) {
                        map.controller.setZoom(17.0)
                        map.controller.setCenter(geo.first())
                    }
                }
                // Bounding-box zoom needs the view's size
                if (map.isLayoutOccurred) fit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
                map.invalidate()
            },
            onRelease = { it.onDetach() }
        )
        // Swallows touches so the map never pans; the surrounding scroll still works
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}
