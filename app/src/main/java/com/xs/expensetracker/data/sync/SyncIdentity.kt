package com.xs.expensetracker.data.sync

/**
 * The identity questions the sync engine asks.
 *
 * Narrower than the full provider on purpose: the production implementation returns a
 * `FirebaseUser`, which cannot be constructed in a JVM unit test. Depending on two plain string
 * accessors instead keeps the engine's tests free of Firebase entirely.
 */
interface SyncIdentity {

    /** Firebase uid, or `null` when the app is being used without an account. */
    fun currentUid(): String?

    /** The uid if signed in, otherwise this device's local id. */
    suspend fun currentOwnerId(): String
}
