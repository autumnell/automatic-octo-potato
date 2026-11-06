package com.mtgscanner.ui.decks

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cards Scryfall's community tags (Tagger) mark as tutors (land searches left out) and mass land denial, as keys from
 * [DeckNames.key]. Wizards publishes no such lists, only definitions, so these stand in for the bracket check.
 */
data class BracketTagLists(val tutors: Set<String>, val massLandDenial: Set<String>)

/** Reads bracket-tags.json, built weekly by .github/workflows/bracket-tags.yml and put in the APK. */
object BracketTags {
    private const val FILE = "bracket-tags.json"
    @Volatile private var cached: BracketTagLists? = null

    /** The lists from the APK, or null in a build without them (the check then reads card text). */
    suspend fun load(ctx: Context): BracketTagLists? = cached ?: withContext(Dispatchers.IO) {
        runCatching { ctx.applicationContext.assets.open(FILE).bufferedReader().use { parse(it.readText()) } }.getOrNull()
            ?.also { cached = it }
    }

    /** {"tutor": [names], "mld": [names]}. Pure, for tests. */
    fun parse(text: String): BracketTagLists {
        val o = Json.parseToJsonElement(text).jsonObject
        fun keys(k: String) = o[k]?.jsonArray?.map { DeckNames.key(it.jsonPrimitive.content) }?.toSet().orEmpty()
        return BracketTagLists(keys("tutor"), keys("mld"))
    }
}
