package com.facultyconnect.app.data

import com.facultyconnect.app.model.FacultyUser

/** In-memory snapshot of the signed-in faculty member, loaded by the auth gate before MainActivity opens. */
object Session {
    @Volatile var me: FacultyUser? = null
    /** From the Firebase Auth custom claim `role` (set only by an administrator script). */
    @Volatile var role: String = "faculty"

    val isCoordinator: Boolean get() = role == "coordinator" || role == "admin"
    fun clear() { me = null; role = "faculty" }
}
