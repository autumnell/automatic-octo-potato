package com.mtgscanner.data

import com.mtgscanner.data.db.PricePoint
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup file: a zip with the collection as JSON, price history as one line per point (written and read
 * a row at a time, so big collections don't run out of memory) and card photos. Older backups were plain
 * JSON; [read] takes both. Pure Kotlin, so it can be tested without a phone.
 */
object BackupArchive {
    const val JSON = "backup.json"
    const val PRICES = "prices.tsv"
    const val PHOTOS = "photos/"

    /**
     * Writes [b] (its price history goes in [prices] instead), every point [prices] emits, and [photos].
     * Closes [out].
     */
    fun write(out: OutputStream, b: Backup, photos: List<File>, prices: ((PricePoint) -> Unit) -> Unit) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON))
            BackupCodec.encode(b.copy(priceHistory = emptyList()), KeepOpen(zip))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(PRICES))
            val w = zip.writer(Charsets.UTF_8)
            b.priceHistory.forEach { w.write(line(it)) }
            prices { w.write(line(it)) }
            w.flush()
            zip.closeEntry()
            // Photos are JPEGs already: written without compressing them again, which only cost time.
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            photos.filter { it.isFile }.distinctBy { it.name }.forEach { f ->
                zip.putNextEntry(ZipEntry(PHOTOS + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads a backup, zip or old plain JSON, and checks it (see [BackupCodec.check]). A zip's price history and
     * photos are unpacked into [stageDir] (emptied first) and named by [Backup.staged]. Throws [BadBackupException].
     */
    fun read(input: InputStream, stageDir: File, checked: Boolean = true): Backup {
        val buf = BufferedInputStream(input)
        buf.mark(4)
        val head = ByteArray(2)
        val n = buf.read(head)
        buf.reset()
        if (n < 2 || head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) return BackupCodec.decode(buf, checked)
        stageDir.deleteRecursively()
        stageDir.mkdirs()
        var b: Backup? = null
        try {
            ZipInputStream(buf).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == JSON -> b = BackupCodec.decode(KeepOpenIn(zip), checked = false)
                        e.name == PRICES -> File(stageDir, PRICES).outputStream().use { zip.copyTo(it) }
                        e.name.startsWith(PHOTOS) && !e.isDirectory -> {
                            // Only a plain file name: nothing in a zip may write outside the folder.
                            val name = e.name.substringAfterLast('/')
                            if (name.isNotBlank() && name != "." && name != "..") {
                                val dir = File(stageDir, "photos").apply { mkdirs() }
                                File(dir, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw BadBackupException("That backup file is damaged")
        }
        val found = b ?: throw BadBackupException("That isn't a backup file")
        if (checked) BackupCodec.check(found)
        return found.copy(staged = stageDir.path)
    }

    /** The staged photos of a read zip backup, by file name. */
    fun stagedPhotos(b: Backup): List<File> =
        b.staged?.let { File(it, "photos").listFiles()?.toList() }.orEmpty()

    /** Calls [block] with the staged price history of a read zip backup, [size] points at a time. */
    inline fun forEachPriceChunk(b: Backup, size: Int = 2000, block: (List<PricePoint>) -> Unit) {
        val f = b.staged?.let { File(it, PRICES) }?.takeIf { it.isFile } ?: return
        f.bufferedReader().useLines { lines ->
            for (chunk in lines.mapNotNull { parseLine(it) }.chunked(size)) block(chunk)
        }
    }

    fun line(p: PricePoint): String = "${p.scryfallId}\t${p.day}\t${p.usd ?: ""}\t${p.usdFoil ?: ""}\n"

    fun parseLine(s: String): PricePoint? {
        val f = s.split('\t')
        if (f.size < 4 || f[0].isBlank() || f[1].isBlank()) return null
        return PricePoint(f[0], f[1], f[2].toDoubleOrNull(), f[3].toDoubleOrNull())
    }

    /** Lets the JSON writer finish without closing the zip around it. */
    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }

    private class KeepOpenIn(input: InputStream) : FilterInputStream(input) {
        override fun close() {}
    }
}
