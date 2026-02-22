package com.xs.expensetracker.di

import com.xs.expensetracker.domain.repo.AuthRepository
import com.xs.expensetracker.domain.repo.AuthRepositoryImpl
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepository
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepositoryImpl
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