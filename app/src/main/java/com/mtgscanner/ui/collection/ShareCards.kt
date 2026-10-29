package com.mtgscanner.ui.collection

import com.mtgscanner.util.shareText
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.DeckCheckLine
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.BuyStore
import com.mtgscanner.util.HaveListPage
import com.mtgscanner.util.ShareAs
import com.mtgscanner.util.ShareFile
import com.mtgscanner.util.ShareLine
import com.mtgscanner.util.ShareList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

fun CollectionCard.shareLine() = ShareLine(
    name = name, quantity = quantity, setCode = setCode, number = collectorNumber, price = unitPrice(),
    foil = foil, condition = condition, colors = colors, imageUrl = imageUrl ?: smallImageUrl, link = scryfallUri, proxy = proxy,
)

fun WishlistItem.shareLine() = ShareLine(name = name, quantity = wanted, price = priceUsd, imageUrl = imageUrl)

fun DeckCheckLine.shareLine() = ShareLine(
    name = name, quantity = missing, setCode = setCode.orEmpty(), number = collectorNumber.orEmpty(), price = price, imageUrl = imageUrl,
)

/** Sends [lines] out as text, a web page or a store cart. */
suspend fun shareLines(context: Context, title: String, lines: List<ShareLine>, how: ShareAs) {
    if (lines.isEmpty()) { Undo.offer("Nothing to share"); return }
    val app = context.applicationContext
    when (how) {
        ShareAs.Text -> shareText(app, title, ShareList.text(title, lines, AppSettings.showPrices) { formatUsd(it) })
        ShareAs.Page -> {
            val html = withContext(Dispatchers.Default) {
                HaveListPage.html(title, ShareList.haveItems(lines), AppSettings.showPrices, LocalDate.now().toString())
            }
            ShareFile.html(app, title, html)
        }
        is ShareAs.Cart -> shareText(app, "${how.store.label} list", ShareList.cart(lines, how.store))
    }
}


/**
 * The one Share choice: text list, web page, or (for cards to buy, [carts]) a store cart.
 * [scope] belongs to the screen, since the dialog closes before the list is ready.
 */
@Composable
fun ShareDialog(
    title: String,
    scope: CoroutineScope,
    carts: Boolean = false,
    onDismiss: () -> Unit,
    load: suspend () -> List<ShareLine>,
) {
    val ctx = LocalContext.current
    fun go(how: ShareAs) {
        onDismiss()
        scope.launch { shareLines(ctx, title, load(), how) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Share $title") },
        text = {
            Column {
                ShareChoice("Text list") { go(ShareAs.Text) }
                ShareChoice("Web page") { go(ShareAs.Page) }
                if (carts) BuyStore.entries.forEach { s -> ShareChoice("${s.label} cart") { go(ShareAs.Cart(s)) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ShareChoice(label: String, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}
