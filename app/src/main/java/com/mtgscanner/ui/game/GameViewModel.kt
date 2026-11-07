package com.mtgscanner.ui.game

import com.mtgscanner.game.TableLayouts
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.game.Game
import com.mtgscanner.game.GameFormat
import com.mtgscanner.game.Player
import com.mtgscanner.game.GameRecord
import com.mtgscanner.game.GameState
import com.mtgscanner.data.db.CollectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** How the player panels look. */
enum class TableStyle(val label: String) { BOLD("Bold"), GRADIENT("Gradient"), DARK("Dark") }

/** The setup of a started game, kept so the same group can start again in one tap. */
data class LastSetup(val format: GameFormat, val players: Int, val life: Int, val partners: Boolean, val layout: Int = 0) {
    fun encode() = "${format.name}|$players|$life|$partners|$layout"

    companion object {
        fun parse(s: String): LastSetup? = runCatching {
            val p = s.split('|')
            val n = p[1].toInt().coerceIn(1, Player.MAX_PLAYERS)
            LastSetup(GameFormat.valueOf(p[0]), n, p[2].toInt(), p[3].toBoolean(), TableLayouts.valid(n, p.getOrNull(4)?.toIntOrNull() ?: 0))
        }.getOrNull()
    }
}

/** A card from your collection that can go behind a player's life total. */
data class ArtChoice(val name: String, val set: String, val art: String)

/** The life counter. The game in progress is saved, so it survives leaving the app. */
/** What random player art is drawn from: creature and planeswalker art at high resolution, no jokes or tokens. */
private const val RANDOM_ART = "(t:creature or t:planeswalker) is:hires -is:funny -t:token game:paper"

class GameViewModel(context: Context, private val repo: CollectionRepository) : ViewModel() {
    private val prefs = context.applicationContext.getSharedPreferences("game", Context.MODE_PRIVATE)
    /** Finished games live in their own file, so saving the game in progress doesn't rewrite them. */
    private val recordPrefs = context.applicationContext.getSharedPreferences("game_records", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val recordsSerializer = ListSerializer(GameRecord.serializer())

    private val _game = MutableStateFlow(
        prefs.getString("current", null)?.let { runCatching { json.decodeFromString(GameState.serializer(), it).normalized() }.getOrNull() },
    )
    val game: StateFlow<GameState?> = _game

    /** Names used last time, so the same group doesn't retype them. */
    val lastNames: List<String> = prefs.getString("names", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    /** The format, seats and life of the last game started, for "Same as last time". */
    var lastSetup: LastSetup? = prefs.getString("last_setup", null)?.let(LastSetup::parse)
        private set

    private val _style = MutableStateFlow(runCatching { TableStyle.valueOf(prefs.getString("style", null) ?: "") }.getOrDefault(TableStyle.BOLD))
    val style: StateFlow<TableStyle> = _style
    fun setStyle(s: TableStyle) { _style.value = s; prefs.edit().putString("style", s.name).apply() }

    /** The short how-to on the table shows once, the first time a game opens; the game menu shows it again. */
    var tutorialSeen: Boolean = prefs.getBoolean("tutorial_seen", false)
        private set
    fun tutorialDone() { tutorialSeen = true; prefs.edit().putBoolean("tutorial_seen", true).apply() }

    /** Finished games, newest first. */
    private val _records = MutableStateFlow(
        loadRecords()?.let { runCatching { json.decodeFromString(recordsSerializer, it) }.getOrNull() }.orEmpty(),
    )
    val records: StateFlow<List<GameRecord>> = _records

    /** Reads saved records, moving them out of the old shared "game" file the first time. */
    private fun loadRecords(): String? {
        recordPrefs.getString("records", null)?.let { return it }
        val old = prefs.getString("records", null) ?: return null
        // commit, so the copy is on disk before the old one is removed.
        recordPrefs.edit().putString("records", old).commit()
        prefs.edit().remove("records").apply()
        return old
    }

    fun start(format: GameFormat, players: Int, life: Int, names: List<String>, partners: Boolean, layout: Int = 0) {
        saveIfWon()
        val clean = names.mapIndexed { i, n -> n.trim().ifEmpty { GameState.NAMES[i] } }
        val seats = TableLayouts.valid(players, layout)
        val setup = LastSetup(format, players, life, partners, seats)
        lastSetup = setup
        prefs.edit().putString("names", clean.joinToString("\n")).putString("last_setup", setup.encode()).apply()
        val g = GameState.new(format, players, life, clean, partners).copy(layout = seats)
        set(g)
        randomArt(g.startedAt, g.players.size)
    }

    /**
     * Gives each player random card art from Scryfall (a creature or planeswalker, cut from the card), as it
     * arrives. Without a connection the panels stay plain; art picked by hand at the table is kept.
     */
    private fun randomArt(game: Long, count: Int) {
        viewModelScope.launch {
            val arts = (0 until count).map {
                async { runCatching { repo.api.random(RANDOM_ART)?.artCropUrl }.getOrNull() }
            }.awaitAll()
            update { g ->
                if (g.startedAt != game) g
                else g.copy(players = g.players.mapIndexed { i, p -> if (p.art == null) p.copy(art = arts.getOrNull(i)) else p })
            }
        }
    }

    fun update(change: (GameState) -> GameState) {
        val g = _game.value ?: return
        set(change(g))
    }

    /** Ends the game. [winner] is saved to the stats when [save] is set and the game wasn't saved already. */
    fun end(save: Boolean = false, winner: Int? = null) {
        val g = _game.value
        if (save && g != null && !g.recorded) addRecord(g.toRecord(winner))
        saveJob?.cancel()
        // Locked with flush, so a save already under way can't bring the ended game back.
        synchronized(this) {
            pending.set(null)
            _game.value = null
            prefs.edit().remove("current").apply()
        }
    }

    /** Deletes one saved game, with Undo (it's one tap on a small icon). */
    fun deleteRecord(r: GameRecord) {
        val before = _records.value
        saveRecords(before - r)
        com.mtgscanner.ui.components.Undo.offer("Game deleted") { saveRecords(before) }
    }
    fun clearRecords() = saveRecords(emptyList())

    private fun addRecord(r: GameRecord) = saveRecords((listOf(r) + _records.value).take(200))

    private fun saveRecords(list: List<GameRecord>) {
        _records.value = list
        recordPrefs.edit().putString("records", json.encodeToString(recordsSerializer, list)).apply()
    }

    /**
     * Saves a finished game to the stats once, when the table moves on from it (Done, Rematch,
     * or a new game). Not the moment someone hits 0, so a mis-tap that's undone isn't counted.
     */
    private fun saveIfWon() {
        val g = _game.value ?: return
        if (g.winner != null && !g.recorded) {
            addRecord(g.toRecord())
            set(g.copy(recorded = true))
        }
    }

    /** The newest game not yet written to disk. */
    private val pending = java.util.concurrent.atomic.AtomicReference<GameState?>(null)
    private var saveJob: Job? = null

    /** Shows the change right away and writes it a moment later, so a swipe of 20 points is one write. */
    private fun set(g: GameState) {
        _game.value = g
        pending.set(g)
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.Default) {
            delay(SAVE_DELAY_MS)
            flush()
        }
    }

    /** Writes the game in progress now, if there's a change waiting. */
    @Synchronized
    fun flush() {
        val g = pending.getAndSet(null) ?: return
        if (_game.value == null) return
        prefs.edit().putString("current", json.encodeToString(GameState.serializer(), g)).apply()
    }

    override fun onCleared() {
        flush()
    }

    // Card art from your collection, for player backgrounds.

    private val _art = MutableStateFlow<List<ArtChoice>>(emptyList())
    val art: StateFlow<List<ArtChoice>> = _art

    private var artJob: Job? = null
    /** Collection cards with art, sorted, loaded when the picker opens rather than on every keystroke. */
    private var artCards: List<CollectionCard>? = null

    /** [refresh] reloads the collection, for when the picker opens. */
    fun searchArt(query: String, refresh: Boolean = false) {
        // A newer search replaces an older one, so results can't arrive out of order.
        artJob?.cancel()
        artJob = viewModelScope.launch {
            if (!refresh) delay(150)
            val q = query.trim()
            val all = artCards.takeUnless { refresh } ?: withContext(Dispatchers.Default) {
                runCatching { repo.allCards() }.getOrDefault(emptyList())
                    .filter { artCropUrl(it.imageUrl) != null }
                    .sortedWith(compareByDescending<CollectionCard> { it.typeLine.contains("Legendary") }.thenBy { it.name })
            }.also { artCards = it }
            _art.value = withContext(Dispatchers.Default) {
                all.asSequence()
                    .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                    .mapNotNull { c -> artCropUrl(c.imageUrl)?.let { ArtChoice(c.name, c.setCode.uppercase(), it) } }
                    .distinctBy { it.art }
                    .take(60)
                    .toList()
            }
        }
    }

    // Shorthands for the screen.
    fun life(i: Int, d: Int) = update { Game.life(it, i, d) }
    fun poison(i: Int, d: Int) = update { Game.poison(it, i, d) }
    fun commanderDamage(target: Int, from: Int, d: Int, partner: Boolean) = update { Game.commanderDamage(it, target, from, d, partner) }
    fun counter(i: Int, kind: String, d: Int) = update { Game.counter(it, i, kind, d) }
    fun undo() = update { Game.undo(it) }
    fun nextTurn() = update { Game.nextTurn(it) }
    fun restart() { saveIfWon(); update { Game.restart(it) } }

    private companion object {
        const val SAVE_DELAY_MS = 300L
    }
}
