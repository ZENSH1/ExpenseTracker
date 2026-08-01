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
 *   receipts/{receiptId}      ← sourceId is a field, not a path segment
 * ```
 *
 * Receipts hang directly off the tracker rather than off a source so an incremental pull costs
 * one query per tracker instead of one per source, and so no collection-group index is needed.
 * Receipts written by older app versions still live under `sources/{id}/receipts`; see
 * [fetchLegacyReceipts].
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
    private fun receiptsRef(trackerId: String) = trackerDoc(trackerId).collection(FirebaseConst.RECEIPTS)

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
                receiptsRef(trackerId).get(Source.SERVER).await()
            } else {
                receiptsRef(trackerId)
                    .whereGreaterThanOrEqualTo(FIELD_UPDATED_AT, since)
                    .get(Source.SERVER)
                    .await()
            }
            receiptDocs.documents.mapNotNullTo(receipts) { it.toRemoteReceipt(trackerId) }
        }

        RemoteSnapshot(sources = sources, receipts = receipts)
    }

    override suspend fun fetchLegacyReceipts(
        trackerId: String,
        sourceIds: List<String>
    ): List<RemoteReceipt> = withContext(Dispatchers.IO) {
        val out = mutableListOf<RemoteReceipt>()
        for (sourceId in sourceIds) {
            val docs = sourcesRef(trackerId)
                .document(sourceId)
                .collection(FirebaseConst.RECEIPTS)
                .get(Source.SERVER)
                .await()
            docs.documents.mapNotNullTo(out) { doc ->
                // Legacy documents predate both fields; default them so the record imports
                // cleanly and then behaves like any other from that point on.
                doc.toRemoteReceipt(trackerId)?.copy(sourceId = sourceId)
            }
        }
        out
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    override suspend fun push(push: RemotePush) = withContext(Dispatchers.IO) {
        if (push.isEmpty) return@withContext

        // Parents first: a source or receipt whose tracker document does not exist yet would be
        // unreadable by the security rules that gate access on the parent.
        val writes = buildList<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>> {
            push.trackers.forEach { add(trackerDoc(it.id) to it.toMap()) }
            push.sources.forEach { add(sourcesRef(it.trackerId).document(it.id) to it.toMap()) }
            push.receipts.forEach { add(receiptsRef(it.trackerId).document(it.id) to it.toMap()) }
        }

        writes.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
    }

    override suspend fun deleteTrackerTree(trackerId: String) = withContext(Dispatchers.IO) {
        val refs = buildList<com.google.firebase.firestore.DocumentReference> {
            receiptsRef(trackerId).get(Source.SERVER).await().documents.forEach { add(it.reference) }
            sourcesRef(trackerId).get(Source.SERVER).await().documents.forEach { sourceDoc ->
                // Sweep any legacy nested receipts too, or they would outlive their tracker.
                sourceDoc.reference.collection(FirebaseConst.RECEIPTS)
                    .get(Source.SERVER).await().documents.forEach { add(it.reference) }
                add(sourceDoc.reference)
            }
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
            receiptsRef(trackerId)
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
            sourceId = getString(FIELD_SOURCE_ID).orEmpty(),
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
        "trackerId" to trackerId,
        FirebaseConst.TYPE to type,
        FirebaseConst.CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED to deleted,
        FirebaseConst.TOTAL_AMOUNT to totalAmount
    )

    private fun RemoteReceipt.toMap(): Map<String, Any?> = mapOf(
        "trackerId" to trackerId,
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

        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_DELETED = "deleted"
        const val FIELD_SOURCE_ID = "sourceId"
        const val FIELD_DESCRIPTION = "description"
    }
}
