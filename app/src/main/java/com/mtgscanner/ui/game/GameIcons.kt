package com.mtgscanner.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small icons the Material set doesn't have: a crown for the monarch and a sword for the initiative. */
object GameIcons {
    val Crown: ImageVector by lazy {
        ImageVector.Builder(
            name = "Crown", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Three points over a band.
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 17f)
                lineTo(3.2f, 6.5f)
                lineTo(8.2f, 11f)
                lineTo(12f, 3.5f)
                lineTo(15.8f, 11f)
                lineTo(20.8f, 6.5f)
                lineTo(22f, 17f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 18.5f)
                lineTo(22f, 18.5f)
                lineTo(22f, 21f)
                lineTo(2f, 21f)
                close()
            }
        }.build()
    }

    val Sword: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sword", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // An upright sword, tipped 45 degrees so the point is at the top right.
            group(rotate = 45f, pivotX = 12f, pivotY = 12f) {
                path(fill = SolidColor(Color.Black)) {
                    // Blade
                    moveTo(12f, -1f)
                    lineTo(14.2f, 2.5f)
                    lineTo(14.2f, 15.5f)
                    lineTo(9.8f, 15.5f)
                    lineTo(9.8f, 2.5f)
                    close()
                    // Crossguard
                    moveTo(6f, 15.5f)
                    lineTo(18f, 15.5f)
                    lineTo(18f, 18f)
                    lineTo(6f, 18f)
                    close()
                    // Grip
                    moveTo(10.9f, 18f)
                    lineTo(13.1f, 18f)
                    lineTo(13.1f, 22.5f)
                    lineTo(10.9f, 22.5f)
                    close()
                    // Pommel
                    moveTo(9.8f, 22.5f)
                    lineTo(14.2f, 22.5f)
                    lineTo(14.2f, 25f)
                    lineTo(9.8f, 25f)
                    close()
                }
            }
        }.build()
    }
}
