package com.mtgscanner.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgscanner.MtgApp
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.DeckCheck
import com.mtgscanner.data.ParsedDecks
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.util.CardNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** What the weekly watch remembers: cards banned per deck, and wishlist reprints found. */
@Serializable
data class WatchState(
    /** Deck id to the keys ([CardNames.key]) of its cards banned in its format, as of the last check. */
    val banned: Map<String, List<String>> = emptyMap(),
    /** The day reprints were last looked for ("2026-10-24"); empty before the first check. */
    val reprintsSince: String = "",
    /** Wishlist oracle id to its new printing. */
    val reprints: Map<String, Reprint> = emptyMap(),
)

/** A new printing of a wishlist card: set code and when it came out. */
@Serializable
data class Reprint(val set: String, val day: String)

/** Ban alerts and reprint watch, kept free of Android so they can be tested. */
object WatchMath {
    /** Reprints show on the wishlist for this long after release. */
    const val SHOW_DAYS = 60L

    /**
     * Cards newly banned per deck: in [now] but not in [before]. A deck checked for the first time
     * (not in [before]) reports nothing, so turning this on doesn't alert for old bans.
     */
    fun newBans(before: Map<String, List<String>>, now: Map<String, List<String>>): Map<String, List<String>> =
        now.mapNotNull { (deck, keys) ->
            val old = before[deck] ?: return@mapNotNull null
            val added = keys.filter { it !in old }
            if (added.isEmpty()) null else deck to added
        }.toMap()

    /** Scryfall searches for printings of [oracleIds] released on or after [since], a few cards per search. */
    fun reprintQueries(oracleIds: List<String>, since: String, chunk: Int = 20): List<String> =
        oracleIds.distinct().chunked(chunk).map { ids ->
            "(" + ids.joinToString(" or ") { "oracleid:$it" } + ") date>=$since game:paper"
        }

    /**
     * The newest printing per oracle id among [found], keeping only ones released on or after [since].
     * Promos are left out: they're rarely the cheap copy you're waiting for.
     */
    fun reprints(found: List<ScryfallCard>, since: String): Map<String, Reprint> =
        found.filter { it.oracleId != null && (it.releasedAt ?: "") >= since && !it.promo }
            .groupBy { it.oracleId!! }
            .mapValues { (_, cards) -> cards.maxBy { it.releasedAt ?: "" }.let { Reprint(it.set.uppercase(), it.releasedAt ?: since) } }

    /** Reprints still worth showing on [today]. */
    fun fresh(reprints: Map<String, Reprint>, today: LocalDate): Map<String, Reprint> =
        reprints.filterValues { r -> runCatching { LocalDate.parse(r.day).plusDays(SHOW_DAYS) >= today }.getOrDefault(false) }

    /** Played cards per deck that has a format: deck id to (format, names). */
    fun deckNames(decks: List<Deck>, lines: (Deck) -> List<com.mtgscanner.util.DeckLine>): Map<Long, Pair<String, List<String>>> =
        decks.filter { it.format.isNotEmpty() }.associate { d ->
            d.id to (d.format to lines(d).filter { it.section in DeckCheck.PLAYED }.map { it.name }.distinct())
        }
}

/** The weekly watch's saved state, with a flow so the wishlist can show reprints. */
object WatchStore {
    private const val PREFS = "watch"
    private const val KEY = "state"
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state
    private var loaded = false

    @Synchronized
    fun load(context: Context): WatchState {
        if (!loaded) {
            val raw = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) }.getOrNull()
            _state.value = raw?.let { runCatching { json.decodeFromString(WatchState.serializer(), it) }.getOrNull() } ?: WatchState()
            loaded = true
        }
        return _state.value
    }

    @Synchronized
    fun save(context: Context, s: WatchState) {
        _state.value = s
        loaded = true
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.encodeToString(WatchState.serializer(), s)).apply()
        }
    }

    /** "Reprint · MKM" for a wishlist card with a recent new printing, else null. */
    fun tag(state: WatchState, oracleId: String?, today: LocalDate = LocalDate.now()): String? {
        val r = state.reprints[oracleId ?: return null] ?: return null
        return if (WatchMath.fresh(mapOf("x" to r), today).isEmpty()) null else "Reprint · ${r.set}"
    }
}

/** Once a week: cards newly banned in your decks' formats, and new printings of wishlist cards. */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = (ctx as MtgApp).repository
        val before = WatchStore.load(ctx)
        return try {
            val bans = checkBans(repo, before)
            val reprints = checkReprints(repo, before)
            WatchStore.save(ctx, before.copy(banned = bans.first, reprintsSince = reprints.first, reprints = reprints.second))
            notify(ctx, bans.second, reprints.third)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    /** Returns the banned cards per deck now, and new bans by deck name for the notification. */
    private suspend fun checkBans(repo: CollectionRepository, before: WatchState): Pair<Map<String, List<String>>, List<Pair<String, List<String>>>> {
        val decks = repo.observeDecks().first()
        val perDeck = WatchMath.deckNames(decks) { ParsedDecks.of(it) }
        if (perDeck.isEmpty()) return emptyMap<String, List<String>>() to emptyList()
        val names = perDeck.values.flatMap { it.second }.distinctBy { CardNames.key(it) }
        val cards = repo.api.identify(names.map { n -> buildJsonObject { put("name", CardNames.front(n)) } })
        val legal = HashMap<String, Map<String, String>>()
        names.zip(cards).forEach { (n, c) -> if (c != null) legal[CardNames.key(n)] = c.legalities }
        val now = perDeck.map { (id, fn) ->
            val (format, ns) = fn
            // A deck's format is Scryfall's legality key ("commander", "modern"…).
            id.toString() to ns.map(CardNames::key).filter { legal[it]?.get(format) == "banned" }.distinct()
        }.toMap()
        // A card Scryfall didn't answer for this time keeps its old state, so a failed lookup isn't an "unban".
        val merged = now.mapValues { (deck, keys) ->
            val missing = before.banned[deck].orEmpty().filter { it !in legal }
            (keys + missing).distinct()
        }
        val names2 = decks.associate { it.id.toString() to it.name }
        val display = names.associateBy { CardNames.key(it) }
        val fresh = WatchMath.newBans(before.banned, merged).map { (deck, keys) ->
            names2[deck].orEmpty() to keys.map { CardNames.full(display[it] ?: it) }
        }
        return merged to fresh
    }

    /** Returns the new "since" day, the reprints to keep, and the new ones as card names with their set. */
    private suspend fun checkReprints(repo: CollectionRepository, before: WatchState): Triple<String, Map<String, Reprint>, List<String>> {
        val today = LocalDate.now()
        val wish = repo.observeWishlist().first()
        val ids = wish.map { it.oracleId }.filter { it.isNotBlank() }
        // The first check only sets the day to look from, so cards printed long ago aren't "reprints".
        if (before.reprintsSince.isEmpty() || ids.isEmpty()) return Triple(today.toString(), WatchMath.fresh(before.reprints, today), emptyList())
        val since = before.reprintsSince
        val found = WatchMath.reprintQueries(ids, since).flatMap { q -> repo.api.searchAll(q, unique = "prints", maxPages = 2, order = "released") }
        val hits = WatchMath.reprints(found, since)
        val newOnes = hits.filterKeys { it !in before.reprints }
        val names = wish.associate { it.oracleId to it.name }
        val kept = WatchMath.fresh(before.reprints + hits, today)
        return Triple(today.toString(), kept, newOnes.map { (id, r) -> "${names[id] ?: "A card"} in ${r.set}" })
    }

    private fun notify(ctx: Context, bans: List<Pair<String, List<String>>>, reprints: List<String>) {
        if (!PriceAlerts.canNotify(ctx)) return
        val nm = NotificationManagerCompat.from(ctx)
        if (bans.isNotEmpty()) {
            val lines = bans.map { (deck, cards) -> "$deck: ${cards.joinToString(", ")}" }
            val title = if (bans.sumOf { it.second.size } == 1) "Card banned in your deck" else "Cards banned in your decks"
            val n = Notifications.builder(ctx, CHANNEL, "Bans and reprints")
                .setContentTitle(title)
                .setContentText(lines.first())
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(Notifications.openApp(ctx, "collection", 21))
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(BAN_ID, n) }
            runCatching { com.mtgscanner.data.NotificationLog.add(ctx, title, lines.joinToString("\n"), "collection") }
        }
        if (reprints.isNotEmpty()) {
            val title = if (reprints.size == 1) "Wishlist card reprinted" else "${reprints.size} wishlist cards reprinted"
            val n = Notifications.builder(ctx, CHANNEL, "Bans and reprints")
                .setContentTitle(title)
                .setContentText(reprints.joinToString(", "))
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(reprints.joinToString("\n")))
                .setContentIntent(Notifications.openApp(ctx, "collection", 22))
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(REPRINT_ID, n) }
            runCatching { com.mtgscanner.data.NotificationLog.add(ctx, title, reprints.joinToString("\n"), "collection") }
        }
    }

    companion object {
        private const val WORK = "weekly-watch"
        private const val CHANNEL = "watch"
        private const val BAN_ID = 1101
        private const val REPRINT_ID = 1102

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WatchWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
