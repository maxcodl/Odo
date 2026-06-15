package com.auto.odo.presentation.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val CarSideProfileIcon: ImageVector
    get() = ImageVector.Builder(
        name = "CarSideProfile",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) { // The app's theme tint will automatically override this black color
            // Main Body
            moveTo(17.5f, 10f)
            lineTo(14.5f, 7.5f)
            curveTo(13.5f, 6.5f, 12f, 6f, 10f, 6f)
            lineTo(7.5f, 6f)
            curveTo(5.5f, 6f, 4f, 7f, 3f, 8.5f)
            lineTo(1.5f, 11f)
            curveTo(0.5f, 11.5f, 0f, 12.5f, 0f, 13.5f)
            lineTo(0f, 16f)
            curveTo(0f, 16.5f, 0.5f, 17f, 1f, 17f)
            lineTo(2.5f, 17f)
            
            // Rear wheel well
            curveTo(2.5f, 15f, 4f, 13.5f, 6f, 13.5f)
            curveTo(8f, 13.5f, 9.5f, 15f, 9.5f, 17f)
            lineTo(14.5f, 17f)
            
            // Front wheel well
            curveTo(14.5f, 15f, 16f, 13.5f, 18f, 13.5f)
            curveTo(20f, 13.5f, 21.5f, 15f, 21.5f, 17f)
            lineTo(22f, 17f)
            curveTo(22.5f, 17f, 23f, 16.5f, 23f, 16f)
            lineTo(23f, 12.5f)
            curveTo(23f, 11.5f, 22f, 10.5f, 17.5f, 10f)
            close()

            // Rear wheel
            moveTo(6f, 14.5f)
            curveTo(4.6f, 14.5f, 3.5f, 15.6f, 3.5f, 17f)
            curveTo(3.5f, 18.4f, 4.6f, 19.5f, 6f, 19.5f)
            curveTo(7.4f, 19.5f, 8.5f, 18.4f, 8.5f, 17f)
            curveTo(8.5f, 15.6f, 7.4f, 14.5f, 6f, 14.5f)
            close()

            // Front wheel
            moveTo(18f, 14.5f)
            curveTo(16.6f, 14.5f, 15.5f, 15.6f, 15.5f, 17f)
            curveTo(15.5f, 18.4f, 16.6f, 19.5f, 18f, 19.5f)
            curveTo(19.4f, 19.5f, 20.5f, 18.4f, 20.5f, 17f)
            curveTo(20.5f, 15.6f, 19.4f, 14.5f, 18f, 14.5f)
            close()

            // Rear Window
            moveTo(8.5f, 7.5f)
            lineTo(10.5f, 7.5f)
            lineTo(10.5f, 10.5f)
            lineTo(4.5f, 10.5f)
            lineTo(5.5f, 8.5f)
            curveTo(6f, 7.5f, 7f, 7.5f, 8.5f, 7.5f)
            close()

            // Front Window
            moveTo(11.5f, 7.5f)
            lineTo(14f, 7.5f)
            lineTo(16f, 10.5f)
            lineTo(11.5f, 10.5f)
            lineTo(11.5f, 7.5f)
            close()
        }
    }.build()