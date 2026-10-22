package com.mtgscanner.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtgscanner.MtgApp
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.ShareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.TabularNums

/** Year in review, full screen over Stats: cards added, best pull, biggest gain, top set, value. */
@Composable
fun YearReviewDialog(onDismiss: () -> Unit, onOpenCard: (Long) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        YearReviewScreen(onBack = onDismiss, onOpenCard = onOpenCard)
    }
}

@Composable
private fun YearReviewScreen(onBack: () -> Unit, onOpenCard: (Long) -> Unit) {
    val ctx = LocalContext.current
    val repo = (ctx.applicationContext as MtgApp).repository
    val scope = rememberCoroutineScope()
    val prices = AppSettings.showPrices
    val zone = remember { ZoneId.systemDefault() }
    var years by remember { mutableStateOf(listOf(LocalDate.now().year)) }
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var review by remember { mutableStateOf<YearReview?>(null) }

    LaunchedEffect(year) {
        review = null
        review = withContext(Dispatchers.IO) {
            val cards = repo.allCards()
            val snaps = repo.dao.allSnapshots()
            val today = LocalDate.now()
            years = YearReviewMath.years(cards, snaps, today, zone)
            YearReviewMath.compute(year, cards, snaps, repo.dao.pricePointsSince("$year-01-01"), today, zone)
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Year in review", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            val r = review
            IconButton(enabled = r != null && !r.isEmpty, onClick = {
                if (r != null) scope.launch {
                    val bmp = withContext(Dispatchers.Default) { shareImage(r, prices) }
                    ShareFile.png(ctx.applicationContext, "${r.year} in cards", bmp)
                }
            }) { Icon(Icons.Filled.Share, "Share") }
        }
        if (years.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                years.forEach { y -> SelectPill("$y", y == year, onClick = { year = y }) }
            }
        }
        val r = review
        when {
            r == null -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Palette.Accent)
            }
            r.isEmpty -> EmptyState("Nothing in $year")
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("${r.year}", style = MaterialTheme.typography.displaySmall, color = Palette.AccentText)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Added", if (r.added == 1) "1 card" else "${r.added} cards", Modifier.weight(1f))
                    r.topSet?.let { (code, name, n) ->
                        Tile("Top set", name.ifBlank { code.uppercase() }, Modifier.weight(1f), sub = "$n cards")
                    }
                }
                if (prices && r.valueStart != null && r.valueEnd != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Value", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                Text("${formatUsd(r.valueStart)} → ${formatUsd(r.valueEnd)}", style = MaterialTheme.typography.titleMedium.merge(TabularNums))
                            }
                            ChangeText(r.valueEnd - r.valueStart, r.valueStart)
                        }
                    }
                }
                r.bestPull?.let { c ->
                    Spacer(Modifier.height(10.dp))
                    CardTile("Best pull", c, if (prices) formatUsd(c.amount) else null) { onOpenCard(c.id) }
                }
                if (prices) r.biggestGain?.let { c ->
                    Spacer(Modifier.height(10.dp))
                    CardTile("Biggest gain", c, "+${formatUsd(c.amount)}") { onOpenCard(c.id) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Surface(modifier, shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

@Composable
private fun CardTile(label: String, c: YearCard, amount: String?, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Corners.Medium), color = Palette.Surface) {
        Row(Modifier.clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CardImage(c.imageUrl, Modifier.width(64.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(c.setCode.uppercase(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            if (amount != null) Text(amount, style = MaterialTheme.typography.titleMedium, color = Palette.AccentText)
        }
    }
}

/** A 1080×1350 picture of the year for the share sheet. Text only, so it needs no network. */
private fun shareImage(r: YearReview, prices: Boolean): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint().apply { color = 0xFF16151D.toInt() }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val accent = 0xFFB9A8FF.toInt()
    val text = 0xFFEDEDEF.toInt()
    val muted = 0xFF9A9BA3.toInt()
    val left = 96f
    c.drawText("${r.year}", left, 250f, paint(180f, accent, bold = true))
    c.drawText("in cards", left, 330f, paint(64f, text))

    val rows = buildList {
        add("Added" to (if (r.added == 1) "1 card" else "${r.added} cards"))
        r.topSet?.let { add("Top set" to it.second.ifBlank { it.first.uppercase() }) }
        r.bestPull?.let { add("Best pull" to it.name + if (prices) "  ${formatUsd(it.amount)}" else "") }
        if (prices) r.biggestGain?.let { add("Biggest gain" to "${it.name}  +${formatUsd(it.amount)}") }
        if (prices && r.valueStart != null && r.valueEnd != null) add("Value" to "${formatUsd(r.valueStart)} → ${formatUsd(r.valueEnd)}")
    }
    val label = paint(40f, muted)
    val value = paint(58f, text, bold = true)
    var y = 480f
    for ((l, v) in rows) {
        c.drawText(l, left, y, label)
        c.drawText(ellipsize(v, value, w - left * 2), left, y + 72f, value)
        y += 170f
    }
    c.drawText("Grimoire", left, h - 90f, paint(36f, muted))
    return bmp
}

private fun ellipsize(s: String, p: Paint, max: Float): String {
    if (p.measureText(s) <= max) return s
    var t = s
    while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
    return "$t…"
}
