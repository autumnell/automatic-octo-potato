package com.mtgscanner.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The seats of a table of [players] in arrangement [layout] (see [com.mtgscanner.game.TableLayouts]), each drawn by
 * [seat] with its player, how far it's turned to face them (0, 90, 180 or 270) and its place. The table itself and
 * the setup's small pictures of it both use this, so they always match.
 */
@Composable
fun TableSeats(players: Int, layout: Int, gap: Dp, seat: @Composable (index: Int, rotation: Int, modifier: Modifier) -> Unit) {
    val s: @Composable (Int, Int, Modifier) -> Unit = { i, rot, m -> seat(i, rot, m.padding(gap)) }
    when {
        players <= 1 -> s(0, 0, Modifier.fillMaxSize())
        players == 2 && layout == 1 -> Column(Modifier.fillMaxSize()) {
            // Side by side: both read the phone from the same edge.
            s(0, 0, Modifier.weight(1f).fillMaxWidth())
            s(1, 0, Modifier.weight(1f).fillMaxWidth())
        }
        players == 2 -> Column(Modifier.fillMaxSize()) {
            s(0, 180, Modifier.weight(1f).fillMaxWidth())
            s(1, 0, Modifier.weight(1f).fillMaxWidth())
        }
        players == 3 && layout == 1 -> Column(Modifier.fillMaxSize()) {
            // One at the far end, two along the sides.
            s(0, 180, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.weight(1.4f)) {
                s(2, 90, Modifier.weight(1f).fillMaxHeight())
                s(1, 270, Modifier.weight(1f).fillMaxHeight())
            }
        }
        players == 3 -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1.4f)) {
                s(0, 90, Modifier.weight(1f).fillMaxHeight())
                s(1, 270, Modifier.weight(1f).fillMaxHeight())
            }
            s(2, 0, Modifier.weight(1f).fillMaxWidth())
        }
        players == 4 && layout == 1 -> Column(Modifier.fillMaxSize()) {
            // One on each side of the table, clockwise from the top.
            s(0, 180, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.weight(1.2f)) {
                s(3, 90, Modifier.weight(1f).fillMaxHeight())
                s(1, 270, Modifier.weight(1f).fillMaxHeight())
            }
            s(2, 0, Modifier.weight(1f).fillMaxWidth())
        }
        players == 4 -> Row(Modifier.fillMaxSize()) {
            // Clockwise from the top left: 1 and 4 on the left side, 2 and 3 on the right.
            Column(Modifier.weight(1f)) {
                s(0, 90, Modifier.weight(1f).fillMaxWidth())
                s(3, 90, Modifier.weight(1f).fillMaxWidth())
            }
            Column(Modifier.weight(1f)) {
                s(1, 270, Modifier.weight(1f).fillMaxWidth())
                s(2, 270, Modifier.weight(1f).fillMaxWidth())
            }
        }
        players == 5 -> Column(Modifier.fillMaxSize()) {
            // Clockwise: 1 top left, 2 and 3 down the right, 4 across the bottom, 5 bottom left.
            Row(Modifier.weight(2.4f)) {
                Column(Modifier.weight(1f)) {
                    s(0, 90, Modifier.weight(1f).fillMaxWidth())
                    s(4, 90, Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    s(1, 270, Modifier.weight(1f).fillMaxWidth())
                    s(2, 270, Modifier.weight(1f).fillMaxWidth())
                }
            }
            s(3, 0, Modifier.weight(1f).fillMaxWidth())
        }
        else -> Row(Modifier.fillMaxSize()) {
            // Clockwise: 1, 2, 3 is the top left, top right, middle right... 6 is middle left.
            Column(Modifier.weight(1f)) {
                s(0, 90, Modifier.weight(1f).fillMaxWidth())
                s(5, 90, Modifier.weight(1f).fillMaxWidth())
                s(4, 90, Modifier.weight(1f).fillMaxWidth())
            }
            Column(Modifier.weight(1f)) {
                s(1, 270, Modifier.weight(1f).fillMaxWidth())
                s(2, 270, Modifier.weight(1f).fillMaxWidth())
                s(3, 270, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
