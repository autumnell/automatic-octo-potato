package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Goal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** How far along a goal is. [missing] lists what's still needed. [error] when Scryfall couldn't be reached. */
data class GoalProgress(
    val goal: Goal,
    val have: Int = 0,
    val total: Int = 0,
    val missing: List<ScryfallCard> = emptyList(),
    val loaded: Boolean = false,
    val error: Boolean = false,
) {
    val fraction: Float get() = if (total > 0) have.toFloat() / total else 0f
    val done: Boolean get() = loaded && total > 0 && have >= total
}

/**
 * Goal targets from Scryfall, kept for the app run so Home doesn't fetch them again. Screens asking for the
 * same goal at once share one download, and a failed download isn't retried for [FAILURE_MS].
 */
object GoalCache {
    private const val FAILURE_MS = 30_000L
    private val cards = HashMap<String, List<ScryfallCard>>()
    private val inFlight = HashMap<String, Deferred<List<ScryfallCard>>>()
    private val failed = HashMap<String, Pair<Long, Throwable>>()

    fun get(key: String): List<ScryfallCard>? = synchronized(cards) { cards[key] }
    fun put(key: String, list: List<ScryfallCard>) = synchronized(cards) { cards[key] = list }

    /** The cached list, or the running download, or a new one in [scope]. Throws the last failure for a while. */
    suspend fun fetch(key: String, scope: CoroutineScope, load: suspend () -> List<ScryfallCard>): List<ScryfallCard> {
        get(key)?.let { return it }
        val job = synchronized(cards) {
            failed[key]?.let { (at, e) -> if (System.currentTimeMillis() - at < FAILURE_MS) throw e else failed.remove(key) }
            inFlight[key] ?: scope.async {
                try {
                    load().also { put(key, it) }
                } catch (e: Throwable) {
                    synchronized(cards) { failed[key] = System.currentTimeMillis() to e }
                    throw e
                } finally {
                    synchronized(cards) { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        return job.await()
    }
}

object Goals {
    const val PRINTINGS = "printings"
    const val SET = "set"
    val RARITIES = listOf("common", "uncommon", "rare", "mythic")

    fun rarities(goal: Goal): Set<String> = goal.rarities.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * Progress from the goal's target cards. Printings: each printing (Scryfall id) you own counts.
     * Set: each card name of the chosen rarities counts once, whichever version of it from that set you own.
     */
    fun progress(goal: Goal, targets: List<ScryfallCard>, owned: List<CollectionCard>): GoalProgress = when (goal.kind) {
        SET -> {
            val want = rarities(goal)
            val code = goal.target.lowercase()
            val list = targets.filter { want.isEmpty() || it.rarity in want }.distinctBy { it.name }
            val ownedNames = owned.filter { it.setCode.lowercase() == code }.map { it.name }.toSet()
            val missing = list.filter { it.name !in ownedNames }
            GoalProgress(goal, list.size - missing.size, list.size, missing, loaded = true)
        }
        else -> {
            val ownedIds = owned.map { it.scryfallId }.toSet()
            val missing = targets.filter { it.id !in ownedIds }
            GoalProgress(goal, targets.size - missing.size, targets.size, missing, loaded = true)
        }
    }
}

private fun cacheKey(goal: Goal) = "${goal.kind}:${goal.target.lowercase()}"

/** The goal's target cards from memory, or null when they haven't been fetched yet. */
fun cachedGoalCards(goal: Goal): List<ScryfallCard>? = GoalCache.get(cacheKey(goal))

/** Fetches (once per app run) the cards a goal counts. Throws when Scryfall can't be reached. */
suspend fun CollectionRepository.goalCards(goal: Goal): List<ScryfallCard> =
    GoalCache.fetch(cacheKey(goal), appScope) {
        when (goal.kind) {
            Goals.SET -> setCards(goal.target)
            else -> api.searchAll("oracleid:${goal.target} game:paper", unique = "prints", maxPages = 4)
        }
    }

/** Progress of one goal; on network failure, a progress marked [GoalProgress.error]. */
suspend fun CollectionRepository.goalProgress(goal: Goal, owned: List<CollectionCard>): GoalProgress {
    val targets = runCatching { goalCards(goal) }.getOrElse { return GoalProgress(goal, error = true) }
    return Goals.progress(goal, targets, owned)
}

/** Progress from cached targets only, no network: a quick first answer while the rest loads. */
fun cachedGoalProgress(goal: Goal, owned: List<CollectionCard>): GoalProgress =
    cachedGoalCards(goal)?.let { Goals.progress(goal, it, owned) } ?: GoalProgress(goal)

/** Every goal's progress, updated as the collection changes. Cached goals show at once; the rest follow. */
@OptIn(ExperimentalCoroutinesApi::class)
fun CollectionRepository.observeGoalProgress(): Flow<List<GoalProgress>> =
    combine(observeGoals(), observeAll()) { goals, cards -> goals to cards }
        .flatMapLatest { (goals, cards) ->
            flow {
                emit(goals.map { cachedGoalProgress(it, cards) })
                if (goals.any { cachedGoalCards(it) == null }) emit(goals.map { goalProgress(it, cards) })
            }
        }
