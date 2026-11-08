package com.mtgscanner.ui.scan

import com.mtgscanner.data.db.Box

/**
 * What the scanner is doing: one at a time, picked from "What are you doing?". Stack mode and a set lock sit on top
 * of the adding activities; everything else replaces what ran before.
 */
enum class ScanActivity(val title: String, val body: String) {
    ADD("Adding cards", "Into your collection"),
    DECK("Adding to a deck", "Copies go to the deck's box"),
    WISHLIST("Adding to the wishlist", "Nothing goes in your collection"),
    CHECK("Checking prices", "Nothing is added"),
    REMOVE("Removing cards", "Each card scanned comes out"),
    BOX_CHECK("Checking a box", "Scan a box, see what's missing"),
    SORT("Sorting a pile", "Scan it all, then file by box"),
    PACK("Opening a pack", "Running total as you scan"),
    BATCH("Scanning a pile", "Review the list, then add it all"),
    PAGE("Binder page or photo", "Many cards at once"),
    ;

    /** Activities that add cards one by one, where hands-free stack mode applies. */
    val oneByOne: Boolean get() = this == ADD || this == DECK || this == WISHLIST || this == REMOVE || this == SORT || this == PACK
}

/** The activity [ScanUiState] is in. A run (pack, sort, box check, batch, page) wins over where cards go. */
fun ScanUiState.activity(): ScanActivity = when {
    pack != null -> ScanActivity.PACK
    sort != null -> ScanActivity.SORT
    boxCheck != null || target == ScanTarget.BOX -> ScanActivity.BOX_CHECK
    batch != null -> ScanActivity.BATCH
    pageMode -> ScanActivity.PAGE
    target == ScanTarget.DECK -> ScanActivity.DECK
    target == ScanTarget.WISHLIST -> ScanActivity.WISHLIST
    target == ScanTarget.CHECK -> ScanActivity.CHECK
    target == ScanTarget.REMOVE -> ScanActivity.REMOVE
    else -> ScanActivity.ADD
}

/** The line over the camera: what happens to the next card scanned. [boxName] names the box picked in the header. */
fun ScanUiState.nextCard(boxName: (Long) -> String?, setLock: String?): String {
    val box = if (boxId == Box.AUTO_SORT) null else boxName(boxId)
    val a = activity()
    val line = when (a) {
        ScanActivity.ADD -> if (box != null) "Next card goes into $box" else "Next card goes where your box rules say"
        ScanActivity.DECK -> "Next card goes into ${deck?.name?.ifBlank { null } ?: "the deck"}"
        ScanActivity.WISHLIST -> "Next card goes on your wishlist"
        ScanActivity.CHECK -> "Next card is only priced"
        ScanActivity.REMOVE -> "Next card comes out of your collection"
        ScanActivity.BOX_CHECK -> "Next card is checked against ${boxCheck?.boxName ?: "the box"}"
        ScanActivity.SORT -> "Next card is added and stacked by box"
        ScanActivity.PACK -> "Next card counts toward ${pack?.name ?: "the pack"}"
        ScanActivity.BATCH -> "Next card joins the pile to review"
        ScanActivity.PAGE -> "Snap a binder page or pick a photo"
    }
    val extras = buildList {
        if (stack && a.oneByOne) add("hands-free")
        if (setLock != null && a != ScanActivity.PAGE) add("${setLock.uppercase()} only")
    }
    return if (extras.isEmpty()) line else line + " · " + extras.joinToString(" · ")
}

/**
 * Switches to [a]. Whatever ran before ends first and keeps its result: a pack shows what it held, a sort its summary,
 * a batch opens its list to review.
 */
fun ScanViewModel.chooseActivity(a: ScanActivity) {
    val st = state.value
    if (st.activity() == a && a != ScanActivity.DECK) return
    if (st.pack != null) finishPack()
    if (st.sort != null) endSort()
    if (st.boxCheck != null) endBoxCheck()
    if (st.batch != null) setBatch(false)
    if (st.pageMode) setPageMode(false)
    when (a) {
        ScanActivity.ADD -> stopTarget()
        // The deck is set once one is picked.
        ScanActivity.DECK -> showDeckPick(true)
        ScanActivity.WISHLIST -> startWishlist()
        ScanActivity.CHECK -> startCheck()
        ScanActivity.REMOVE -> startRemove()
        ScanActivity.BOX_CHECK -> { stopTarget(); showBoxPick(true) }
        ScanActivity.SORT -> { stopTarget(); startSort() }
        ScanActivity.PACK -> { stopTarget(); showPackStart(true) }
        ScanActivity.BATCH -> { stopTarget(); setBatch(true) }
        ScanActivity.PAGE -> { stopTarget(); setPageMode(true) }
    }
}
