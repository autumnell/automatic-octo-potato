package com.mtgscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mtgscanner.data.ScryfallApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Base64

/**
 * Set symbol shapes, to tell printings apart by the symbol on the type line when the footer can't be read.
 * Scryfall's set icons (SVG) are drawn small once, the first time a set is needed, and only their shapes are
 * kept, in filesDir/set-symbols.txt (about 400 bytes a set).
 */
class SetSymbols(private val context: Context, private val api: ScryfallApi) {
    private val file = File(context.filesDir, "set-symbols.txt")
    private val lock = Mutex()
    /** Set code to (icon address, shape). */
    private val prints = HashMap<String, Pair<String, SymbolPrint>>()
    private var loaded = false
    /** Set code to icon address, from Scryfall's set list; fetched once a session, when first needed. */
    private var icons: Map<String, String>? = null

    /**
     * Which of [codes] the [target] symbol belongs to, when that's clear: the set codes sharing the winning icon
     * (promo and sub-sets often reuse their parent's). Null when unsure or the icons can't be had.
     */
    suspend fun pick(target: SymbolPrint, codes: Collection<String>): Set<String>? {
        val wanted = codes.map { it.lowercase() }.distinct()
        if (wanted.size < 2) return null
        val refs = withTimeoutOrNull(TIMEOUT_MS) { lock.withLock { printsFor(wanted) } } ?: return null
        // Sets sharing one icon are one choice.
        val byIcon = refs.entries.groupBy({ it.value.first }, { it.key })
        if (byIcon.size < 2) return null
        val choices = byIcon.map { (url, sets) -> url to refs.getValue(sets.first()).second }
        val url = SetSymbol.pick(target, choices) ?: return null
        return byIcon.getValue(url).toSet()
    }

    private suspend fun printsFor(codes: List<String>): Map<String, Pair<String, SymbolPrint>> {
        if (!loaded) { load(); loaded = true }
        val missing = codes.filter { it !in prints }
        if (missing.isNotEmpty()) {
            val known = icons ?: runCatching { api.sets().mapNotNull { s -> s.iconSvgUri?.let { s.code.lowercase() to it } }.toMap() }
                .getOrNull()?.also { icons = it }
            if (known != null) {
                var added = false
                for (code in missing) {
                    val url = known[code] ?: continue
                    // Another set with the same icon is already drawn.
                    val same = prints.values.firstOrNull { it.first == url }
                    val print = same?.second ?: draw(url) ?: continue
                    prints[code] = url to print
                    added = true
                }
                if (added) save()
            }
        }
        return codes.mapNotNull { c -> prints[c]?.let { c to it } }.toMap()
    }

    /** Draws the icon black on white and takes its shape. */
    private suspend fun draw(url: String): SymbolPrint? {
        val request = ImageRequest.Builder(context).data(url).size(ICON_PX, ICON_PX).allowHardware(false).build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
        return withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(ICON_PX + 2 * PAD, ICON_PX + 2 * PAD, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_PX
                val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_PX
                val s = ICON_PX.toFloat() / maxOf(iw, ih)
                val w = (iw * s).toInt()
                val h = (ih * s).toInt()
                val l = PAD + (ICON_PX - w) / 2
                val t = PAD + (ICON_PX - h) / 2
                drawable.setBounds(l, t, l + w, t + h)
                drawable.draw(canvas)
                SetSymbol.fingerprint(CardWarp.luma(bmp), bmp.width, bmp.height)
            } finally {
                bmp.recycle()
            }
        }
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching
            file.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return@forEach
                val print = runCatching { SymbolPrint.fromBytes(Base64.getDecoder().decode(parts[2])) }.getOrNull() ?: return@forEach
                prints[parts[0]] = parts[1] to print
            }
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        runCatching {
            val text = prints.entries.joinToString("\n") { (code, v) ->
                "$code\t${v.first}\t${Base64.getEncoder().encodeToString(v.second.toBytes())}"
            }
            val part = File(file.parentFile, file.name + ".part")
            part.writeText(text)
            if (!part.renameTo(file)) { file.delete(); part.renameTo(file) }
        }
    }

    private companion object {
        const val ICON_PX = 64
        const val PAD = 4
        /** A lookup waits at most this long for icons; it goes on without them after. */
        const val TIMEOUT_MS = 6_000L
    }
}
