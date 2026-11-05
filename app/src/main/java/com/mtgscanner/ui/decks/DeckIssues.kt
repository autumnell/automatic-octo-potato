package com.mtgscanner.ui.decks

import com.mtgscanner.ui.theme.Space
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.data.CommanderRules
import com.mtgscanner.ui.theme.Palette

/** The format check as text. Pure, for tests. */
object DeckIssueText {
    /** "1 issue", "3 issues". */
    fun count(n: Int): String = if (n == 1) "1 issue" else "$n issues"

    /** One (card, what's wrong) row per problem, worst first. */
    fun rows(i: CommanderRules.Issues): List<Pair<String, String>> =
        i.banned.map { it to "Banned" } +
            i.notLegal.map { it to "Not legal" } +
            i.overLimit.map { it to "Too many" } +
            i.offColor.map { it to "Off-color" }
}

/** A small "3 issues" line under the deck's name; tapping it lists them. Nothing when there are none. */
@Composable
fun DeckIssuesLine(issues: CommanderRules.Issues) {
    if (issues.isEmpty) return
    var open by remember { mutableStateOf(false) }
    Row(Modifier.clickable { open = true }.padding(vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Warning, null, tint = Palette.Danger, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Space.s1))
        Text(DeckIssueText.count(issues.count), style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
    }
    if (open) DeckIssuesSheet(issues, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckIssuesSheet(issues: CommanderRules.Issues, onDismiss: () -> Unit) {
    val rows = DeckIssueText.rows(issues)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                DeckIssueText.count(rows.size), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                itemsIndexed(rows, key = { i, _ -> i }) { _, (name, what) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(Space.s3))
                        Text(what, style = MaterialTheme.typography.labelLarge, color = Palette.Danger)
                    }
                }
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
}
