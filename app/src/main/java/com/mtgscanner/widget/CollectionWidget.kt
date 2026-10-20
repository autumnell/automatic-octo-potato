package com.mtgscanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.mtgscanner.MainActivity
import com.mtgscanner.R
import java.text.NumberFormat
import java.util.Locale

/** Home screen widget: just the collection value (or card count when prices are hidden). */
class CollectionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        const val EXTRA_OPEN = "open"

        /** Saves the latest totals and redraws any widgets on the home screen. */
        fun publish(context: Context, value: Double, cards: Int) {
            context.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
                .putFloat("value", value.toFloat()).putInt("cards", cards).apply()
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CollectionWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val p = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val showPrices = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("showPrices", true)
            val value = p.getFloat("value", 0f).toDouble()
            val cards = p.getInt("cards", 0)
            val v = RemoteViews(context.packageName, R.layout.widget_collection)
            // Just the total value (or the card count when prices are hidden). Tap opens the app.
            v.setTextViewText(R.id.widget_value, if (showPrices) NumberFormat.getCurrencyInstance(Locale.US).format(value) else "$cards cards")
            v.setViewVisibility(R.id.widget_cards, View.GONE)
            v.setViewVisibility(R.id.widget_scan, View.GONE)
            v.setOnClickPendingIntent(R.id.widget_root, open(context, "home", 0))
            return v
        }

        private fun open(context: Context, where: String, code: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, where)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
