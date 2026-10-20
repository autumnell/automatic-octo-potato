package com.mtgscanner.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.components.SelectPill
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
    }
    Card {
        ToggleRow(
            "Show prices",
            "Turn off to hide every price, value, gain and loss in the app. Price alerts stop too.",
            AppSettings.showPrices,
        ) {
            AppSettings.updateShowPrices(it)
            PriceAlerts.schedule(ctx)
            com.mtgscanner.widget.CollectionWidget.refresh(ctx)
        }
        if (AppSettings.showPrices) {
            HorizontalDivider(color = Palette.Outline)
            ToggleRow(
                "Price alerts",
                "Once a day, get a notification when a card you own or want moves a lot in price.",
                AppSettings.priceAlerts,
            ) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    AppSettings.updatePriceAlerts(on)
                    PriceAlerts.schedule(ctx)
                }
            }
            if (AppSettings.priceAlerts) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    Text("Alert when the price moves at least", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
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

/** The same scan settings and presets as the scanner's settings sheet. */
@Composable
fun ScanningSection() {
    Card {
        com.mtgscanner.ui.scan.ScanSettingsEditor(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), showFoil = false)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), color = Palette.Surface) {
        Column { content() }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent))
    }
}
