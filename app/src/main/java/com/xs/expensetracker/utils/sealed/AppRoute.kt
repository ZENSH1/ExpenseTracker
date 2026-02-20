package com.xs.expensetracker.utils.sealed

import com.xs.expensetracker.data.enums.TransactionType
import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
sealed interface AppRoute : NavKey

@Serializable
object SplashRoute : AppRoute

@Serializable
object AuthRoute : AppRoute

@Serializable
object HomeRoute : AppRoute

@Serializable
data class SourcesRoute(val type: TransactionType) : AppRoute

@Serializable
data class ReceiptsRoute(val type: TransactionType) : AppRoute