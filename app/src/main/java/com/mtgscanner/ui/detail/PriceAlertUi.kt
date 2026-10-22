package com.mtgscanner.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.home.alertLines
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.work.CardAlertMath
import com.mtgscanner.work.CardAlertStore
import com.mtgscanner.work.CardPriceAlert
import com.mtgscanner.work.PriceAlerts

/** Checks the two amounts: both optional, at least one set, and "under" below "over". Null when fine. */
internal fun alertProblem(above: Pair<Boolean, Double?>, below: Pair<Boolean, Double?>): String? = when {
    !above.first || !below.first -> "Not a number"
    above.second == null && below.second == null -> null
    above.second != null && below.second != null && below.second!! >= above.second!! -> "Under must be less than over"
    else -> null
}

/** "Price alert" row for the price panel: shows the alert set for this printing and finish, tap to change. */
@Composable
internal fun PriceAlertRow(c: CollectionCard) {
    val ctx = LocalContext.current
    remember { CardAlertStore.load(ctx) }
    val alerts by CardAlertStore.alerts.collectAsStateWithLifecycle()
    val key = CardPriceAlert.key(c.scryfallId, c.foil)
    val alert = alerts.firstOrNull { it.key == key }
    var editing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    TextButton(onClick = { editing = true }) {
        Icon(
            if (alert != null) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone, null,
            Modifier.size(18.dp), tint = if (alert != null) Palette.AccentText else Palette.Icon,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (alert != null) alertLines(alert) else "Price alert",
            color = if (alert != null) Palette.AccentText else Palette.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }

    if (editing) {
        PriceAlertDialog(
            c = c,
            existing = alert,
            onSave = { above, below ->
                editing = false
                val base = CardPriceAlert(
                    scryfallId = c.scryfallId, foil = c.foil, name = c.name, setCode = c.setCode,
                    above = above, below = below, createdAt = System.currentTimeMillis(),
                )
                CardAlertStore.put(ctx, CardAlertMath.create(base, c.nearMintPrice()))
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) runCatching { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            },
            onDelete = {
                editing = false
                alert?.let { gone ->
                    CardAlertStore.remove(ctx, gone.key)
                    Undo.offer("Alert removed") { CardAlertStore.put(ctx, gone) }
                }
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun PriceAlertDialog(
    c: CollectionCard,
    existing: CardPriceAlert?,
    onSave: (Double?, Double?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    fun fmt(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: ""
    var overText by remember { mutableStateOf(fmt(existing?.above)) }
    var underText by remember { mutableStateOf(fmt(existing?.below)) }
    val over = parseAmount(overText)
    val under = parseAmount(underText)
    val problem = alertProblem(over, under)
    val empty = over.second == null && under.second == null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Price alert") },
        text = {
            Column {
                Text(
                    "${if (c.foil) "Foil" else "Non-foil"} · now ${formatUsd(c.nearMintPrice())} · checked daily",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = overText,
                    onValueChange = { overText = it.take(12) },
                    label = { Text("Over $") },
                    singleLine = true,
                    isError = !over.first,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = underText,
                    onValueChange = { underText = it.take(12) },
                    label = { Text("Under $") },
                    singleLine = true,
                    isError = !under.first,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (problem != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
                } else if (!PriceAlerts.canNotify(ctx) && Build.VERSION.SDK_INT < 33) {
                    Spacer(Modifier.height(6.dp))
                    Text("Notifications are off for the app.", style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = problem == null && !empty, onClick = { onSave(over.second, under.second) }) { Text("Save") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) TextButton(onClick = onDelete) { Text("Delete", color = Palette.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
