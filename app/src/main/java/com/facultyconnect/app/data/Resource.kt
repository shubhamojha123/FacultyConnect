package com.facultyconnect.app.data

sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<T>(val data: T, val fromCache: Boolean = false) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}

/** Thrown for business-rule violations so the UI can show the message as-is. */
class RuleViolation(message: String) : Exception(message)
