package com.mtgscanner.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mtgscanner.BuildConfig
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.util.CopyText
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Keeps the stack trace of the last crashes in a file on the phone, so it can be shared from Settings.
 * Release builds are minified: the trace is read with that build's mapping.txt from its GitHub release.
 */
object CrashLog {
    private const val FILE_NAME = "crash.txt"
    private const val MAX_CHARS = 60_000

    @Volatile private var installed = false

    /** Bumped when the file changes, so Settings re-reads it. */
    private var changes by mutableIntStateOf(0)

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val f = file(context)
        val older = runCatching { if (f.exists()) f.readText() else "" }.getOrDefault("")
        val entry = buildString {
            appendLine("${buildLabel(BuildConfig.BUILD_NUMBER, BuildConfig.BUILD_DATE)} (${BuildConfig.VERSION_NAME})")
            appendLine("Time: ${ZonedDateTime.now()}")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine(error.stackTraceToString())
        }
        // Newest first; older crashes are kept until the file reaches its size limit.
        val text = if (older.isBlank()) entry else entry + "\n----\n\n" + older
        f.writeText(text.take(MAX_CHARS))
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
        changes++
    }

    fun restore(context: Context, text: String) {
        runCatching { file(context).writeText(text) }
        changes++
    }

    @Composable
    internal fun current(context: Context): String? {
        val stamp = changes
        return remember(stamp) { read(context) }
    }
}

/** "Grimoire build 412 · Oct 21"; local builds (number 0) say "dev build". */
fun buildLabel(number: Int, isoDate: String): String {
    val date = runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    }.getOrDefault(isoDate)
    val build = if (number > 0) "build $number" else "dev build"
    return "Grimoire $build · $date"
}

/** Settings › About: shown only after a crash. Copy puts the whole log on the clipboard; Share sends the trace as text; Clear can be undone. */
@Composable
fun CrashReportRow() {
    val ctx = LocalContext.current
    val report = CrashLog.current(ctx) ?: return
    HorizontalDivider(color = Palette.Rule)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Crash report", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { CopyText.copy(ctx, report, "Crash report") }) { Text("Copy") }
        TextButton(onClick = {
            com.mtgscanner.util.shareText(ctx, "Grimoire crash report", report)
        }) { Text("Share") }
        TextButton(onClick = {
            CrashLog.clear(ctx)
            Undo.offer("Crash report cleared") { CrashLog.restore(ctx, report) }
        }) { Text("Clear") }
    }
}
