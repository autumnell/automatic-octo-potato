package com.mtgscanner.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mtgscanner.data.ScanBarPrefs
import com.mtgscanner.data.ScryfallSet
import com.mtgscanner.data.SortPrefs
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.detail.sharedCard
import com.mtgscanner.ui.components.scryfallPriceShort
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/** Scryfall's set symbol for a set code. */
fun setIconUrl(code: String): String = "https://svgs.scryfall.io/sets/${code.lowercase()}.svg"

@Composable
fun SetIcon(code: String, modifier: Modifier = Modifier, size: Dp = 18.dp, tint: Color = Color.White, url: String? = null) {
    AsyncImage(
        model = url ?: setIconUrl(code),
        contentDescription = code.uppercase(),
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

/**
 * Finish, set, language, condition and copies for the next cards scanned, in Scan settings.
 * Foil is the scan setting, the set is the set lock, the rest are in [ScanBarPrefs].
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NextCardsSection(state: ScanUiState, vm: ScanViewModel) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Next cards", style = MaterialTheme.typography.titleSmall)
        NextRow("Finish") {
            SelectPill("Normal", !state.foil, onClick = { vm.setFoil(false) })
            SelectPill("Foil", state.foil, onClick = { vm.setFoil(true) })
        }
        val lock = SortPrefs.setLock
        NextRow("Set") {
            SelectPill("Any", lock == null, onClick = { vm.setLock(null) })
            SelectPill(
                lock?.uppercase() ?: "Pick a set",
                lock != null,
                onClick = { vm.showSettings(false); vm.showSetLock(true) },
                leading = lock?.let { { SetIcon(it, size = 16.dp, tint = Palette.Text) } },
            )
        }
        NextRow("Language") {
            ScanBarPrefs.LANGS.forEach { (code, label) ->
                SelectPill(label, ScanBarPrefs.lang == code, onClick = { ScanBarPrefs.updateLang(code) })
            }
        }
        NextRow("Condition") {
            ScanBarPrefs.CONDITIONS.forEach { c ->
                SelectPill(c, ScanBarPrefs.condition == c, onClick = { ScanBarPrefs.updateCondition(c) })
            }
        }
        NextRow("Copies") {
            ScanBarPrefs.QUANTITIES.forEach { n ->
                SelectPill("+$n", ScanBarPrefs.quantity == n, onClick = { ScanBarPrefs.updateQuantity(n) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NextRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/**
 * The next-card choices that differ from the usual (foil, a set, a language, a condition, more than one copy),
 * as one small tag over the camera so they are never forgotten. Tap to change them. Hidden when all are usual.
 */
@Composable
fun NextCardsChip(state: ScanUiState, vm: ScanViewModel, modifier: Modifier = Modifier) {
    val lock = SortPrefs.setLock
    val parts = buildList {
        if (state.foil) add("Foil")
        lock?.let { add(it.uppercase()) }
        if (ScanBarPrefs.lang != "en") add(ScanBarPrefs.langLabel(ScanBarPrefs.lang))
        if (ScanBarPrefs.condition != "NM") add(ScanBarPrefs.condition)
        if (ScanBarPrefs.quantity > 1) add("+${ScanBarPrefs.quantity}")
    }
    if (parts.isEmpty()) return
    Row(
        modifier
            .glass(RoundedCornerShape(Corners.Medium))
            .clickable(onClickLabel = "Change next cards") { vm.showSettings(true) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lock != null) {
            SetIcon(lock, size = 14.dp, tint = Palette.Gold)
            Spacer(Modifier.width(6.dp))
        }
        Text(parts.joinToString(" · "), color = Palette.Gold, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** The see-through dark panel used for everything floating over the camera. */
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.clip(shape).background(Color.Black.copy(alpha = 0.58f)).border(1.dp, Color.White.copy(alpha = 0.12f), shape)

/** The last card scanned: image, set, name and price. Tap to open it. [onSameAgain] shows "+1" for another copy. */
@Composable
fun LastCardBar(
    state: ScanUiState, onOpenCard: (Long) -> Unit, onOpenList: () -> Unit, modifier: Modifier = Modifier,
    onSameAgain: (() -> Unit)? = null,
) {
    val e = state.session.firstOrNull()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .glass(RoundedCornerShape(Corners.Medium))
            .clickable(enabled = e != null) { if (e != null && e.deckId == 0L && e.rowId != 0L) onOpenCard(e.rowId) else onOpenList() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e == null) {
            Text(
                "Scanned cards show up here",
                color = Palette.OnDarkMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            return@Row
        }
        // Grows into the card page when tapped.
        CardImage(e.card.smallImageUrl, Modifier.height(40.dp).sharedCard(if (e.deckId == 0L) e.rowId else 0L))
        Spacer(Modifier.width(10.dp))
        SetIcon(e.card.set, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            e.card.name + if (e.count > 1) " ×${e.count}" else "",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (e.foil) {
            Spacer(Modifier.width(6.dp))
            FoilTag()
        }
        if (AppSettings.showPrices) {
            Spacer(Modifier.width(8.dp))
            Text(scryfallPriceShort(e.card, e.foil), color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Open card", tint = Palette.OnDarkMuted)
        if (onSameAgain != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(Corners.Small))
                    .background(Palette.Gold)
                    .clickable(onClickLabel = "Add another copy", onClick = onSameAgain)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("+1", color = Color.Black, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Pick a set to lock the scanner to, or Auto for any set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPickSheet(vm: ScanViewModel) {
    val sets by vm.sets.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val lock = SortPrefs.setLock
    ModalBottomSheet(onDismissRequest = { vm.showSetLock(false) }, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Set", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name or code") },
            )
            val q = query.trim().lowercase()
            val shown = if (q.isEmpty()) sets else sets.filter { it.code == q || it.name.lowercase().contains(q) }
            // A typed code works even when the list can't load (offline).
            val typed = q.takeIf { it.length in 2..6 && it.all { c -> c.isLetterOrDigit() } && shown.none { s -> s.code == it } }
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                item(key = "auto") {
                    SetRowItem(
                        title = "Auto", sub = "Any set", selected = lock == null,
                        icon = { Icon(Icons.Outlined.LockOpen, null, tint = if (lock == null) Palette.AccentText else Palette.Icon, modifier = Modifier.size(22.dp)) },
                        onClick = { vm.setLock(null) },
                    )
                }
                if (typed != null) item(key = "typed") {
                    SetRowItem(
                        title = "Lock to ${typed.uppercase()}", sub = "Set code", selected = false,
                        icon = { Icon(Icons.Outlined.Lock, null, tint = Palette.Icon, modifier = Modifier.size(22.dp)) },
                        onClick = { vm.setLock(typed) },
                    )
                }
                items(shown, key = { it.code }) { s -> SetRow(s, s.code == lock) { vm.setLock(s.code) } }
                if (sets.isEmpty() && typed == null) item(key = "empty") {
                    EmptyState("No sets. Type a code.", Modifier.fillMaxWidth().padding(vertical = 24.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SetRow(s: ScryfallSet, selected: Boolean, onClick: () -> Unit) {
    SetRowItem(
        title = s.name, sub = s.code.uppercase() + (s.releasedAt?.take(4)?.let { " · $it" } ?: ""), selected = selected,
        icon = { SetIcon(s.code, size = 22.dp, tint = if (selected) Palette.AccentText else Palette.Text, url = s.iconSvgUri) },
        onClick = onClick,
    )
}

@Composable
private fun SetRowItem(title: String, sub: String, selected: Boolean, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Corners.Small)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) Palette.AccentText else Palette.Text,
            )
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}
