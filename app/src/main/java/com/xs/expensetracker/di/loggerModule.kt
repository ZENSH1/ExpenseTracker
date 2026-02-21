package com.xs.expensetracker.di

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.xs.expensetracker.utils.AppLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val loggerModule = module {
    single<AppLogger> {
        AppLogger(
            crashlytics = FirebaseCrashlytics.getInstance(),
            analytics   = FirebaseAnalytics.getInstance(androidContext())
        )
    }
}