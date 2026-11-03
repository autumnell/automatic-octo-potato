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
import androidx.compose.foundation.layout.wrapContentHeight
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
    Surface(
        Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(Corners.Medium)),
        shape = RoundedCornerShape(Corners.Medium),
        color = Palette.Surface.copy(alpha = 0.96f),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
            Row {
                CardImage(card.imageUrl, Modifier.width(84.dp).foilShimmer(foil))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    // Name, with its cost at the right as on the card.
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            card.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.padding(top = 3.dp)) { ManaCost(card.effectiveManaCost, size = 14.dp) }
                    }
                    Text(card.effectiveTypeLine, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SetIcon(card.set, size = 14.dp, tint = Palette.Muted)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${card.set.uppercase()} · #${card.collectorNumber}" + (state.currentLang?.let { " · ${it.uppercase()}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                            color = Palette.Muted,
                        )
                        Spacer(Modifier.width(6.dp))
                        RarityDot(card.rarity, 7.dp)
                        if (foil && !(card.hasFoil && card.hasNonFoil)) {
                            Spacer(Modifier.width(6.dp))
                            FoilTag()
                        }
                    }
                    // Set lock on, and this card is from another set (it said so in its footer).
                    val lock = com.mtgscanner.data.SortPrefs.setLock
                    if (lock != null && !card.set.equals(lock, ignoreCase = true)) {
                        Spacer(Modifier.height(4.dp))
                        Note("Not in ${lock.uppercase()}", Palette.Gold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (AppSettings.showPrices) {
                            Text(scryfallPrice(card, foil), style = MaterialTheme.typography.headlineSmall.merge(TabularNums), color = Palette.Text)
                            // Price check fetches today's price; say so once it's in.
                            if (state.target == ScanTarget.CHECK && state.priceFresh) {
                                Spacer(Modifier.width(6.dp))
                                Text("today", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        state.owned?.let { n -> OwnedPill(n) }
                    }
                }
            }
            // The smaller notes, full width under the card.
            val notes = buildList<@Composable () -> Unit> {
                // Where you keep it: "Rares ×3 · Bulk ×1".
                if (state.keptIn.isNotEmpty()) add { Note(state.keptIn.joinToString(" · ") { (box, n) -> "$box ×$n" }, Palette.Text) }
                if (!state.confident) add { Note("Not sure about this one. Check the printing.", Palette.Gold) }
                // Spot proxies: the card's footer, symbol and colours are far from its printing's own picture.
                if (state.lookOff) add { Note("Looks different from the real printing. Proxy or misprint?", Palette.Gold) }
                // Centering check: left/right · top/bottom. Borderless and full-art cards have no border to measure.
                state.centering?.takeIf { card.borderColor != "borderless" && !card.fullArt }?.let { c -> add { Note("Centering ${c.label}", Palette.Text) } }
                state.wish?.let { w ->
                    add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, null, tint = Palette.Success, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Note(if (w.wanted > 1) "On your wishlist (want ${w.wanted})" else "On your wishlist!", Palette.Success)
                        }
                    }
                }
            }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { notes.forEach { it() } }
            }
            // Foil for this card only, when it comes both ways.
            if (card.hasFoil && card.hasNonFoil && state.target != ScanTarget.CHECK) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FinishToggle(foil, onPick = { if (it != foil) vm.toggleCardFoil() })
                    // Glare changed between frames like a foil's does. Only a hint: tap to accept.
                    if (state.foilHint && !foil) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Looks foil?",
                            color = Palette.Gold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(Corners.Small))
                                .clickable(onClickLabel = "Mark foil", onClick = vm::acceptFoilHint)
                                .heightIn(min = 40.dp).padding(horizontal = 8.dp).wrapContentHeight(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Price check: look, then move on. Nothing is added.
            if (state.target == ScanTarget.CHECK) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Ghost("Printing", vm::openPrintings)
                GradientButton("Next", vm::skip, Modifier.weight(1f).heightIn(min = 48.dp))
            } else if (state.target == ScanTarget.REMOVE || state.target == ScanTarget.BOX) Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Ghost("Skip", vm::skip)
                Ghost("Printing", vm::openPrintings)
                if (state.target == ScanTarget.REMOVE) {
                    GradientButton("Remove", { vm.add(1) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = (state.owned ?: 1) > 0)
                } else {
                    GradientButton("Count", { vm.add(1) }, Modifier.weight(1f).heightIn(min = 48.dp))
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Ghost("Skip", vm::skip)
                Ghost("Printing", vm::openPrintings)
                // Copies come from Scan settings; +4 stays as a shortcut while it's at one.
                val qty = ScanBarPrefs.quantity
                if (qty == 1) Ghost("+4", { vm.add(4) })
                // Auto-add already added it: the button adds one more copy, same finish and language.
                val justAdded = state.autoAdd && state.session.firstOrNull()?.card?.id == card.id
                if (justAdded) GradientButton("+1 more", vm::sameAgain, Modifier.weight(1f).heightIn(min = 48.dp), hero = true)
                else GradientButton(if (qty > 1) "Add $qty" else "Add", { vm.add(qty) }, Modifier.weight(1f).heightIn(min = 48.dp), hero = true)
            }
        }
    }
}

/** A small line under the card: a note, in [color]. */
@Composable
private fun Note(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** "Own 3" in green, or "New" when you have none. */
@Composable
private fun OwnedPill(n: Int) {
    val own = n > 0
    Text(
        if (own) "Own $n" else "New",
        style = MaterialTheme.typography.labelMedium.merge(TabularNums),
        color = if (own) Palette.Success else Palette.Muted,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background((if (own) Palette.Success else Color.White).copy(alpha = if (own) 0.14f else 0.06f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A quiet secondary button: soft fill, plain text, 48dp tall. */
@Composable
private fun Ghost(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(Corners.Medium)).background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 1) }
}

/** Normal or Foil, side by side; the picked one is filled. */
@Composable
private fun FinishToggle(foil: Boolean, onPick: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false to "Normal", true to "Foil").forEach { (isFoil, label) ->
            val on = foil == isFoil
            Box(
                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50))
                    .then(if (on) Modifier.background(if (isFoil) Palette.Gold else Color.White.copy(alpha = 0.16f)) else Modifier)
                    .clickable(onClickLabel = if (isFoil) "Foil" else "Not foil") { onPick(isFoil) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = when { on && isFoil -> Color.Black; on -> Palette.Text; else -> Palette.Muted },
                )
            }
        }
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
