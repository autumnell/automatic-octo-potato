package com.mtgscanner.ui.collection

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the Collection tab shows. Kept in the "app" prefs, which backups already include. */
object CollectionPrefs {
    private var prefs: SharedPreferences? = null

    var layout by mutableStateOf(CollectionLayout.DEFAULT)
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        prefs = p
        layout = CollectionLayout.load(
            p.getString("colSections", null),
            p.getString("colKnown", null),
            p.getString("colHidden", null),
            p.getBoolean("colCompact", false),
        )
    }

    fun update(l: CollectionLayout) {
        layout = l.copy(sections = l.sections.distinct())
        prefs?.edit()
            ?.putString("colSections", CollectionLayout.sectionsKey(layout.sections))
            ?.putString("colKnown", CollectionLayout.knownKey())
            ?.putString("colHidden", CollectionLayout.hiddenKey(layout.hidden))
            ?.putBoolean("colCompact", layout.compact)
            ?.apply()
    }
}
