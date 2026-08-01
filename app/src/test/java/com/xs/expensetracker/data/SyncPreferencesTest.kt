package com.xs.expensetracker.data

import com.xs.expensetracker.data.prefs.SyncPreferences
import com.xs.expensetracker.sync.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncPreferencesTest {

    private lateinit var preferences: SyncPreferences

    @Before
    fun setUp() {
        preferences = SyncPreferences(FakePreferencesDataStore())
    }

    @Test
    fun `the device id is generated once and then stable`() = runTest {
        val first = preferences.localUserId()
        val second = preferences.localUserId()

        assertEquals("records created offline must keep one owner across restarts", first, second)
        assertTrue(SyncPreferences.isLocalId(first))
    }

    @Test
    fun `a device id is never mistaken for a Firebase uid`() {
        assertTrue(SyncPreferences.isLocalId("local:abc"))
        assertFalse(SyncPreferences.isLocalId("kJ8sd7fLKJ2mnb"))
    }

    @Test
    fun `auto-sync defaults on and wifi-only defaults off`() = runTest {
        assertTrue(preferences.autoSyncEnabledOnce())
        assertFalse(preferences.wifiOnlyOnce())
    }

    @Test
    fun `the pull watermark reads back earlier than written to absorb clock skew`() = runTest {
        val written = 10_000_000L
        preferences.setLastPullAt(written)

        val read = preferences.lastPullAt()

        assertEquals(
            "a peer with a lagging clock would otherwise slip under the watermark",
            written - SyncPreferences.PULL_SAFETY_WINDOW_MS,
            read
        )
    }

    @Test
    fun `an unset watermark reads as zero rather than a negative time`() = runTest {
        assertEquals(0L, preferences.lastPullAt())
    }

    @Test
    fun `linking is tracked per account`() = runTest {
        preferences.markLinked("uid-alice")

        assertTrue(preferences.isLinked("uid-alice"))
        assertFalse("a second account still gets its own first-sync decision", preferences.isLinked("uid-bob"))
    }

    @Test
    fun `unlinking forces the next sync back through the first-sync decision`() = runTest {
        preferences.markLinked("uid-alice")
        preferences.unlink("uid-alice")

        assertFalse(preferences.isLinked("uid-alice"))
    }

    @Test
    fun `resetting sync state keeps the device identity and user toggles`() = runTest {
        val deviceId = preferences.localUserId()
        preferences.setWifiOnly(true)
        preferences.markLinked("uid-alice")
        preferences.recordSyncSuccess("uid-alice", 123)

        preferences.resetSyncState()

        assertEquals("wiping sync state must not orphan locally-owned records", deviceId, preferences.localUserId())
        assertTrue("a user preference is not sync bookkeeping", preferences.wifiOnlyOnce())
        assertFalse(preferences.isLinked("uid-alice"))
        assertNull(preferences.lastSyncedUid())
    }

    @Test
    fun `recording success clears any previous error`() = runTest {
        preferences.recordSyncError("boom")
        preferences.recordSyncSuccess("uid-alice", 999)

        assertNull(preferences.lastSyncError.first())
        assertEquals(999L, preferences.lastSyncedAt.first())
        assertEquals("uid-alice", preferences.lastSyncedUid())
    }

    @Test
    fun `a pending link decision is durable and clearable`() = runTest {
        preferences.setPendingLink("uid-alice", localRecords = 7, remoteTrackers = 2)

        val pending = preferences.pendingLinkFlow.first()
        assertNotNull(pending)
        assertEquals(7, pending!!.localRecords)
        assertEquals(2, pending.remoteTrackers)

        preferences.clearPendingLink()
        assertNull(preferences.pendingLinkFlow.first())
    }
}
