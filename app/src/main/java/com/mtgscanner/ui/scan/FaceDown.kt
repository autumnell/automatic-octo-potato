package com.mtgscanner.ui.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Pauses scanning while the phone lies face down and resumes when it's picked up, so a phone put down on
 * the pile doesn't keep reading. Only resumes a pause it made. The sensor is on only while the Scan screen
 * is showing and the app is in front.
 */
@Composable
fun FaceDownPause(paused: Boolean, onPause: () -> Unit, onResume: () -> Unit, onFaceDown: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val isPaused by rememberUpdatedState(paused)
    val pause by rememberUpdatedState(onPause)
    val resume by rememberUpdatedState(onResume)
    val report by rememberUpdatedState(onFaceDown)
    val detector = remember { FaceDownDetector() }
    DisposableEffect(owner) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        /** We paused it, so lifting the phone should resume. */
        var ours = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (detector.feed(event.values[2], SystemClock.uptimeMillis())) {
                    true -> if (!isPaused) { ours = true; pause(); report(true) }
                    false -> { if (ours && isPaused) resume(); if (ours) report(false); ours = false }
                    null -> Unit
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var on = false
        fun start() {
            if (on || sm == null || sensor == null) return
            on = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        fun stop() {
            if (!on) return
            sm?.unregisterListener(listener)
            on = false
            detector.reset()
            // Leaving the screen undoes our pause, so it doesn't come back paused for no clear reason.
            if (ours) { ours = false; if (isPaused) resume(); report(false) }
        }
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            owner.lifecycle.removeObserver(obs)
            stop()
        }
    }
}
