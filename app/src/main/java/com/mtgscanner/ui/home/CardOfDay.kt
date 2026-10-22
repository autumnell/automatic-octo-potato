package com.mtgscanner.ui.home

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.Placement
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.boxIcon
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.detail.sharedCard
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.PriceStyle

/** Today's card and where it lives: box name, plus slot or divider when known. */
data class DayCard(val card: CollectionCard, val where: String, val boxKind: String)

object CardOfDay {
    /** Today's card from [cards], kept in prefs so it stays the same all day. Null with no cards. */
    fun of(context: Context, cards: List<CollectionCard>, boxes: List<CardBox>): DayCard? {
        val pool = cards.filter { !it.proxy && it.imageUrl != null }.ifEmpty { cards }
        if (pool.isEmpty()) return null
        val today = java.time.LocalDate.now().toEpochDay()
        val p = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        val kept = if (p.contains("cotdId")) p.getLong("cotdDay", -1L) to p.getLong("cotdId", -1L) else null
        val id = CardOfDayMath.pick(pool.map { it.id }, today, kept) ?: return null
        if (kept == null || kept.first != today || kept.second != id) p.edit().putLong("cotdDay", today).putLong("cotdId", id).apply()
        val card = pool.first { it.id == id }
        val box = boxes.firstOrNull { it.id == card.boxId }
        val where = runCatching { Placement.whereIs(card, box, cards.filter { it.boxId == card.boxId }) }
            .getOrDefault(box?.name ?: "No box")
        return DayCard(card, where, box?.kind ?: "box")
    }
}

/** The card big on the left; name, set, price and its spot on the right. Tap opens it. */
@Composable
internal fun CardOfDaySection(day: DayCard, onOpenCard: (Long) -> Unit, shared: Boolean) {
    val c = day.card
    val open = { onOpenCard(c.id) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(
            c.imageUrl,
            Modifier.width(112.dp).then(if (shared) Modifier.sharedCard(c.id) else Modifier),
            zoom = true, onTap = open,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                c.setName + if (c.foil) " · Foil" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (AppSettings.showPrices && !c.proxy) {
                Spacer(Modifier.height(6.dp))
                Text(formatUsd(c.unitPrice()), style = PriceStyle.copy(fontSize = PriceStyle.fontSize * 1.3f), color = Palette.AccentText)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(boxIcon(day.boxKind), null, tint = Palette.Icon, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(day.where, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
