package com.mtgscanner.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.FoilTag
import com.mtgscanner.ui.theme.Palette

/** A card found by the Boxes search: picture, name, set, and exactly where it is. */
@Composable
internal fun FoundRow(c: CollectionCard, where: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(c.smallImageUrl ?: c.imageUrl, Modifier.width(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (c.quantity > 1) "${c.quantity}× " else "") + c.name,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (c.foil) { Spacer(Modifier.width(6.dp)); FoilTag() }
                Spacer(Modifier.width(6.dp))
                Text(c.setCode, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            Text(where, style = MaterialTheme.typography.bodySmall, color = Palette.AccentText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
