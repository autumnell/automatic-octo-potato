package com.mtgscanner.ui.home

import android.content.Context

/** The bottom tab open last, so the app reopens there instead of on Home. */
object LastTab {
    private const val KEY = "lastTab"

    /** The saved tab when it's one of [routes], else "home". */
    fun load(context: Context, routes: List<String>): String =
        pick(context.getSharedPreferences("app", Context.MODE_PRIVATE).getString(KEY, null), routes)

    fun save(context: Context, route: String) {
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString(KEY, route).apply()
    }

    fun pick(saved: String?, routes: List<String>): String = saved?.takeIf { it in routes } ?: "home"
}
