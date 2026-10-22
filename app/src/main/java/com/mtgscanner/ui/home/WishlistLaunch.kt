package com.mtgscanner.ui.home

/** A one-time handoff: Home's Wishlist tile asks the Collection tab to open on its Wishlist. */
object WishlistLaunch {
    @Volatile private var pending = false

    fun open() { pending = true }

    /** Whether the Wishlist was asked for, then clears it. */
    fun take(): Boolean = pending.also { pending = false }
}
