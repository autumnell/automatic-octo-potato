package com.mtgscanner.ui.collection

/** Where a divider's tab sits: real dividers step their tabs across so each one shows behind the one in front. */
object DividerTabMath {
    /** How many positions the tabs step through before starting again at the left. */
    const val STEPS = 3

    /** Left edge of tab [index] (0 = frontmost) on a divider [width] wide, for a tab [tabWidth] wide. */
    fun tabLeft(index: Int, width: Float, tabWidth: Float, steps: Int = STEPS): Float {
        if (steps <= 1 || tabWidth >= width) return 0f
        val step = (width - tabWidth) / (steps - 1)
        return Math.floorMod(index, steps) * step
    }
}
