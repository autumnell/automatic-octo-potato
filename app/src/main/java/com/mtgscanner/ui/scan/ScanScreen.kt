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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.scan.CardTextAnalyzer
import com.mtgscanner.ui.AppViewModels
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.Palette
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

    val state by vm.state.collectAsStateWithLifecycle()
    val boxes by vm.boxes.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.addedPulse) {
        if (state.addedPulse > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ScanHeader(state, boxes, vm)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasPermission) {
                CameraPreview(vm = vm, torch = state.torch)
            } else {
                EmptyState(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera needed",
                    body = "The scanner reads your cards with the camera. Nothing leaves your phone except the card name lookup.",
                    modifier = Modifier.align(Alignment.Center),
                    action = { GradientButton("Allow camera", { launcher.launch(Manifest.permission.CAMERA) }) },
                )
            }

            if (hasPermission) {
                FrameGuide(found = state.current != null, flash = state.addedPulse, paused = state.paused, modifier = Modifier.align(Alignment.Center))
            }

            // Pause + settings float over the top of the camera, like a viewfinder.
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundIconButton(
                    icon = if (state.torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    label = "Flashlight",
                    onClick = { vm.setTorch(!state.torch) },
                    tint = if (state.torch) Palette.Gold else Color.White,
                )
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { vm.togglePause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (state.paused) "Resume scanning" else "Pause scanning",
                            tint = Color.White,
                        )
                    }
                }
                RoundIconButton(Icons.Filled.Settings, "Scan settings", { vm.showSettings(true) })
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                AnimatedVisibility(
                    visible = state.current != null,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut(),
                ) {
                    state.current?.let { ResultCard(it, state, vm) }
                }
                if (state.current == null) StatusPill(state, Modifier.align(Alignment.CenterHorizontally))
            }
        }

        ScanListBar(state, onOpen = { vm.showList(true) })
    }

    if (state.showPrintings) {
        PrintingsSheet(state.printings, state.current?.id, vm::pickPrinting, vm::closePrintings)
    }
    if (state.showSettings) SettingsSheet(state, vm)
    if (state.showList) ScanListSheet(state, vm, onOpenCard)
}

@Composable
private fun ScanHeader(state: ScanUiState, boxes: List<CardBox>, vm: ScanViewModel) {
    var boxMenu by remember { mutableStateOf(false) }
    val box = boxes.firstOrNull { it.id == state.boxId }
    Row(
        Modifier.fillMaxWidth().background(Palette.Background).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Scan list", style = MaterialTheme.typography.titleLarge)
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { boxMenu = true }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(boxIcon(box?.kind ?: "box"), null, tint = Palette.Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Adding to ${box?.name ?: "Bulk"}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Icon(Icons.Filled.ExpandMore, null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = boxMenu, onDismissRequest = { boxMenu = false }) {
                    boxes.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name) },
                            leadingIcon = { Icon(boxIcon(b.kind), null) },
                            onClick = { vm.setBox(b.id); boxMenu = false },
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.sessionCount}", style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Style, null, tint = Palette.Text, modifier = Modifier.size(22.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.session.isEmpty()) "--" else formatUsd(state.sessionValue),
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Gold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Toll, null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Card-shaped frame. Turns orange when a card is recognized and flashes when one is added. */
@Composable
private fun FrameGuide(found: Boolean, flash: Int, paused: Boolean, modifier: Modifier) {
    val color by animateColorAsState(
        when {
            paused -> Color.White.copy(alpha = 0.25f)
            found -> Palette.Accent
            else -> Color.White.copy(alpha = 0.55f)
        },
        label = "frame",
    )
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash > 0) {
            flashing = true
            kotlinx.coroutines.delay(220)
            flashing = false
        }
    }
    val fill by animateFloatAsState(if (flashing) 0.25f else 0f, tween(200), label = "flash")
    Box(
        modifier
            .fillMaxWidth(0.66f)
            .aspectRatio(63f / 88f)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Accent.copy(alpha = fill))
            .border(3.dp, color, RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun StatusPill(state: ScanUiState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.lookingUp) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.Accent)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.status, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (!state.paused) {
                state.reading?.let {
                    Text("Reading “$it”", color = Palette.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            state.error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ResultCard(card: ScryfallCard, state: ScanUiState, vm: ScanViewModel) {
    val foil = state.foil && card.hasFoil || !card.hasNonFoil
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Palette.Surface.copy(alpha = 0.97f)) {
        Column(Modifier.padding(14.dp)) {
            Row {
                CardImage(card.imageUrl, Modifier.width(92.dp), corner = 6.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(card.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = vm::skip, modifier = Modifier.size(28.dp).offset(x = 6.dp, y = (-6).dp)) {
                            Icon(Icons.Filled.Close, "Skip", tint = Palette.Muted)
                        }
                    }
                    ManaCost(card.effectiveManaCost, size = 15.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(card.effectiveTypeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RarityDot(card.rarity, 8.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("${card.set.uppercase()} · #${card.collectorNumber}", style = MaterialTheme.typography.bodySmall)
                        if (foil) {
                            Spacer(Modifier.width(6.dp))
                            FoilTag()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(formatUsd(card.price(foil)), style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
                    if (!state.confident) {
                        Text("Not sure about this one. Check the printing.", color = Palette.Gold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = vm::openPrintings, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Printing")
                }
                OutlinedButton(onClick = { vm.add(4) }, shape = RoundedCornerShape(14.dp)) { Text("+4") }
                GradientButton("Add", { vm.add(1) }, Modifier.weight(1f), icon = Icons.Filled.Add)
            }
        }
    }
}

/** Strip under the camera with the latest scans; tap to see the full list. */
@Composable
private fun ScanListBar(state: ScanUiState, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background)
            .clickable(enabled = state.session.isNotEmpty(), onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.session.isEmpty()) {
            Text("Scanned cards show up here", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            return@Row
        }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.session.take(10).forEach { e -> CardImage(e.card.smallImageUrl, Modifier.height(48.dp), corner = 3.dp) }
        }
        Spacer(Modifier.width(10.dp))
        Text("See all", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: ScanUiState, vm: ScanViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.showSettings(false) }, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Scan settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            SettingRow("Auto-add", "Add confident matches straight away so you can feed cards one after another.", state.autoAdd, vm::setAutoAdd)
            HorizontalDivider(color = Palette.Outline)
            SettingRow("Foil", "Mark the next cards as foil.", state.foil, vm::setFoil)
            HorizontalDivider(color = Palette.Outline)
            SettingRow("Flashlight", "Helps in dim rooms. Turn it off if foils glare.", state.torch, vm::setTorch)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanListSheet(state: ScanUiState, vm: ScanViewModel, onOpenCard: (Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = { vm.showList(false) }, containerColor = Palette.Surface) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scan list", style = MaterialTheme.typography.titleLarge)
                Text("${state.sessionCount} cards · ${formatUsd(state.sessionValue)}", style = MaterialTheme.typography.bodySmall, color = Palette.Gold)
            }
            TextButton(onClick = vm::clearList) { Text("Clear list") }
        }
        Text(
            "Clearing the list keeps the cards in your collection. Use the bin to take one back out.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
            items(state.session, key = { it.key }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.showList(false); onOpenCard(e.rowId) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardImage(e.card.smallImageUrl, Modifier.width(44.dp), corner = 4.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (e.foil) {
                                Spacer(Modifier.width(6.dp))
                                FoilTag()
                            }
                        }
                        Text("${e.card.set.uppercase()} · #${e.card.collectorNumber} · ×${e.count}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    Text(formatUsd(e.value), style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
                    IconButton(onClick = { vm.remove(e) }) { Icon(Icons.Filled.DeleteOutline, "Remove from collection", tint = Palette.Danger) }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
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
