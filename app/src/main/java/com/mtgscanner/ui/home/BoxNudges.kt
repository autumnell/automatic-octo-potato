package com.mtgscanner.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import com.mtgscanner.ui.scan.SetIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.BoxPrefs
import com.mtgscanner.data.BoxReminders
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.NewSets
import com.mtgscanner.data.SetRelease
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.collection.BoxLabels
import com.mtgscanner.ui.collection.SetLaunch
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.mtgscanner.ui.theme.Corners

/** "Rares: not checked in 3 months" with a button that starts a Box check of it. One box at a time. */
@Composable
fun BoxCheckNudge(onCheck: (Long) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember { BoxPrefs.load(ctx) }
    val repo = (ctx.applicationContext as MtgApp).repository
    val boxes by remember { repo.observeBoxSummaries() }.collectAsState(initial = emptyList())
    val live = boxes.filter { it.kind != "deck" && it.cards > 0 }
    val ids = live.map { it.id }
    // A box never checked counts from the day the app first saw it.
    LaunchedEffect(ids) { if (ids.isNotEmpty()) BoxPrefs.noteBoxes(ctx, ids) }
    val months = BoxPrefs.reminderMonths
    val checks = BoxPrefs.checks
    val snoozed = BoxPrefs.snoozedUntil
    val now = remember(checks, snoozed, months) { System.currentTimeMillis() }
    if (months == 0 || now < snoozed) return
    val due = BoxReminders.due(ids, checks, now, months).firstOrNull() ?: return
    val box = live.firstOrNull { it.id == due.first } ?: return

    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.DoneAll, null, tint = Palette.Icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            BoxReminders.label(box.name, due.second.coerceAtLeast(months)),
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { BoxPrefs.snooze(ctx) }) { Text("Later", color = Palette.Muted) }
        TextButton(onClick = { onCheck(box.id) }) { Text("Check") }
    }
}

/**
 * New set setup: when a main set has just come out, offers once to make a box or print a divider for it, and
 * opens what's missing from it. Scryfall's sets are looked at once a day.
 */
@Composable
fun NewSetOffer(onOpenSet: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember { BoxPrefs.load(ctx) }
    val repo = (ctx.applicationContext as MtgApp).repository
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val today = LocalDate.now()
        if (BoxPrefs.setsCheckedDay == today.toString()) return@LaunchedEffect
        val sets = runCatching { repo.sets() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val pick = NewSets.pick(sets.map { SetRelease(it.code, it.name, it.setType, it.releasedAt) }, today, BoxPrefs.offeredSets)
        BoxPrefs.setPendingSet(ctx, pick, today.toString())
    }
    // Stays up after the first tap (so a box and the checklist can both be had); it's marked as offered at once.
    var shown by remember { mutableStateOf<SetRelease?>(null) }
    var closed by remember { mutableStateOf(false) }
    // The card stays up after "Make a box": a second tap mustn't make the same box again.
    var made by remember { mutableStateOf(false) }
    val divider = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val s = shown
        if (uri != null && s != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { BoxLabels.writeTabs(listOf(NewSets.dividerLabel(s)), it) } }.isSuccess
            }
            Undo.offer(if (ok) "Divider saved" else "Couldn't save the divider")
        }
    }
    val set = (BoxPrefs.pendingSet ?: shown)?.takeIf { !closed } ?: return

    fun handled() {
        shown = set
        BoxPrefs.markOffered(ctx, set.code)
    }

    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(Corners.Medium),
        color = Palette.Surface,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetIcon(set.code, size = 20.dp, tint = Palette.Icon)
                Spacer(Modifier.width(10.dp))
                Text(
                    "New set: ${set.name}",
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { closed = true; BoxPrefs.markOffered(ctx, set.code) }) {
                    Icon(Icons.Outlined.Close, "Not now", tint = Palette.Icon)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        if (!made) {
                            made = true
                            handled()
                            scope.launch {
                                val id = repo.createBox(set.name, "box", BoxRules(sets = setOf(set.code.uppercase())))
                                Undo.offer("Made ${set.name}") { repo.deleteBox(id, CardBox.AUTO_SORT); made = false }
                            }
                        }
                    },
                    enabled = !made,
                ) { Text(if (made) "Box made" else "Make a box") }
                TextButton(onClick = { handled(); divider.launch("divider-${set.code.lowercase()}.pdf") }) { Text("Divider") }
                TextButton(onClick = {
                    handled()
                    SetLaunch.missing = true
                    onOpenSet(set.code.lowercase())
                }) { Text("Missing") }
            }
        }
    }
}
