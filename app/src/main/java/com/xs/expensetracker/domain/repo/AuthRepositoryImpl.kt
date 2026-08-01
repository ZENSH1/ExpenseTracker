package com.xs.expensetracker.domain.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val auth: FirebaseAuth
) : AuthRepository {

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    override fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener {
            trySend(it.currentUser)
        }


        auth.addAuthStateListener(listener)

        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String
    ): Result<FirebaseUser> = runCatching {

        require(idToken.isNotBlank()) { "Invalid Google ID token" }

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        val result = auth.signInWithCredential(credential).await()

        result.user ?: throw IllegalStateException("User is null after sign-in")
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        auth.signOut()
    }

    // The precondition lives inside runCatching so a signed-out caller gets a failed Result
    // like every other error path, instead of an exception thrown past the fold().
    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = currentUser ?: throw IllegalStateException("User is not authenticated")
        withContext(Dispatchers.IO) {
            user.delete().await()
        }
    }
}