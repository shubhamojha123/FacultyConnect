package com.facultyconnect.app.data

import com.facultyconnect.app.model.FacultyUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val users = Fb.db.collection(Col.USERS)

    suspend fun getUser(uid: String): FacultyUser? {
        val snap = users.document(uid).get().await()
        return if (snap.exists()) snap.toObject(FacultyUser::class.java) else null
    }

    /** Creates users/{uid} as unverified; an administrator approves the profile manually. */
    suspend fun createProfile(
        uid: String, email: String, fullName: String, department: String,
        designation: String, subjects: List<String>, photoUrl: String
    ) {
        val data = mapOf(
            "fullName" to fullName,
            "email" to email,
            "department" to department,
            "designation" to designation,
            "subjects" to subjects,
            "photoUrl" to photoUrl,
            "teamsLink" to "",
            "verified" to false,
            "notifyMessages" to true,
            "notifyAssignments" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )
        users.document(uid).set(data).await()
    }

    /** Only the keys listed in the rules' editable set may be passed. */
    suspend fun updateProfile(uid: String, changes: Map<String, Any>) {
        users.document(uid).update(changes).await()
    }

    suspend fun listVerified(): List<FacultyUser> =
        users.whereEqualTo("verified", true).limit(500).get().await().toObjects(FacultyUser::class.java)

    suspend fun uploadAvatar(uid: String, jpegBytes: ByteArray): String {
        val ref = Fb.storage.reference.child("profileImages/$uid/avatar_${System.currentTimeMillis()}.jpg")
        ref.putBytes(jpegBytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun saveDeviceToken(token: String) {
        val uid = Fb.auth.currentUser?.uid ?: return
        users.document(uid).collection("devices").document(token)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun removeDeviceToken(token: String) {
        val uid = Fb.auth.currentUser?.uid ?: return
        users.document(uid).collection("devices").document(token).delete().await()
    }
}
