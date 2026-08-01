package com.xs.expensetracker.utils.sealed

import androidx.navigation3.runtime.NavKey
import com.xs.expensetracker.domain.data.enums.TransactionType
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey

@Serializable
object SplashRoute : AppRoute

/**
 * Sign-in. Reachable from settings and the tracker list, but never forced — the app is fully
 * usable without ever visiting it.
 */
@Serializable
object AuthRoute : AppRoute

@Serializable
object TrackerSelectionRoute : AppRoute

/**
 * Carries only the id. An earlier version passed the whole [com.xs.expensetracker.domain.data.models.Tracker]
 * through the back stack, which meant a serialised snapshot of the balance that went stale the
 * moment a receipt changed — and, now that sync can rewrite records in the background, stale
 * far more often. The screen observes the tracker instead.
 */
@Serializable
data class HomeRoute(val trackerId: String) : AppRoute

@Serializable
object ProfileRoute : AppRoute

@Serializable
object SettingsRoute : AppRoute

@Serializable
object ConflictsRoute : AppRoute

@Serializable
data class SourcesRoute(val trackerId: String, val type: TransactionType) : AppRoute

@Serializable
data class ReceiptsRoute(val trackerId: String, val type: TransactionType) : AppRoute
