package com.xs.expensetracker.di

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.xs.expensetracker.data.sync.SyncLogger
import com.xs.expensetracker.utils.AppLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val loggerModule = module {
    // Bound under SyncLogger as well, because that is the type SyncEngine asks for. Without the
    // bind the graph only resolves by naming the concrete type at every call site, which hides
    // the missing binding from anything that reads constructor signatures.
    single<AppLogger> {
        AppLogger(
            crashlytics = FirebaseCrashlytics.getInstance(),
            analytics   = FirebaseAnalytics.getInstance(androidContext())
        )
    } bind SyncLogger::class
}