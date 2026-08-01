package com.xs.expensetracker

import android.app.Application
import com.xs.expensetracker.data.sync.SyncScheduler
import com.xs.expensetracker.di.appModules
import com.xs.expensetracker.domain.repo.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class ExpenseApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val scheduler: SyncScheduler by inject()
    private val authRepository: AuthRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ExpenseApp)
            modules(appModules)
        }

        // Only meaningful with an account. Signing in later schedules this itself, so a user who
        // never signs in never has background work registered at all.
        if (authRepository.currentUser != null) {
            applicationScope.launch {
                runCatching {
                    scheduler.schedulePeriodicSync()
                    // Catches up anything written while the app was closed, or left behind when
                    // a retry chain ran out.
                    scheduler.requestSync()
                }
            }
        }
    }
}
