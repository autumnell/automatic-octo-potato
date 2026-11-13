package com.mtgscanner.util

import com.mtgscanner.BuildConfig

/** The one User-Agent every web call sends: Scryfall asks for the app's real name and version. */
object UserAgent {
    val value: String = "Grimoire/${BuildConfig.VERSION_NAME} (personal collection app)"
}
