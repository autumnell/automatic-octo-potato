package com.mtgscanner.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import com.mtgscanner.scan.CardTextParser
import com.mtgscanner.util.CardNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.Inflater

/** What the card list on the phone is doing, for Settings. */
data class LocalCardsStatus(
    val cards: Int = 0,
    /** Day of the last update ("2026-10-24"); empty before the first download. */
    val updated: String = "",
    /** 0..1 while the first download runs, else null. */
    val progress: Float? = null,
    val failed: Boolean = false,
    /** Why the last download failed, in a few words. */
    val reason: String? = null,
    /** Size of the saved list in MB, once it's on the phone. */
    val mb: Int = 0,
)

/** A download failure in a few words, for Settings. */
internal fun downloadReason(e: Throwable): String = when (e) {
    is android.database.sqlite.SQLiteFullException -> "phone storage is full"
    is java.net.UnknownHostException, is java.net.ConnectException -> "no connection"
    is java.net.SocketTimeoutException -> "the connection dropped"
    is java.io.IOException -> if (e is ScryfallException) "Scryfall is busy" else "the connection dropped"
    else -> e.message?.takeIf { it.length <= 40 } ?: "something went wrong"
}

/** The card list's status line, shared by Settings › Data and the Scan settings. */
fun LocalCardsStatus.label(on: Boolean): String = when {
    !on -> "Matches cards without waiting on Scryfall, even offline. A large download, best on Wi-Fi."
    progress != null -> "Downloading… ${(progress * 100).toInt()}% · carries on if you leave the app"
    cards > 0 -> "${"%,d".format(cards)} cards" + (if (mb > 0) " · $mb MB" else "") + " · new ones added when the app opens"
    failed -> "Download failed" + (reason?.let { ": $it" } ?: "") + ". Try again, or it retries next time on Wi-Fi."
    else -> "Downloads next time the app opens on Wi-Fi. A large download."
}

/** Picking among a card's printings found on the phone, kept free of Android so it can be tested. */
object LocalPick {
    /**
     * The printing a name alone stands for, like Scryfall's name lookup: English first, then regular printings
     * over promos, then the newest.
     */
    fun newest(cards: List<ScryfallCard>): ScryfallCard? =
        cards.sortedWith(
            compareBy<ScryfallCard> { if (isArtCard(it)) 1 else 0 }
                .thenBy { if (it.lang == "en") 0 else 1 }
                .thenBy { if (it.promo) 1 else 0 }
                .thenByDescending { it.releasedAt ?: "" },
        ).firstOrNull()

    /**
     * Art Series cards: the artwork with a signature spot, no title, rules or footer. Their front name is the
     * card's, so a name read on a card must never land on one.
     */
    fun isArtCard(c: ScryfallCard): Boolean = c.layout == "art_series"

    /** [cards] without Art Series cards, unless that's all there is. */
    fun realCards(cards: List<ScryfallCard>): List<ScryfallCard> = cards.filterNot(::isArtCard).ifEmpty { cards }

    /** The fields a scan needs, without the ones only the card page shows (it reads those fresh from Scryfall). */
    fun slim(c: ScryfallCard): ScryfallCard = c.copy(legalities = emptyMap(), relatedUris = emptyMap(), purchaseUris = emptyMap())

    /** Days to look back when asking for new cards: from the day before the last update, so a late addition isn't missed. */
    fun since(updated: String): String = runCatching { LocalDate.parse(updated).minusDays(1).toString() }.getOrDefault(updated)
}

/**
 * Every printing on Scryfall, kept on the phone so scans are matched without waiting on Scryfall, and offline.
 * The first time, the whole list is downloaded once (on Wi-Fi). After that, only cards released since the last
 * update are asked for, when the app opens.
 */
class LocalCards(private val context: Context, private val api: ScryfallApi) {
    private val dbFile get() = context.getDatabasePath(DB_NAME)
    private val tmpFile get() = context.getDatabasePath("$DB_NAME.tmp")
    private val prefs get() = context.getSharedPreferences("local_cards", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()

    @Volatile private var db: SQLiteDatabase? = null
    private val _status = MutableStateFlow(LocalCardsStatus())
    val status: StateFlow<LocalCardsStatus> = _status

    /** On unless turned off in Settings › Data or the Scan settings. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ON, true)
        set(v) { prefs.edit().putBoolean(KEY_ON, v).apply() }

    /** True once the list is on the phone. */
    val ready: Boolean get() = db != null

    /** The list's file is on the phone (it may not be open yet). */
    val saved: Boolean get() = dbFile.exists()

    private fun open(): SQLiteDatabase? {
        db?.let { return it }
        if (!dbFile.exists()) return null
        return runCatching { SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE) }.getOrNull()?.also { db = it }
    }

    /**
     * Opens the saved list, then downloads it or adds new cards. Call when the app opens.
     * [force] ("Download now" in Settings) downloads on mobile data too and checks for new cards again today.
     */
    suspend fun sync(force: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!enabled) return@withContext
            tmpFile.delete()
            val d = open()
            _status.value = LocalCardsStatus(count(d), prefs.getString(KEY_DAY, "").orEmpty(), mb = if (d != null) sizeMb() else 0)
            val today = LocalDate.now().toString()
            val updated = prefs.getString(KEY_DAY, "").orEmpty()
            if (d == null) {
                // The whole list is a large download: only on Wi-Fi (or another unmetered network).
                if (!force && !unmetered()) return@withContext
                runCatching { downloadAll() }.onFailure { e ->
                    SQLiteDatabase.deleteDatabase(tmpFile)
                    _status.value = _status.value.copy(progress = null, failed = true, reason = downloadReason(e))
                }
            } else if (force || updated != today) {
                runCatching { addNew(LocalPick.since(updated)) }
            }
        }
    }

    /** Turns the list off and deletes it. */
    suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) {
            db?.close()
            db = null
            SQLiteDatabase.deleteDatabase(dbFile)
            tmpFile.delete()
            prefs.edit().remove(KEY_DAY).apply()
            _status.value = LocalCardsStatus()
        }
    }

    /** On Wi-Fi (or another network that isn't metered). */
    fun onWifi(): Boolean = unmetered()

    /** Art matching for your biggest sets, downloaded on Wi-Fi (on unless turned off in Settings › Data). */
    var artAuto: Boolean
        get() = prefs.getBoolean(KEY_ART, true)
        set(v) { prefs.edit().putBoolean(KEY_ART, v).apply() }

    /** The day the biggest sets' art was last checked; once a week is enough. */
    var artCheckedDay: String
        get() = prefs.getString(KEY_ART_DAY, "").orEmpty()
        set(v) { prefs.edit().putString(KEY_ART_DAY, v).apply() }

    private fun unmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return !cm.isActiveNetworkMetered
    }

    private fun count(d: SQLiteDatabase?): Int =
        d?.let { runCatching { it.rawQuery("SELECT COUNT(*) FROM cards", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }.getOrNull() } ?: 0

    private fun create(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE IF NOT EXISTS cards (id TEXT PRIMARY KEY, setc TEXT, num TEXT, oracle TEXT, nk TEXT, fk TEXT, released TEXT, priced TEXT, data BLOB)")
        d.execSQL("CREATE INDEX IF NOT EXISTS cards_setnum ON cards(setc, num)")
        d.execSQL("CREATE INDEX IF NOT EXISTS cards_oracle ON cards(oracle)")
        d.execSQL("CREATE INDEX IF NOT EXISTS cards_nk ON cards(nk)")
        d.execSQL("CREATE INDEX IF NOT EXISTS cards_fk ON cards(fk)")
    }

    private fun put(d: SQLiteDatabase, c: ScryfallCard, day: String) {
        if (c.digital) return
        val v = ContentValues(9)
        v.put("id", c.id)
        v.put("setc", c.set.lowercase())
        v.put("num", c.collectorNumber.lowercase())
        v.put("oracle", c.oracleId)
        v.put("nk", CardNames.key(c.name))
        v.put("fk", CardNames.key(CardNames.front(c.name)))
        v.put("released", c.releasedAt)
        v.put("priced", day)
        v.put("data", pack(json.encodeToString(ScryfallCard.serializer(), LocalPick.slim(c))))
        d.insertWithOnConflict("cards", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Downloads Scryfall's list of every printing into a new file, then puts it in place. */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun downloadAll() {
        // The list needs a few hundred MB while it's built (the old one is kept until the new one is done).
        val free = runCatching { context.filesDir.usableSpace }.getOrDefault(Long.MAX_VALUE)
        if (free < MIN_FREE) error("not enough free space")
        val file = api.bulkDownloadUri("default_cards") ?: error("Scryfall's card list wasn't found")
        _status.value = _status.value.copy(progress = 0f, failed = false)
        val client = sharedHttpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
        val req = Request.Builder().url(file.uri).header("User-Agent", com.mtgscanner.util.UserAgent.value).build()
        tmpFile.parentFile?.mkdirs()
        val d = SQLiteDatabase.openOrCreateDatabase(tmpFile, null)
        val day = LocalDate.now().toString()
        try {
            create(d)
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed ${resp.code}")
                val body = resp.body ?: error("Empty download")
                // Bytes as they arrive (still compressed), against the file's size.
                val total = body.contentLength().takeIf { it > 0 } ?: file.size
                val counting = CountingStream(body.byteStream())
                val input = gunzipIfNeeded(counting)
                var n = 0
                d.beginTransaction()
                try {
                    for (c in json.decodeToSequence(input, ScryfallCard.serializer(), DecodeSequenceMode.AUTO_DETECT)) {
                        put(d, c, day)
                        n++
                        if (n % 2000 == 0) {
                            // Turned off in Settings while downloading: stop and leave nothing behind.
                            if (!enabled) error("Turned off")
                            d.setTransactionSuccessful(); d.endTransaction(); d.beginTransaction()
                            // Without any size, progress is shown by cards against a typical count.
                            val p = if (total > 0) counting.count.toFloat() / total else n / 110_000f
                            _status.value = _status.value.copy(progress = p.coerceIn(0f, 0.99f))
                        }
                    }
                    d.setTransactionSuccessful()
                } finally {
                    d.endTransaction()
                }
                if (n < 10_000) error("Too few cards")
            }
        } finally {
            d.close()
        }
        db?.close()
        db = null
        SQLiteDatabase.deleteDatabase(dbFile)
        if (!tmpFile.renameTo(dbFile)) error("Couldn't save the card list")
        val today = LocalDate.now().toString()
        prefs.edit().putString(KEY_DAY, today).apply()
        val opened = open()
        _status.value = LocalCardsStatus(count(opened), today, mb = sizeMb())
    }

    private fun sizeMb(): Int = (dbFile.length() / (1024 * 1024)).toInt()

    /** Asks Scryfall only for cards released since [since], and adds them. */
    private suspend fun addNew(since: String) {
        val found = api.searchAll("date>=$since include:extras", unique = "prints", maxPages = ADD_PAGES, order = "released")
        val today = LocalDate.now().toString()
        val d = open() ?: return
        if (found.isNotEmpty()) write(d) { found.forEach { put(d, it, today) } }
        // Every page full: more came out than one top-up fetches (newest first, so the oldest are missing). The
        // date stays put, so they aren't skipped for good; on Wi-Fi the whole list is fetched again instead.
        if (found.size >= ADD_PAGES * 175) {
            if (unmetered()) downloadAll()
            return
        }
        prefs.edit().putString(KEY_DAY, today).apply()
        _status.value = LocalCardsStatus(count(d), today, mb = sizeMb())
    }

    private inline fun write(d: SQLiteDatabase, block: () -> Unit) {
        d.beginTransaction()
        try { block(); d.setTransactionSuccessful() } finally { d.endTransaction() }
    }

    /** Saves today's prices for cards just fetched from Scryfall. */
    fun update(fresh: List<ScryfallCard>) {
        val d = db ?: return
        val today = LocalDate.now().toString()
        runCatching { write(d) { fresh.forEach { put(d, it, today) } } }
        fresh.forEach { staleIds.remove(it.id) }
    }

    /** Printings read from the list whose prices are a few days old (see [isStale]). */
    private val staleIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** The card came from the list with prices more than [STALE_DAYS] old: worth asking Scryfall for today's. */
    fun isStale(id: String): Boolean = id in staleIds

    private fun query(sql: String, vararg args: String): List<ScryfallCard> {
        val d = db ?: return emptyList()
        val oldDay = LocalDate.now().minusDays(STALE_DAYS).toString()
        return runCatching {
            d.rawQuery(sql, args).use { c ->
                val out = ArrayList<ScryfallCard>(c.count)
                while (c.moveToNext()) {
                    val card = runCatching { json.decodeFromString(ScryfallCard.serializer(), unpack(c.getBlob(0))) }.getOrNull() ?: continue
                    if ((c.getString(1) ?: "") < oldDay) staleIds.add(card.id)
                    out += card
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    /** The printing with that set code and collector number, or null when the list doesn't have it (or isn't on the phone). */
    fun bySetAndNumber(set: String, number: String): ScryfallCard? =
        query("SELECT data, priced FROM cards WHERE setc = ? AND num = ? LIMIT 1", set.lowercase(), number.lowercase()).firstOrNull()

    /** The printing a card name stands for ([set]'s, when given), matching the whole name or its front face. */
    fun named(name: String, set: String? = null): ScryfallCard? {
        val k = CardNames.key(name)
        if (k.isEmpty()) return null
        val all = if (set != null) query("SELECT data, priced FROM cards WHERE (nk = ? OR fk = ?) AND setc = ?", k, k, set.lowercase())
        else query("SELECT data, priced FROM cards WHERE nk = ? OR fk = ?", k, k)
        return LocalPick.newest(LocalPick.realCards(all))
    }

    /** Whether the list has any card from [set]: a set it doesn't know is newer than the list, so Scryfall is asked. */
    fun hasSet(set: String): Boolean {
        val d = db ?: return false
        return runCatching { d.rawQuery("SELECT 1 FROM cards WHERE setc = ? LIMIT 1", arrayOf(set.lowercase())).use { it.moveToFirst() } }.getOrDefault(false)
    }

    /** One printing by its Scryfall id, or null when the list doesn't have it. */
    fun byId(id: String): ScryfallCard? = query("SELECT data, priced FROM cards WHERE id = ? LIMIT 1", id).firstOrNull()

    /** Every printing of exactly [name] (or of a card whose front face is [name]). */
    fun allNamed(name: String): List<ScryfallCard> {
        val k = CardNames.key(name)
        if (k.isEmpty()) return emptyList()
        return query("SELECT data, priced FROM cards WHERE nk = ? OR fk = ?", k, k)
    }

    /** Printings whose name holds every one of [words] (letters and digits only), up to [limit]. */
    fun nameHas(words: List<String>, limit: Int = 3000): List<ScryfallCard> {
        val keys = words.map { CardTextParser.normalize(it) }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return emptyList()
        val where = keys.joinToString(" AND ") { "nk LIKE ?" }
        return query("SELECT data, priced FROM cards WHERE $where LIMIT $limit", *keys.map { "%$it%" }.toTypedArray())
    }

    /** Names starting with [prefix], for suggestions as you type: front faces, one per card, shortest first. */
    fun namesStarting(prefix: String, limit: Int = 8): List<String> {
        val d = db ?: return emptyList()
        val k = CardNames.key(prefix)
        if (k.isEmpty()) return emptyList()
        return runCatching {
            d.rawQuery("SELECT oracle, data FROM cards WHERE nk LIKE ? GROUP BY coalesce(oracle, nk) ORDER BY length(nk), nk LIMIT ?", arrayOf("$k%", "$limit")).use { c ->
                val out = ArrayList<String>()
                while (c.moveToNext()) {
                    runCatching { json.decodeFromString(ScryfallCard.serializer(), unpack(c.getBlob(1))).name }.getOrNull()?.let { out += it }
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    /** Every card in a set, tokens and extras included. */
    fun inSet(set: String): List<ScryfallCard> = query("SELECT data, priced FROM cards WHERE setc = ?", set.lowercase())

    /** Printings released on or after [day] (yyyy-mm-dd), newest first, at most [limit]. */
    fun releasedSince(day: String, limit: Int = 5000): List<ScryfallCard> =
        query("SELECT data, priced FROM cards WHERE released >= ? ORDER BY released DESC LIMIT $limit", day)

    /** Every printing's id, newest first. */
    fun idsNewestFirst(): List<String> {
        val d = db ?: return emptyList()
        return runCatching {
            d.rawQuery("SELECT id FROM cards ORDER BY released DESC", null).use { c ->
                val out = ArrayList<String>(c.count)
                while (c.moveToNext()) out += c.getString(0)
                out
            }
        }.getOrDefault(emptyList())
    }

    /** Every printing of a card, by oracle id. */
    fun printings(oracleId: String): List<ScryfallCard> = query("SELECT data, priced FROM cards WHERE oracle = ?", oracleId)

    private class CountingStream(private val inner: java.io.InputStream) : java.io.FilterInputStream(inner) {
        var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        private const val DB_NAME = "local-cards.db"
        private const val KEY_DAY = "updated"
        /** Search pages a top-up fetches at most (175 printings each). */
        private const val ADD_PAGES = 30
        private const val KEY_ON = "on"
        private const val KEY_ART = "art"
        private const val KEY_ART_DAY = "art_day"
        private const val STALE_DAYS = 2L
        /** Free space needed before downloading the list. */
        private const val MIN_FREE = 600L * 1024 * 1024

        fun pack(s: String): ByteArray {
            val input = s.toByteArray(Charsets.UTF_8)
            val d = Deflater(Deflater.BEST_SPEED)
            d.setInput(input)
            d.finish()
            val out = ByteArrayOutputStream(input.size / 2 + 16)
            val buf = ByteArray(4096)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            return out.toByteArray()
        }

        fun unpack(b: ByteArray): String {
            val inf = Inflater()
            inf.setInput(b)
            val out = ByteArrayOutputStream(b.size * 3)
            val buf = ByteArray(4096)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
            inf.end()
            return out.toString(Charsets.UTF_8.name())
        }
    }
}

/** Scryfall's list now comes as a .jsonl.gz file: unzips it unless it arrives already unzipped. */
internal fun gunzipIfNeeded(raw: java.io.InputStream): java.io.InputStream {
    val b = java.io.BufferedInputStream(raw, 64 * 1024)
    b.mark(2)
    val first = b.read()
    val second = b.read()
    b.reset()
    return if (first == 0x1f && second == 0x8b) java.util.zip.GZIPInputStream(b, 64 * 1024) else b
}
