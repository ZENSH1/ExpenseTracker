package com.xs.expensetracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Resolves the dependency graph without starting Android.
 *
 * The rest of the suite builds its subjects by hand — [com.xs.expensetracker.sync.SyncEngineTest]
 * constructs the engine with fakes — so nothing else notices when a definition is registered
 * under a type nobody asks for. That gap shipped a crash: `IdentityProvider` was bound only
 * under its own type while `SyncEngine` asks for the `SyncIdentity` interface, so the app died
 * building its first ViewModel, on every cold start, in debug and release alike.
 *
 * [verify] walks each definition's constructor and checks every parameter type is resolvable
 * from the graph. It is reflection over signatures, not instantiation, which is what lets it run
 * on a plain JVM: nothing here touches a real Context, database or network.
 */
class KoinModulesTest {

    /**
     * Types that enter the graph without a constructor for [verify] to walk — `androidContext()`,
     * a factory method, a builder.
     *
     * Only third-party types belong here. Listing one asserts "the platform supplies this",
     * which is true for these and false for anything the app owns; adding an app type would
     * hide the very bug this test exists to catch.
     */
    private val platformTypes = listOf(
        Context::class,
        WorkManager::class,
        // Reached through the `Context.syncDataStore` extension property.
        DataStore::class,
        FirebaseFirestore::class,
        FirebaseAuth::class,
        // AppLogger takes these straight from getInstance() rather than the graph.
        FirebaseCrashlytics::class,
        FirebaseAnalytics::class
    )

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every definition can resolve its dependencies`() {
        // firebaseModule is excluded, not overlooked. It holds only SDK singletons obtained from
        // getInstance(), and verify() reads the declared type's constructor rather than the
        // lambda that builds it -- for FirebaseAuth that means descending into SDK internals
        // (FirebaseApp, com.google.firebase.inject.Provider, ...). Pinning those here would tie
        // the build to Firebase's private wiring and break on an SDK bump for no real reason.
        // The types it publishes are declared above instead, so everything that consumes them
        // is still checked.
        //
        // Verified as one graph rather than module by module: dependencies cross module
        // boundaries constantly (SyncEngine is declared in databaseModule but needs the logger
        // from loggerModule), and verifying each in isolation would report those as missing.
        val appOwnedModules = appModules - firebaseModule

        module { includes(appOwnedModules) }.verify(extraTypes = platformTypes)
    }
}
