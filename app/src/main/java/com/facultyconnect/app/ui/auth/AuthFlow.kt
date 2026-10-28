package com.facultyconnect.app.ui.auth

import android.app.Activity
import android.content.Intent
import com.facultyconnect.app.MainActivity
import com.facultyconnect.app.data.AuthRepository
import com.facultyconnect.app.data.Gate

object AuthFlow {
    /** Resolves the auth gate and opens the right screen. Throws on network/database errors. */
    suspend fun route(activity: Activity, repo: AuthRepository = AuthRepository()) {
        val target = when (repo.resolveGate()) {
            Gate.LOGIN -> LoginActivity::class.java
            Gate.EMAIL_UNVERIFIED, Gate.PENDING_APPROVAL -> PendingActivity::class.java
            Gate.READY -> MainActivity::class.java
        }
        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        activity.finish()
    }
}
