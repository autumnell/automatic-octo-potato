package com.mtgscanner.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mtgscanner.MtgApp
import com.mtgscanner.data.artForBiggestSets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Downloads the card list (or adds new cards to it) as a background job, so the first large download keeps going
 * when you leave the app. While the whole list downloads, its progress shows in a quiet notification.
 */
class CardListWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as MtgApp).repository
        val local = repo.localCards
        val force = inputData.getBoolean(KEY_FORCE, false)
        return try {
            coroutineScope {
                // Only the first, large download shows a notification; adding a day's new cards is quick.
                val watch = if (!local.saved) launch {
                    runCatching { setForeground(info(null)) }
                    local.status.map { it.progress?.let { p -> (p * 100).toInt() } }.distinctUntilChanged().collect { pct ->
                        if (pct != null) runCatching { setForeground(info(pct)) }
                    }
                } else null
                try {
                    local.sync(force)
                } finally {
                    watch?.cancel()
                }
            }
            runCatching { repo.artForBiggestSets() }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(null)

    private fun info(pct: Int?): ForegroundInfo {
        val n = Notifications.progressBuilder(applicationContext)
            .setContentTitle("Downloading the card list")
            .setContentText(pct?.let { "$it%" } ?: "Starting…")
            .setProgress(100, pct ?: 0, pct == null)
            .setContentIntent(Notifications.openApp(applicationContext, "settings", 31))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTE_ID, n)
    }

    companion object {
        private const val WORK = "card-list"
        private const val KEY_FORCE = "force"
        private const val NOTE_ID = 1201

        /**
         * Starts the download or update. [force] ("Download now") goes on mobile data too and replaces a job
         * waiting for Wi-Fi; otherwise the first download waits for Wi-Fi (or another unmetered network).
         */
        fun start(context: Context, force: Boolean = false) {
            val saved = (context.applicationContext as MtgApp).repository.localCards.saved
            // Adding new cards to a saved list is small: any connection will do.
            val net = if (force || saved) NetworkType.CONNECTED else NetworkType.UNMETERED
            val req = OneTimeWorkRequestBuilder<CardListWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(net).build())
                .setInputData(workDataOf(KEY_FORCE to force))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }
    }
}
