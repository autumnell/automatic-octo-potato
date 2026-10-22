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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.priceSuffix
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import java.util.concurrent.Executors
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.TabularNums

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
    // The "Price check" app shortcut.
    val priceCheck by com.mtgscanner.util.OpenRequests.priceCheck.collectAsStateWithLifecycle()
    LaunchedEffect(priceCheck) {
        if (priceCheck) { com.mtgscanner.util.OpenRequests.priceCheck.value = false; vm.startCheck() }
    }
    // Home's "not checked in 3 months" nudge: start a Box check of that box once the boxes are loaded.
    val boxCheckAsk by com.mtgscanner.util.OpenRequests.boxCheck.collectAsStateWithLifecycle()
    LaunchedEffect(boxCheckAsk, loadedBoxes) {
        val id = boxCheckAsk ?: return@LaunchedEffect
        val box = loadedBoxes?.firstOrNull { it.id == id } ?: return@LaunchedEffect
        com.mtgscanner.util.OpenRequests.boxCheck.value = null
        vm.startBoxCheck(box)
    }
    val boxes = loadedBoxes.orEmpty()
    val needsBox = loadedBoxes != null && boxes.isEmpty()
    val view = LocalView.current
    DisposableEffect(Unit) { onDispose { ScanFeedback.release() } }
    // Volume up adds the card showing (or moves on, in a price check), volume down skips it.
    // Only while a card is showing and no sheet is open; otherwise the buttons change the volume.
    val latest by androidx.compose.runtime.rememberUpdatedState(state)
    DisposableEffect(vm) {
        VolumeKeys.load(ctx)
        VolumeKeys.handler = handler@{ up ->
            val st = latest
            val card = st.current ?: return@handler false
            if (st.pageMode || st.showPrintings || st.showSettings || st.showList || st.showReview || st.showSort || st.showBoxPick ||
                st.showDeckPick || st.showNewBox || st.showSkipped || st.showUnknown || st.showBoxCheck || st.showSetLock || st.showPackStart
            ) return@handler false
            if (!up) {
                vm.skip()
                if (st.settings.vibrate) Haptics.setAside(view)
            } else when (st.target) {
                ScanTarget.CHECK -> vm.skip()
                ScanTarget.REMOVE -> if ((st.owned ?: 1) > 0) vm.add(1) else return@handler false
                ScanTarget.BOX -> vm.add(1)
                else -> if (st.autoAdd && st.session.firstOrNull()?.card?.id == card.id) vm.sameAgain() else vm.add(ScanBarPrefs.quantity)
            }
            true
        }
        onDispose { VolumeKeys.handler = null }
    }
    val hintTargets = remember { mutableStateMapOf<String, Rect>() }
    // Lines added since the screen opened slide into the pile; older ones are already there.
    val startKey = remember { state.session.maxOfOrNull { it.key } ?: 0L }
    var flight by remember { mutableStateOf<Flight?>(null) }
    var lastLine by remember { mutableStateOf(state.session.firstOrNull()?.let { it.key to it.count }) }
    // Bumps once per new add on this screen; drives the frame flash and box callout without replaying them on return.
    var addTick by remember { mutableStateOf(0) }
    LaunchedEffect(state.addedPulse) {
        if (vm.freshAdded(state.addedPulse)) {
            if (state.settings.vibrate) {
                if (state.lastFull) Haptics.boxFull(view) else Haptics.added(view)
            }
            ScanFeedback.play(ctx, state.settings.sound)
            // Hands-free sorting: say where it goes.
            if (state.stack && state.settings.speakBox && state.target == ScanTarget.COLLECTION) {
                state.lastBoxName?.let { ScanFeedback.speak(ctx, it + (state.lastSpot?.let { s -> ", $s" } ?: "")) }
            }
            addTick++
            val e = state.session.firstOrNull()
            val line = e?.let { it.key to it.count }
            if (e != null && isNewAdd(lastLine, line)) {
                // One frame so the pile has laid out with the new card before aiming at it.
                withFrameNanos { }
                val from = hintTargets["frame"]
                val to = hintTargets["fan"] ?: hintTargets["list"]
                if (from != null && to != null) {
                    flight = Flight(
                        id = addTick,
                        imageUrl = e.card.imageUrl,
                        from = from,
                        to = to,
                        label = e.where.ifEmpty { "Added" } + if (e.full) " · Full" else "",
                        color = if (e.deckId != 0L) 0 else state.lastBoxColor,
                    )
                }
            }
            lastLine = line
        }
    }
    LaunchedEffect(state.asidePulse) {
        if (vm.freshAside(state.asidePulse)) {
            if (state.settings.vibrate) Haptics.setAside(view)
            ScanFeedback.play(ctx, state.settings.asideSound)
        }
    }
    LaunchedEffect(state.skipPulse) {
        // Stack mode: a soft tick so you know a skipped card was seen.
        if (vm.freshSkip(state.skipPulse) && state.stack && state.settings.sound != com.mtgscanner.scan.ScanSound.NONE) ScanFeedback.tick(ctx)
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalHintTargets provides hintTargets) {
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                ScanHeader(state, boxes, vm)

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (needsBox) {
                        EmptyState(
                            "Create a box first",
                            Modifier.align(Alignment.Center),
                            action = "Create a box",
                            onAction = { vm.showNewBox(true) },
                        )
                    } else if (hasPermission) {
                        CameraPreview(vm = vm, torch = state.torch)
                    } else {
                        EmptyState(
                            "Camera needed",
                            Modifier.align(Alignment.Center),
                            action = if (blocked) "Open settings" else "Allow camera",
                            onAction = {
                                if (blocked) {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.fromParts("package", ctx.packageName, null),
                                        ),
                                    )
                                } else {
                                    launcher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        )
                    }

                    if (hasPermission && !needsBox) {
                        if (state.pageMode) PageGuide(Modifier.align(Alignment.Center))
                        else FrameGuide(
                            found = state.current != null, flash = if (state.settings.flash) addTick else 0, paused = state.paused,
                            // Flashes in the color of the box the card goes to, so a pile can be sorted by color.
                            flashColor = if (state.lastBoxName != null && state.lastBoxColor != 0) com.mtgscanner.ui.components.boxColor(state.lastBoxColor)
                            else Palette.Accent,
                            modifier = Modifier.align(Alignment.Center).hintTarget("frame"),
                        )
                        Column(
                            Modifier.align(Alignment.TopStart).padding(top = 76.dp, start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PackChip(state, vm)
                            SortChip(state, vm)
                            BoxCheckChip(state, vm)
                            RemoveChip(state, vm)
                        }
                        BoxCallout(state, addTick, Modifier.align(Alignment.Center))
                        Column(
                            Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 12.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.review.isNotEmpty()) {
                                Surface(onClick = { vm.showReview(true) }, shape = RoundedCornerShape(Corners.Medium), color = Palette.Gold) {
                                    Text("Check ${state.review.size}", color = Color.Black, style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                            if (state.skipped > 0) {
                                Text(
                                    "Skipped ${state.skipped}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Color.Black.copy(alpha = 0.7f))
                                        .clickable(onClickLabel = "Show skipped cards") { vm.showSkipped(true) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                            // Cards saved for later because they couldn't be identified.
                            if (state.unknownCount > 0) {
                                Text(
                                    "Unknown ${state.unknownCount}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clip(RoundedCornerShape(Corners.Medium)).background(Color.Black.copy(alpha = 0.7f))
                                        .clickable(onClickLabel = "Show unknown scans") { vm.showUnknown(true) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }

                    // Settings and modes float over the top of the camera.
                    if (!needsBox) Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RoundIconButton(Icons.Filled.Settings, "Scan settings", { vm.showSettings(true) }, modifier = Modifier.hintTarget("settings"))
                        ModesButton(state, vm)
                    }

                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                        state.unknownRead?.let { if (!needsBox) UnknownChip(it, vm, Modifier.align(Alignment.CenterHorizontally)) }
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
                            // This session's cards, fanned at the left; the status beside them.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                if (state.session.isNotEmpty()) {
                                    SessionFan(state.session, startKey, onClick = { vm.showList(true) }, modifier = Modifier.hintTarget("fan"))
                                    Spacer(Modifier.width(10.dp))
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) { StatusPill(state) }
                            }
                        }
                        if (!needsBox) {
                            // The last card hides while a new one is on screen.
                            if (state.current == null && state.offline == null) {
                                Spacer(Modifier.height(8.dp))
                                // Stack mode drops a copy that looks just like the last one; "+1" adds it.
                                val sameAgain = if ((state.stack || state.autoAdd) && state.target != ScanTarget.CHECK && state.session.isNotEmpty()) vm::sameAgain else null
                                LastCardBar(state, onOpenCard, onOpenList = { vm.showList(true) }, Modifier.hintTarget("last"), onSameAgain = sameAgain)
                            }
                            Spacer(Modifier.height(8.dp))
                            ScanToggleBar(state, vm, Modifier.hintTarget("bar"))
                        }
                    }
                }

                if (!needsBox) ControlsRow(state, vm)
            }
        }
        // Drawn over everything on the screen, never catching touches.
        AddFlightOverlay(flight)
        val sheetOpen = state.showPrintings || state.showSettings || state.showList || state.showReview || state.showPackStart ||
            state.showSort || state.showSetLock || state.showDeckPick || state.page != null || state.photo != null || state.showNewBox ||
            state.showSkipped || state.showUnknown || state.showBoxPick || state.showBoxCheck
        if (!ScanBarPrefs.hintsSeen && loadedBoxes != null && !needsBox && !sheetOpen) {
            ScanHintsOverlay(hintTargets, onDone = { ScanBarPrefs.updateHintsSeen(true) })
        }
    }

    if (state.showPrintings) {
        PrintingsSheet(state.printings, state.current?.id, vm::pickPrinting, vm::closePrintings)
    }
    if (state.showSettings) SettingsSheet(state, vm)
    if (state.showList) ScanListSheet(state, vm, onOpenCard)
    if (state.showReview) ReviewSheet(state, vm)
    if (state.showPackStart) PackStartDialog(vm)
    if (state.showSort) SortSheet(state, boxes, vm)
    if (state.showSetLock) SetPickSheet(vm)
    if (state.showDeckPick) DeckPickDialog(vm)
    if (state.page != null) PageSheet(state, vm)
    if (state.photo != null) PhotoSheet(state, vm)
    if (state.showSkipped) SkippedSheet(state, vm)
    if (state.showUnknown) UnknownSheet(vm)
    if (state.showBoxPick) BoxPickDialog(boxes, vm)
    if (state.showBoxCheck) BoxCheckSheet(state, vm, onOpenCard)
    if (state.showNewBox) {
        BoxEditorSheet(
            title = if (boxes.isEmpty()) "Your first box" else "New box",
            onSave = vm::createBox,
            onDismiss = { vm.showNewBox(false) },
        )
    }
}

/** Flashlight, pause and the scan list, within thumb reach. */
@Composable
private fun ControlsRow(state: ScanUiState, vm: ScanViewModel) {
    Row(
        // Extra room at the bottom for the raised Scan tab under it.
        Modifier.fillMaxWidth().background(Color.Black).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundIconButton(
            icon = if (state.torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            label = "Flashlight",
            onClick = { vm.setTorch(!state.torch) },
            tint = if (state.torch) Palette.Gold else Color.White,
            modifier = Modifier.hintTarget("flash"),
        )
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.85f))
                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { vm.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (state.paused) "Resume scanning" else "Pause scanning",
                    tint = Color.White,
                )
            }
        }
        Box(Modifier.hintTarget("list")) {
            RoundIconButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Scan list", { vm.showList(true) })
            val count = state.sessionCount
            if (count > 0) Text(
                if (count > 99) "99+" else "$count",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(Palette.Gold)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** Foil on/off chip, at least 48dp tall. */
@Composable
private fun FoilChip(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Corners.Small))
            .background(if (on) Palette.Gold else Color.Black.copy(alpha = 0.6f))
            .border(1.dp, if (on) Palette.Gold else Color.White.copy(alpha = 0.35f), RoundedCornerShape(Corners.Small))
            .clickable(onClickLabel = if (on) "Turn foil off" else "Turn foil on", onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (on) "Foil on" else "Foil", color = if (on) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun OfflineCard(name: String, vm: ScanViewModel) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface.copy(alpha = 0.97f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudOff, null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(
                "Offline. Save now, added when back online.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = vm::skip, shape = RoundedCornerShape(Corners.Medium), modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip") }
                GradientButton("Save for later", { vm.saveOffline() }, Modifier.weight(1f).heightIn(min = 48.dp), icon = Icons.Filled.Schedule)
            }
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
            Text("Scan", style = MaterialTheme.typography.titleLarge)
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(Corners.Small)).clickable { boxMenu = true }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val auto = state.boxId == CardBox.AUTO_SORT || box == null
                    Icon(if (auto) Icons.AutoMirrored.Filled.Sort else boxIcon(box!!.kind), null, tint = Palette.Icon, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    val where = when (state.target) {
                        ScanTarget.CHECK -> "Just checking · not adding"
                        ScanTarget.DECK -> "Deck: ${state.deck?.name.orEmpty()}"
                        ScanTarget.REMOVE -> "Removing from collection"
                        ScanTarget.BOX -> "Checking ${state.boxCheck?.boxName.orEmpty()}"
                        ScanTarget.COLLECTION -> if (auto) "Auto-sorting into boxes" else "Adding to ${box!!.name}"
                    }
                    Text(
                        (if (state.stack) "Stack · " else "") + where,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.target != ScanTarget.COLLECTION || state.stack) Palette.Gold else Palette.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Filled.ExpandMore, null, tint = Palette.Icon, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = boxMenu, onDismissRequest = { boxMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Auto-sort")
                                Text("Each card goes to the box whose rules fit", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
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
                        leadingIcon = { Icon(Icons.Filled.Add, null, tint = Palette.Icon) },
                        onClick = { boxMenu = false; vm.showNewBox(true) },
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val count = state.sessionCount
        Text(
            (if (count == 1) "1 card" else "$count cards") + priceSuffix(state.sessionValue),
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White, modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Card-shaped frame. Turns orange when a card is recognized and flashes when one is added. */
@Composable
private fun FrameGuide(found: Boolean, flash: Int, paused: Boolean, modifier: Modifier, flashColor: Color = Palette.Accent) {
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
            .clip(CardShape)
            .background(flashColor.copy(alpha = fill))
            .border(3.dp, if (flashing) flashColor else color, CardShape),
    )
}

@Composable
private fun StatusPill(state: ScanUiState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(Corners.Medium))
            .background(Color.Black.copy(alpha = 0.8f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(Corners.Medium))
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
                if (state.glare) {
                    Text("Tilt to reduce glare", color = Palette.Gold, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ResultCard(card: ScryfallCard, state: ScanUiState, vm: ScanViewModel) {
    val foil = state.isFoil(card)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface.copy(alpha = 0.97f)) {
        Column(Modifier.padding(14.dp)) {
            Row {
                CardImage(card.imageUrl, Modifier.width(92.dp).foilShimmer(foil))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium)
                    ManaCost(card.effectiveManaCost, size = 15.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(card.effectiveTypeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RarityDot(card.rarity, 8.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${card.set.uppercase()} · #${card.collectorNumber}" + (state.currentLang?.let { " · ${it.uppercase()}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (foil && !(card.hasFoil && card.hasNonFoil)) {
                            Spacer(Modifier.width(6.dp))
                            FoilTag()
                        }
                        // Set lock on, and this card is from another set (it said so in its footer).
                        val lock = com.mtgscanner.data.SortPrefs.setLock
                        if (lock != null && !card.set.equals(lock, ignoreCase = true)) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Not in ${lock.uppercase()}",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.Gold).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    // Foil for this card only, when it comes both ways.
                    if (card.hasFoil && card.hasNonFoil && state.target != ScanTarget.CHECK) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FoilChip(foil, vm::toggleCardFoil)
                            // Glare changed between frames like a foil's does. Only a hint: tap to accept.
                            if (state.foilHint && !foil) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Looks foil",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.Gold.copy(alpha = 0.85f))
                                        .clickable(onClickLabel = "Mark foil", onClick = vm::acceptFoilHint)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (AppSettings.showPrices) Text(scryfallPrice(card, foil), style = MaterialTheme.typography.titleLarge.merge(TabularNums), color = Palette.Accent)
                        state.owned?.let { n ->
                            if (AppSettings.showPrices) Spacer(Modifier.width(10.dp))
                            Text(
                                if (n > 0) "Own ×$n" else "Not owned",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (n > 0) Palette.Success else Palette.Muted,
                            )
                        }
                    }
                    // Where you keep it: "Rares ×3 · Bulk ×1".
                    if (state.keptIn.isNotEmpty()) {
                        Text(
                            state.keptIn.joinToString(" · ") { (box, n) -> "$box ×$n" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!state.confident) {
                        Text("Not sure about this one. Check the printing.", color = Palette.Gold, style = MaterialTheme.typography.bodySmall)
                    }
                    state.wish?.let { w ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(Corners.Small)).background(Palette.Success.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
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
            val tight = PaddingValues(horizontal = 12.dp)
            // Price check: look, then move on. Nothing is added.
            if (state.target == ScanTarget.CHECK) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = vm::openPrintings, shape = RoundedCornerShape(Corners.Medium), contentPadding = tight, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Printing", maxLines = 1)
                }
                GradientButton("Next", vm::skip, Modifier.weight(1f).heightIn(min = 48.dp))
            } else if (state.target == ScanTarget.REMOVE || state.target == ScanTarget.BOX) Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = vm::skip, shape = RoundedCornerShape(Corners.Medium), contentPadding = tight, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Skip", maxLines = 1)
                }
                OutlinedButton(onClick = vm::openPrintings, shape = RoundedCornerShape(Corners.Medium), contentPadding = tight, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Printing", maxLines = 1)
                }
                if (state.target == ScanTarget.REMOVE) {
                    GradientButton("Remove", { vm.add(1) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = (state.owned ?: 1) > 0)
                } else {
                    GradientButton("Count", { vm.add(1) }, Modifier.weight(1f).heightIn(min = 48.dp))
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = vm::skip, shape = RoundedCornerShape(Corners.Medium), contentPadding = tight, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Skip", maxLines = 1)
                }
                OutlinedButton(onClick = vm::openPrintings, shape = RoundedCornerShape(Corners.Medium), contentPadding = tight, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Printing", maxLines = 1)
                }
                // Copies come from the scan bar; +4 stays as a shortcut while it's at one.
                val qty = ScanBarPrefs.quantity
                if (qty == 1) OutlinedButton(onClick = { vm.add(4) }, shape = RoundedCornerShape(Corners.Medium), contentPadding = tight, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("+4", maxLines = 1)
                }
                // Auto-add already added it: the button adds one more copy, same finish and language.
                val justAdded = state.autoAdd && state.session.firstOrNull()?.card?.id == card.id
                if (justAdded) GradientButton("+1", vm::sameAgain, Modifier.weight(1f).heightIn(min = 48.dp), hero = true)
                else GradientButton(if (qty > 1) "Add $qty" else "Add", { vm.add(qty) }, Modifier.weight(1f).heightIn(min = 48.dp), hero = true)
            }
        }
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
            ArtIndexSection(vm)
            HorizontalDivider(color = Palette.Outline)
            SettingRow("Flashlight", "Helps in dim rooms. Turn it off if foils glare.", state.torch, vm::setTorch)
            HorizontalDivider(color = Palette.Outline)
            SettingRow("Volume buttons scan", "Up adds, down skips.", VolumeKeys.enabled, VolumeKeys::update)
            HorizontalDivider(color = Palette.Outline)
            Text(
                "Show hints",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
                    .clickable { vm.showSettings(false); ScanBarPrefs.updateHintsSeen(false) }
                    .padding(vertical = 14.dp),
            )
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
                Text("${state.sessionCount} cards${priceSuffix(state.sessionValue)}", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Gold)
            }
            if (state.sessionCount >= 2) {
                val n = minOf(5, state.sessionCount)
                TextButton(onClick = { vm.removeLast(n) }) { Text("Remove last $n", color = Palette.Danger) }
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
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
          if (state.session.isEmpty()) item(key = "empty") {
              EmptyState("Nothing scanned yet", Modifier.padding(vertical = 24.dp), fullScreen = true)
          }
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
                        .clickable(enabled = e.deckId == 0L) { vm.showList(false); onOpenCard(e.rowId) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardImage(e.card.smallImageUrl, Modifier.width(44.dp))
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
                            "${e.card.set.uppercase()} · #${e.card.collectorNumber} · ×${e.count}" + (e.lang?.let { " · ${it.uppercase()}" } ?: "") +
                                (if (e.boxName.isNotEmpty()) " · ${e.where}" else "") +
                                (e.note?.let { " · $it" } ?: "") + if (e.full) " · Full" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (AppSettings.showPrices) Text(formatUsd(e.value), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Accent)
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
            onLight = vm::onLight,
            wantsArt = vm::wantsArt,
            embed = vm::embedArt,
            scanLang = { com.mtgscanner.data.ScanBarPrefs.lang },
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
                            ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
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
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
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

/** Big label showing which box (and divider) the last card went into, so a pile can be filed as it's scanned. */
@Composable
private fun BoxCallout(state: ScanUiState, pulse: Int, modifier: Modifier) {
    val boxName = state.lastBoxName
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
                .clip(RoundedCornerShape(Corners.Medium))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (state.target == ScanTarget.REMOVE) "Take it from" else "Put it in",
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    boxName.orEmpty() + (state.lastSpot?.let { " → $it" } ?: ""),
                    color = com.mtgscanner.ui.components.boxColor(state.lastBoxColor),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (state.lastFull) {
                    Spacer(Modifier.width(8.dp))
                    FullTag()
                }
            }
            state.lastNote?.let { Text(it, color = Palette.Gold, style = MaterialTheme.typography.labelMedium) }
            // Rule preview: where else it would have fit.
            if (state.lastAlso.isNotEmpty()) {
                Text(
                    "Also fits: " + state.lastAlso.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Offer to keep a card the scanner can't find, to search by hand later. */
@Composable
private fun UnknownChip(text: String, vm: ScanViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(Corners.Medium))
            .background(Color.Black.copy(alpha = 0.8f))
            .border(1.dp, Palette.Gold.copy(alpha = 0.6f), RoundedCornerShape(Corners.Medium))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Can't find “$text”",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
        )
        TextButton(onClick = vm::saveUnknown) { Text("Save for later", color = Palette.Gold) }
        IconButton(onClick = vm::dismissUnknown, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Close, "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
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
        // Tap a card to see it big and pick its printing.
        var open by remember { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf<ReviewItem?>(null) }
        var printings by remember { mutableStateOf<List<ScryfallCard>?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            if (state.review.isEmpty()) item(key = "empty") { EmptyState("All checked", Modifier.padding(vertical = 24.dp)) }
            items(state.review, key = { it.card.id }) { item ->
                val c = item.card
                Column(Modifier.fillMaxWidth().clickable { open = if (open == c.id) null else c.id }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardImage(c.smallImageUrl, Modifier.width(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${c.set.uppercase()} · #${c.collectorNumber}" + (item.lang?.let { " · ${it.uppercase()}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            )
                        }
                        TextButton(onClick = { vm.dropFromReview(item) }) { Text("Drop", color = Palette.Muted) }
                        TextButton(onClick = { vm.addFromReview(item) }) { Text("Add") }
                    }
                    if (open == c.id) {
                        Spacer(Modifier.height(8.dp))
                        CardImage(c.imageUrl, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                picking = item
                                printings = null
                                scope.launch { printings = vm.printingsOf(c) }
                            },
                            shape = RoundedCornerShape(Corners.Medium),
                            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
                        ) { Text("Printing") }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
        picking?.let { item ->
            PrintingsSheet(
                printings, item.card.id,
                { card -> vm.reviewPrinting(item, card); open = card.id; picking = null },
                { picking = null },
            )
        }
    }
}
