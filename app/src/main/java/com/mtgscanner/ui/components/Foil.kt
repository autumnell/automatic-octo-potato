package com.mtgscanner.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/** True when the phone's animations are switched off (developer options or accessibility). */
fun animationsOff(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)

// Pale foil colors, see-through so the art stays readable.
private val FoilColors = listOf(
    Color.Transparent,
    Color(0x00FFFFFF),
    Color(0x38FF9AD5),
    Color(0x40FFE79A),
    Color(0x3D9AF5FF),
    Color(0x38B49AFF),
    Color(0x00FFFFFF),
    Color.Transparent,
)

/**
 * A soft rainbow sheen over a foil card's picture that slides as the phone tilts. Without a tilt
 * sensor it drifts slowly on its own; with animations off it stays still. Drawn only on the
 * picture's own pixels, so rounded corners stay clean.
 */
fun Modifier.foilShimmer(enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    val ctx = LocalContext.current
    val still = remember(ctx) { animationsOff(ctx) }
    val sensors = remember(ctx) { ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember(sensors) {
        sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    // Where the band of light sits, roughly -1..1 across the card.
    val tilt = remember { mutableFloatStateOf(0f) }
    val phase: State<Float> = if (still) {
        tilt
    } else if (sensor != null && sensors != null) {
        DisposableEffect(sensors, sensor) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    // Side-to-side tilt moves it most, forward-back a little.
                    val target = ((e.values[0] / SensorManager.GRAVITY_EARTH) * 1.4f -
                        (e.values[1] / SensorManager.GRAVITY_EARTH) * 0.5f).coerceIn(-1.2f, 1.2f)
                    // Smooth out hand shake.
                    tilt.floatValue += (target - tilt.floatValue) * 0.18f
                }

                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensors.unregisterListener(listener) }
        }
        tilt
    } else {
        rememberInfiniteTransition(label = "foil").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
            label = "foilDrift",
        )
    }
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            // The phase is read here, in drawing, so tilting redraws without recomposing.
            val shift = phase.value * w * 0.6f
            drawRect(
                brush = Brush.linearGradient(
                    colors = FoilColors,
                    start = Offset(-w * 0.4f + shift, 0f),
                    end = Offset(w * 0.9f + shift, h),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}
