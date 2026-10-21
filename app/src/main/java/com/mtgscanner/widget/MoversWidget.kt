package com.mtgscanner.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.mtgscanner.MtgApp
import com.mtgscanner.R
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.nearMintPrice
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home screen widget: the three cards you own whose price moved most this week. Reads saved prices only. */
class MoversWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as? MtgApp ?: return
        val pending = goAsync()
        app.repository.appScope.launch {
            try {
                runCatching { update(app, manager, ids) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val ROWS = listOf(
            R.id.mover_name_1 to R.id.mover_change_1,
            R.id.mover_name_2 to R.id.mover_change_2,
            R.id.mover_name_3 to R.id.mover_change_3,
        )

        /** Redraws any movers widgets, after prices change. */
        fun refresh(context: Context) {
            val app = context.applicationContext as? MtgApp ?: return
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, MoversWidget::class.java))
            if (ids.isEmpty()) return
            app.repository.appScope.launch { runCatching { update(app, manager, ids) } }
        }

        private suspend fun update(app: MtgApp, manager: AppWidgetManager, ids: IntArray) {
            val showPrices = app.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("showPrices", true)
            val movers = if (showPrices) movers(app) else emptyList()
            val v = RemoteViews(app.packageName, R.layout.widget_movers)
            ROWS.forEachIndexed { i, (nameId, changeId) ->
                val m = movers.getOrNull(i)
                v.setViewVisibility(nameId, if (m == null) View.GONE else View.VISIBLE)
                v.setViewVisibility(changeId, if (m == null) View.GONE else View.VISIBLE)
                if (m != null) {
                    v.setTextViewText(nameId, m.name)
                    v.setTextViewText(changeId, WidgetText.change(m.change))
                    v.setTextColor(changeId, if (m.change >= 0) 0xFF5FD38D.toInt() else 0xFFFF7A7A.toInt())
                }
            }
            v.setTextViewText(R.id.movers_empty, if (showPrices) "No big moves this week" else "Prices are hidden")
            v.setViewVisibility(R.id.movers_empty, if (movers.isEmpty()) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.movers_root, CollectionWidget.open(app, "home", 2))
            ids.forEach { manager.updateAppWidget(it, v) }
        }

        private suspend fun movers(app: MtgApp): List<Mover> {
            val dao = app.repository.dao
            val cards: List<CollectionCard> = dao.all()
            val points: List<PricePoint> = dao.pricePointsSince(LocalDate.now().minusDays(7).toString())
            // The oldest price in the last week, per printing and finish.
            val then = HashMap<String, Double>()
            points.sortedBy { it.day }.forEach { p ->
                p.usd?.let { then.putIfAbsent("${p.scryfallId}:false", it) }
                (p.usdFoil ?: p.usd)?.let { then.putIfAbsent("${p.scryfallId}:true", it) }
            }
            val now = cards.filter { !it.proxy }.map { Triple("${it.scryfallId}:${it.foil}", it.name, it.nearMintPrice()) }
            return WidgetText.movers(now, then)
        }
    }
}
