package com.mtgscanner.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.game.Game
import com.mtgscanner.game.GameFormat
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

/** A card from your collection that can go behind a player's life total. */
data class ArtChoice(val name: String, val set: String, val art: String)

/** The life counter. The game in progress is saved, so it survives leaving the app. */
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

    private val _style = MutableStateFlow(runCatching { TableStyle.valueOf(prefs.getString("style", null) ?: "") }.getOrDefault(TableStyle.BOLD))
    val style: StateFlow<TableStyle> = _style
    fun setStyle(s: TableStyle) { _style.value = s; prefs.edit().putString("style", s.name).apply() }

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

    fun start(format: GameFormat, players: Int, life: Int, names: List<String>, partners: Boolean) {
        saveIfWon()
        val clean = names.mapIndexed { i, n -> n.trim().ifEmpty { GameState.NAMES[i] } }
        prefs.edit().putString("names", clean.joinToString("\n")).apply()
        set(GameState.new(format, players, life, clean, partners))
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

    fun deleteRecord(r: GameRecord) = saveRecords(_records.value - r)
    fun clearRecords() = saveRecords(emptyList())

    private fun addRecord(r: GameRecord) = saveRecords((listOf(r) + _records.value).take(200))

    private fun saveRecords(list: List<GameRecord>) {
        _records.value = list
        recordPrefs.edit().putString("records", json.encodeToString(recordsSerializer, list)).apply()
    }

    /**
     * Saves a finished game to the stats once, when the table moves on from it (Done, Play again,
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
