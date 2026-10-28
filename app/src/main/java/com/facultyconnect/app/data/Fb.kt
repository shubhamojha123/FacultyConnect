package com.facultyconnect.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/** Single access point for Firebase services. Only touch these after [isConfigured] is true. */
object Fb {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    /** False when google-services.json is missing (Firebase never initialised). */
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}

object Col {
    const val USERS = "users"
    const val POSTS = "lecturePosts"
    const val CONVERSATIONS = "conversations"
    const val MESSAGES = "messages"
    const val ASSIGNMENTS = "assignments"
    const val NOTIFICATIONS = "notifications"
}
