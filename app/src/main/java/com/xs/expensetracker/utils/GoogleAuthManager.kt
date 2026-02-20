package com.xs.expensetracker.utils

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.xs.expensetracker.utils.Utils.log

class GoogleAuthManager(
    private val firebaseAuth: FirebaseAuth
) {
    suspend fun signIn(activity: Activity): Result<String> {
        return try {
            val credentialManager = CredentialManager.create(activity)

           /* val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(AppConst.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()*/

            val signInWithGoogleOption = GetSignInWithGoogleOption
                .Builder(AppConst.GOOGLE_WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
              //  .addCredentialOption(googleIdOption)
                .addCredentialOption(signInWithGoogleOption) // <-- add this
                .build()

            // Launch the selector
            val result = credentialManager.getCredential(activity, request)

            val credential = result.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(Exception("Invalid credential type"))
            }

            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            Result.success(googleIdToken)

        } catch (e: Exception) {
            e.log()
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    fun currentUser(): FirebaseUser? = firebaseAuth.currentUser
}