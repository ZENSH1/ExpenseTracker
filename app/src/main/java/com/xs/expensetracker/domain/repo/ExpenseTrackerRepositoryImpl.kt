package com.xs.expensetracker.domain.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.TransactionSource
import com.xs.expensetracker.utils.FirebaseConst
import com.xs.expensetracker.utils.FirebaseConst.AMOUNT
import com.xs.expensetracker.utils.FirebaseConst.DATE
import com.xs.expensetracker.utils.FirebaseConst.GRAND_TOTAL
import com.xs.expensetracker.utils.FirebaseConst.OWNER_ID_MISSING
import com.xs.expensetracker.utils.FirebaseConst.RECEIPTS
import com.xs.expensetracker.utils.FirebaseConst.SHARED_WITH
import com.xs.expensetracker.utils.FirebaseConst.SOURCES
import com.xs.expensetracker.utils.FirebaseConst.TOTAL_AMOUNT
import com.xs.expensetracker.utils.FirebaseConst.TRACKERS
import com.xs.expensetracker.utils.FirebaseConst.TRACK_NAME_CANNOT_BE_EMPTY
import com.xs.expensetracker.utils.Utils.runInBackground
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ExpenseTrackerRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ExpenseTrackerRepository {

    private val trackersRef get() = firestore.collection(TRACKERS)

    // ------------------------------------------------
    // TRACKERS
    // ------------------------------------------------

    override fun observeTracker(trackerId: String): Flow<Tracker?> = callbackFlow {
        if (trackerId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = trackersRef.document(trackerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Tracker::class.java))
            }
        awaitClose { listener.remove() }
    }

    override fun observeTrackers(userId: String): Flow<List<Tracker>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = trackersRef
            .whereArrayContainsAny(SHARED_WITH, listOf(userId))
            .orderBy(FirebaseConst.CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val trackers = snapshot?.documents
                    ?.mapNotNull { it.toObject(Tracker::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(trackers)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createTracker(
        name: String,
        ownerId: String
    ): Result<String> = runCatching {
        runInBackground {
            require(name.isNotBlank()) { TRACK_NAME_CANNOT_BE_EMPTY }
            require(ownerId.isNotBlank()) { OWNER_ID_MISSING }

            val doc = trackersRef.document()
            val tracker = Tracker(
                id = doc.id,
                name = name.trim(),
                ownerId = ownerId,
                grandTotal = 0.0,
                sharedWith = listOf(ownerId)
            )
            doc.set(tracker).await()
            doc.id
        }
    }

    override suspend fun updateTrackerName(
        trackerId: String,
        newName: String
    ): Result<Unit> = runCatching {
        runInBackground {
            require(trackerId.isNotBlank())
            require(newName.isNotBlank()) { TRACK_NAME_CANNOT_BE_EMPTY }

            trackersRef.document(trackerId)
                .update(FirebaseConst.NAME, newName.trim())
                .await()
        }
    }

    override suspend fun shareTracker(
        trackerId: String,
        userIdToShare: String
    ): Result<Unit> = runCatching {
        runInBackground {
            require(trackerId.isNotBlank())
            require(userIdToShare.isNotBlank())

            val trackerRef = trackersRef.document(trackerId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(trackerRef)
                if (!snapshot.exists()) throw IllegalStateException(FirebaseConst.TRACK_NOT_FOUND)

                val current = snapshot.get(SHARED_WITH) as? List<String> ?: emptyList()
                if (current.contains(userIdToShare)) {
                    throw IllegalStateException(FirebaseConst.USER_ALREADY_SHARED)
                }
                transaction.update(trackerRef, SHARED_WITH, current + userIdToShare)
            }.await()
        }
    }

    override suspend fun deleteTracker(trackerId: String): Result<Unit> = runCatching {
        runInBackground {
            require(trackerId.isNotBlank())
            trackersRef.document(trackerId).delete().await()
        }
    }

    // ------------------------------------------------
    // SOURCES
    // ------------------------------------------------

    override fun observeSources(
        trackerId: String,
        type: TransactionType?
    ): Flow<List<TransactionSource>> = callbackFlow {
        if (trackerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var query: Query = trackersRef
            .document(trackerId)
            .collection(SOURCES)

        if (type != null) {
            query = query.whereEqualTo(FirebaseConst.TYPE, type.name)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val sources = snapshot?.documents
                ?.mapNotNull { it.toObject(TransactionSource::class.java)?.copy(id = it.id) }
                ?: emptyList()
            trySend(sources)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun createSource(
        trackerId: String,
        name: String,
        type: TransactionType
    ): Result<String> = runCatching {
        runInBackground {
            require(trackerId.isNotBlank())
            require(name.isNotBlank())

            val doc = trackersRef
                .document(trackerId)
                .collection(SOURCES)
                .document()

            val source = TransactionSource(
                id = doc.id,
                trackerId = trackerId,
                name = name.trim(),
                type = type,
                totalAmount = 0.0
            )
            doc.set(source).await()
            doc.id
        }
    }

    override suspend fun deleteSource(
        trackerId: String,
        sourceId: String
    ): Result<Unit> = runCatching {
        runInBackground {
            require(trackerId.isNotBlank())
            require(sourceId.isNotBlank())

            trackersRef
                .document(trackerId)
                .collection(SOURCES)
                .document(sourceId)
                .delete()
                .await()
        }
    }

    // ------------------------------------------------
    // RECEIPTS
    // ------------------------------------------------

    override fun observeReceipts(
        trackerId: String,
        sourceId: String?
    ): Flow<List<TransactionReceipt>> = callbackFlow {
        if (trackerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = if (sourceId.isNullOrBlank()) {
            firestore.collectionGroup(RECEIPTS)
                .whereEqualTo("trackerId", trackerId)
                .orderBy(DATE, Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val receipts = snapshot?.documents
                        ?.mapNotNull { it.toObject(TransactionReceipt::class.java)?.copy(id = it.id) }
                        ?: emptyList()
                    trySend(receipts)
                }
        } else {
            trackersRef
                .document(trackerId)
                .collection(SOURCES)
                .document(sourceId)
                .collection(RECEIPTS)
                .orderBy(DATE, Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val receipts = snapshot?.documents
                        ?.mapNotNull { it.toObject(TransactionReceipt::class.java)?.copy(id = it.id) }
                        ?: emptyList()
                    trySend(receipts)
                }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun addReceipt(
        trackerId: String,
        sourceId: String,
        type: TransactionType,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ): Result<String> = runCatching {
        runInBackground {
            require(amount > 0.0) { "Amount must be greater than zero" }
            require(name.isNotBlank()) { "Receipt name cannot be empty" }

            val trackerRef = trackersRef.document(trackerId)
            val sourceRef = trackerRef.collection(SOURCES).document(sourceId)
            val receiptRef = sourceRef.collection(RECEIPTS).document()

            val signedAmount = if (type == TransactionType.INCOME) amount else -amount

            firestore.runTransaction { transaction ->
                val sourceSnap = transaction.get(sourceRef)
                if (!sourceSnap.exists()) throw IllegalStateException(FirebaseConst.SOURCE_NOT_FOUND)

                val trackerSnap = transaction.get(trackerRef)
                val currentTotal = sourceSnap.getDouble(TOTAL_AMOUNT) ?: 0.0
                val currentGrand = trackerSnap.getDouble(GRAND_TOTAL) ?: 0.0

                val receipt = TransactionReceipt(
                    id = receiptRef.id,
                    trackerId = trackerId,
                    sourceId = sourceId,
                    name = name.trim(),
                    description = description,
                    amount = amount,
                    date = date,
                    type = type
                )

                transaction.set(receiptRef, receipt)
                transaction.update(sourceRef, TOTAL_AMOUNT, currentTotal + amount)
                transaction.update(trackerRef, GRAND_TOTAL, currentGrand + signedAmount)
            }.await()

            receiptRef.id
        }
    }

    override suspend fun updateReceipt(
        trackerId: String,
        sourceId: String,
        receipt: TransactionReceipt
    ): Result<Unit> = runCatching {
        runInBackground {
            val trackerRef = trackersRef.document(trackerId)
            val sourceRef = trackerRef.collection(SOURCES).document(sourceId)
            val receiptRef = sourceRef.collection(RECEIPTS).document(receipt.id)

            firestore.runTransaction { transaction ->
                val oldSnap = transaction.get(receiptRef)
                if (!oldSnap.exists()) throw IllegalStateException(FirebaseConst.RECEIPT_NOT_FOUND)

                val oldAmount = oldSnap.getDouble(AMOUNT) ?: 0.0
                val oldType = oldSnap.getString(FirebaseConst.TYPE)
                    ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

                val sourceSnap = transaction.get(sourceRef)
                val trackerSnap = transaction.get(trackerRef)

                val currentTotal = sourceSnap.getDouble(TOTAL_AMOUNT) ?: 0.0
                val currentGrand = trackerSnap.getDouble(GRAND_TOTAL) ?: 0.0

                val sourceDelta = receipt.amount - oldAmount

                val oldSigned = if (oldType == TransactionType.INCOME) oldAmount else -oldAmount
                val newSigned = if (receipt.type == TransactionType.INCOME) receipt.amount else -receipt.amount
                val grandDelta = newSigned - oldSigned

                transaction.set(receiptRef, receipt)
                transaction.update(sourceRef, TOTAL_AMOUNT, currentTotal + sourceDelta)
                transaction.update(trackerRef, GRAND_TOTAL, currentGrand + grandDelta)
            }.await()
        }
    }

    override suspend fun deleteReceipt(
        trackerId: String,
        sourceId: String,
        receiptId: String
    ): Result<Unit> = runCatching {
        runInBackground {
            val trackerRef = trackersRef.document(trackerId)
            val sourceRef = trackerRef.collection(SOURCES).document(sourceId)
            val receiptRef = sourceRef.collection(RECEIPTS).document(receiptId)

            firestore.runTransaction { transaction ->
                val receiptSnap = transaction.get(receiptRef)
                if (!receiptSnap.exists()) throw IllegalStateException(FirebaseConst.RECEIPT_NOT_FOUND)

                val sourceSnap = transaction.get(sourceRef)
                val trackerSnap = transaction.get(trackerRef)

                val amount = receiptSnap.getDouble(AMOUNT) ?: 0.0
                val type = receiptSnap.getString(FirebaseConst.TYPE)
                    ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
                val signedAmount = if (type == TransactionType.INCOME) amount else -amount

                val currentTotal = sourceSnap.getDouble(TOTAL_AMOUNT) ?: 0.0
                val currentGrand = trackerSnap.getDouble(GRAND_TOTAL) ?: 0.0

                transaction.delete(receiptRef)
                transaction.update(sourceRef, TOTAL_AMOUNT, (currentTotal - amount).coerceAtLeast(0.0))
                transaction.update(trackerRef, GRAND_TOTAL, currentGrand - signedAmount)
            }.await()
        }
    }
}