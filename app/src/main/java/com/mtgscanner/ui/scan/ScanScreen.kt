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
    val batchAsk by com.mtgscanner.util.OpenRequests.batch.collectAsStateWithLifecycle()
    LaunchedEffect(batchAsk) {
        if (batchAsk) { com.mtgscanner.util.OpenRequests.batch.value = false; if (vm.state.value.batch == null) vm.setBatch(true) }
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
    // The phone doesn't dim or lock in the middle of a pile.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Volume up adds the card showing (or moves on, in a price check), volume down skips it.
    // Only while a card is showing and no sheet is open; otherwise the buttons change the volume.
    val latest by androidx.compose.runtime.rememberUpdatedState(state)
    DisposableEffect(vm) {
        VolumeKeys.load(ctx)
        VolumeKeys.handler = handler@{ up ->
            val st = latest
            val card = st.current ?: return@handler false
            if (st.pageMode || st.anySheetOpen) return@handler false
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
    // Voice commands: "foil", "undo", "set aside", "four"… while the camera is reading.
    if (hasPermission && state.settings.voice) {
        ScanVoiceListener(
            active = !state.paused && !state.anySheetOpen && !state.pageMode,
            onCommand = { c -> if (vm.onVoice(c) && state.settings.vibrate) Haptics.setAside(view) },
            onUnavailable = { vm.updateSettings { it.copy(voice = false) } },
        )
    }
    // Face down on the table: pause, and pick back up where it left off when lifted.
    var faceDown by remember { mutableStateOf(false) }
    if (hasPermission) FaceDownPause(state.paused, onPause = vm::togglePause, onResume = vm::togglePause, onFaceDown = { faceDown = it })
    val hintTargets = remember { mutableStateMapOf<String, Rect>() }
    // Lines added since the screen opened slide into the pile; older ones are already there.
    val startKey = remember { state.session.maxOfOrNull { it.key } ?: 0L }
    var flight by remember { mutableStateOf<Flight?>(null) }
    // Where the frame sits on the card, so an added card flies off from there.
    val tracked = remember { TrackedCard() }
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
                // From the card where the frame found it (at its tilt), else from the guide.
                val pose = tracked.pose()
                val from = pose?.first ?: hintTargets["frame"]
                val to = hintTargets["fan"] ?: hintTargets["list"]
                if (from != null && to != null) {
                    flight = Flight(
                        id = addTick,
                        imageUrl = e.card.imageUrl,
                        from = from,
                        angle = pose?.second ?: 0f,
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
    LaunchedEffect(state.batchPulse) {
        // Batch: the usual add sound and buzz, so you know to flip the next card.
        if (vm.freshBatch(state.batchPulse)) {
            if (state.settings.vibrate) Haptics.added(view)
            ScanFeedback.play(ctx, state.settings.sound)
        }
    }
    LaunchedEffect(state.skipPulse) {
        // Stack mode: a soft tick so you know a skipped card was seen.
        if (vm.freshSkip(state.skipPulse) && state.stack && state.settings.sound != com.mtgscanner.scan.ScanSound.NONE) ScanFeedback.tick(ctx)
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalHintTargets provides hintTargets) {
            // The camera fills the whole screen; the header and controls float over it on soft shades.
            if (hasPermission && !needsBox) Box(Modifier.fillMaxSize().background(Color.Black)) {
                CameraPreview(vm = vm, torch = state.torch)
                if (state.pageMode) PageGuide(Modifier.align(Alignment.Center))
                else TrackingFrame(
                    found = state.current != null, flash = if (state.settings.flash) addTick else 0, paused = state.paused,
                    tracked = tracked,
                    guide = Modifier.hintTarget("frame"),
                    // Flashes in the color of the box the card goes to, so a pile can be sorted by color.
                    flashColor = if (state.lastBoxName != null && state.lastBoxColor != 0) com.mtgscanner.ui.components.boxColor(state.lastBoxColor)
                    else Palette.Accent,
                )
            } else Box(Modifier.fillMaxSize().background(Palette.Background))
            Column(Modifier.fillMaxSize()) {
                ScanHeader(state, boxes, vm)

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (needsBox) {
                        EmptyState(
                            "Create a box first",
                            Modifier.align(Alignment.Center),
                            action = "Create a box",
                            onAction = { vm.showNewBox(true) },
                        )
                    } else if (!hasPermission) {
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
                        Column(
                            Modifier.align(Alignment.TopStart).padding(top = 76.dp, start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NextCardsChip(state, vm)
                            PackChip(state, vm)
                            BatchChip(state, vm)
                            SortChip(state, vm)
                            BoxCheckChip(state, vm)
                            RemoveChip(state, vm)
                        }
                        BoxCallout(state, addTick, Modifier.align(Alignment.Center))
                        if (faceDown && state.paused) Text(
                            "Paused",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.Center).glass(RoundedCornerShape(Corners.Medium))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        Column(
                            Modifier.align(Alignment.TopEnd).padding(top = 68.dp, end = 12.dp),
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
                                    modifier = Modifier.glass(RoundedCornerShape(Corners.Medium))
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
                                    modifier = Modifier.glass(RoundedCornerShape(Corners.Medium))
                                        .clickable(onClickLabel = "Show unknown scans") { vm.showUnknown(true) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }

                    // Settings and modes float over the top of the camera.
                    if (!needsBox) Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RoundIconButton(Icons.Outlined.Settings, "Scan settings", { vm.showSettings(true) }, modifier = Modifier.hintTarget("settings"))
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
                            if (state.current == null && state.offline == null && state.session.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                // Stack mode drops a copy that looks just like the last one; "+1" adds it.
                                val sameAgain = if ((state.stack || state.autoAdd) && state.target != ScanTarget.CHECK && state.session.isNotEmpty()) vm::sameAgain else null
                                LastCardBar(state, onOpenCard, onOpenList = { vm.showList(true) }, Modifier.hintTarget("last"), onSameAgain = sameAgain)
                            }
                        }
                    }
                }

                if (!needsBox) ControlsRow(state, vm)
            }
        }
        // Drawn over everything on the screen, never catching touches.
        AddFlightOverlay(flight)
        if (!ScanBarPrefs.hintsSeen && loadedBoxes != null && !needsBox && !state.anySheetOpen) {
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
    state.summary?.let { ScanSummarySheet(it, vm::dismissSummary) }
    if (state.showNewBox) {
        BoxEditorSheet(
            title = if (boxes.isEmpty()) "Your first box" else "New box",
            onSave = vm::createBox,
            onDismiss = { vm.showNewBox(false) },
        )
    }
}
