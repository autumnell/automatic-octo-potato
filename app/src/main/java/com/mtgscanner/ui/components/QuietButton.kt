package com.mtgscanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Corners
import com.mtgscanner.ui.theme.Palette

/**
 * The app's one secondary button: a thin outline with plain text, so the accent stays on the main action
 * next to it ([GradientButton]).
 */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(Corners.Medium),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, if (enabled) Palette.Outline else Palette.Outline.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text, disabledContentColor = Palette.Muted),
        contentPadding = contentPadding,
        content = content,
    )
}
