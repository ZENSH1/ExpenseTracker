package com.xs.expensetracker.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.xs.expensetracker.utils.GoogleAuthManager
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


val firebaseModule = module {

    // Firestore
    single<FirebaseFirestore> {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder().build()
                )
            }
        }
    }

    // Firebase Auth
    single<FirebaseAuth> {
        FirebaseAuth.getInstance()
    }

    // Google Sign-In options
    single<GoogleAuthManager> {
        GoogleAuthManager(
            firebaseAuth = get()
        )
    }

}