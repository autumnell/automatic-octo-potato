package com.mtgscanner.ui.scan

import com.mtgscanner.ui.theme.Space
import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.ScanBarPrefs
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.scan.CardTextAnalyzer
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.Haptics
import com.mtgscanner.ui.components.foilShimmer
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.scryfallPrice
import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import java.util.concurrent.Executors
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.TabularNums
import com.mtgscanner.ui.components.ruleBelow

@Composable
internal fun CameraPreview(vm: ScanViewModel, torch: Boolean) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val scanSettings by com.mtgscanner.scan.ScanPrefs.settings.collectAsStateWithLifecycle()
    val sharper = scanSettings.sharperFrames

    DisposableEffect(lifecycleOwner, sharper) {
        val executor = Executors.newSingleThreadExecutor()
        // Set once bound: the analyzer asks the camera to focus on the card it finds.
        val bound = java.util.concurrent.atomic.AtomicReference<Pair<Camera, ImageAnalysis>?>(null)
        val analyzer = CardTextAnalyzer(
            onGuess = vm::onGuess,
            isPaused = { vm.isPaused },
            wantsPage = { vm.state.value.let { it.pageMode && it.wantPage } },
            onPage = vm::onPage,
            onLight = vm::onLight,
            wantsArt = vm::wantsArt,
            embed = vm::embedArt,
            scanLang = { com.mtgscanner.data.ScanBarPrefs.lang },
            holding = vm::holdingCard,
            wantsSpread = { vm.state.value.let { it.pageMode && it.wantSpread } },
            onSpread = vm::onSpread,
            setLock = { com.mtgscanner.data.SortPrefs.setLock },
            wantsCentering = { vm.state.value.settings.centering },
            onQuad = CardTrack::report,
            onFocus = focus@{ x, y, w, h ->
                val (cam, use) = bound.get() ?: return@focus
                // Points in the analysis frame's own pixels; the factory maps them to the sensor.
                val point = androidx.camera.core.SurfaceOrientedMeteringPointFactory(w.toFloat(), h.toFloat(), use).createPoint(x, y)
                val action = androidx.camera.core.FocusMeteringAction.Builder(
                    point, androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE,
                ).setAutoCancelDuration(4, java.util.concurrent.TimeUnit.SECONDS).build()
                runCatching { cam.cameraControl.startFocusAndMetering(action) }
            },
        )
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        // The camera can take a moment to start; if the screen closed meanwhile, don't bind it.
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            // 1080p: the footer's small print is drawn from these pixels. Whole-card reads are
                            // scaled down to about the size they had at 720p, so they stay quick.
                            // "Sharper frames" asks for 1440p: about a third more pixels across the card.
                            ResolutionStrategy(
                                if (sharper) Size(2560, 1440) else Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // YUV as the camera gives it: the card and light checks read the Y plane in place, and only a frame
                // that passes the card check is turned into a picture (about 4 a second, not 30).
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            camera = runCatching {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.getOrNull()
            camera?.let { bound.set(it to analysis) }
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            disposed = true
            bound.set(null)
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            analyzer.close()
            executor.shutdown()
        }
    }

    // The starting zoom from Scan settings; pinching still changes it.
    LaunchedEffect(camera, scanSettings.zoom) {
        camera?.let { cam ->
            val z = cam.cameraInfo.zoomState.value
            val ratio = if (z == null) scanSettings.zoom else scanSettings.zoom.coerceIn(z.minZoomRatio, z.maxZoomRatio)
            runCatching { cam.cameraControl.setZoomRatio(ratio) }
        }
    }

    LaunchedEffect(camera, torch) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) }
    }
    // The camera turns the flashlight off when it closes (app in the background): turn it back on when it reopens.
    val torchNow by androidx.compose.runtime.rememberUpdatedState(torch)
    DisposableEffect(camera, lifecycleOwner) {
        val cam = camera
        val obs = androidx.lifecycle.Observer<androidx.camera.core.CameraState> { cs ->
            if (cs.type == androidx.camera.core.CameraState.Type.OPEN && torchNow && cam != null && cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(true)
            }
        }
        cam?.cameraInfo?.cameraState?.observe(lifecycleOwner, obs)
        onDispose { cam?.cameraInfo?.cameraState?.removeObserver(obs) }
    }

    // Tap to focus there, pinch to zoom.
    var focusAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var focusTick by remember { mutableStateOf(0) }
    var zoomLabel by remember { mutableStateOf<String?>(null) }
    val camNow by androidx.compose.runtime.rememberUpdatedState(camera)
    DisposableEffect(previewView) {
        val tap = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean = true
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                val cam = camNow ?: return false
                val point = previewView.meteringPointFactory.createPoint(e.x, e.y)
                val action = androidx.camera.core.FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(4, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                runCatching { cam.cameraControl.startFocusAndMetering(action) }
                focusAt = androidx.compose.ui.geometry.Offset(e.x, e.y)
                focusTick++
                return true
            }
        })
        val pinch = android.view.ScaleGestureDetector(ctx, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val cam = camNow ?: return false
                val zoom = cam.cameraInfo.zoomState.value ?: return false
                val ratio = (zoom.zoomRatio * detector.scaleFactor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
                runCatching { cam.cameraControl.setZoomRatio(ratio) }
                zoomLabel = "%.1f×".format(java.util.Locale.US, ratio)
                return true
            }
        })
        previewView.setOnTouchListener { v, ev ->
            pinch.onTouchEvent(ev)
            if (!pinch.isInProgress) tap.onTouchEvent(ev)
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) v.performClick()
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        FocusRing(focusAt, focusTick)
        zoomLabel?.let { z ->
            LaunchedEffect(z) {
                kotlinx.coroutines.delay(1200)
                zoomLabel = null
            }
            Text(
                z, color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 210.dp).clip(RoundedCornerShape(Corners.Medium))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = Space.s3, vertical = Space.s1),
            )
        }
    }
}

/** A brief ring where the camera was tapped to focus. [at] is in the preview's pixels. */
@Composable
private fun FocusRing(at: androidx.compose.ui.geometry.Offset?, tick: Int) {
    if (at == null || tick == 0) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        visible = true
        kotlinx.coroutines.delay(700)
        visible = false
    }
    if (!visible) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val size = 64.dp
    val half = with(density) { (size / 2).roundToPx() }
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(at.x.toInt() - half, at.y.toInt() - half) }
            .size(size)
            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
    )
}
