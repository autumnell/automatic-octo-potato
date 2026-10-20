package com.mtgscanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mtgscanner.MainActivity
import com.mtgscanner.R
import java.text.NumberFormat
import java.util.Locale

/** Home screen widget: collection value (or card count when prices are hidden) and a Scan button. */
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
            if (showPrices) {
                v.setTextViewText(R.id.widget_value, NumberFormat.getCurrencyInstance(Locale.US).format(value))
                v.setTextViewText(R.id.widget_cards, "$cards cards")
            } else {
                v.setTextViewText(R.id.widget_value, "$cards cards")
                v.setTextViewText(R.id.widget_cards, "")
            }
            v.setOnClickPendingIntent(R.id.widget_root, open(context, "home", 0))
            v.setOnClickPendingIntent(R.id.widget_scan, open(context, "scan", 1))
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
