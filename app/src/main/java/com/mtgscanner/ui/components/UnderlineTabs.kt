package com.mtgscanner.ui.components

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Palette

/**
 * Text tabs: the selected one in [Palette.Text] with a 2dp accent line under it, the rest muted, and a 1dp
 * [Palette.Rule] under the whole row. With [fill] the tabs share the width; otherwise they sit left, 24dp apart,
 * and scroll sideways if they don't fit.
 */
@Composable
fun UnderlineTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    horizontalPadding: Dp = 20.dp,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (fill) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(Space.s6),
        ) {
            labels.forEachIndexed { i, label ->
                UnderlineTab(label, i == selected, if (fill) Modifier.weight(1f) else Modifier) { onSelect(i) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Rule))
    }
}

@Composable
private fun UnderlineTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        // As wide as the label (or the shared width with fill), so the underline matches it.
        modifier.width(IntrinsicSize.Max).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Palette.Text else Palette.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = Space.s3),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) Palette.Accent else Color.Transparent))
    }
}
