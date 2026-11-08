package com.mtgscanner.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A one-time handoff: Home's Wishlist tile or the tab's hold menu asks the Collection tab to open on its Wishlist.
 * A flow the screen watches, so it works when Collection is already open too, and never fires later by surprise.
 */
object WishlistLaunch {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending

    fun open() { _pending.value = true }

    /** Clears the request once the Wishlist is showing. */
    fun done() { _pending.value = false }
}
