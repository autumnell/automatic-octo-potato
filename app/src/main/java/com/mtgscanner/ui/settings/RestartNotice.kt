package com.mtgscanner.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.mtgscanner.MtgApp
import com.mtgscanner.data.BackupIO
import com.mtgscanner.ui.components.Undo

/**
 * A message kept across a restart. A restore that brings back settings restarts the app, which would
 * swallow its "Restored N cards" and the Undo with it; instead the message waits here and shows on the
 * next start, Undo and all.
 */
object RestartNotice {
    private const val PREFS = "restart_notice"
    private const val KEY_MESSAGE = "message"
    private const val KEY_UNDO = "undoRestore"

    /** Saves [message] for the next start; with [undoRestore], it offers Undo of the last restore. */
    fun stash(context: Context, message: String, undoRestore: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MESSAGE, message)
                .putBoolean(KEY_UNDO, undoRestore)
                .commit() // The app exits right after, so write now.
        }
    }

    /** The stashed message and whether it offers Undo, then clears it. */
    fun take(context: Context): Pair<String, Boolean>? = runCatching {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val message = p.getString(KEY_MESSAGE, null) ?: return@runCatching null
        val undo = p.getBoolean(KEY_UNDO, false)
        p.edit().clear().apply()
        message to undo
    }.getOrNull()

    /** Starts the app afresh, so every screen reads restored settings. */
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }
}

/** Shows a message stashed before a restart, once the app's snackbar is listening. Call from the app root. */
@Composable
fun RestartNoticeEffect() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        val (message, undo) = RestartNotice.take(ctx) ?: return@LaunchedEffect
        // Let the app root's Undo listener start first.
        kotlinx.coroutines.delay(600)
        val app = ctx.applicationContext
        if (!undo) { Undo.offer(message); return@LaunchedEffect }
        Undo.offer(message) {
            val repo = (app as MtgApp).repository
            val n = BackupIO.undoRestore(repo) ?: return@offer
            RestartNotice.stash(app, if (n == 1) "Back to 1 card" else "Back to $n cards", undoRestore = false)
            RestartNotice.restart(app)
        }
    }
}
