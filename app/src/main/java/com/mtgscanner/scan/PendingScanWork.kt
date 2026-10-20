package com.mtgscanner.scan

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgscanner.MtgApp

/** Looks up and adds the cards saved while offline, as soon as the phone is back online. */
class PendingScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as MtgApp).repository
        return runCatching { repo.resolvePending() }.fold({ Result.success() }, { Result.retry() })
    }
}

object PendingScanWork {
    private const val WORK = "pending-scans"

    /** Runs the lookup once there's a network connection. Safe to call often: one run waits at a time. */
    fun schedule(context: Context) {
        val req = OneTimeWorkRequestBuilder<PendingScanWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, req)
    }
}
