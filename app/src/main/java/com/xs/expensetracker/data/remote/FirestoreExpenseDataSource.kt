package com.xs.expensetracker.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.utils.FirebaseConst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore layout:
 *
 * ```
 * trackers/{trackerId}
 *   sources/{sourceId}
 *     receipts/{receiptId}
 * ```
 *
 * Receipts live under their source. This is the layout every released version has used, and it
 * is the one the app is expected to keep: an install that is never upgraded has to go on reading
 * and writing the same documents as one that is.
 *
 * Reading them back does not cost one query per source. A collection-group query on `receipts`
 * filtered by the denormalised `trackerId` field fetches a whole tracker's receipts in one
 * round trip — the same query the pre-offline client used for its receipt list. It also spans
 * every collection named `receipts`, so the flat `trackers/{id}/receipts` documents written by
 * 0.1.5-alpha are picked up by the same read without a separate rescue pass.
 *
 * Every read goes to [Source.SERVER]. Firestore's own offline cache would happily answer from
 * stale data, which is exactly the wrong thing during a sync: the local database is already the
 * offline story, and a cached read would make the engine reconcile against a snapshot it has
 * seen before.
 */
class FirestoreExpenseDataSource(
    private val firestore: FirebaseFirestore
) : RemoteExpenseDataSource {

    private val trackersRef get() = firestore.collection(FirebaseConst.TRACKERS)

    private fun trackerDoc(trackerId: String) = trackersRef.document(trackerId)
    private fun sourcesRef(trackerId: String) = trackerDoc(trackerId).collection(FirebaseConst.SOURCES)

    private fun receiptsRef(trackerId: String, sourceId: String) =
        sourcesRef(trackerId).document(sourceId).collection(FirebaseConst.RECEIPTS)

    /**
     * Every receipt belonging to one tracker, wherever it physically sits. Backed by a
     * collection-group index on `trackerId`.
     */
    private fun receiptsQuery(trackerId: String) =
        firestore.collectionGroup(FirebaseConst.RECEIPTS)
            .whereEqualTo(FIELD_TRACKER_ID, trackerId)

    // ── Reads ────────────────────────────────────────────────────────────────

    override suspend fun fetchTrackers(uid: String): List<RemoteTracker> = withContext(Dispatchers.IO) {
        // Filtering by `updatedAt` here too would pair an array-contains with a range filter and
        // require a composite index. A user has a handful of trackers, so fetch them all and let
        // the caller filter in memory — no index to provision, no query that can fail in the field.
        trackersRef
            .whereArrayContains(FirebaseConst.SHARED_WITH, uid)
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { it.toRemoteTracker() }
    }

    override suspend fun fetchChanges(
        uid: String,
        trackerIds: List<String>,
        since: Long
    ): RemoteSnapshot = withContext(Dispatchers.IO) {
        val sources = mutableListOf<RemoteSource>()
        val receipts = mutableListOf<RemoteReceipt>()

        for (trackerId in trackerIds) {
            val sourceDocs = if (since <= 0L) {
                sourcesRef(trackerId).get(Source.SERVER).await()
            } else {
                sourcesRef(trackerId)
                    .whereGreaterThanOrEqualTo(FIELD_UPDATED_AT, since)
                    .get(Source.SERVER)
                    .await()
            }
            sourceDocs.documents.mapNotNullTo(sources) { it.toRemoteSource(trackerId) }

            val receiptDocs = if (since <= 0L) {
                receiptsQuery(trackerId).get(Source.SERVER).await()
            } else {
                receiptsQuery(trackerId)
                    .whereGreaterThanOrEqualTo(FIELD_UPDATED_AT, since)
                    .get(Source.SERVER)
                    .await()
            }
            receiptDocs.documents.mapNotNullTo(receipts) { it.toRemoteReceipt(trackerId) }
        }

        RemoteSnapshot(
            sources = sources,
            // One receipt can answer the query from two places: 0.1.5-alpha wrote it flat, and a
            // later edit rewrote it under its source. Same id, two documents. Keep the newer and
            // let the stale copy be swept by [purgeTombstones] or [deleteTrackerTree], which
            // both sweep on the same query.
            receipts = receipts.groupBy { it.id }.values.mapNotNull { copies ->
                copies.maxByOrNull { it.updatedAt }
            }
        )
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    override suspend fun push(push: RemotePush) = withContext(Dispatchers.IO) {
        if (push.isEmpty) return@withContext

        // Parents first. Within one batch the order is irrelevant -- a batch is atomic, and the
        // rules judge child writes on the post-commit state, so a tracker written alongside its
        // children is visible to them. It matters across chunk boundaries: the chunks below
        // commit as separate batches, and children landing in an earlier chunk than their
        // tracker would be rejected outright by the rules that gate access on the parent.
        val writes = buildList<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>> {
            push.trackers.forEach { add(trackerDoc(it.id) to it.toMap()) }
            push.sources.forEach { add(sourcesRef(it.trackerId).document(it.id) to it.toMap()) }
            push.receipts.forEach { receipt ->
                // A receipt is addressed by its source, so one without a source has nowhere to
                // go. Locally that cannot happen; a record that reaches here without one is
                // corrupt, and inventing a path for it would strand it where nothing looks.
                if (receipt.sourceId.isNotBlank()) {
                    add(receiptsRef(receipt.trackerId, receipt.sourceId).document(receipt.id) to receipt.toMap())
                }
            }
        }

        writes.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
    }

    override suspend fun deleteTrackerTree(trackerId: String) = withContext(Dispatchers.IO) {
        val refs = buildList<com.google.firebase.firestore.DocumentReference> {
            // Spans both receipt layouts, so nothing outlives its tracker.
            receiptsQuery(trackerId).get(Source.SERVER).await().documents.forEach { add(it.reference) }
            sourcesRef(trackerId).get(Source.SERVER).await().documents.forEach { add(it.reference) }
            add(trackerDoc(trackerId))
        }

        refs.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }

    override suspend fun purgeTombstones(trackerId: String, before: Long) = withContext(Dispatchers.IO) {
        val stale = buildList<com.google.firebase.firestore.DocumentReference> {
            receiptsQuery(trackerId)
                .whereEqualTo(FIELD_DELETED, true)
                .whereLessThan(FIELD_UPDATED_AT, before)
                .get(Source.SERVER).await().documents.forEach { add(it.reference) }
            sourcesRef(trackerId)
                .whereEqualTo(FIELD_DELETED, true)
                .whereLessThan(FIELD_UPDATED_AT, before)
                .get(Source.SERVER).await().documents.forEach { add(it.reference) }
        }

        stale.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }

    // ── Document mapping ─────────────────────────────────────────────────────
    //
    // Mapped by hand rather than via `toObject`, so a document written by an older app version
    // (no `updatedAt`, no `deleted`) decodes to sensible defaults instead of throwing, and so
    // an unrecognised enum value can never crash a sync.

    private fun DocumentSnapshot.toRemoteTracker(): RemoteTracker? {
        val name = getString(FirebaseConst.NAME) ?: return null
        return RemoteTracker(
            id = id,
            name = name,
            ownerId = getString(FirebaseConst.OWNER_ID).orEmpty(),
            sharedWith = (get(FirebaseConst.SHARED_WITH) as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty(),
            createdAt = getLong(FirebaseConst.CREATED_AT) ?: 0L,
            updatedAt = getLong(FIELD_UPDATED_AT) ?: getLong(FirebaseConst.CREATED_AT) ?: 0L,
            deleted = getBoolean(FIELD_DELETED) ?: false,
            grandTotal = getDouble(FirebaseConst.GRAND_TOTAL) ?: 0.0
        )
    }

    private fun DocumentSnapshot.toRemoteSource(trackerId: String): RemoteSource? {
        val name = getString(FirebaseConst.NAME) ?: return null
        return RemoteSource(
            id = id,
            trackerId = trackerId,
            name = name,
            type = getString(FirebaseConst.TYPE) ?: TransactionType.EXPENSE.name,
            createdAt = getLong(FirebaseConst.CREATED_AT) ?: 0L,
            updatedAt = getLong(FIELD_UPDATED_AT) ?: getLong(FirebaseConst.CREATED_AT) ?: 0L,
            deleted = getBoolean(FIELD_DELETED) ?: false,
            totalAmount = getDouble(FirebaseConst.TOTAL_AMOUNT) ?: 0.0
        )
    }

    private fun DocumentSnapshot.toRemoteReceipt(trackerId: String): RemoteReceipt? {
        val name = getString(FirebaseConst.NAME) ?: return null
        return RemoteReceipt(
            id = id,
            trackerId = trackerId,
            sourceId = getString(FIELD_SOURCE_ID)?.takeIf { it.isNotBlank() } ?: sourceIdFromPath,
            type = getString(FirebaseConst.TYPE) ?: TransactionType.EXPENSE.name,
            name = name,
            description = getString(FIELD_DESCRIPTION).orEmpty(),
            amount = getDouble(FirebaseConst.AMOUNT) ?: 0.0,
            date = getLong(FirebaseConst.DATE) ?: 0L,
            createdAt = getLong(FirebaseConst.CREATED_AT) ?: 0L,
            updatedAt = getLong(FIELD_UPDATED_AT) ?: getLong(FirebaseConst.CREATED_AT) ?: 0L,
            deleted = getBoolean(FIELD_DELETED) ?: false
        )
    }

    /**
     * The owning source taken from the document's own path, for receipts written before
     * `sourceId` was stored as a field. Empty for a flat `trackers/{id}/receipts` document,
     * whose grandparent is the tracker rather than a source.
     */
    private val DocumentSnapshot.sourceIdFromPath: String
        get() {
            val parent = reference.parent.parent ?: return ""
            return if (parent.parent.id == FirebaseConst.SOURCES) parent.id else ""
        }

    private fun RemoteTracker.toMap(): Map<String, Any?> = mapOf(
        FirebaseConst.NAME to name,
        FirebaseConst.OWNER_ID to ownerId,
        FirebaseConst.SHARED_WITH to sharedWith,
        FirebaseConst.CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED to deleted,
        FirebaseConst.GRAND_TOTAL to grandTotal
    )

    private fun RemoteSource.toMap(): Map<String, Any?> = mapOf(
        FirebaseConst.NAME to name,
        FIELD_TRACKER_ID to trackerId,
        FirebaseConst.TYPE to type,
        FirebaseConst.CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED to deleted,
        FirebaseConst.TOTAL_AMOUNT to totalAmount
    )

    private fun RemoteReceipt.toMap(): Map<String, Any?> = mapOf(
        FIELD_TRACKER_ID to trackerId,
        FIELD_SOURCE_ID to sourceId,
        FirebaseConst.TYPE to type,
        FirebaseConst.NAME to name,
        FIELD_DESCRIPTION to description,
        FirebaseConst.AMOUNT to amount,
        FirebaseConst.DATE to date,
        FirebaseConst.CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED to deleted
    )

    private companion object {
        /** Firestore caps a batch at 500 operations; leave headroom. */
        const val BATCH_LIMIT = 450

        const val FIELD_TRACKER_ID = "trackerId"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_DELETED = "deleted"
        const val FIELD_SOURCE_ID = "sourceId"
        const val FIELD_DESCRIPTION = "description"
    }
}
