package com.xs.expensetracker.di

import com.xs.expensetracker.usecases.AuthUseCase
import com.xs.expensetracker.usecases.ReceiptUseCase
import com.xs.expensetracker.usecases.SourceUseCase
import com.xs.expensetracker.usecases.TrackerUseCase
import org.koin.dsl.module

val useCasesModule = module {

    single<AuthUseCase> {
        AuthUseCase(
            authRepository = get(),
            googleAuthManager = get()
        )
    }

    single<TrackerUseCase> {
        TrackerUseCase(repository = get())
    }

    single<SourceUseCase> {
        SourceUseCase(repository = get())
    }

    single<ReceiptUseCase> {
        ReceiptUseCase(repository = get())
    }
}