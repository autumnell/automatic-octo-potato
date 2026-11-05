package com.mtgscanner.ui.settings

import com.mtgscanner.ui.theme.Space
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
import com.mtgscanner.ui.components.SelectPill
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.work.PriceAlerts
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.components.PlainGroup

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
            HorizontalDivider(color = Palette.Rule)
            CurrencyRow()
            HorizontalDivider(color = Palette.Rule)
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
                Column(Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s3)) {
                    Text("Moves at least", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.padding(top = Space.s2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        listOf(10.0, 20.0, 50.0).forEach { p ->
                            SelectPill("${p.toInt()}%", AppSettings.alertPercent == p, { AppSettings.updateAlertPercent(p) })
                        }
                    }
                    Spacer(Modifier.padding(top = Space.s2))
                    Text("and at least", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Spacer(Modifier.padding(top = Space.s2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
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
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        Text("Currency", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            com.mtgscanner.util.PriceCurrency.entries.forEach { c ->
                SelectPill(c.label, AppSettings.currency == c, {
                    AppSettings.updateCurrency(c)
                    com.mtgscanner.widget.CollectionWidget.refresh(ctx)
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

@Composable
internal fun Card(content: @Composable () -> Unit) {
    // Plain rows on the background, closed by a rule; rows keep their own 16dp inset.
    PlainGroup(Modifier.padding(horizontal = Space.s1)) { content() }
}

@Composable
internal fun ToggleRow(title: String, body: String, checked: Boolean, lines: Int = 1, onChange: (Boolean) -> Unit) {
    // One target for TalkBack: the whole row is the switch.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.s3))
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Text))
    }
}
