package com.xs.expensetracker.utils.sealed

import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.Tracker
import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
sealed interface AppRoute : NavKey

@Serializable
object SplashRoute : AppRoute

@Serializable
object AuthRoute : AppRoute

@Serializable
object TrackerSelectionRoute : AppRoute

// Carries the selected Tracker so HomeScreen can use it immediately
// without a secondary fetch
data class HomeRoute(val tracker: Tracker) : AppRoute

@Serializable
object ProfileRoute : AppRoute

// trackerId carried here so child screens don't touch auth state directly
data class SourcesRoute(val trackerId: String, val type: TransactionType) : AppRoute

data class ReceiptsRoute(val trackerId: String, val type: TransactionType) : AppRoute