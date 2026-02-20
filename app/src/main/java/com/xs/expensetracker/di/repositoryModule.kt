package com.xs.expensetracker.di

import com.xs.expensetracker.repo.AuthRepository
import com.xs.expensetracker.repo.AuthRepositoryImpl
import com.xs.expensetracker.repo.ExpenseTrackerRepository
import com.xs.expensetracker.repo.ExpenseTrackerRepositoryImpl
import org.koin.dsl.module

val repositoryModule = module {

    single<ExpenseTrackerRepository> {
        ExpenseTrackerRepositoryImpl(
            firestore = get(),
            auth = get()
        )
    }

    single<AuthRepository> {
        AuthRepositoryImpl(
            auth = get()
        )
    }
}