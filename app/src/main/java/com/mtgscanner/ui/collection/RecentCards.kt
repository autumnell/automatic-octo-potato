package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.theme.Palette

/** The last card pages opened, newest first, for the strip at the top of Browse. Saved on the phone. */
object RecentCards {
    private var prefs: SharedPreferences? = null

    var ids by mutableStateOf<List<Long>>(emptyList())
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        prefs = p
        ids = RecentMath.decode(p.getString("recentCards", null))
    }

    /** Called when a card page opens. */
    fun opened(context: Context, id: Long) {
        load(context)
        val next = RecentMath.push(ids, id)
        if (next == ids) return
        ids = next
        prefs?.edit()?.putString("recentCards", RecentMath.encode(next))?.apply()
    }
}

/** List math for [RecentCards], kept apart so it can be tested. */
object RecentMath {
    const val MAX = 10

    /** [id] moved to the front, no repeats, at most [max]. */
    fun push(list: List<Long>, id: Long, max: Int = MAX): List<Long> =
        if (id <= 0) list else (listOf(id) + list.filter { it != id }).take(max)

    fun encode(list: List<Long>): String = list.joinToString(",")

    fun decode(s: String?): List<Long> =
        s.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }.distinct().take(MAX)

    /** The cards found for [ids], in [ids] order; ids of cards no longer in the collection drop out. */
    fun <T> inOrder(ids: List<Long>, found: List<T>, idOf: (T) -> Long): List<T> {
        val byId = found.associateBy(idOf)
        return ids.mapNotNull { byId[it] }
    }
}

/** A short row of the cards opened last. Draws nothing when there are none. */
@Composable
fun RecentCardsStrip(refreshKey: Any?, onOpen: (CollectionCard) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember { RecentCards.load(ctx) }
    val ids = RecentCards.ids
    val repo = (ctx.applicationContext as MtgApp).repository
    var cards by remember { mutableStateOf<List<CollectionCard>>(emptyList()) }
    LaunchedEffect(ids, refreshKey) {
        cards = if (ids.isEmpty()) emptyList()
        else RecentMath.inOrder(ids, runCatching { repo.dao.byIds(ids) }.getOrDefault(emptyList())) { it.id }
    }
    if (cards.isEmpty()) return
    Column(modifier) {
        Text(
            "Recently opened", style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            modifier = Modifier.padding(start = Space.s4, top = Space.s1, bottom = Space.s1),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            items(cards, key = { it.id }) { c ->
                CardImage(
                    c.smallImageUrl ?: c.imageUrl,
                    Modifier.width(52.dp).clip(CardShape).clickable { onOpen(c) },
                )
            }
        }
    }
}
