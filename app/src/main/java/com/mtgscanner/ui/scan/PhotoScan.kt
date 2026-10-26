package com.mtgscanner.ui.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.PrintingsSheet
import com.mtgscanner.data.ScanBarPrefs
import com.mtgscanner.ui.components.moneySuffix
import com.mtgscanner.ui.theme.Palette
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.CardShape
import com.mtgscanner.ui.components.ruleBelow

/** The cards read from a photo: each with its match, to fix the printing or take off, then add them all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSheet(state: ScanUiState, vm: ScanViewModel) {
    val run = state.photo ?: return
    var picking by remember { mutableStateOf<PhotoItem?>(null) }
    var printings by remember { mutableStateOf<List<ScryfallCard>?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = vm::closePhoto, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            val found = run.items.filter { it.card != null }
            Text(run.title, style = MaterialTheme.typography.titleLarge)
            if (!run.loading && !run.failed && run.items.isNotEmpty()) Text(
                "Found ${found.size} of ${run.items.size}",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            Spacer(Modifier.height(8.dp))
            when {
                run.loading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Palette.Accent)
                        Spacer(Modifier.height(10.dp))
                        Text("Reading cards…", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
                run.failed -> EmptyState("Couldn't open that photo")
                run.items.isEmpty() -> EmptyState("No cards found", body = "Try a sharper, closer photo")
                else -> LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                    items(run.items, key = { it.key }) { item ->
                        PhotoRow(
                            item, item.card?.let { c -> item.foil?.let { f -> f && c.hasFoil || !c.hasNonFoil } ?: state.isFoil(c) } ?: false,
                            onPrinting = {
                                item.card?.let { c ->
                                    picking = item
                                    printings = null
                                    scope.launch { printings = vm.printingsOf(c) }
                                }
                            },
                            onRemove = { vm.photoRemove(item.key) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::closePhoto) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                if (!run.loading && found.isNotEmpty()) {
                    val worth = addWorth(state, found.mapNotNull { it.card }, ScanBarPrefs.quantity)
                    val verb = when (state.target) {
                        ScanTarget.CHECK -> "Done"
                        ScanTarget.REMOVE -> "Remove ${found.size}"
                        ScanTarget.BOX -> "Count ${found.size}"
                        else -> "Add ${found.size}"
                    }
                    GradientButton(verb + worth, vm::addPhoto, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    picking?.let { item ->
        PrintingsSheet(
            printings, item.card?.id,
            { card -> vm.photoPrinting(item.key, card); picking = null },
            { picking = null },
        )
    }
}

@Composable
private fun PhotoRow(item: PhotoItem, foil: Boolean, onPrinting: () -> Unit, onRemove: () -> Unit) {
    val c = item.card
    Row(Modifier.fillMaxWidth().ruleBelow().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val thumb = item.thumb
        val mod = Modifier.width(52.dp).aspectRatio(63f / 88f).clip(CardShape)
        if (c != null) CardImage(c.smallImageUrl, Modifier.width(52.dp))
        else if (thumb != null && !thumb.isRecycled) {
            val img = remember(thumb) { thumb.asImageBitmap() }
            Image(img, null, mod, contentScale = ContentScale.Crop)
        } else Box(mod)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c != null) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.unsure) Text("Not sure · check it", style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
                Text(
                    "${c.set.uppercase()} · #${c.collectorNumber}" + (if (foil) " · Foil" else "") + (item.lang?.let { " · ${it.uppercase()}" } ?: "") +
                        moneySuffix(c.usdPrice(foil), c.eurPrice(foil)),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1,
                )
            } else {
                Text("Not found", style = MaterialTheme.typography.titleSmall, color = Palette.Muted)
                item.read?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (c != null) TextButton(onClick = onPrinting) { Text("Printing") }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Close, "Remove", tint = Palette.Icon, modifier = Modifier.size(18.dp))
        }
    }
}
