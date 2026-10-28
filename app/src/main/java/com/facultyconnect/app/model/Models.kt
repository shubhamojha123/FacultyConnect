package com.facultyconnect.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

enum class PostType(val value: String, val label: String) {
    REPLACEMENT("replacement", "Lecture Replacement"),
    EXTRA("extra", "Extra Lecture");

    companion object {
        fun from(v: String?) = values().firstOrNull { it.value == v } ?: REPLACEMENT
    }
}

enum class PostStatus(val value: String, val label: String) {
    AVAILABLE("available", "Available"),
    ASSIGNED("assigned", "Assigned"),
    CLOSED("closed", "Closed");

    companion object {
        fun from(v: String?) = values().firstOrNull { it.value == v } ?: AVAILABLE
    }
}

/** users/{uid}. `verified` is changed only by an administrator after reviewing registration. */
data class FacultyUser(
    @DocumentId val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val department: String = "",
    val designation: String = "",
    val subjects: List<String> = emptyList(),
    val photoUrl: String = "",
    val teamsLink: String = "",
    val verified: Boolean = false,
    val notifyMessages: Boolean = true,
    val notifyAssignments: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

/** lecturePosts/{postId}. Poster name/department/photo are denormalised snapshots for fast feed rendering. */
data class LecturePost(
    @DocumentId val id: String = "",
    val posterId: String = "",
    val posterName: String = "",
    val posterDepartment: String = "",
    val posterPhotoUrl: String = "",
    val type: String = PostType.REPLACEMENT.value,
    val subject: String = "",
    val className: String = "",
    val room: String = "",
    val reason: String = "",
    val teamsLink: String = "",
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val status: String = PostStatus.AVAILABLE.value,
    val assignedToId: String? = null,
    val assignedToName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    @get:Exclude val postType get() = PostType.from(type)
    @get:Exclude val postStatus get() = PostStatus.from(status)
}

/** conversations/{conversationId}; messages live in the `messages` sub-collection. */
data class Conversation(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val postId: String? = null,
    val postSubject: String? = null,
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val lastSenderId: String = "",
    val unread: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun otherUid(me: String) = participants.firstOrNull { it != me } ?: ""
}

enum class SendState { SENT, PENDING, FAILED }

/** UI model of a chat message (not a Firestore POJO). */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Timestamp?,
    val readBy: List<String>,
    val state: SendState
)

/** assignments/{postId}: one document per post, so a post can never have two assignments. */
data class Assignment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val posterId: String = "",
    val posterName: String = "",
    val assigneeId: String = "",
    val assigneeName: String = "",
    val participantIds: List<String> = emptyList(),
    val status: String = "confirmed",
    val type: String = PostType.REPLACEMENT.value,
    val subject: String = "",
    val className: String = "",
    val room: String = "",
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val confirmedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/** notifications/{id}; readable only by `toUid`. */
data class AppNotification(
    @DocumentId val id: String = "",
    val toUid: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val refType: String = "",
    val refId: String = "",
    val read: Boolean = false,
    val createdAt: Timestamp? = null
)
