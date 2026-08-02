package com.xs.expensetracker.di

import androidx.work.WorkManager
import com.xs.expensetracker.data.local.ExpenseDatabase
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

    single { ExpenseDatabase.build(androidContext()) }
    single { get<ExpenseDatabase>().trackerDao() }
    single { get<ExpenseDatabase>().sourceDao() }
    single { get<ExpenseDatabase>().receiptDao() }
    single { get<ExpenseDatabase>().syncConflictDao() }
    single { get<ExpenseDatabase>().syncDao() }

    single { SyncPreferences(androidContext().syncDataStore) }

    // Bound under both types deliberately. The repository and TransactionsViewModel ask for the
    // concrete IdentityProvider, SyncEngine asks for the SyncIdentity interface, and they have
    // to be the same instance -- two identities would let the engine sync against one uid while
    // the UI reads another. Without the bind, resolving SyncEngine throws
    // NoDefinitionFoundException and the app dies building its first ViewModel.
    single { IdentityProvider(authRepository = get(), preferences = get()) } bind SyncIdentity::class

    single<RemoteExpenseDataSource> { FirestoreExpenseDataSource(firestore = get()) }

    single { WorkManager.getInstance(androidContext()) }
    single { SyncScheduler(workManager = get(), preferences = get()) }

    single {
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
