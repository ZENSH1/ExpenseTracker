package com.xs.expensetracker.di

import androidx.work.WorkManager
import com.xs.expensetracker.data.local.ExpenseDatabase
import com.xs.expensetracker.data.local.dao.ReceiptDao
import com.xs.expensetracker.data.local.dao.SourceDao
import com.xs.expensetracker.data.local.dao.SyncConflictDao
import com.xs.expensetracker.data.local.dao.SyncDao
import com.xs.expensetracker.data.local.dao.TrackerDao
import com.xs.expensetracker.data.prefs.IdentityProvider
import com.xs.expensetracker.data.prefs.SyncPreferences
import com.xs.expensetracker.data.prefs.syncDataStore
import com.xs.expensetracker.data.remote.FirestoreExpenseDataSource
import com.xs.expensetracker.data.remote.RemoteExpenseDataSource
import com.xs.expensetracker.data.sync.SyncEngine
import com.xs.expensetracker.data.sync.SyncIdentity
import com.xs.expensetracker.data.sync.SyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val databaseModule = module {

    single<ExpenseDatabase> { ExpenseDatabase.build(androidContext()) }
    single<TrackerDao> { get<ExpenseDatabase>().trackerDao() }
    single<SourceDao> { get<ExpenseDatabase>().sourceDao() }
    single<ReceiptDao> { get<ExpenseDatabase>().receiptDao() }
    single<SyncConflictDao> { get<ExpenseDatabase>().syncConflictDao() }
    single<SyncDao> { get<ExpenseDatabase>().syncDao() }

    single<SyncPreferences> { SyncPreferences(androidContext().syncDataStore) }

    // Bound under both types deliberately, from one instance -- two identities would let the
    // engine sync against one uid while the UI reads another.
    //
    // Neither type alone works. Drop the bind and SyncEngine, which asks for SyncIdentity,
    // throws NoDefinitionFoundException and the app dies building its first ViewModel. Declare
    // it as single<SyncIdentity> instead and the same crash lands on IdentityProvider, which
    // ExpenseTrackerRepositoryImpl and TransactionsViewModel both inject -- and the ViewModel
    // cannot be narrowed to the interface, because observeOwnerId() is not on it.
    single<IdentityProvider> {
        IdentityProvider(authRepository = get(), preferences = get())
    } bind SyncIdentity::class

    single<RemoteExpenseDataSource> { FirestoreExpenseDataSource(firestore = get()) }

    single<WorkManager> { WorkManager.getInstance(androidContext()) }
    single<SyncScheduler> { SyncScheduler(workManager = get(), preferences = get()) }

    single<SyncEngine> {
        SyncEngine(
            trackerDao = get(),
            sourceDao = get(),
            receiptDao = get(),
            conflictDao = get(),
            syncDao = get(),
            remote = get(),
            identity = get(),
            preferences = get(),
            logger = get()
        )
    }
}
