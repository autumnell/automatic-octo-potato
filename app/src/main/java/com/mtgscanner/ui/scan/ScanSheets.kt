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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(state: ScanUiState, vm: ScanViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.showSettings(false) }, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = Space.s4).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Scan settings", style = MaterialTheme.typography.titleLarge)
            NextCardsSection(state, vm)
            HorizontalDivider(color = Palette.Rule)
            ScanSettingsEditor()
            HorizontalDivider(color = Palette.Rule)
            ArtIndexSection(vm)
            HorizontalDivider(color = Palette.Rule)
            SettingRow("Flashlight", "Helps in dim rooms. Turn it off if foils glare.", state.torch, vm::setTorch)
            HorizontalDivider(color = Palette.Rule)
            SettingRow("Volume buttons scan", "Up adds, down skips", VolumeKeys.enabled, VolumeKeys::update)
            HorizontalDivider(color = Palette.Rule)
            Text(
                "Show hints",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
                    .clickable { vm.showSettings(false); ScanBarPrefs.updateHintsSeen(false) }
                    .padding(vertical = Space.s4),
            )
            if (state.pendingCount > 0) {
                HorizontalDivider(color = Palette.Rule)
                Text(
                    "${state.pendingCount} cards saved while offline. They're added automatically when you're back online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Gold,
                    modifier = Modifier.padding(vertical = Space.s3),
                )
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = Space.s3), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Spacer(Modifier.width(Space.s3))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Text))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanListSheet(state: ScanUiState, vm: ScanViewModel, onOpenCard: (Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = { vm.showList(false) }, containerColor = Palette.Surface) {
        Row(Modifier.padding(horizontal = Space.s4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scan list", style = MaterialTheme.typography.titleLarge)
                Text("${state.sessionCount} cards${moneySuffix(state.sessionValue, state.sessionEur)}", style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = Palette.Muted)
            }
            if (state.sessionCount >= 2) {
                val n = minOf(5, state.sessionCount)
                TextButton(onClick = { vm.removeLast(n) }) { Text("Remove last $n", color = Palette.Danger) }
            }
            TextButton(onClick = vm::clearList) { Text("Clear list") }
        }
        Row(Modifier.padding(horizontal = Space.s4, vertical = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            SelectPill("Newest first", !state.listByBox, { vm.setListByBox(false) })
            SelectPill("By box", state.listByBox, { vm.setListByBox(true) })
        }
        Text(
            "Clearing keeps cards in your collection",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
        )
        // Regrouped only when the list or the grouping changes, not on every redraw.
        val groups = remember(state.session, state.listByBox) {
            if (state.listByBox) state.session.groupBy { it.boxName.ifEmpty { "Unsorted" } }.toList().sortedBy { it.first }
            else listOf("" to state.session)
        }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
          if (state.session.isEmpty()) item(key = "empty") {
              EmptyState("Nothing scanned yet", Modifier.padding(vertical = Space.s6), fullScreen = true)
          }
          groups.forEach { (boxName, entries) ->
            if (boxName.isNotEmpty()) item(key = "h$boxName") {
                Text(
                    "$boxName · ${entries.sumOf { it.count }} cards",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Text,
                    modifier = Modifier.padding(start = Space.s4, top = Space.s3, bottom = Space.s1),
                )
            }
            items(entries, key = { it.key }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = e.deckId == 0L) { vm.showList(false); onOpenCard(e.rowId) }
                        .padding(horizontal = Space.s4).ruleBelow().padding(vertical = Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardImage(e.card.smallImageUrl, Modifier.width(44.dp))
                    Spacer(Modifier.width(Space.s3))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (e.foil) {
                                Spacer(Modifier.width(Space.s2))
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
                    if (AppSettings.showPrices) Text(entryMoney(e), style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Palette.Text)
                    IconButton(onClick = { vm.remove(e) }) { Icon(Icons.Outlined.DeleteOutline, "Remove from collection", tint = Palette.Danger) }
                }
            }
          }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** Cards auto-add wasn't sure about, to confirm or drop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewSheet(state: ScanUiState, vm: ScanViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.showReview(false) }, containerColor = Palette.Surface) {
        Text("Check these", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.s4))
        Text(
            "Auto-add wasn't sure about these",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
        )
        // Tap a card to see it big and pick its printing.
        var open by remember { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf<ReviewItem?>(null) }
        var printings by remember { mutableStateOf<List<ScryfallCard>?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            if (state.review.isEmpty()) item(key = "empty") { EmptyState("All checked", Modifier.padding(vertical = Space.s6)) }
            items(state.review, key = { it.card.id }) { item ->
                val c = item.card
                Column(Modifier.fillMaxWidth().clickable { open = if (open == c.id) null else c.id }.padding(horizontal = Space.s4, vertical = Space.s2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardImage(c.smallImageUrl, Modifier.width(52.dp))
                        // The card as the camera saw it, next to the guess.
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        com.mtgscanner.scan.ScanPhotos.file(ctx, item.photo)?.let { f ->
                            Spacer(Modifier.width(Space.s2))
                            coil.compose.AsyncImage(f, "Your scan", Modifier.width(52.dp).clip(RoundedCornerShape(Corners.Small)))
                        }
                        Spacer(Modifier.width(Space.s3))
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
                        Spacer(Modifier.height(Space.s2))
                        CardImage(c.imageUrl, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(Space.s2))
                        com.mtgscanner.ui.components.QuietButton(
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
