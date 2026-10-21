package com.mtgscanner.ui.home

/** A one-time handoff: the goal tapped on Home, for the Goals screen to open straight away. */
object GoalsLaunch {
    @Volatile private var openId: Long = -1L

    fun open(id: Long) { openId = id }

    /** The goal to open (or -1), then clears it. */
    fun take(): Long = openId.also { openId = -1L }
}
