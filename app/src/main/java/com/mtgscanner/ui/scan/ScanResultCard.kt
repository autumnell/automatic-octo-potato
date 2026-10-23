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
internal fun ResultCard(card: ScryfallCard, state: ScanUiState, vm: ScanViewModel) {
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
                        if (AppSettings.showPrices) Text(scryfallPrice(card, foil), style = MaterialTheme.typography.titleLarge.merge(TabularNums), color = Palette.Text)
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
internal fun OfflineCard(name: String, vm: ScanViewModel) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface.copy(alpha = 0.97f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
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
                GradientButton("Save for later", { vm.saveOffline() }, Modifier.weight(1f).heightIn(min = 48.dp), icon = Icons.Outlined.Schedule)
            }
        }
    }
}

/** Offer to keep a card the scanner can't find, to search by hand later. */
@Composable
internal fun UnknownChip(text: String, vm: ScanViewModel, modifier: Modifier = Modifier) {
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
            Icon(Icons.Outlined.Close, "Dismiss", tint = Palette.OnDarkMuted, modifier = Modifier.size(18.dp))
        }
    }
}
