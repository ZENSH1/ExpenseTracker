package com.xs.expensetracker.data.sync

/**
 * The slice of logging the sync layer needs.
 *
 * Extracted as an interface so [SyncEngine] can be unit-tested on the JVM — the production
 * implementation reaches into Crashlytics and Analytics statics, which are not available
 * outside an instrumented environment.
 */
interface SyncLogger {
    fun debug(tag: String, message: String)
    fun error(tag: String, operation: String, throwable: Throwable, extra: Map<String, String> = emptyMap())
    fun event(name: String, params: Map<String, String> = emptyMap())
}
