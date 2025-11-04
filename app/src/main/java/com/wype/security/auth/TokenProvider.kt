package com.wype.security.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class TokenProvider {
    suspend fun bearer(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User not signed in")
        val token = user.getIdToken(true).await().token
            ?: throw IllegalStateException("Failed to get ID token")
        return "Bearer $token"
    }
}
