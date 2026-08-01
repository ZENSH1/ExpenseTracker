package com.xs.expensetracker.di

import com.xs.expensetracker.data.sync.SyncScheduler
import com.xs.expensetracker.domain.repo.AuthRepository
import com.xs.expensetracker.domain.repo.AuthRepositoryImpl
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepository
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepositoryImpl
import com.xs.expensetracker.domain.repo.SyncRepository
import com.xs.expensetracker.domain.repo.SyncRepositoryImpl
import org.koin.dsl.module

val repositoryModule = module {

    single<ExpenseTrackerRepository> {
        ExpenseTrackerRepositoryImpl(
            database = get(),
            trackerDao = get(),
            sourceDao = get(),
            receiptDao = get(),
            conflictDao = get(),
            identity = get(),
            // Every local write nudges the scheduler. Passed as a lambda rather than the
            // repository depending on SyncRepository, which would close a dependency cycle
            // (SyncRepository → AuthRepository → … → ExpenseTrackerRepository).
            onLocalChange = { get<SyncScheduler>().requestSync() }
        )
    }

    single<AuthRepository> { AuthRepositoryImpl(auth = get()) }

    single<SyncRepository> {
        SyncRepositoryImpl(
            syncEngine = get(),
            scheduler = get(),
            preferences = get(),
            conflictDao = get(),
            syncDao = get(),
            authRepository = get()
        )
    }
}
