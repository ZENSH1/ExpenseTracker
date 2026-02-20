package com.xs.expensetracker

import android.app.Application
import com.xs.expensetracker.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.GlobalContext.startKoin

class ExpenseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ExpenseApp)
            modules(appModules)
        }
    }


}