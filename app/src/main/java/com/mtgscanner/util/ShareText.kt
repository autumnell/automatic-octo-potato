package com.mtgscanner.util

import android.content.Context
import android.content.Intent

/** Opens the share sheet with [text] (and [title] as its subject, for email). */
fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
