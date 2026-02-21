package com.xs.expensetracker.di

import com.xs.expensetracker.repo.ExpenseTrackerRepositoryImpl
import com.xs.expensetracker.usecases.AuthUseCase
import org.koin.dsl.module


val useCasesModule = module {
    single<AuthUseCase> {
        AuthUseCase(
            authRepository = get(),
            googleAuthManager = get()
        )
    }
}