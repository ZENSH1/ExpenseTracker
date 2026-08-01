package com.xs.expensetracker.sync

import com.xs.expensetracker.data.sync.SyncStatus
import com.xs.expensetracker.data.sync.SyncStatusFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status shown in the app bar is a priority decision, and getting the order wrong hides
 * things the user needs to act on. These pin the order down.
 */
class SyncStatusFactoryTest {

    private fun derive(
        isSignedIn: Boolean = true,
        pendingCount: Int = 0,
        conflictCount: Int = 0,
        isRunning: Boolean = false,
        isBackingOff: Boolean = false,
        hasFailed: Boolean = false,
        errorMessage: String? = null,
        pendingAccountUid: String? = null,
        linkLocal: Int? = null,
        linkRemote: Int? = null
    ) = SyncStatusFactory.derive(
        isSignedIn, pendingCount, conflictCount, isRunning, isBackingOff, hasFailed,
        errorMessage, pendingAccountUid, linkLocal, linkRemote
    )

    @Test
    fun `signed out reads as local-only, never as an error`() {
        assertEquals(
            SyncStatus.Offline,
            derive(isSignedIn = false, pendingCount = 12, conflictCount = 3, hasFailed = true)
        )
    }

    @Test
    fun `an account decision outranks everything else`() {
        val status = derive(
            pendingAccountUid = "uid-bob",
            conflictCount = 5,
            isRunning = true,
            linkLocal = 2,
            linkRemote = 2
        )
        assertTrue(status is SyncStatus.NeedsAccountDecision)
    }

    @Test
    fun `a link decision outranks conflicts and progress`() {
        val status = derive(linkLocal = 4, linkRemote = 2, conflictCount = 9, isRunning = true)
        assertEquals(SyncStatus.NeedsLinkDecision(4, 2), status)
    }

    @Test
    fun `conflicts are not hidden behind a running sync`() {
        assertEquals(SyncStatus.ConflictsPending(2), derive(conflictCount = 2, isRunning = true))
    }

    @Test
    fun `a backoff reads as retrying rather than failed`() {
        val status = derive(isBackingOff = true, hasFailed = true, errorMessage = "no network")
        assertEquals(SyncStatus.Retrying("no network"), status)
    }

    @Test
    fun `a terminal failure outranks a pending count`() {
        assertEquals(SyncStatus.Failed("denied"), derive(hasFailed = true, pendingCount = 3, errorMessage = "denied"))
    }

    @Test
    fun `pending work is reported when nothing is wrong`() {
        assertEquals(SyncStatus.Pending(3), derive(pendingCount = 3))
    }

    @Test
    fun `everything settled reads as up to date`() {
        assertEquals(SyncStatus.UpToDate, derive())
    }

    @Test
    fun `retrying falls back to a message when none was recorded`() {
        val status = derive(isBackingOff = true) as SyncStatus.Retrying
        assertTrue(status.message.isNotBlank())
    }
}
