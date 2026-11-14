package com.mtgscanner.ui.collection

import com.mtgscanner.ui.theme.Space
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.BoxPrefs
import com.mtgscanner.data.ShelfLayout
import com.mtgscanner.data.db.Box as CardBox
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.Corners

/**
 * The shelves as a picture: one row of spots per shelf, each spot showing the box that sits there. [highlight]
 * boxes light up ("where is it"); [selected] gets an outline while it's being moved.
 */
@Composable
fun ShelfPicture(
    layout: ShelfLayout,
    names: Map<Long, String>,
    modifier: Modifier = Modifier,
    highlight: Set<Long> = emptySet(),
    selected: Long? = null,
    spotHeight: Dp = 44.dp,
    onSpot: ((Int) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        for (row in 0 until layout.rows) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                for (col in 0 until layout.cols) {
                    val spot = row * layout.cols + col
                    val id = layout.boxAt(spot)
                    val lit = id != null && id in highlight
                    val shape = RoundedCornerShape(Corners.Small)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(spotHeight)
                            .clip(shape)
                            .background(
                                when {
                                    lit -> Palette.Accent
                                    id != null -> Palette.SurfaceHigh
                                    else -> Color.Transparent
                                },
                            )
                            .border(
                                if (id == selected && id != null) 2.dp else 1.dp,
                                if (id == selected && id != null) Palette.Accent else Palette.Hairline,
                                shape,
                            )
                            .let { m -> if (onSpot != null) m.clickable { onSpot(spot) } else m }
                            .padding(horizontal = Space.s1),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (id != null) {
                            Text(
                                names[id] ?: "?",
                                style = if (spotHeight < 36.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                color = if (lit) Palette.OnAccent else Palette.Text,
                                maxLines = if (spotHeight < 36.dp) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            // The shelf board under each row.
            Box(Modifier.fillMaxWidth().padding(top = Space.s1, bottom = Space.s2).height(3.dp).clip(RoundedCornerShape(Corners.Small)).background(Palette.Outline))
        }
    }
}

/** Lay out boxes the way they sit on the real shelves: pick a box, then tap its spot. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShelfSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    remember { BoxPrefs.load(ctx) }
    val repo = (ctx.applicationContext as MtgApp).repository
    val all by remember { repo.observeBoxes() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val boxes = all.filter { it.kind != "deck" }
    val layout = BoxPrefs.shelf.keepOnly(boxes.map { it.id }.toSet())
    val names = boxes.associate { it.id to it.name }
    var selected by remember { mutableStateOf<Long?>(null) }
    fun save(l: ShelfLayout) = BoxPrefs.saveShelf(ctx, l)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).navigationBarsPadding(),
        ) {
            Text("Shelf", style = MaterialTheme.typography.titleLarge)
            if (boxes.isEmpty()) {
                EmptyState("No boxes yet", body = "Make one from the Boxes tab")
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("Shelves", layout.rows) { save(layout.resize(it, layout.cols)) }
                Spacer(Modifier.width(Space.s4))
                Stepper("Per shelf", layout.cols) { save(layout.resize(layout.rows, it)) }
            }
            Spacer(Modifier.height(Space.s2))
            ShelfPicture(
                layout, names, selected = selected,
                onSpot = { spot ->
                    val pick = selected
                    val there = layout.boxAt(spot)
                    when {
                        pick != null && pick != there -> { save(layout.place(pick, spot)); selected = null }
                        pick != null -> selected = null
                        there != null -> selected = there
                    }
                },
            )
            val sel = selected
            Text(
                when {
                    sel == null -> "Pick a box, then tap its spot"
                    else -> "Tap a spot for ${names[sel] ?: "this box"}"
                },
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                modifier = Modifier.padding(vertical = Space.s2),
            )
            if (sel != null && layout.spotOf(sel) != null) {
                TextButton(onClick = { save(layout.remove(sel)); selected = null }) { Text("Take off shelf", color = Palette.Danger) }
            }
            val off = boxes.filter { layout.spotOf(it.id) == null }
            if (off.isNotEmpty()) {
                Text("Not on a shelf", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.padding(top = Space.s2, bottom = Space.s2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    off.forEach { b -> SelectPill(b.name, selected == b.id, { selected = if (selected == b.id) null else b.id }) }
                }
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        IconButton(onClick = { onChange(value - 1) }, enabled = value > 1) { Icon(Icons.Outlined.Remove, "Fewer") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < ShelfLayout.MAX) { Icon(Icons.Outlined.Add, "More") }
    }
}

/** "Shelf 2 · spot 3" with a small picture, for a card's box; nothing when the box isn't on the shelf. */
@Composable
fun ShelfSpot(boxes: List<CardBox>, boxIds: Set<Long>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember { BoxPrefs.load(ctx) }
    val layout = BoxPrefs.shelf
    val placed = boxIds.filter { layout.spotOf(it) != null }
    if (placed.isEmpty()) return
    val names = boxes.associate { it.id to it.name }
    Column(modifier.fillMaxWidth()) {
        Text(
            placed.mapNotNull { id -> layout.label(id)?.let { "${names[id] ?: "Box"} · $it" } }.joinToString("\n"),
            style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            modifier = Modifier.padding(bottom = Space.s2),
        )
        ShelfPicture(layout, names, highlight = placed.toSet(), spotHeight = 26.dp)
    }
}
