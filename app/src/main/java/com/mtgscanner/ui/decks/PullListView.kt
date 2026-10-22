package com.mtgscanner.ui.decks

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.SectionHeader
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums

/** Pull list checkmarks per deck (deck id to [PullItem.key]s), kept in SharedPreferences. */
object PullChecksStore {
    private const val KEY = "checks"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(Long.serializer(), SetSerializer(String.serializer()))
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    val state: StateFlow<Map<Long, Set<String>>> = _state

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("deck_pull", Context.MODE_PRIVATE)
        prefs = p
        val saved = try { p.getString(KEY, null) } catch (e: Exception) { null }
        _state.value = saved?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyMap()
    }

    private fun edit(f: (Map<Long, Set<String>>) -> Map<Long, Set<String>>) {
        val s = _state.updateAndGet(f)
        prefs?.edit()?.putString(KEY, json.encodeToString(serializer, s))?.apply()
    }

    fun toggle(deckId: Long, key: String) = edit { m ->
        val now = m[deckId].orEmpty().let { if (key in it) it - key else it + key }
        if (now.isEmpty()) m - deckId else m + (deckId to now)
    }

    /** Puts a deck's checkmarks back, for Undo. */
    fun set(deckId: Long, keys: Set<String>) = edit { m -> if (keys.isEmpty()) m - deckId else m + (deckId to keys) }

    fun clear(deckId: Long) {
        if (_state.value.containsKey(deckId)) edit { it - deckId }
    }

    /** Drops checkmarks of decks that no longer exist. */
    fun prune(deckIds: Set<Long>) {
        if (_state.value.keys.all { it in deckIds }) return
        edit { m -> m.filterKeys { it in deckIds } }
    }
}

/** The cards to take out of your boxes for a deck, box by box, with checkmarks. */
@Composable
fun PullListView(
    list: PullList?,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    /** Files the checked cards into the deck's box. */
    onMoveChecked: (() -> Unit)? = null,
) {
    if (list == null) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        return
    }
    val total = list.toPull
    val done = list.groups.sumOf { g -> g.items.filter { it.key in checked }.sumOf { it.qty } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (total == 0) "Nothing to pull" else "$done / $total pulled",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (list.inDeckBox > 0) {
                        Text("${list.inDeckBox} already in deck box", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
                if (checked.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
            }
            if (onMoveChecked != null && done > 0) {
                OutlinedButton(
                    onClick = onMoveChecked,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(Corners.Medium),
                ) {
                    Icon(Icons.Filled.Style, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Move checked to deck box")
                }
            }
        }
        for (g in list.groups) {
            item(key = "b${g.boxId}") { SectionHeader("${g.boxName} (${g.items.sumOf { it.qty }})") }
            items(g.items, key = { "i" + it.key }) { p -> PullRow(p, p.key in checked) { onToggle(p.key) } }
        }
        if (list.missing.isNotEmpty()) {
            item(key = "missing") { SectionHeader("Missing (${list.missing.sumOf { it.qty }})") }
            items(list.missing, key = { "m" + it.name }) { m ->
                Text(
                    "${m.qty}× ${m.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PullRow(p: PullItem, ticked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(ticked, { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Palette.Accent))
        CardImage(p.imageUrl, Modifier.width(34.dp), zoom = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (ticked) TextDecoration.LineThrough else null,
                color = if (ticked) Palette.Muted else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val bits = buildList {
                add("${p.setCode.uppercase()} ${p.collectorNumber}".trim())
                if (p.foil) add("Foil")
                if (p.proxy) add("Proxy")
            }
            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
        }
        Text("×${p.qty}", style = MaterialTheme.typography.titleSmall.merge(TabularNums), fontWeight = FontWeight.SemiBold, color = if (ticked) Palette.Muted else Palette.Text)
    }
}
