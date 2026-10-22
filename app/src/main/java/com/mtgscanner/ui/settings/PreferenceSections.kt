package com.mtgscanner.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.JustTcg
import com.mtgscanner.ui.components.SelectPill
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.work.PriceAlerts

/** Show or hide prices everywhere, and daily price alerts. */
@Composable
fun PricesSection() {
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AppSettings.updatePriceAlerts(granted)
        PriceAlerts.schedule(ctx)
        // Denied for good, Android answers at once without asking: open the app's notification settings instead.
        val activity = ctx as? android.app.Activity
        if (!granted && Build.VERSION.SDK_INT >= 33 && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) openNotificationSettings(ctx)
    }
    Card {
        ToggleRow(
            "Show prices",
            "Off hides all prices",
            AppSettings.showPrices,
        ) {
            AppSettings.updateShowPrices(it)
            PriceAlerts.schedule(ctx)
            com.mtgscanner.widget.CollectionWidget.refresh(ctx)
        }
        if (AppSettings.showPrices) {
            HorizontalDivider(color = Palette.Outline)
            CurrencyRow()
            HorizontalDivider(color = Palette.Outline)
            ToggleRow(
                "Price alerts",
                "Daily, for big moves",
                AppSettings.priceAlerts,
            ) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else if (on && !androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                    // Notifications are switched off for the app: alerts would never show.
                    AppSettings.updatePriceAlerts(true)
                    PriceAlerts.schedule(ctx)
                    openNotificationSettings(ctx)
                } else {
                    AppSettings.updatePriceAlerts(on)
                    PriceAlerts.schedule(ctx)
                }
            }
            if (AppSettings.priceAlerts) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text("Moves at least", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10.0, 20.0, 50.0).forEach { p ->
                            SelectPill("${p.toInt()}%", AppSettings.alertPercent == p, { AppSettings.updateAlertPercent(p) })
                        }
                    }
                    Spacer(Modifier.padding(top = 6.dp))
                    Text("and at least", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5, 1.0, 5.0, 20.0).forEach { d ->
                            SelectPill(if (d < 1) "$0.50" else "$${d.toInt()}", AppSettings.alertDollars == d, { AppSettings.updateAlertDollars(d) })
                        }
                    }
                }
            }
        }
    }
}

/** Currency: USD / EUR / Both. Picking euros fetches prices once when none have euro prices yet. */
@Composable
private fun CurrencyRow() {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Currency", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.mtgscanner.util.PriceCurrency.entries.forEach { c ->
                SelectPill(c.label, AppSettings.currency == c, {
                    AppSettings.updateCurrency(c)
                    if (c != com.mtgscanner.util.PriceCurrency.USD) {
                        val repo = (ctx.applicationContext as com.mtgscanner.MtgApp).repository
                        // On the app's scope, so leaving Settings doesn't stop it.
                        repo.appScope.launch {
                            runCatching {
                                val cards = repo.allCards()
                                if (cards.isNotEmpty() && cards.none { it.priceEur != null || it.priceEurFoil != null }) repo.refreshPrices()
                            }
                        }
                    }
                })
            }
        }
    }
}

/** The app's notification settings page, where notifications can be allowed again. */
private fun openNotificationSettings(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName),
        )
    }
}

/** Optional JustTCG key for real per-condition prices. Without one, prices are near mint. */
@Composable
fun ConditionPricesSection() {
    val ctx = LocalContext.current
    val repo = (ctx.applicationContext as com.mtgscanner.MtgApp).repository
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var key by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(JustTcg.apiKey) }
    var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun refresh() {
        busy = true
        status = "Updating…"
        scope.launch {
            status = runCatching { repo.refreshConditionPrices() }
                .fold({ "Priced $it cards by condition" }, { it.message ?: "Couldn't reach JustTCG" })
            busy = false
        }
    }
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Condition prices", style = MaterialTheme.typography.titleSmall)
            Text(
                if (JustTcg.enabled) "JustTCG · ${JustTcg.usedThisMonth}/${JustTcg.MONTHLY_LIMIT} lookups this month"
                else "Free JustTCG key for NM/LP/MP/HP prices",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("JustTCG API key") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                androidx.compose.material3.TextButton(
                    enabled = !busy && key.trim() != JustTcg.apiKey,
                    onClick = {
                        val removed = key.isBlank()
                        JustTcg.updateKey(key)
                        if (removed) {
                            scope.launch { repo.clearConditionPrices() }
                            status = "Using near-mint prices"
                        } else refresh()
                    },
                ) { Text("Save") }
                if (JustTcg.enabled) {
                    androidx.compose.material3.TextButton(enabled = !busy, onClick = { refresh() }) { Text("Update now") }
                }
                androidx.compose.material3.TextButton(onClick = {
                    // No browser installed throws; nothing to do then.
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://justtcg.com"))) }
                }) { Text("Get a key") }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
        }
    }
}

/** The same scan settings and presets as the scanner's settings sheet. */
@Composable
fun ScanningSection() {
    Card {
        com.mtgscanner.ui.scan.ScanSettingsEditor(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), showFoil = false)
    }
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column { content() }
    }
}

@Composable
internal fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // One target for TalkBack: the whole row is the switch.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
}
