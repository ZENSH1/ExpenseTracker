package com.xs.expensetracker.di

import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.get
import org.koin.dsl.module

val viewModelModule = module {

    viewModel {
        AuthViewModel(
            authRepository = get(),
            googleAuthManager = get()
        )
    }

    viewModel {
        TransactionsViewModel(get())
    }

  /*  viewModel {
        TrackerViewModel(get())
    }

    viewModel {
        SourceViewModel(get())
    }

    viewModel {
        ReceiptViewModel(get())
    }*/
}