package com.mtgscanner.scan

import android.graphics.BitmapFactory
import com.mtgscanner.data.ScryfallApi
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.sharedHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A downloaded art index: the set and how many pictures it holds. [embedded]: built with an art model, [model]
 * the id of which one; files built without the chosen model are rebuilt once it's here.
 */
data class ArtSetInfo(val set: String, val cards: Int, val embedded: Boolean = false, val model: String? = null)

/** A download under way: [done] of [total] pictures for [set], and how many sets wait after it. */
data class ArtDownload(val set: String, val done: Int, val total: Int, val queued: Int)

/**
 * Art indexes kept in filesDir/art-index, one small file per set (about 50 bytes a card). Downloading one
 * fetches every card's small image from Scryfall once and keeps only its art hash. They're read from disk the
 * first time a match is needed, and again after a download or delete.
 */
class ArtIndexStore(filesDir: File, private val api: ScryfallApi, private val embedder: ArtEmbedder? = null) {
    private val dir = File(filesDir, "art-index")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = sharedHttpClient.newBuilder().readTimeout(15, TimeUnit.SECONDS).build()

    private val _sets = MutableStateFlow<List<ArtSetInfo>>(emptyList())
    val sets: StateFlow<List<ArtSetInfo>> = _sets

    private val _download = MutableStateFlow<ArtDownload?>(null)
    val download: StateFlow<ArtDownload?> = _download

    /** The last download that failed, to show; cleared when the next one starts. */
    private val _failed = MutableStateFlow<String?>(null)
    val failed: StateFlow<String?> = _failed

    @Volatile private var index: ArtIndex? = null
    /** Bumps on every change, so a load that started before it doesn't put back stale data. */
    @Volatile private var generation = 0
    private val loading = AtomicBoolean(false)
    private val queue = ArrayDeque<String>()
    private val queueLock = Mutex()
    /** A queue run is under way. Only changed while holding [queueLock]. */
    private var running = false

    init {
        scope.launch { refreshList() }
        // Another art model picked: embeddings already loaded can't be compared with the new one's.
        embedder?.onModelChanged = {
            rebuildAsked.set(false)
            changed()
        }
    }

    /** The id of the art model whose embeddings count now. */
    private val modelId: String? get() = embedder?.model?.id

    /** Any index downloaded: worth hashing the art of cards with no readable text. */
    val hasAny: Boolean get() = _sets.value.isNotEmpty()

    fun has(set: String): Boolean = _sets.value.any { it.set == set.lowercase() }

    private fun fileFor(set: String) = File(dir, set.lowercase().filter { it.isLetterOrDigit() } + ".bin")

    private fun refreshList() {
        val list = dir.listFiles { f -> f.name.endsWith(".bin") }.orEmpty().mapNotNull { f ->
            runCatching { f.inputStream().use { ArtIndexCodec.info(it) } }.getOrNull()
                ?.let { ArtSetInfo(it.set, it.cards, it.embedded, it.model) }
        }.sortedBy { it.set }
        _sets.value = list
    }

    /**
     * Matches art against the loaded indexes. Not loaded yet: starts loading and returns null, so the camera never
     * waits on the disk. With [setLock], only that set counts when its index is here. [embeddings] count only
     * when they came from the chosen art model ([model] is the id of the one that made them).
     */
    fun match(targets: LongArray, setLock: String?, embeddings: List<ByteArray> = emptyList(), model: String? = null): ArtHit? {
        val idx = index
        if (idx == null) {
            if (hasAny && loading.compareAndSet(false, true)) scope.launch { load() }
            return null
        }
        rebuildOld()
        val only = setLock?.lowercase()?.takeIf { idx.hasSet(it) }
        val chosen = embedder?.model ?: ArtModel.STANDARD
        val usable = if (model != null && model == chosen.id) embeddings else emptyList()
        return idx.match(targets, only, targetEmbeddings = usable, model = chosen)
    }

    /** Asked to rebuild the old indexes this session already. */
    private val rebuildAsked = AtomicBoolean(false)

    /**
     * Indexes built before the art model, or with the other model, have no embeddings that count. Once the chosen
     * model is here they're downloaded again in the background, one after another, the first time art is matched.
     * Until then those sets are matched by their hashes.
     */
    private fun rebuildOld() {
        val e = embedder ?: return
        if (!e.ready) { e.ensure(); return }
        val current = e.model.id
        val old = _sets.value.filter { !it.embedded || it.model != current }.map { it.set }
        if (old.isEmpty() || !rebuildAsked.compareAndSet(false, true)) return
        enqueue(old, rebuild = true)
    }

    private fun load() {
        val gen = generation
        val current = modelId
        try {
            val all = dir.listFiles { f -> f.name.endsWith(".bin") }.orEmpty().flatMap { f ->
                runCatching {
                    val file = f.inputStream().use { ArtIndexCodec.readFile(it) }
                    // Embeddings from another model can't be compared: that set is matched by hash until it's rebuilt.
                    if (file.model == current) file.entries else file.entries.map { it.copy(embedding = null) }
                }.getOrDefault(emptyList())
            }
            if (gen == generation) index = ArtIndex(all)
        } finally {
            loading.set(false)
        }
    }

    /** Drops the loaded index so the next match reads the files again. */
    private fun changed() {
        generation++
        refreshList()
        index = null
    }

    /** Downloads these sets one after another, in the background. Ones already here or queued are left alone. */
    fun enqueue(codes: Collection<String>, rebuild: Boolean = false) {
        scope.launch {
            queueLock.withLock {
                val current = _download.value?.set
                codes.map { c -> c.lowercase().filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }.distinct()
                    .filter { (rebuild || !fileFor(it).exists()) && it !in queue && it != current }
                    .forEach { queue.addLast(it) }
                if (!running && queue.isNotEmpty()) {
                    running = true
                    scope.launch { runQueue() }
                }
            }
        }
    }

    /** Stops after the set being downloaded and forgets the rest of the queue. */
    fun cancelQueue() {
        scope.launch { queueLock.withLock { queue.clear() } }
    }

    private suspend fun runQueue() {
        while (true) {
            val (set, left) = queueLock.withLock {
                val next = queue.removeFirstOrNull() ?: run {
                    _download.value = null
                    running = false
                    return
                }
                next to queue.size
            }
            _failed.value = null
            _download.value = ArtDownload(set, 0, 0, left)
            runCatching { downloadSet(set) }.onFailure { _failed.value = set }
        }
    }

    private suspend fun downloadSet(set: String) {
        val cards = api.searchAll("e:$set", unique = "prints", maxPages = 20, order = "set")
        if (cards.isEmpty()) throw IOException("No cards in $set")
        _download.update { it?.copy(total = cards.size) }
        // The art model first (downloaded once), so the index gets embeddings too; all of them from this one model.
        val model = embedder?.let { if (it.awaitReady()) it.model else null }
        val gate = Semaphore(PARALLEL)
        val entries = coroutineScope {
            cards.map { card ->
                async {
                    gate.withPermit { entriesFor(card, model) }.also { _download.update { d -> d?.copy(done = d.done + 1) } }
                }
            }.awaitAll().flatten()
        }
        // Most pictures missing means the connection dropped: don't keep a half index.
        if (entries.size < cards.size / 2) throw IOException("Download incomplete")
        dir.mkdirs()
        val target = fileFor(set)
        val part = File(dir, target.name + ".part")
        part.outputStream().use { ArtIndexCodec.write(set, entries, it, model = model?.id) }
        if (!part.renameTo(target)) {
            target.delete()
            if (!part.renameTo(target)) { part.delete(); throw IOException("Couldn't save") }
        }
        changed()
    }

    /** One entry per face picture (double-faced cards have two). */
    private fun entriesFor(card: ScryfallCard, model: ArtModel?): List<ArtEntry> {
        val urls = card.cardFaces.orEmpty().mapNotNull { it.imageUris?.small }.ifEmpty { listOfNotNull(card.smallImageUrl) }
        return urls.mapNotNull { url ->
            hashUrl(url, model)?.let { (hash, emb) -> ArtEntry(hash, card.id, card.name, card.set.lowercase(), card.collectorNumber, emb) }
        }
    }

    /** The picture's art hash, and its embedding by [model] when that's still the chosen one. */
    private fun hashUrl(url: String, model: ArtModel?): Pair<Long, ByteArray?>? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val bytes = resp.body?.bytes() ?: return@use null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
            try {
                val hash = ArtHashing.hashCardImage(bmp) ?: return@use null
                val emb = if (model == null) null else embedder?.let { ArtHashing.embedCardImage(bmp, it, model)?.values }
                hash to emb
            } finally {
                bmp.recycle()
            }
        }
    }.getOrNull()

    fun delete(set: String) {
        scope.launch {
            fileFor(set).delete()
            changed()
        }
    }

    private companion object {
        /** Pictures fetched at once. They come from Scryfall's image servers, not the rate-limited API. */
        const val PARALLEL = 6
    }
}
