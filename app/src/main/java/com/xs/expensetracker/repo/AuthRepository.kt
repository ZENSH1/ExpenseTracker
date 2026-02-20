package com.xs.expensetracker.repo

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val currentUser: FirebaseUser?

    fun observeAuthState(): Flow<FirebaseUser?>

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>

    suspend fun signOut(): Result<Unit>
}