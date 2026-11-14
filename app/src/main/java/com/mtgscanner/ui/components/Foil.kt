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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
 * One tilt listener for every foil on screen. Foils [hold] it while their screen is resumed; the sensor runs
 * only while at least one holds it, so nothing listens with the app in the background, and ten foil rows
 * share one listener instead of ten.
 */
private object TiltSensor {
    /** Where the band of light sits, roughly -1..1 across the card. */
    val tilt = mutableFloatStateOf(0f)
    private var holders = 0
    private var manager: SensorManager? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Side-to-side tilt moves it most, forward-back a little.
            val target = ((e.values[0] / SensorManager.GRAVITY_EARTH) * 1.4f -
                (e.values[1] / SensorManager.GRAVITY_EARTH) * 0.5f).coerceIn(-1.2f, 1.2f)
            // Smooth out hand shake.
            val next = tilt.floatValue + (target - tilt.floatValue) * 0.18f
            // A phone lying still reports tiny changes 50 times a second; skipping them stops every foil on screen
            // from being redrawn for a move too small to see.
            if (kotlin.math.abs(next - tilt.floatValue) > 0.002f) tilt.floatValue = next
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    fun sensorFor(sensors: SensorManager?): Sensor? =
        sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun hold(sensors: SensorManager) {
        holders++
        if (holders == 1) {
            val sensor = sensorFor(sensors) ?: return
            manager = sensors
            sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun release() {
        if (holders == 0) return
        holders--
        if (holders == 0) {
            manager?.unregisterListener(listener)
            manager = null
        }
    }
}

/**
 * A soft rainbow sheen over a foil card's picture that slides as the phone tilts. Without a tilt
 * sensor it drifts slowly on its own; with animations off it stays still. Drawn only on the
 * picture's own pixels, so rounded corners stay clean.
 */
fun Modifier.foilShimmer(enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    val ctx = LocalContext.current
    val still = remember(ctx) { animationsOff(ctx) }
    val sensors = remember(ctx) { ctx.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val hasSensor = remember(sensors) { TiltSensor.sensorFor(sensors) != null }
    val phase: State<Float> = if (still) {
        remember { mutableFloatStateOf(0f) }
    } else if (hasSensor && sensors != null) {
        // Listen only while this screen is resumed: leaving it or putting the app away stops the sensor.
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner, sensors) {
            var holding = false
            fun hold() { if (!holding) { holding = true; TiltSensor.hold(sensors) } }
            fun drop() { if (holding) { holding = false; TiltSensor.release() } }
            val observer = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_RESUME -> hold()
                    Lifecycle.Event.ON_PAUSE -> drop()
                    else -> Unit
                }
            }
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) hold()
            owner.lifecycle.addObserver(observer)
            onDispose {
                owner.lifecycle.removeObserver(observer)
                drop()
            }
        }
        TiltSensor.tilt
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
