package com.mtgscanner.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.ScryfallRuling
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.OwnedBadge
import com.mtgscanner.ui.components.RarityDot
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.UnderlineTabs
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.components.AutoSizeText
import com.mtgscanner.ui.components.textInfo
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.onFill
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.PlaceholderRows
import com.mtgscanner.ui.components.ruleBelow

/** Something fetched on demand: [key] says what it was fetched for. */
data class Fetched<T>(
    val key: String? = null,
    val data: T? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Scryfall format keys with short names, in the order shown. */
private val FORMAT_NAMES = listOf(
    "standard" to "Standard", "pioneer" to "Pioneer",
    "modern" to "Modern", "legacy" to "Legacy",
    "vintage" to "Vintage", "commander" to "Commander",
    "pauper" to "Pauper", "paupercommander" to "Pauper Cmdr.",
    "oathbreaker" to "Oathbreaker", "duel" to "Duel Cmdr.",
    "predh" to "PreDH", "premodern" to "Premodern",
    "oldschool" to "Old School", "penny" to "Penny",
    "brawl" to "Brawl", "standardbrawl" to "Std. Brawl",
    "historic" to "Historic", "timeless" to "Timeless",
    "alchemy" to "Alchemy", "explorer" to "Explorer",
    "gladiator" to "Gladiator", "future" to "Future",
)

/** Every format in [legalities], known ones first in a fixed order, with short names. Pure, for tests. */
internal fun legalityRows(legalities: Map<String, String>): List<Pair<String, String>> {
    val known = FORMAT_NAMES.filter { it.first in legalities }.map { (k, label) -> label to legalities[k].orEmpty() }
    val names = FORMAT_NAMES.map { it.first }.toSet()
    val other = legalities.keys.filter { it !in names }.sorted()
        .map { k -> k.replaceFirstChar(Char::uppercase) to legalities[k].orEmpty() }
    return known + other
}

/** Printings grouped by set, keeping Scryfall's order (newest first), filtered by set name or code. */
internal fun groupPrintings(all: List<ScryfallCard>, filter: String): List<Pair<String, List<ScryfallCard>>> {
    val f = filter.trim()
    val shown = if (f.isEmpty()) all else all.filter {
        it.setName.contains(f, ignoreCase = true) || it.set.equals(f, ignoreCase = true) ||
            it.set.startsWith(f, ignoreCase = true)
    }
    val out = LinkedHashMap<String, MutableList<ScryfallCard>>()
    shown.forEach { out.getOrPut(it.set) { mutableListOf() } += it }
    return out.map { it.key to it.value }
}

/** The first [max] printings across groups, dropping groups left empty. Pure, for tests. */
internal fun limitGroups(groups: List<Pair<String, List<ScryfallCard>>>, max: Int): List<Pair<String, List<ScryfallCard>>> {
    var left = max
    val out = mutableListOf<Pair<String, List<ScryfallCard>>>()
    for ((set, cards) in groups) {
        if (left <= 0) break
        out += set to cards.take(left)
        left -= cards.size
    }
    return out
}

/** "2024-02-09" as "Feb 9, 2024". */
private fun rulingDate(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))
}.getOrDefault(day)

/** "Versions" (every printing with prices) and "Rules" (legality and rulings) behind a small tab switcher. */
@Composable
fun CardInfoTabs(c: CollectionCard, live: ScryfallCard?, vm: CardDetailViewModel) {
    var tab by rememberSaveable { mutableStateOf("rules") }
    UnderlineTabs(
        listOf("Rules", "Versions"), if (tab == "rules") 0 else 1,
        { tab = if (it == 0) "rules" else "versions" }, horizontalPadding = 0.dp,
    )
    Spacer(Modifier.height(10.dp))
    if (tab == "rules") RulesTab(c, live, vm) else VersionsTab(c, vm)
}

@Composable
private fun TabPanel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Small), color = Palette.Surface) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun RulesTab(c: CollectionCard, live: ScryfallCard?, vm: CardDetailViewModel) {
    val rulings by vm.rulings.collectAsStateWithLifecycle()
    LaunchedEffect(c.scryfallId) { vm.loadRulings() }
    val legal = live?.legalities?.takeIf { it.isNotEmpty() } ?: c.textInfo().legalities
    TabPanel {
        Text("Legality", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Spacer(Modifier.height(8.dp))
        val rows = legalityRows(legal)
        if (rows.isEmpty()) {
            Text("No data", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (label, status) -> LegalityCell(status, label, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        val offline by vm.liveFailed.collectAsStateWithLifecycle()
        if (live == null && offline) {
            Spacer(Modifier.height(4.dp))
            Text("Offline: main formats only", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
    Spacer(Modifier.height(10.dp))
    TabPanel {
        Text("Rulings", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Spacer(Modifier.height(8.dp))
        val list = rulings.data.orEmpty()
        when {
            rulings.loading || (rulings.data == null && !rulings.failed) -> Loading()
            rulings.failed -> Retry("Couldn't load") { vm.loadRulings(force = true) }
            list.isEmpty() -> Text("No rulings", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { list.forEach { RulingRow(it) } }
        }
    }
}

@Composable
private fun RulingRow(r: ScryfallRuling) {
    Column {
        Text(
            rulingDate(r.publishedAt) + if (r.source == "wotc") " · WotC" else "",
            style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
        )
        Spacer(Modifier.height(2.dp))
        Text(r.comment, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LegalityCell(status: String, format: String, modifier: Modifier) {
    val (label, bg, fg) = when (status) {
        "legal" -> Triple("LEGAL", Palette.Success, onFill(Palette.Success))
        "banned" -> Triple("BANNED", Palette.Danger, onFill(Palette.Danger))
        "restricted" -> Triple("RESTRICTED", Palette.Gold, onFill(Palette.Gold))
        else -> Triple("NOT LEGAL", Palette.SurfaceHigh, Palette.Muted)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(74.dp).clip(RoundedCornerShape(Corners.Small)).background(bg).padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Shrinks to fit the badge with a big font, instead of cutting "RESTRICTED" off.
            AutoSizeText(
                label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                color = fg, textAlign = TextAlign.Center, minScale = 0.6f,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(format, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Loading() {
    PlaceholderRows(3, Modifier.padding(vertical = 12.dp), rowHeight = 44.dp)
}

@Composable
private fun Retry(text: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

private const val PAGE = 40

@Composable
private fun VersionsTab(c: CollectionCard, vm: CardDetailViewModel) {
    val printings by vm.printings.collectAsStateWithLifecycle()
    val icons by vm.setIcons.collectAsStateWithLifecycle()
    val owned by vm.ownedPrintings.collectAsStateWithLifecycle()
    LaunchedEffect(c.oracleId ?: c.name) { vm.loadPrintings() }
    var filter by rememberSaveable { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val all = printings.data.orEmpty()
    TabPanel {
        when {
            printings.loading || (printings.data == null && !printings.failed) -> Loading()
            printings.failed -> Retry("Couldn't load") { vm.loadPrintings(force = true) }
            all.isEmpty() -> Text("No printings", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${all.size} printings · ${all.map { it.set }.distinct().size} sets",
                        style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f),
                    )
                    if (AppSettings.showPrices) {
                        Text("Normal", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                        Text("Foil", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                    }
                }
                if (all.size > 6) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it.take(40) },
                        placeholder = { Text("Filter sets") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Palette.Icon) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                val groups = remember(all, filter) { groupPrintings(all, filter) }
                if (groups.isEmpty()) {
                    Text("No match", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    val total = groups.sumOf { it.second.size }
                    val visible = if (showAll) groups else limitGroups(groups, PAGE)
                    visible.forEach { (set, cards) ->
                        SetHeader(set, cards.first().setName, icons[set.lowercase()])
                        cards.forEach { p ->
                            PrintingRow(
                                p,
                                current = p.id == c.scryfallId,
                                ownedCount = owned[p.id] ?: 0,
                                onSwitch = { vm.changePrinting(p) },
                            )
                        }
                    }
                    if (total > PAGE) {
                        TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Less" else "All $total") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetHeader(code: String, name: String, icon: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = icon ?: "https://svgs.scryfall.io/sets/${code.lowercase()}.svg",
            contentDescription = null,
            colorFilter = ColorFilter.tint(Palette.Text),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(code.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}

@Composable
private fun PrintingRow(p: ScryfallCard, current: Boolean, ownedCount: Int, onSwitch: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (current) Modifier.background(Palette.SurfaceHigh) else Modifier)
                .clickable { menu = true }
                .ruleBelow()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardImage(p.smallImageUrl, Modifier.width(34.dp), zoom = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RarityDot(p.rarity, 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("#${p.collectorNumber}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    if (current) {
                        Spacer(Modifier.width(6.dp))
                        Text("This", style = MaterialTheme.typography.labelSmall, color = Palette.Text)
                    }
                }
                val extra = listOfNotNull(p.lang.takeIf { !it.equals("en", true) }?.uppercase(), p.releasedAt?.take(4)) +
                    p.treatments.take(2).map { treatmentName(it) }
                if (extra.isNotEmpty()) {
                    Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (ownedCount > 0) {
                OwnedBadge(ownedCount)
                Spacer(Modifier.width(6.dp))
            }
            if (AppSettings.showPrices) {
                PriceCell(if (p.hasNonFoil) p.prices.usd else null)
                PriceCell(if (p.hasFoil) p.prices.usdFoil ?: p.prices.usdEtched else null, foil = true)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Open on Scryfall") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                onClick = {
                    menu = false
                    if (p.scryfallUri.isNotBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.scryfallUri))) }
                },
            )
            if (!current) DropdownMenuItem(
                text = { Text("Switch to this") },
                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                onClick = { menu = false; onSwitch() },
            )
        }
    }
}

@Composable
private fun PriceCell(price: String?, foil: Boolean = false) {
    // "$1,234.56" shrinks to fit the column instead of being cut off.
    AutoSizeText(
        if (price == null) "--" else formatUsd(price),
        style = MaterialTheme.typography.labelMedium,
        color = if (price == null) Palette.Muted else if (foil) Palette.GoldText else Palette.Text,
        textAlign = TextAlign.End,
        modifier = Modifier.width(56.dp),
    )
}

private fun treatmentName(t: String): String = when (t) {
    "borderless" -> "Borderless"
    "showcase" -> "Showcase"
    "extendedart" -> "Extended"
    "fullart" -> "Full art"
    "etched" -> "Etched"
    "promo" -> "Promo"
    "retro" -> "Retro"
    else -> t
}
