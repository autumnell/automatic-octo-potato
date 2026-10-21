package com.mtgscanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.mtgscanner.MainActivity
import com.mtgscanner.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Home screen widget: the collection value (or card count when prices are hidden) and when it was last
 * updated. Made wider, it shows a Scan button too.
 */
class CollectionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, wide(manager, it))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        manager.updateAppWidget(id, views(context, wide(newOptions)))
    }

    companion object {
        const val EXTRA_OPEN = "open"

        /** Saves the latest totals and redraws any widgets on the home screen. */
        fun publish(context: Context, value: Double, cards: Int) {
            context.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
                .putString("valueUsd", value.toString()).remove("value").putInt("cards", cards)
                .putLong("at", System.currentTimeMillis()).apply()
            refresh(context)
            MoversWidget.refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CollectionWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context, wide(manager, it))) }
        }

        /** Room for a Scan button: the widget is at least four cells wide. */
        private fun wide(options: Bundle?): Boolean = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0) >= 250

        private fun wide(manager: AppWidgetManager, id: Int): Boolean = wide(runCatching { manager.getAppWidgetOptions(id) }.getOrNull())

        private fun views(context: Context, wide: Boolean): RemoteViews {
            val p = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val showPrices = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("showPrices", true)
            // Saved as text so big totals keep their cents; older versions saved a Float under "value".
            val value = p.getString("valueUsd", null)?.toDoubleOrNull() ?: p.getFloat("value", 0f).toDouble()
            val cards = p.getInt("cards", 0)
            val v = RemoteViews(context.packageName, R.layout.widget_collection)
            // Just the total value (or the card count when prices are hidden). Tap opens the app.
            v.setTextViewText(R.id.widget_value, if (showPrices) NumberFormat.getCurrencyInstance(Locale.US).format(value) else "$cards cards")
            val updated = WidgetText.updated(p.getLong("at", 0L), System.currentTimeMillis())
            v.setTextViewText(R.id.widget_cards, updated)
            v.setViewVisibility(R.id.widget_cards, if (updated.isEmpty()) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.widget_scan, if (wide) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.widget_scan, open(context, "scan", 1))
            v.setOnClickPendingIntent(R.id.widget_root, open(context, "home", 0))
            return v
        }

        internal fun open(context: Context, where: String, code: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, where)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
