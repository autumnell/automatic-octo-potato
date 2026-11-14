package com.mtgscanner.util

/** "1 card", "3 cards". Irregular words pass [many], as in plural(n, "copy", "copies"). */
fun plural(n: Number, one: String, many: String = one + "s"): String =
    "$n ${if (n.toLong() == 1L) one else many}"
