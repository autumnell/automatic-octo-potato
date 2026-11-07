package com.mtgscanner.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but a cancelled coroutine stays cancelled: [CancellationException] is thrown on instead of
 * becoming a failure. Use it around suspend calls, so leaving a screen or a newer request doesn't show an error,
 * cache an empty answer or mark work as tried.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
