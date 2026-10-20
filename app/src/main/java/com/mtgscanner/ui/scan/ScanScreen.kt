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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
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
import com.mtgscanner.data.db.boxRules
import com.mtgscanner.ui.components.BoxEditorSheet
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.ManaCost
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
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
    // After "Don't ask again", Android won't show the prompt; the button opens the app's settings instead.
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        val activity = ctx as? android.app.Activity
        blocked = !granted && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }
    // Coming back from settings: check again.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && !hasPermission) {
                hasPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val loadedBoxes by vm.boxes.collectAsStateWithLifecycle()
    val boxes = loadedBoxes.orEmpty()
    val needsBox = loadedBoxes != null && boxes.isEmpty()
    val haptics = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { ScanFeedback.release() } }
    LaunchedEffect(state.addedPulse) {
        if (state.addedPulse > 0) {
            if (state.settings.vibrate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            ScanFeedback.play(ctx, state.settings.sound)
        }
    }
    LaunchedEffect(state.asidePulse) {
        if (state.asidePulse > 0) {
            if (state.settings.vibrate) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            ScanFeedback.play(ctx, state.settings.asideSound)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ScanHeader(state, boxes, vm)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (needsBox) {
                EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = "Create a box first",
                    body = "Scanned cards go into boxes.",
                    modifier = Modifier.align(Alignment.Center),
                    action = { GradientButton("Create a box", { vm.showNewBox(true) }, icon = Icons.Filled.Add) },
                )
            } else if (hasPermission) {
                CameraPreview(vm = vm, torch = state.torch)
            } else {
                EmptyState(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera needed",
                    body = "Needed to read your cards.",
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        if (blocked) {
                            GradientButton("Open settings", {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", ctx.packageName, null),
                                    ),
                                )
                            })
                        } else {
                            GradientButton("Allow camera", { launcher.launch(Manifest.permission.CAMERA) })
                        }
                    },
                )
            }

            if (hasPermission && !needsBox) {
                if (state.pageMode) PageGuide(Modifier.align(Alignment.Center))
                else FrameGuide(found = state.current != null, flash = if (state.settings.flash) state.addedPulse else 0, paused = state.paused, modifier = Modifier.align(Alignment.Center))
                PackChip(state, vm, Modifier.align(Alignment.TopStart).padding(top = 76.dp, start = 12.dp))
                BoxCallout(state.lastBoxName, state.lastBoxColor, state.addedPulse, Modifier.align(Alignment.Center))
                Column(
                    Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.review.isNotEmpty()) {
                        Surface(onClick = { vm.showReview(true) }, shape = RoundedCornerShape(14.dp), color = Palette.Gold) {
                            Text("Check ${state.review.size}", color = Color.Black, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    if (state.skipped > 0) {
                        Text(
                            "Skipped ${state.skipped}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Pause + settings float over the top of the camera, like a viewfinder.
            if (!needsBox) Row(
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
                ModesButton(state, vm)
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                AnimatedVisibility(
                    visible = state.current != null,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut(),
                ) {
                    state.current?.let { ResultCard(it, state, vm) }
                }
                state.offline?.let { if (state.current == null) OfflineCard(it.name, vm) }
                if (state.pageMode && hasPermission && !needsBox) PageControls(state, vm)
                else if (state.current == null && state.offline == null && !needsBox) {
                    StatusPill(state, Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }

        ScanListBar(state, onOpen = { vm.showList(true) })
    }

    if (state.showPrintings) {
        PrintingsSheet(state.printings, state.current?.id, vm::pickPrinting, vm::closePrintings)
    }
    if (state.showSettings) SettingsSheet(state, vm)
    if (state.showList) ScanListSheet(state, vm, onOpenCard)
    if (state.showReview) ReviewSheet(state, vm)
    if (state.showPackStart) PackStartDialog(vm)
    if (state.packSummary != null) PackSummaryDialog(state, vm)
    if (state.page != null) PageSheet(state, vm)
    if (state.showNewBox) {
        BoxEditorSheet(
            title = if (boxes.isEmpty()) "Your first box" else "New box",
            onSave = vm::createBox,
            onDismiss = { vm.showNewBox(false) },
        )
    }
}

@Composable
private fun OfflineCard(name: String, vm: ScanViewModel) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Palette.Surface.copy(alpha = 0.97f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudOff, null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::skip, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Skip", tint = Palette.Muted) }
            }
            Text(
                "Offline. Save now, added when back online.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(12.dp))
            GradientButton("Save for later", { vm.saveOffline() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Schedule)
        }
    }
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
                    val auto = state.boxId == CardBox.AUTO_SORT || box == null
                    Icon(if (auto) Icons.Filled.AutoFixHigh else boxIcon(box!!.kind), null, tint = Palette.Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (auto) "Auto-sorting into boxes" else "Adding to ${box!!.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                    Icon(Icons.Filled.ExpandMore, null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = boxMenu, onDismissRequest = { boxMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Auto-sort")
                                Text("Each card goes to the box whose rules fit", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.AutoFixHigh, null) },
                        onClick = { vm.setBox(CardBox.AUTO_SORT); boxMenu = false },
                    )
                    HorizontalDivider(color = Palette.Outline)
                    boxes.forEach { b ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(b.name)
                                    Text(b.boxRules().describe(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                }
                            },
                            leadingIcon = { Icon(boxIcon(b.kind), null, tint = com.mtgscanner.ui.components.boxColor(b.color)) },
                            onClick = { vm.setBox(b.id); boxMenu = false },
                        )
                    }
                    HorizontalDivider(color = Palette.Outline)
                    DropdownMenuItem(
                        text = { Text("New box…") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, tint = Palette.Accent) },
                        onClick = { boxMenu = false; vm.showNewBox(true) },
                    )
                }
            }
        }
        PresetSwitcher()
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.sessionCount}", style = MaterialTheme.typography.titleLarge, color = Palette.Muted)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Style, null, tint = Palette.Text, modifier = Modifier.size(22.dp))
            }
            if (AppSettings.showPrices) Row(verticalAlignment = Alignment.CenterVertically) {
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

/** Quick switch between saved scan presets, right in the header. */
@Composable
private fun PresetSwitcher() {
    val presets by com.mtgscanner.scan.ScanPrefs.presets.collectAsStateWithLifecycle()
    val active by com.mtgscanner.scan.ScanPrefs.activePreset.collectAsStateWithLifecycle()
    if (presets.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Palette.SurfaceHigh).clickable { open = true }
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(active ?: "Custom", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp))
            Icon(Icons.Filled.ExpandMore, "Scan presets", tint = Palette.Muted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            presets.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, color = if (p.name == active) Palette.Accent else Palette.Text) },
                    onClick = { com.mtgscanner.scan.ScanPrefs.apply(p); open = false },
                )
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
                    if (AppSettings.showPrices) Text(formatUsd(card.price(foil)), style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
                    if (!state.confident) {
                        Text("Not sure about this one. Check the printing.", color = Palette.Gold, style = MaterialTheme.typography.bodySmall)
                    }
                    state.wish?.let { w ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.Success.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Favorite, null, tint = Palette.Success, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (w.wanted > 1) "On your wishlist (want ${w.wanted})" else "On your wishlist!",
                                color = Palette.Success,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
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
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Scan settings", style = MaterialTheme.typography.titleLarge)
            ScanSettingsEditor()
            HorizontalDivider(color = Palette.Outline)
            SettingRow("Flashlight", "Helps in dim rooms. Turn it off if foils glare.", state.torch, vm::setTorch)
            if (state.pendingCount > 0) {
                HorizontalDivider(color = Palette.Outline)
                Text(
                    "${state.pendingCount} cards saved while offline. They're added automatically when you're back online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Gold,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
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
                Text("${state.sessionCount} cards${priceSuffix(state.sessionValue)}", style = MaterialTheme.typography.bodySmall, color = Palette.Gold)
            }
            TextButton(onClick = vm::clearList) { Text("Clear list") }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectPill("Newest first", !state.listByBox, { vm.setListByBox(false) })
            SelectPill("By box", state.listByBox, { vm.setListByBox(true) })
        }
        Text(
            "Clearing keeps cards in your collection.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
          val groups = if (state.listByBox) state.session.groupBy { it.boxName.ifEmpty { "Unsorted" } }.toList().sortedBy { it.first }
            else listOf("" to state.session)
          groups.forEach { (boxName, entries) ->
            if (boxName.isNotEmpty()) item(key = "h$boxName") {
                Text(
                    "$boxName · ${entries.sumOf { it.count }} cards",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Accent,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(entries, key = { it.key }) { e ->
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
                        Text(
                            "${e.card.set.uppercase()} · #${e.card.collectorNumber} · ×${e.count}" + if (e.boxName.isNotEmpty()) " · ${e.boxName}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (AppSettings.showPrices) Text(formatUsd(e.value), style = MaterialTheme.typography.titleSmall, color = Palette.Accent)
                    IconButton(onClick = { vm.remove(e) }) { Icon(Icons.Filled.DeleteOutline, "Remove from collection", tint = Palette.Danger) }
                }
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
        val analyzer = CardTextAnalyzer(
            onGuess = vm::onGuess,
            isPaused = { vm.isPaused },
            wantsPage = { vm.state.value.let { it.pageMode && it.wantPage } },
            onPage = vm::onPage,
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
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            camera = runCatching {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.getOrNull()
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            disposed = true
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            analyzer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(camera, torch) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Big label showing which box the last card went into, so a pile can be filed as it's scanned. */
@Composable
private fun BoxCallout(boxName: String?, color: Int, pulse: Int, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(pulse) {
        if (pulse > 0 && boxName != null) {
            visible = true
            kotlinx.coroutines.delay(1400)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible && boxName != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Put it in", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            Text(boxName.orEmpty(), color = com.mtgscanner.ui.components.boxColor(color), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
    }
}

/** Cards auto-add wasn't sure about, to confirm or drop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(state: ScanUiState, vm: ScanViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.showReview(false) }, containerColor = Palette.Surface) {
        Text("Check these", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Auto-add wasn't sure about these.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
            items(state.review, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardImage(c.smallImageUrl, Modifier.width(52.dp), corner = 4.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.set.uppercase()} · #${c.collectorNumber}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    TextButton(onClick = { vm.dropFromReview(c) }) { Text("Drop", color = Palette.Muted) }
                    TextButton(onClick = { vm.addFromReview(c) }) { Text("Add") }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}
