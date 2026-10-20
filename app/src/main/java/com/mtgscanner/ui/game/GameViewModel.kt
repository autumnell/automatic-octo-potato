package com.mtgscanner.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import com.mtgscanner.game.Game
import com.mtgscanner.game.GameFormat
import com.mtgscanner.game.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** The life counter. The game in progress is saved, so it survives leaving the app. */
class GameViewModel(context: Context) : ViewModel() {
    private val prefs = context.applicationContext.getSharedPreferences("game", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _game = MutableStateFlow(
        prefs.getString("current", null)?.let { runCatching { json.decodeFromString(GameState.serializer(), it) }.getOrNull() },
    )
    val game: StateFlow<GameState?> = _game

    /** Names used last time, so the same group doesn't retype them. */
    val lastNames: List<String> = prefs.getString("names", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun start(format: GameFormat, players: Int, life: Int, names: List<String>, partners: Boolean) {
        val clean = names.mapIndexed { i, n -> n.trim().ifEmpty { GameState.NAMES[i] } }
        prefs.edit().putString("names", clean.joinToString("\n")).apply()
        set(GameState.new(format, players, life, clean, partners))
    }

    fun update(change: (GameState) -> GameState) {
        val g = _game.value ?: return
        set(change(g))
    }

    fun end() {
        _game.value = null
        prefs.edit().remove("current").apply()
    }

    private fun set(g: GameState) {
        _game.value = g
        prefs.edit().putString("current", json.encodeToString(GameState.serializer(), g)).apply()
    }

    // Shorthands for the screen.
    fun life(i: Int, d: Int) = update { Game.life(it, i, d) }
    fun poison(i: Int, d: Int) = update { Game.poison(it, i, d) }
    fun commanderDamage(target: Int, from: Int, d: Int, partner: Boolean) = update { Game.commanderDamage(it, target, from, d, partner) }
    fun counter(i: Int, kind: String, d: Int) = update { Game.counter(it, i, kind, d) }
    fun undo() = update { Game.undo(it) }
    fun nextTurn() = update { Game.nextTurn(it) }
    fun restart() = update { Game.restart(it) }
}
