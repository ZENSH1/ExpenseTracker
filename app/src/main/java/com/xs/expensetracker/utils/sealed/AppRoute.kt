package com.xs.expensetracker.utils.sealed

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