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
internal fun ScanHeader(state: ScanUiState, boxes: List<CardBox>, vm: ScanViewModel) {
    var boxMenu by remember { mutableStateOf(false) }
    val box = boxes.firstOrNull { it.id == state.boxId }
    Row(
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.45f), Color.Transparent)))
            .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Scan", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(Corners.Small)).clickable { boxMenu = true }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val auto = state.boxId == CardBox.AUTO_SORT || box == null
                    Icon(if (auto) Icons.AutoMirrored.Outlined.Sort else boxIcon(box!!.kind), null, tint = Palette.OnDarkMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    val where = when (state.target) {
                        ScanTarget.CHECK -> "Just checking · not adding"
                        ScanTarget.DECK -> "Deck: ${state.deck?.name.orEmpty()}"
                        ScanTarget.WISHLIST -> "Adding to wishlist"
                        ScanTarget.REMOVE -> "Removing from collection"
                        ScanTarget.BOX -> "Checking ${state.boxCheck?.boxName.orEmpty()}"
                        ScanTarget.COLLECTION -> if (auto) "Auto-sorting into boxes" else "Adding to ${box!!.name}"
                    }
                    Text(
                        (if (state.stack) "Stack · " else "") + where,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.target != ScanTarget.COLLECTION || state.stack) Palette.Gold else Palette.OnDarkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ExpandMore, null, tint = Palette.OnDarkMuted, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = boxMenu, onDismissRequest = { boxMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Auto-sort")
                                Text("Each card goes to the box whose rules fit", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null) },
                        onClick = { vm.setBox(CardBox.AUTO_SORT); boxMenu = false },
                    )
                    HorizontalDivider(color = Palette.Rule)
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
                    HorizontalDivider(color = Palette.Rule)
                    DropdownMenuItem(
                        text = { Text("New box…") },
                        leadingIcon = { Icon(Icons.Outlined.Add, null, tint = Palette.Icon) },
                        onClick = { boxMenu = false; vm.showNewBox(true) },
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val count = state.sessionCount
        Text(
            (if (count == 1) "1 card" else "$count cards") + moneySuffix(state.sessionValue, state.sessionEur),
            style = MaterialTheme.typography.titleSmall.merge(TabularNums),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Flashlight, pause and the scan list, within thumb reach. */
@Composable
internal fun ControlsRow(state: ScanUiState, vm: ScanViewModel) {
    Row(
        // Extra room at the bottom for the raised Scan tab under it.
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.8f))))
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundIconButton(
            icon = if (state.torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
            label = "Flashlight",
            onClick = { vm.setTorch(!state.torch) },
            tint = if (state.torch) Palette.Gold else Color.White,
            modifier = Modifier.hintTarget("flash"),
        )
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .clickable { vm.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(if (state.paused) Palette.Accent else Color.White), contentAlignment = Alignment.Center) {
                Icon(
                    if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (state.paused) "Resume scanning" else "Pause scanning",
                    tint = if (state.paused) Color.White else Color.Black,
                )
            }
        }
        Box(Modifier.hintTarget("list")) {
            RoundIconButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Scan list", { vm.showList(true) })
            val count = state.sessionCount
            if (count > 0) Text(
                if (count > 99) "99+" else "$count",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(Corners.Small)).background(Palette.Gold)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
internal fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White, modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(44.dp).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
internal fun StatusPill(state: ScanUiState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(RoundedCornerShape(Corners.Medium))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.lookingUp) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.Accent)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.status, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (!state.paused) {
                // The picture's likely card reads better than half-read text; either shows faintly until it settles.
                val looks = state.looksLike
                if (looks != null) {
                    Text("Looks like $looks", color = Palette.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else state.reading?.let {
                    Text("Reading “$it”", color = Palette.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.glare) {
                    Text("Tilt to reduce glare", color = Palette.Gold, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
            CardListProgress()
        }
    }
}

/** "Getting card list 42%" while the card list downloads, so a slower first scan has a reason. */
@Composable
private fun CardListProgress() {
    val repo = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.mtgscanner.MtgApp).repository
    val st by repo.localCards.status.collectAsStateWithLifecycle()
    val p = st.progress ?: return
    Text("Getting card list ${(p * 100).toInt()}%", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
}

/** Big label showing which box (and divider) the last card went into, so a pile can be filed as it's scanned. */
@Composable
internal fun BoxCallout(state: ScanUiState, pulse: Int, modifier: Modifier) {
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
                color = Palette.OnDarkMuted, style = MaterialTheme.typography.labelMedium,
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
            // Where it goes among the box's cards, so sorting it in needs no searching.
            if (state.target != ScanTarget.REMOVE) state.lastNear?.let {
                Text(it.replaceFirstChar { c -> c.uppercase() }, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            state.lastNote?.let { Text(it, color = Palette.Gold, style = MaterialTheme.typography.labelMedium) }
            // Rule preview: where else it would have fit.
            if (state.lastAlso.isNotEmpty()) {
                Text(
                    "Also fits: " + state.lastAlso.joinToString(" · "),
                    color = Palette.OnDarkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
