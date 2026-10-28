package com.facultyconnect.app.data

import com.google.firebase.firestore.FieldValue

/** Builds the exact key set that firestore.rules allows for notifications/{id}. */
object NotificationFactory {
    const val TYPE_MESSAGE = "message"
    const val TYPE_CONFIRMED = "assignment_confirmed"
    const val TYPE_CANCELLED = "assignment_cancelled"
    const val TYPE_POST_CLOSED = "post_closed"

    fun build(
        toUid: String, fromUid: String, fromName: String, type: String,
        title: String, body: String, refType: String, refId: String
    ): Map<String, Any> = mapOf(
        "toUid" to toUid,
        "fromUid" to fromUid,
        "fromName" to fromName,
        "type" to type,
        "title" to title,
        "body" to body,
        "refType" to refType,
        "refId" to refId,
        "read" to false,
        "createdAt" to FieldValue.serverTimestamp()
    )

    /** One notification per conversation per recipient (overwritten by newer messages) to avoid duplicates. */
    fun messageId(conversationId: String, toUid: String) = "msg_${conversationId}_$toUid"
    fun assignmentId(postId: String, toUid: String, type: String) = "asg_${postId}_${toUid}_$type"
}
