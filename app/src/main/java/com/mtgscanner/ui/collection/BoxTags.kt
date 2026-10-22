package com.mtgscanner.ui.collection

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mtgscanner.data.BoxLinks
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.QrCodes
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import com.mtgscanner.ui.theme.Corners

/** Whether this phone has NFC at all (it may be turned off). */
fun hasNfc(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context) != null

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Scans the QR code on a printed box label and hands back the box id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxQrScanSheet(onBox: (Long) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    var wrong by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Scan a box label", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Corners.Medium)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    QrCamera { text ->
                        val id = BoxLinks.boxId(text)
                        if (id == null) wrong = true
                        else if (!handled) {
                            handled = true
                            onBox(id)
                        }
                    }
                } else {
                    Text("Camera is off", color = Color.White)
                }
            }
            Text(
                if (wrong) "That's not a box code" else "Point at the QR code on a box",
                style = MaterialTheme.typography.bodySmall,
                color = if (wrong) Palette.GoldText else Palette.Muted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

/** The back camera, reading QR codes; [onText] runs on the main thread for each code seen. */
@Composable
private fun QrCamera(onText: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val onTextNow by rememberUpdatedState(onText)
    val previewView = remember {
        PreviewView(ctx).apply {
            // A TextureView: draws correctly inside a sheet's window.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(ctx)
        val future = ProcessCameraProvider.getInstance(ctx)
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    val plane = image.planes[0]
                    val buf = plane.buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    val text = QrCodes.decode(bytes, plane.rowStride, image.width, image.height)
                    if (text != null) main.execute { if (!disposed) onTextNow(text) }
                } finally {
                    image.close()
                }
            }
            provider.unbindAll()
            runCatching { provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
        }, main)
        onDispose {
            disposed = true
            if (future.isDone) runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

/** Writes a box's link to an NFC tag, so tapping the tag with the phone opens that box. */
@Composable
fun NfcWriteDialog(boxId: Long, boxName: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val adapter = remember(ctx) { NfcAdapter.getDefaultAdapter(ctx) }
    var on by remember { mutableStateOf(adapter?.isEnabled == true) }
    var done by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // Turned on in Settings while this is open.
    LaunchedEffect(adapter) {
        while (adapter != null && !on) {
            delay(1000)
            on = adapter.isEnabled
        }
    }
    DisposableEffect(adapter, activity, on) {
        if (adapter == null || activity == null || !on) return@DisposableEffect onDispose { }
        val main = Handler(Looper.getMainLooper())
        val link = BoxLinks.link(boxId)
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(activity, NfcAdapter.ReaderCallback { tag ->
            val err = writeLink(tag, link)
            main.post {
                problem = err
                if (err == null) {
                    done = true
                    com.mtgscanner.util.Haptics.tick()
                }
            }
        }, flags, null)
        onDispose { runCatching { adapter.disableReaderMode(activity) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("NFC tag for $boxName") },
        text = {
            Text(
                when {
                    adapter == null -> "This phone has no NFC."
                    !on -> "NFC is off."
                    done -> "Done. Tap the tag to open $boxName."
                    problem != null -> problem.orEmpty()
                    else -> "Hold a tag to the back of the phone."
                },
                color = if (problem != null && !done) Palette.GoldText else Palette.Text,
            )
        },
        confirmButton = {
            if (adapter != null && !on) {
                TextButton(onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } }) { Text("Turn on") }
            } else {
                TextButton(onClick = onDismiss) { Text(if (done) "Done" else "Cancel") }
            }
        },
        dismissButton = { if (adapter != null && !on) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Writes [link] as the tag's only record. Null when it worked, else a short reason. */
private fun writeLink(tag: Tag, link: String): String? {
    val msg = NdefMessage(arrayOf(NdefRecord.createUri(link)))
    return try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                when {
                    !ndef.isWritable -> "That tag is locked."
                    ndef.maxSize < msg.byteArrayLength -> "That tag is too small."
                    else -> {
                        ndef.writeNdefMessage(msg)
                        null
                    }
                }
            } finally {
                runCatching { ndef.close() }
            }
        } else {
            val blank = NdefFormatable.get(tag) ?: return "Can't write to that tag."
            blank.connect()
            try {
                blank.format(msg)
                null
            } finally {
                runCatching { blank.close() }
            }
        }
    } catch (e: Exception) {
        "Couldn't write. Hold the tag still and try again."
    }
}
