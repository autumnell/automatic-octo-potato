package com.mtgscanner.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mtgscanner.R

/**
 * IBM Plex Mono (SIL OFL 1.1, license in assets/licenses/ibm-plex-mono.txt) at text weights, for numbers and
 * set codes in the price columns.
 */
val PlexMonoText = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)
