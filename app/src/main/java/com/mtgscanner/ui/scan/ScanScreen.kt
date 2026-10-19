package com.mtgscanner.ui.scan

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.scan.CardTextAnalyzer
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.formatUsd
import java.util.concurrent.Executors

@Composable
fun ScanScreen(
    onOpenCard: (Long) -> Unit,
    vm: ScanViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val ctx = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("The scanner needs the camera to read your cards.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(vm = vm, torch = state.torch)

        // Card-shaped guide. Any orientation works; it just helps framing.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .aspectRatio(63f / 88f)
                .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
        )

        TopControls(state, vm, Modifier.align(Alignment.TopCenter))

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
            state.lastAdded?.let { last ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Added ${last.name}${if (last.foil) " (foil)" else ""} · ${state.sessionCount} this session",
                            modifier = Modifier.weight(1f).clickable { onOpenCard(last.rowId) },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        TextButton(onClick = vm::undoLast) { Text("Undo") }
                    }
                }
            }
            val current = state.current
            if (current != null) {
                ResultCard(current, state, vm)
            } else {
                StatusPill(state)
            }
        }
    }

    if (state.showPrintings) {
        PrintingsSheet(
            printings = state.printings,
            selectedId = state.current?.id,
            onPick = vm::pickPrinting,
            onDismiss = vm::closePrintings,
        )
    }
}

@Composable
private fun TopControls(state: ScanUiState, vm: ScanViewModel, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.autoAdd,
            onClick = { vm.setAutoAdd(!state.autoAdd) },
            label = { Text(if (state.autoAdd) "Auto-add on" else "Auto-add off") },
        )
        FilterChip(
            selected = state.foil,
            onClick = { vm.setFoil(!state.foil) },
            label = { Text("Foil") },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.setTorch(!state.torch) }) {
            Icon(
                if (state.torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Flashlight",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun StatusPill(state: ScanUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.lookingUp) {
            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(state.status, color = Color.White)
            state.reading?.let { Text("Reading: $it", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall) }
            state.error?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ResultCard(card: ScryfallCard, state: ScanUiState, vm: ScanViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            CardImage(card.imageUrl, Modifier.width(96.dp).aspectRatio(63f / 88f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                ManaCost(card.effectiveManaCost)
                Text(card.effectiveTypeLine, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RarityDot(card.rarity)
                    Spacer(Modifier.width(6.dp))
                    Text("${card.setName} · #${card.collectorNumber}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Text(
                    formatUsd(card.price(state.foil && card.hasFoil)) + if (state.foil && card.hasFoil) " foil" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!state.confident) {
                    Text("Not sure about this one. Check the printing.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = vm::openPrintings, label = { Text("Other printings") })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = vm::skip, modifier = Modifier.weight(1f)) { Text("Skip") }
            Button(onClick = { vm.add(1) }, modifier = Modifier.weight(1f)) { Text("Add") }
            OutlinedButton(onClick = { vm.add(4) }) { Text("+4") }
        }
    }
}

@Composable
private fun CameraPreview(vm: ScanViewModel, torch: Boolean) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = CardTextAnalyzer(onGuess = vm::onGuess, isPaused = { vm.isPaused })
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            analyzer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(camera, torch) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
