package com.mtgscanner.work

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.WishTargets
import java.util.Locale

/** The daily job's wishlist check: "Ragavan is $38.50 · your target $40". */
object WishTargetAlerts {
    suspend fun check(context: Context, repo: CollectionRepository) {
        WishTargets.load(context)
        if (WishTargets.targets.isEmpty()) return
        val wishes = repo.dao.allWishlist()
        val hits = WishTargets.due(context, wishes.associate { it.oracleId to it.priceUsd })
        if (hits.isEmpty()) return
        val names = wishes.associate { it.oracleId to it.name }
        fun line(h: Pair<String, Double>) = String.format(
            Locale.US, "%s $%.2f · your target $%.2f", names[h.first].orEmpty(), h.second, WishTargets.targets[h.first] ?: 0.0,
        )
        val title = if (hits.size == 1) "${names[hits[0].first]} hit your price" else "${hits.size} wishlist cards hit your price"
        val style = NotificationCompat.InboxStyle()
        hits.take(6).forEach { style.addLine(line(it)) }
        if (hits.size > 6) style.setSummaryText("and ${hits.size - 6} more")
        val n = Notifications.builder(context, "prices", "Price alerts")
            .setContentTitle(title)
            .setContentText(line(hits[0]))
            .setStyle(style)
            .setContentIntent(Notifications.openApp(context, "wishlist", 9))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1003, n) }
        runCatching { com.mtgscanner.data.NotificationLog.add(context, title, hits.joinToString("\n") { line(it) }, "wishlist") }
    }
}
