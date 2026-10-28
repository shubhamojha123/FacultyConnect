package com.facultyconnect.app.data

import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await

enum class Gate { LOGIN, EMAIL_UNVERIFIED, PENDING_APPROVAL, READY }

class AuthRepository(private val users: UserRepository = UserRepository()) {
    private val auth = Fb.auth

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun sendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun deleteCurrentUser() {
        auth.currentUser?.delete()?.await()
    }

    fun signOut() {
        auth.signOut()
        Session.clear()
    }

    /**
     * Decides where the signed-in user may go.
     * 1. Email must be verified (proves they own the address).
     * 2. users/{uid}.verified must be true (set by an administrator after reviewing the registration).
     * On READY, [Session] is populated.
     */
    suspend fun resolveGate(): Gate {
        val user = auth.currentUser ?: return Gate.LOGIN
        try {
            user.reload().await()
        } catch (e: FirebaseAuthInvalidUserException) {
            signOut(); return Gate.LOGIN
        } catch (_: Exception) {
            // Offline: continue with the cached auth state.
        }
        val fresh = auth.currentUser ?: return Gate.LOGIN
        if (!fresh.isEmailVerified) return Gate.EMAIL_UNVERIFIED
        // Refresh the ID token so rules see email_verified = true.
        val tokenResult = runCatching { fresh.getIdToken(true).await() }.getOrNull()
        val profile = users.getUser(fresh.uid)
        if (profile == null || !profile.verified) return Gate.PENDING_APPROVAL
        Session.me = profile
        Session.role = (tokenResult?.claims?.get("role") as? String) ?: "faculty"
        return Gate.READY
    }
}
