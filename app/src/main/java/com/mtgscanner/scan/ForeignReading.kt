package com.mtgscanner.scan

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Reading names printed in Japanese, Chinese or Korean. Off until turned on in Settings › Data: the three reading
 * models come from Google Play services, so only people who scan those cards download them.
 * The scanner uses them only when [ready].
 */
object ForeignReading {
    sealed interface State {
        data object Off : State
        data class Downloading(val percent: Int?) : State
        data object Ready : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state
    val ready: Boolean get() = _state.value == State.Ready

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val PREFS = "app"
    private const val KEY_ON = "foreignReading"

    fun isOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, false)

    /** At app start: when turned on, checks the models are still there and fetches them again if not. */
    fun init(ctx: Context) {
        if (isOn(ctx)) download(ctx.applicationContext)
    }

    fun turnOn(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ON, true).apply()
        download(ctx.applicationContext)
    }

    /** Off: the scanner stops using them, and Play services may delete the models when nothing else uses them. */
    fun turnOff(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ON, false).apply()
        _state.value = State.Off
        val app = ctx.applicationContext
        scope.launch {
            val readers = readers()
            try {
                val apis = readers.filterIsInstance<OptionalModuleApi>()
                if (apis.isNotEmpty()) runCatching { ModuleInstall.getClient(app).releaseModules(*apis.toTypedArray()).await() }
            } finally {
                readers.forEach { runCatching { it.close() } }
            }
        }
    }

    private fun download(app: Context) {
        if (_state.value is State.Downloading || _state.value == State.Ready) return
        _state.value = State.Downloading(null)
        scope.launch {
            val readers = readers()
            try {
                val ok = runCatching { install(app, readers) }.getOrDefault(false) || warmUp(app, readers)
                // Turned off while it was downloading: stay off.
                _state.value = when {
                    !isOn(app) -> State.Off
                    ok -> State.Ready
                    else -> State.Failed
                }
            } finally {
                readers.forEach { runCatching { it.close() } }
            }
        }
    }

    /** Asks Play services for the models, with progress. False when it can't, so [warmUp] is tried instead. */
    private suspend fun install(app: Context, readers: List<TextRecognizer>): Boolean {
        val apis = readers.filterIsInstance<OptionalModuleApi>()
        if (apis.size != readers.size) return false
        val client = ModuleInstall.getClient(app)
        if (client.areModulesAvailable(*apis.toTypedArray()).await().areModulesAvailable()) return true
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val listener = InstallStatusListener { update ->
            when (update.installState) {
                ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> done.complete(true)
                ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> done.complete(false)
                else -> update.progressInfo?.let { p ->
                    if (p.totalBytesToDownload > 0) {
                        _state.value = State.Downloading((p.bytesDownloaded * 100 / p.totalBytesToDownload).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
        val request = ModuleInstallRequest.newBuilder().apply { apis.forEach { addApi(it) } }.setListener(listener).build()
        return try {
            val response = client.installModules(request).await()
            if (response.areModulesAlreadyInstalled()) true else done.await()
        } finally {
            runCatching { client.unregisterListener(listener) }
        }
    }

    /**
     * Without the install service, the first read starts the download and fails until it's done:
     * tries a small read every 10 seconds for up to 10 minutes.
     */
    private suspend fun warmUp(app: Context, readers: List<TextRecognizer>): Boolean {
        val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        try {
            repeat(60) {
                if (!isOn(app)) return false
                val ok = readers.all { r -> runCatching { r.process(InputImage.fromBitmap(bmp, 0)).await() }.isSuccess }
                if (ok) return true
                delay(10_000)
            }
            return false
        } finally {
            bmp.recycle()
        }
    }

    private fun readers(): List<TextRecognizer> = listOf(
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
