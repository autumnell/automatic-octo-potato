package com.mtgscanner.ui.collection

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.db.BoxSummary
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.HaveItem
import com.mtgscanner.util.HaveListPage
import com.mtgscanner.util.ShareFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Proxies are left out: they're not for trade. */
fun haveItems(cards: List<CollectionCard>): List<HaveItem> = cards.filter { !it.proxy }.map { c ->
    HaveItem(
        name = c.name, setCode = c.setCode, number = c.collectorNumber, quantity = c.quantity,
        price = c.unitPrice(), colors = c.colors, imageUrl = c.imageUrl ?: c.smallImageUrl,
        foil = c.foil, condition = c.condition, link = c.scryfallUri,
    )
}

/** Writes [cards] as a have-list web page and opens the share sheet. */
suspend fun shareHaveList(context: Context, title: String, cards: List<CollectionCard>) {
    val html = withContext(Dispatchers.Default) {
        HaveListPage.html(title, haveItems(cards), AppSettings.showPrices, LocalDate.now().toString())
    }
    ShareFile.html(context, title, html)
}

/** Share button for a trade binder: as text, or as a web page. */
@Composable
internal fun ShareBinderButton(b: BoxSummary, cardsIn: suspend (Long) -> List<CollectionCard>, onText: (List<CollectionCard>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = b.cards > 0, onClick = { menu = true }) { Icon(Icons.Filled.Share, "Share ${b.name}") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Text") }, onClick = { menu = false; scope.launch { onText(cardsIn(b.id)) } })
            DropdownMenuItem(text = { Text("Web page") }, onClick = { menu = false; scope.launch { shareHaveList(ctx, b.name, cardsIn(b.id)) } })
        }
    }
}

/**
 * Picks what goes on a have-list page: trade binders, the whole collection, or one box.
 * [scope] belongs to the screen, since the dialog closes before the page is ready.
 */
@Composable
internal fun HaveListDialog(boxes: List<BoxSummary>, scope: CoroutineScope, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = (ctx.applicationContext as MtgApp).repository
    val trade = boxes.filter { it.kind == "trade" && it.cards > 0 }

    fun share(title: String, load: suspend () -> List<CollectionCard>) {
        onDismiss()
        scope.launch { shareHaveList(ctx.applicationContext, title, load().sortedBy { it.name }) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Have list") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (trade.isNotEmpty()) {
                    Pick("Trade binders", "${trade.sumOf { it.cards }} cards") {
                        share("Trade binder") { trade.flatMap { repo.cardsInBox(it.id) } }
                    }
                }
                Pick("Whole collection", "${boxes.sumOf { it.cards }} cards") { share("My collection") { repo.allCards() } }
                boxes.filter { it.cards > 0 }.forEach { b ->
                    Pick(b.name, "${b.cards} cards") { share(b.name) { repo.cardsInBox(b.id) } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Pick(title: String, body: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}
