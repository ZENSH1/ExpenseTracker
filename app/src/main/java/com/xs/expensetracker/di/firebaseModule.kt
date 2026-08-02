package com.xs.expensetracker.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.firestoreSettings
import org.koin.dsl.module

/**
 * SDK singletons only, all of them reached through `getInstance()` rather than a constructor.
 * App-owned classes belong in the module for their layer — keeping this one to the Firebase
 * boundary is what lets [com.xs.expensetracker.di.KoinModulesTest] verify everything else,
 * since these types have no constructor for it to walk.
 */
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
}
