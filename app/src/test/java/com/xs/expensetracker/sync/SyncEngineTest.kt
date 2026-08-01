package com.xs.expensetracker.sync

import com.xs.expensetracker.data.local.entity.ConflictEntityType
import com.xs.expensetracker.data.local.entity.ReceiptEntity
import com.xs.expensetracker.data.local.entity.SourceEntity
import com.xs.expensetracker.data.local.entity.TrackerEntity
import com.xs.expensetracker.data.prefs.SyncPreferences
import com.xs.expensetracker.data.remote.RemoteReceipt
import com.xs.expensetracker.data.remote.RemoteSource
import com.xs.expensetracker.data.remote.RemoteTracker
import com.xs.expensetracker.data.sync.ConflictResolution
import com.xs.expensetracker.data.sync.SyncBlockReason
import com.xs.expensetracker.data.sync.SyncEngine
import com.xs.expensetracker.data.sync.SyncMode
import com.xs.expensetracker.data.sync.SyncOutcome
import com.xs.expensetracker.domain.data.enums.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SyncEngineTest {

    private lateinit var trackerDao: FakeTrackerDao
    private lateinit var sourceDao: FakeSourceDao
    private lateinit var receiptDao: FakeReceiptDao
    private lateinit var conflictDao: FakeConflictDao
    private lateinit var syncDao: FakeSyncDao
    private lateinit var remote: FakeRemote
    private lateinit var identity: FakeIdentity
    private lateinit var preferences: SyncPreferences
    private lateinit var logger: RecordingLogger
    private lateinit var engine: SyncEngine

    /** Fixed clock so `updatedAt` values in assertions are predictable. */
    private var clock = 1_000L

    private val uid = "uid-alice"

    @Before
    fun setUp() {
        trackerDao = FakeTrackerDao()
        sourceDao = FakeSourceDao()
        receiptDao = FakeReceiptDao()
        conflictDao = FakeConflictDao()
        syncDao = FakeSyncDao(trackerDao, sourceDao, receiptDao)
        remote = FakeRemote()
        identity = FakeIdentity()
        preferences = SyncPreferences(FakePreferencesDataStore())
        logger = RecordingLogger()

        sourceDao.receiptsProvider = { receiptDao.rows.values.toList() }
        sourceDao.trackerIdsProvider = { trackerDao.rows.keys }
        receiptDao.trackerIdsProvider = { trackerDao.rows.keys }

        engine = SyncEngine(
            trackerDao, sourceDao, receiptDao, conflictDao, syncDao,
            remote, identity, preferences, logger,
            now = { clock }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Signed out — the app's default state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `sync without an account is blocked, not failed`() = runTest {
        val outcome = engine.sync()

        assertEquals(SyncOutcome.Blocked(SyncBlockReason.NOT_SIGNED_IN), outcome)
        assertTrue("nothing should reach the network", remote.pushes.isEmpty())
    }

    @Test
    fun `local data created without an account is untouched by a failed sync`() = runTest {
        trackerDao.upsert(tracker("t1", owner = "local:device-1"))

        engine.sync()

        assertEquals(1, trackerDao.rows.size)
        assertTrue(trackerDao.rows.getValue("t1").pendingSync)
    }

    @Test
    fun `background sync respects the auto-sync toggle but a manual sync overrides it`() = runTest {
        identity.uid = uid
        preferences.setAutoSyncEnabled(false)

        assertEquals(
            SyncOutcome.Blocked(SyncBlockReason.AUTO_SYNC_DISABLED),
            engine.sync(manual = false)
        )
        assertTrue(engine.sync(manual = true) is SyncOutcome.Success)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // First sync for an account
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `first sync with only local data uploads it and claims ownership`() = runTest {
        identity.uid = uid
        trackerDao.upsert(tracker("t1", owner = "local:device-1", sharedWith = listOf("local:device-1")))

        val outcome = engine.sync()

        assertTrue(outcome is SyncOutcome.Success)
        val claimed = trackerDao.rows.getValue("t1")
        assertEquals("ownership moves to the signed-in account", uid, claimed.ownerId)
        assertEquals(listOf(uid), claimed.sharedWith)
        assertTrue("t1" in remote.pushedTrackerIds())
        assertFalse("pushed rows are no longer dirty", claimed.pendingSync)
    }

    @Test
    fun `first sync with only cloud data downloads it`() = runTest {
        identity.uid = uid
        remote.trackers["r1"] = remoteTracker("r1", updatedAt = 500)

        val outcome = engine.sync()

        assertTrue(outcome is SyncOutcome.Success)
        assertEquals(setOf("r1"), trackerDao.rows.keys)
        assertFalse(trackerDao.rows.getValue("r1").pendingSync)
    }

    @Test
    fun `first sync with data on both sides stops and asks the user`() = runTest {
        identity.uid = uid
        trackerDao.upsert(tracker("t1"))
        remote.trackers["r1"] = remoteTracker("r1", updatedAt = 500)

        val outcome = engine.sync()

        val blocked = outcome as SyncOutcome.Blocked
        assertEquals(SyncBlockReason.NEEDS_LINK_DECISION, blocked.reason)
        assertEquals(1, blocked.localRecordCount)
        assertEquals(1, blocked.remoteTrackerCount)
        assertTrue("neither side may be written before the user decides", remote.pushes.isEmpty())
        assertEquals(setOf("t1"), trackerDao.rows.keys)
    }

    @Test
    fun `a pending link decision is recorded durably for the UI to pick up later`() = runTest {
        identity.uid = uid
        trackerDao.upsert(tracker("t1"))
        remote.trackers["r1"] = remoteTracker("r1", updatedAt = 500)

        engine.sync()

        val pending = preferences.pendingLinkFlow.first()
        assertNotNull("raised inside a worker; must survive until the app is opened", pending)
        assertEquals(uid, pending!!.uid)
        assertEquals(1, pending.remoteTrackers)
    }

    @Test
    fun `merge keeps both sides`() = runTest {
        identity.uid = uid
        trackerDao.upsert(tracker("t1"))
        remote.trackers["r1"] = remoteTracker("r1", updatedAt = 500)

        val outcome = engine.resolveLinkDecision(SyncMode.MERGE)

        assertTrue(outcome is SyncOutcome.Success)
        assertEquals(setOf("t1", "r1"), trackerDao.rows.keys)
        assertTrue("the local-only tracker reaches the cloud", "t1" in remote.trackers.keys)
    }

    @Test
    fun `keeping the cloud replaces local data outright`() = runTest {
        identity.uid = uid
        trackerDao.upsert(tracker("t1"))
        remote.trackers["r1"] = remoteTracker("r1", updatedAt = 500)

        engine.resolveLinkDecision(SyncMode.FORCE_DOWNLOAD)

        assertEquals(setOf("r1"), trackerDao.rows.keys)
    }

    @Test
    fun `keeping this device tombstones the cloud-only records`() = runTest {
        identity.uid = uid
        trackerDao.upsert(tracker("t1"))
        remote.trackers["r1"] = remoteTracker("r1", updatedAt = 500)

        engine.resolveLinkDecision(SyncMode.FORCE_UPLOAD)

        assertEquals(setOf("t1"), trackerDao.rows.keys)
        assertTrue("the cloud-only tracker must be tombstoned, not silently left behind",
            remote.trackers.getValue("r1").deleted)
        assertFalse(remote.trackers.getValue("t1").deleted)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Steady-state incremental sync
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a remote change with no local edits is applied`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Old", pending = false, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", name = "New", updatedAt = 900)

        engine.sync()

        assertEquals("New", trackerDao.rows.getValue("t1").name)
        assertEquals(900L, trackerDao.rows.getValue("t1").remoteUpdatedAt)
    }

    @Test
    fun `a local edit is pushed when the cloud has not moved`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Local edit", pending = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", name = "Old", updatedAt = 100)

        engine.sync()

        assertEquals("Local edit", remote.trackers.getValue("t1").name)
        assertFalse(trackerDao.rows.getValue("t1").pendingSync)
        assertTrue(conflictDao.rows.isEmpty())
    }

    @Test
    fun `an echo of our own push is not mistaken for a conflict`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        // Baseline equals the remote revision: the cloud is showing us back what we last saw,
        // even though we have since edited locally.
        trackerDao.upsert(tracker("t1", name = "Local", pending = true, remoteUpdatedAt = 700))
        remote.trackers["t1"] = remoteTracker("t1", name = "Same", updatedAt = 700)

        engine.sync()

        assertTrue("no conflict for an unchanged baseline", conflictDao.rows.isEmpty())
        assertEquals("Local", remote.trackers.getValue("t1").name)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conflicts
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `edits on both sides become a conflict instead of overwriting either`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Mine", pending = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", name = "Theirs", updatedAt = 900)

        val outcome = engine.sync() as SyncOutcome.Success

        assertEquals(1, outcome.conflicts)
        assertEquals("local value is left intact", "Mine", trackerDao.rows.getValue("t1").name)
        assertEquals("cloud value is left intact", "Theirs", remote.trackers.getValue("t1").name)

        val conflict = conflictDao.rows.values.single()
        assertEquals(ConflictEntityType.TRACKER, conflict.entityType)
        assertTrue(conflict.localSummary.contains("Mine"))
        assertTrue(conflict.remoteSummary.contains("Theirs"))
    }

    @Test
    fun `a conflicted record is held back from the push queue`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Mine", pending = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", name = "Theirs", updatedAt = 900)

        engine.sync()
        remote.pushes.clear()
        engine.sync()

        assertTrue(
            "an unresolved conflict must never be pushed",
            "t1" !in remote.pushedTrackerIds()
        )
    }

    @Test
    fun `keeping the local version wins the next push`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Mine", pending = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", name = "Theirs", updatedAt = 900)
        engine.sync()

        clock = 2_000
        engine.resolveConflict(conflictDao.rows.keys.first(), ConflictResolution.KEEP_LOCAL)

        val row = trackerDao.rows.getValue("t1")
        assertTrue(conflictDao.rows.isEmpty())
        assertTrue("still queued to overwrite the cloud", row.pendingSync)
        assertEquals("the remote revision becomes the new baseline", 900L, row.remoteUpdatedAt)
        assertEquals("restamped so peers' incremental pulls see it", 2_000L, row.updatedAt)

        engine.sync()
        assertEquals("Mine", remote.trackers.getValue("t1").name)
    }

    @Test
    fun `keeping the cloud version discards the local edit`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Mine", pending = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", name = "Theirs", updatedAt = 900)
        engine.sync()

        engine.resolveConflict(conflictDao.rows.keys.first(), ConflictResolution.KEEP_REMOTE)

        val row = trackerDao.rows.getValue("t1")
        assertEquals("Theirs", row.name)
        assertFalse(row.pendingSync)
        assertTrue(conflictDao.rows.isEmpty())
    }

    @Test
    fun `a delete on one side against an edit on the other is flagged, not applied`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Mine", pending = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", updatedAt = 900).copy(deleted = true)

        engine.sync()

        val conflict = conflictDao.rows.values.single()
        assertTrue("the UI must be able to call this case out", conflict.remoteDeleted)
        assertFalse(conflict.localDeleted)
        assertFalse("the local record survives until the user decides", trackerDao.rows.getValue("t1").deleted)
    }

    @Test
    fun `resolving every conflict at once applies one choice to all of them`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "Mine", pending = true, remoteUpdatedAt = 100))
        trackerDao.upsert(tracker("t2", name = "Mine2", pending = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", name = "Theirs", updatedAt = 900)
        remote.trackers["t2"] = remoteTracker("t2", name = "Theirs2", updatedAt = 900)
        engine.sync()
        assertEquals(2, conflictDao.rows.size)

        engine.resolveAllConflicts(ConflictResolution.KEEP_REMOTE)

        assertTrue(conflictDao.rows.isEmpty())
        assertEquals("Theirs", trackerDao.rows.getValue("t1").name)
        assertEquals("Theirs2", trackerDao.rows.getValue("t2").name)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Account switching
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a different account on a device holding data must be resolved first`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        preferences.recordSyncSuccess(uid, clock)
        trackerDao.upsert(tracker("t1", owner = uid))

        identity.uid = "uid-bob"
        val outcome = engine.sync() as SyncOutcome.Blocked

        assertEquals(SyncBlockReason.NEEDS_ACCOUNT_DECISION, outcome.reason)
        assertEquals("uid-bob", outcome.newUid)
        assertTrue("one account's data must never auto-upload to another", remote.pushes.isEmpty())
    }

    @Test
    fun `switching accounts with an empty device needs no prompt`() = runTest {
        identity.uid = uid
        preferences.recordSyncSuccess(uid, clock)

        identity.uid = "uid-bob"
        val outcome = engine.sync()

        assertTrue(outcome is SyncOutcome.Success)
    }

    @Test
    fun `moving data to the new account re-owns it`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        preferences.recordSyncSuccess(uid, clock)
        trackerDao.upsert(tracker("t1", owner = uid, sharedWith = listOf(uid)))
        identity.uid = "uid-bob"
        engine.sync()

        engine.resolveAccountChange(keepLocalData = true)

        val row = trackerDao.rows.getValue("t1")
        assertEquals("uid-bob", row.ownerId)
        assertEquals(listOf("uid-bob"), row.sharedWith)
        assertNull(preferences.pendingAccountUid())
    }

    @Test
    fun `discarding local data on an account switch clears the device`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        preferences.recordSyncSuccess(uid, clock)
        trackerDao.upsert(tracker("t1", owner = uid))
        identity.uid = "uid-bob"
        engine.sync()

        engine.resolveAccountChange(keepLocalData = false)

        assertTrue(trackerDao.rows.isEmpty())
        assertTrue("the previous account's records are not uploaded to the new one", remote.pushes.isEmpty())
    }

    @Test
    fun `a tracker owned by someone else is not stolen on an account switch`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        preferences.recordSyncSuccess(uid, clock)
        // Shared with the previous account by a third party.
        trackerDao.upsert(tracker("shared", owner = "uid-carol", sharedWith = listOf("uid-carol", uid)))
        identity.uid = "uid-bob"
        engine.sync()

        engine.resolveAccountChange(keepLocalData = true)

        val row = trackerDao.rows["shared"]
        assertTrue(
            "a third party's tracker is dropped locally, never re-owned",
            row == null || (row.deleted && !row.pendingSync)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push correctness
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `an edit made while a push is in flight stays queued`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", name = "First", pending = true, remoteUpdatedAt = 100))

        // Simulates the user saving again mid-request: the row's revision moves on, so the
        // compare-and-clear must not retire it.
        remote.onPush = { trackerDao.rows["t1"] = trackerDao.rows.getValue("t1").copy(name = "Second", updatedAt = 5_000) }

        engine.sync()

        assertTrue("the newer edit must survive to the next sync", trackerDao.rows.getValue("t1").pendingSync)
        assertEquals("Second", trackerDao.rows.getValue("t1").name)
    }

    @Test
    fun `pushed totals are derived from receipts and signed by type`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", pending = true, remoteUpdatedAt = null))
        sourceDao.upsert(source("s1", "t1", TransactionType.INCOME))
        receiptDao.upsert(receipt("r1", "t1", "s1", TransactionType.INCOME, 300.0))
        receiptDao.upsert(receipt("r2", "t1", "s1", TransactionType.EXPENSE, 120.0))
        receiptDao.upsert(receipt("r3", "t1", "s1", TransactionType.EXPENSE, 50.0).copy(deleted = true))

        engine.sync()

        // 300 income − 120 expense = 180; the tombstoned row contributes nothing.
        assertEquals(180.0, remote.trackers.getValue("t1").grandTotal, 0.001)
    }

    @Test
    fun `deletes propagate as tombstones rather than vanishing`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", pending = true, deleted = true, remoteUpdatedAt = 100))
        remote.trackers["t1"] = remoteTracker("t1", updatedAt = 100)

        engine.sync()

        assertTrue(
            "other devices learn about the delete only from the tombstone",
            remote.trackers.getValue("t1").deleted
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy data and error handling
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `receipts stored under the old nested path are rescued on first import`() = runTest {
        identity.uid = uid
        remote.trackers["t1"] = remoteTracker("t1", updatedAt = 500)
        remote.sources["s1"] = RemoteSource(
            id = "s1", trackerId = "t1", name = "Salary",
            type = TransactionType.INCOME.name, updatedAt = 500
        )
        remote.legacyReceipts += RemoteReceipt(
            id = "old1", trackerId = "t1", sourceId = "s1",
            name = "Legacy payslip", amount = 42.0, updatedAt = 400, type = ""
        )

        engine.sync()

        val imported = receiptDao.rows["old1"]
        assertNotNull("pre-offline receipts must not be lost on upgrade", imported)
        assertEquals(
            "a missing type falls back to the source's, not a blind default",
            TransactionType.INCOME,
            imported!!.type
        )
    }

    @Test
    fun `a network failure is retryable and leaves local data queued`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        trackerDao.upsert(tracker("t1", pending = true))
        remote.failWith = IOException("offline")

        val outcome = engine.sync()

        assertTrue("a connectivity blip must not be treated as permanent", outcome is SyncOutcome.Retryable)
        assertTrue(trackerDao.rows.getValue("t1").pendingSync)
        assertNotNull(preferences.lastSyncError.first())
    }

    @Test
    fun `a successful sync clears a previously recorded error`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        preferences.recordSyncError("boom")

        engine.sync()

        assertNull(preferences.lastSyncError.first())
        assertNotNull(preferences.lastSyncedAt.first())
    }

    @Test
    fun `orphaned children are swept up after reconcile`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        // A receipt whose tracker does not exist locally — the shape left behind by a partially
        // applied pull.
        receiptDao.upsert(receipt("orphan", "ghost-tracker", "s1", TransactionType.EXPENSE, 10.0))

        engine.sync()

        assertTrue(receiptDao.rows.isEmpty())
    }

    @Test
    fun `a tracker new to this device is pulled even when it predates the watermark`() = runTest {
        identity.uid = uid
        preferences.markLinked(uid)
        preferences.setLastPullAt(10_000_000L)

        // Shared with this user long ago; its document sits well below the watermark. Filtering
        // purely on updatedAt would skip the parent and then orphan its children.
        remote.trackers["t1"] = remoteTracker("t1", updatedAt = 1_000)
        remote.sources["s1"] = RemoteSource(
            id = "s1", trackerId = "t1", name = "Rent",
            type = TransactionType.EXPENSE.name, updatedAt = 20_000_000
        )

        engine.sync()

        assertNotNull("an unseen tracker must be pulled regardless of its age", trackerDao.rows["t1"])
        assertNotNull("and its children must survive the orphan sweep", sourceDao.rows["s1"])
    }

    @Test
    fun `export to cloud leaves another owner's shared tracker alone`() = runTest {
        identity.uid = uid
        remote.trackers["mine"] = remoteTracker("mine", updatedAt = 500)
        remote.trackers["theirs"] = RemoteTracker(
            id = "theirs", name = "Carol's", ownerId = "uid-carol",
            sharedWith = listOf("uid-carol", uid), createdAt = 100, updatedAt = 500
        )
        remote.sources["cs1"] = RemoteSource(
            id = "cs1", trackerId = "theirs", name = "Carol's source",
            type = TransactionType.EXPENSE.name, updatedAt = 500
        )
        trackerDao.upsert(tracker("local1"))

        engine.sync(mode = SyncMode.FORCE_UPLOAD, manual = true)

        assertFalse(
            "a collaborator's records are not ours to delete",
            remote.trackers.getValue("theirs").deleted
        )
        assertFalse(remote.sources.getValue("cs1").deleted)
        assertTrue("our own cloud-only tracker is still tombstoned", remote.trackers.getValue("mine").deleted)
    }

    @Test
    fun `purging cloud data survives being unable to leave a shared tracker`() = runTest {
        identity.uid = uid
        remote.trackers["mine"] = remoteTracker("mine", updatedAt = 500)
        remote.trackers["theirs"] = RemoteTracker(
            id = "theirs", name = "Carol's", ownerId = "uid-carol",
            sharedWith = listOf("uid-carol", uid), createdAt = 100, updatedAt = 500
        )
        // The owner's rules reject a collaborator editing the share list.
        remote.failPushWith = IllegalStateException("permission denied")

        val result = engine.purgeCloudData()

        assertTrue("account deletion must not hinge on a cosmetic leftover", result.isSuccess)
        assertTrue("mine" in remote.deletedTrees)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builders
    // ─────────────────────────────────────────────────────────────────────────

    private fun tracker(
        id: String,
        name: String = "Tracker $id",
        owner: String = uid,
        sharedWith: List<String> = listOf(owner),
        pending: Boolean = true,
        deleted: Boolean = false,
        remoteUpdatedAt: Long? = null,
        updatedAt: Long = 1_000
    ) = TrackerEntity(
        id = id, name = name, ownerId = owner, sharedWith = sharedWith,
        createdAt = 1_000, updatedAt = updatedAt, deleted = deleted,
        pendingSync = pending, remoteUpdatedAt = remoteUpdatedAt
    )

    private fun source(id: String, trackerId: String, type: TransactionType) = SourceEntity(
        id = id, trackerId = trackerId, name = "Source $id", type = type,
        createdAt = 1_000, updatedAt = 1_000, pendingSync = false, remoteUpdatedAt = 1_000
    )

    private fun receipt(
        id: String,
        trackerId: String,
        sourceId: String,
        type: TransactionType,
        amount: Double
    ) = ReceiptEntity(
        id = id, trackerId = trackerId, sourceId = sourceId, type = type,
        name = "Receipt $id", amount = amount, date = 1_000,
        createdAt = 1_000, updatedAt = 1_000, pendingSync = false, remoteUpdatedAt = 1_000
    )

    private fun remoteTracker(
        id: String,
        name: String = "Remote $id",
        updatedAt: Long,
        owner: String = uid
    ) = RemoteTracker(
        id = id, name = name, ownerId = owner, sharedWith = listOf(owner),
        createdAt = 1_000, updatedAt = updatedAt
    )
}
