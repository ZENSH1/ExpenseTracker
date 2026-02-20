package com.xs.expensetracker.di

import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel {
        AuthViewModel(
            authRepository = get()
        )
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