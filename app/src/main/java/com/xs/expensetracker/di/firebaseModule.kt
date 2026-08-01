package com.xs.expensetracker.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.xs.expensetracker.utils.GoogleAuthManager
import org.koin.dsl.module

val firebaseModule = module {

    single<FirebaseFirestore> {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings {
                // Room is the offline story now, and sync reads explicitly target the server.
                // A second on-disk cache would duplicate the whole dataset and serve stale
                // snapshots to the one component that must never see them — the sync engine.
                setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            }
        }
    }

    single<FirebaseAuth> { FirebaseAuth.getInstance() }

    single<GoogleAuthManager> { GoogleAuthManager(firebaseAuth = get()) }
}
