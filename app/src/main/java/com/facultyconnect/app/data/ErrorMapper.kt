package com.facultyconnect.app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.TimeoutCancellationException

/** Turns low-level exceptions into short, honest messages for the UI. */
object ErrorMapper {
    fun message(e: Throwable): String {
        val chain = generateSequence(e) { it.cause }.toList()
        chain.firstOrNull { it is RuleViolation }?.let { return it.message ?: "Not allowed" }
        for (t in chain) {
            when (t) {
                is FirebaseNetworkException -> return "No internet connection. Check your network and try again."
                is FirebaseAuthException -> return authMessage(t.errorCode)
                is FirebaseFirestoreException -> return when (t.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Firebase blocked this request. Deploy the FacultyConnect Firestore rules in firebase/firestore.rules, then try again."
                    FirebaseFirestoreException.Code.UNAVAILABLE -> "Service unavailable or you are offline."
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        "A Firestore index is missing. Deploy firebase/firestore.indexes.json (see README)."
                    FirebaseFirestoreException.Code.NOT_FOUND -> "Item not found."
                    FirebaseFirestoreException.Code.ABORTED -> "Another change happened at the same time. Please retry."
                    else -> "Database error: ${t.code.name.lowercase()}"
                }
                is StorageException -> return "Upload failed. Please try again."
                is TimeoutCancellationException -> return "The request timed out."
            }
            if (t.message?.contains("CONFIGURATION_NOT_FOUND") == true) {
                return "Email/Password sign-in is disabled. Enable Email/Password in Firebase Console > Authentication > Sign-in method."
            }
        }
        return e.message ?: "Something went wrong."
    }

    private fun authMessage(code: String) = when (code) {
        "ERROR_INVALID_EMAIL" -> "That email address is not valid."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIAL", "ERROR_USER_NOT_FOUND" ->
            "Email and password do not match. Try again or use Forgot password."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists."
        "ERROR_WEAK_PASSWORD" -> "Password is too weak (minimum 8 characters)."
        "ERROR_USER_DISABLED" -> "This account has been disabled."
        "ERROR_OPERATION_NOT_ALLOWED", "ERROR_CONFIGURATION_NOT_FOUND", "CONFIGURATION_NOT_FOUND" ->
            "Email/Password sign-in is disabled. Enable Email/Password in Firebase Console > Authentication > Sign-in method."
        "ERROR_TOO_MANY_REQUESTS", "ERROR_TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Please wait and try again."
        "ERROR_NETWORK_REQUEST_FAILED" -> "No internet connection."
        else -> if (code.contains("CONFIGURATION_NOT_FOUND"))
            "Email/Password sign-in is disabled. Enable Email/Password in Firebase Console > Authentication > Sign-in method."
        else "Authentication failed ($code)."
    }
}
