package com.xs.expensetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xs.expensetracker.data.local.dao.ReceiptDao
import com.xs.expensetracker.data.local.dao.SourceDao
import com.xs.expensetracker.data.local.dao.SyncConflictDao
import com.xs.expensetracker.data.local.dao.SyncDao
import com.xs.expensetracker.data.local.dao.TrackerDao
import com.xs.expensetracker.data.local.entity.ReceiptEntity
import com.xs.expensetracker.data.local.entity.SourceEntity
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.local.entity.TrackerEntity

@Database(
    entities = [
        TrackerEntity::class,
        SourceEntity::class,
        ReceiptEntity::class,
        SyncConflictEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun trackerDao(): TrackerDao
    abstract fun sourceDao(): SourceDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "expense_tracker.db"

        fun build(context: Context): ExpenseDatabase =
            Room.databaseBuilder(context.applicationContext, ExpenseDatabase::class.java, NAME)
                // No destructive fallback: this database is the source of truth and may hold
                // records that have never reached the cloud. A missing migration must fail
                // loudly in development rather than wipe a user's unsynced data in production.
                .build()
    }
}
