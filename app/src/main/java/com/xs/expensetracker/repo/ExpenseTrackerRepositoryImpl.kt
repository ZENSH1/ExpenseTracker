package com.xs.expensetracker.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.Tracker
import com.xs.expensetracker.data.models.TransactionReceipt
import com.xs.expensetracker.data.models.TransactionSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ExpenseTrackerRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ExpenseTrackerRepository {

    private val trackersRef get() = firestore.collection("trackers")

    // ------------------------------------------------
    // TRACKERS
    // ------------------------------------------------

    override fun observeTrackers(userId: String): Flow<List<Tracker>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = trackersRef
            .whereArrayContainsAny("sharedWith", listOf(userId))
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

        require(name.isNotBlank()) { "Tracker name cannot be empty" }
        require(ownerId.isNotBlank()) { "OwnerId missing" }

        val doc = trackersRef.document()

        val tracker = Tracker(
            id = doc.id,
            name = name.trim(),
            ownerId = ownerId,
            sharedWith = listOf(ownerId)
        )

        doc.set(tracker).await()
        doc.id
    }

    override suspend fun shareTracker(
        trackerId: String,
        userIdToShare: String
    ): Result<Unit> = runCatching {

        require(trackerId.isNotBlank())
        require(userIdToShare.isNotBlank())

        val trackerRef = trackersRef.document(trackerId)

        firestore.runTransaction { transaction ->

            val snapshot = transaction.get(trackerRef)
            if (!snapshot.exists()) {
                throw IllegalStateException("Tracker not found")
            }

            val current =
                snapshot.get("sharedWith") as? List<String> ?: emptyList()

            if (!current.contains(userIdToShare)) {
                transaction.update(
                    trackerRef,
                    "sharedWith",
                    current + userIdToShare
                )
            }
        }.await()
    }

    override suspend fun deleteTracker(trackerId: String): Result<Unit> =
        runCatching {
            require(trackerId.isNotBlank())
            trackersRef.document(trackerId).delete().await()
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
            .collection("sources")

        if (type != null) {
            query = query.whereEqualTo("type", type.name)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val sources = snapshot?.documents
                ?.mapNotNull {
                    it.toObject(TransactionSource::class.java)
                        ?.copy(id = it.id)
                } ?: emptyList()

            trySend(sources)
        }

        awaitClose { listener.remove() }
    }

    override suspend fun createSource(
        trackerId: String,
        name: String,
        type: TransactionType
    ): Result<String> = runCatching {

        require(trackerId.isNotBlank())
        require(name.isNotBlank())

        val doc = trackersRef
            .document(trackerId)
            .collection("sources")
            .document()

        val source = TransactionSource(
            id = doc.id,
            trackerId = trackerId,
            name = name.trim(),
            type = type,
            totalAmount = 0.0
        )

        doc.set(source.copy(type = type)).await()
        doc.id
    }

    override suspend fun deleteSource(
        trackerId: String,
        sourceId: String
    ): Result<Unit> = runCatching {

        require(trackerId.isNotBlank())
        require(sourceId.isNotBlank())

        trackersRef
            .document(trackerId)
            .collection("sources")
            .document(sourceId)
            .delete()
            .await()
    }

    // ------------------------------------------------
    // RECEIPTS
    // ------------------------------------------------

    override fun observeReceipts(
        trackerId: String,
        sourceId: String
    ): Flow<List<TransactionReceipt>> = callbackFlow {

        if (trackerId.isBlank() || sourceId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = trackersRef
            .document(trackerId)
            .collection("sources")
            .document(sourceId)
            .collection("receipts")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val receipts = snapshot?.documents
                    ?.mapNotNull {
                        it.toObject(TransactionReceipt::class.java)
                            ?.copy(id = it.id)
                    } ?: emptyList()

                trySend(receipts)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun addReceipt(
        trackerId: String,
        sourceId: String,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ): Result<String> = runCatching {

        require(amount > 0.0)
        require(name.isNotBlank())

        val sourceRef = trackersRef
            .document(trackerId)
            .collection("sources")
            .document(sourceId)

        val receiptRef = sourceRef
            .collection("receipts")
            .document()

        firestore.runTransaction { transaction ->

            val sourceSnap = transaction.get(sourceRef)
            if (!sourceSnap.exists())
                throw IllegalStateException("Source not found")

            val currentTotal =
                sourceSnap.getDouble("totalAmount") ?: 0.0

            val receipt = TransactionReceipt(
                id = receiptRef.id,
                trackerId = trackerId,
                sourceId = sourceId,
                name = name.trim(),
                description = description,
                amount = amount,
                date = date
            )

            transaction.set(receiptRef, receipt)
            transaction.update(
                sourceRef,
                "totalAmount",
                currentTotal + amount
            )
        }.await()

        receiptRef.id
    }

    override suspend fun updateReceipt(
        trackerId: String,
        sourceId: String,
        receipt: TransactionReceipt
    ): Result<Unit> = runCatching {

        val sourceRef = trackersRef
            .document(trackerId)
            .collection("sources")
            .document(sourceId)

        val receiptRef = sourceRef
            .collection("receipts")
            .document(receipt.id)

        firestore.runTransaction { transaction ->

            val oldSnap = transaction.get(receiptRef)
            if (!oldSnap.exists())
                throw IllegalStateException("Receipt not found")

            val oldAmount =
                oldSnap.getDouble("amount") ?: 0.0

            val delta = receipt.amount - oldAmount

            transaction.set(receiptRef, receipt)

            val sourceSnap = transaction.get(sourceRef)
            val currentTotal =
                sourceSnap.getDouble("totalAmount") ?: 0.0

            transaction.update(
                sourceRef,
                "totalAmount",
                currentTotal + delta
            )
        }.await()
    }

    override suspend fun deleteReceipt(
        trackerId: String,
        sourceId: String,
        receiptId: String
    ): Result<Unit> = runCatching {

        val sourceRef = trackersRef
            .document(trackerId)
            .collection("sources")
            .document(sourceId)

        val receiptRef = sourceRef
            .collection("receipts")
            .document(receiptId)

        firestore.runTransaction { transaction ->

            val receiptSnap = transaction.get(receiptRef)
            if (!receiptSnap.exists())
                throw IllegalStateException("Receipt not found")

            val amount =
                receiptSnap.getDouble("amount") ?: 0.0

            transaction.delete(receiptRef)

            val sourceSnap = transaction.get(sourceRef)
            val currentTotal =
                sourceSnap.getDouble("totalAmount") ?: 0.0

            transaction.update(
                sourceRef,
                "totalAmount",
                currentTotal - amount
            )
        }.await()
    }
}