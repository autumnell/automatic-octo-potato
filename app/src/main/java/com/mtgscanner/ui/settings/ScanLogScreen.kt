package com.mtgscanner.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.MtgApp
import com.mtgscanner.scan.ScanLogEntry
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One formatter for every row (only used on the main thread). */
private val logTime = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

/** The hidden scan log: recent lookups with what was read and what matched, to share when scanning goes wrong. */
@Composable
fun ScanLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val log = remember(ctx) { (ctx.applicationContext as MtgApp).repository.scanLog }
    val entries by log.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { log.load() }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Scan log", style = MaterialTheme.typography.titleLarge)
                Text("Last ${ScanLogEntryLimit} lookups, kept on this phone", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            IconButton(onClick = {
                val text = log.asText(entries.orEmpty())
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Grimoire scan log")
                    .putExtra(Intent.EXTRA_TEXT, text)
                ctx.startActivity(Intent.createChooser(send, "Share scan log"))
            }, enabled = !entries.isNullOrEmpty()) { Icon(Icons.Outlined.Share, "Share") }
            IconButton(onClick = { scope.launch { log.clear() } }, enabled = !entries.isNullOrEmpty()) {
                Icon(Icons.Outlined.DeleteOutline, "Clear")
            }
        }
        val list = entries
        if (list != null && list.isEmpty()) {
            EmptyState("Nothing logged yet")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.orEmpty()) { e -> LogRow(e); HorizontalDivider(color = Palette.Rule) }
            item { Spacer(Modifier.navigationBarsPadding().padding(8.dp)) }
        }
    }
}

private const val ScanLogEntryLimit = com.mtgscanner.scan.ScanLog.MAX

@Composable
private fun LogRow(e: ScanLogEntry) {
    val color = when (e.outcome) {
        "matched" -> Palette.Success
        "unsure", "corrected" -> Palette.GoldText
        else -> Palette.Danger
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.outcome.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelLarge, color = color)
            Spacer(Modifier.width(8.dp))
            Text(logTime.format(Date(e.at)), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            Spacer(Modifier.weight(1f))
            if (e.ms > 0) Text("${e.ms} ms", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
        e.matchName?.let { Text("$it · ${e.matchSet?.uppercase()} #${e.matchNumber}", style = MaterialTheme.typography.titleSmall) }
        val read = buildString {
            if (e.read.isNotEmpty()) append("Read: ").append(e.read.joinToString(" | "))
            if (e.setCode != null || e.collectorNumber != null) append("  Footer: ${e.setCode ?: "?"} #${e.collectorNumber ?: "?"}")
            if (e.art) append("  Art compared")
        }
        if (read.isNotBlank()) Text(read, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, fontFamily = FontFamily.Monospace)
        e.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
    }
}
