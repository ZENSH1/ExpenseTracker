package com.xs.expensetracker.utils

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Centralized logger for the app.
 *
 * - Errors   → Crashlytics non-fatal report + Analytics event
 * - Events   → Analytics event + Crashlytics breadcrumb
 * - Debug    → Crashlytics breadcrumb only (never sent unless a crash follows)
 *
 * Usage:
 *   AppLogger.error("TrackerUseCase", "createTracker", e)
 *   AppLogger.event("tracker_created", mapOf("name" to name))
 *   AppLogger.debug("TrackerUseCase", "Fetched ${trackers.size} trackers")
 */
class AppLogger(
    private val crashlytics: FirebaseCrashlytics,
    private val analytics: FirebaseAnalytics
) {

    // ─────────────────────────────────────────────────────────────────────────
    // ERROR  — non-fatal Crashlytics report + Analytics error event
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Log a caught exception as a non-fatal Crashlytics report.
     * Also fires an Analytics event so you can build funnels around failure rates.
     *
     * @param tag       Class or layer name, e.g. "TrackerUseCase"
     * @param operation Function name, e.g. "createTracker"
     * @param throwable The caught exception
     * @param extra     Optional key/value pairs attached to the Crashlytics report
     */
    fun error(
        tag: String,
        operation: String,
        throwable: Throwable,
        extra: Map<String, String> = emptyMap()
    ) {
        // 1. Set Crashlytics keys for grouping in the console
        crashlytics.setCustomKey(KEY_TAG, tag)
        crashlytics.setCustomKey(KEY_OPERATION, operation)
        extra.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }

        // 2. Add a human-readable breadcrumb
        crashlytics.log("ERROR [$tag.$operation] ${throwable.message}")

        // 3. Record as non-fatal — shows up in Crashlytics > Non-fatals
        crashlytics.recordException(throwable)

        // 4. Fire Analytics event so you can query error rates in BigQuery / dashboards
        analytics.logEvent(EVENT_ERROR) {
            param(PARAM_TAG, tag.take(100))
            param(PARAM_OPERATION, operation.take(100))
            param(PARAM_MESSAGE, (throwable.message ?: "unknown").take(100))
        }
    }

    /**
     * Log a string error (no throwable) — wraps it in a RuntimeException so
     * Crashlytics still gets a stack-trace-like entry.
     */
    fun error(
        tag: String,
        operation: String,
        message: String,
        extra: Map<String, String> = emptyMap()
    ) = error(tag, operation, RuntimeException("[$tag.$operation] $message"), extra)

    // ─────────────────────────────────────────────────────────────────────────
    // EVENT  — Analytics event + Crashlytics breadcrumb
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Log a business event (success path).
     * Appears in Analytics > Events and as a Crashlytics breadcrumb.
     *
     * @param name   Analytics event name — snake_case, max 40 chars
     * @param params Additional Analytics parameters (max 25 per event)
     */
    fun event(
        name: String,
        params: Map<String, String> = emptyMap()
    ) {
        crashlytics.log("EVENT [$name] $params")
        analytics.logEvent(name.take(40)) {
            params.forEach { (k, v) -> param(k.take(40), v.take(100)) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEBUG  — Crashlytics breadcrumb only (zero Analytics noise)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lightweight breadcrumb visible only when a crash/non-fatal follows.
     * Zero cost in production — no Analytics event fired.
     */
    fun debug(tag: String, message: String) {
        crashlytics.log("DEBUG [$tag] $message")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User identity — call after login so reports are tied to a user
    // ─────────────────────────────────────────────────────────────────────────

    fun setUser(userId: String) {
        crashlytics.setUserId(userId)
        analytics.setUserId(userId)
    }

    fun clearUser() {
        crashlytics.setUserId("")
        analytics.setUserId(null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        private const val KEY_TAG       = "tag"
        private const val KEY_OPERATION = "operation"
        private const val EVENT_ERROR   = "app_error"
        private const val PARAM_TAG     = "tag"
        private const val PARAM_OPERATION = "operation"
        private const val PARAM_MESSAGE = "message"
    }
}