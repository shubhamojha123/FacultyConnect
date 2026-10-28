package com.facultyconnect.app.data

import com.facultyconnect.app.domain.AssignmentRules
import com.facultyconnect.app.model.Assignment
import com.facultyconnect.app.model.FacultyUser
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.model.PostStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Assignment workflow. Each operation is ONE Firestore transaction that re-reads the post first, so two
 * conflicting attempts cannot both succeed. firestore.rules independently re-validates every write.
 * assignments/{postId} uses the post id as document id: a post can never have two assignment documents.
 */
class AssignmentRepository {
    private val db = Fb.db

    private fun postRef(id: String) = db.collection(Col.POSTS).document(id)
    private fun asgRef(id: String) = db.collection(Col.ASSIGNMENTS).document(id)
    private fun notifRef(id: String) = db.collection(Col.NOTIFICATIONS).document(id)

    suspend fun confirm(postId: String, assigneeId: String, assigneeName: String, me: FacultyUser) {
        db.runTransaction { tx ->
            val post = tx.get(postRef(postId)).toObject(LecturePost::class.java)
                ?: throw RuleViolation("This post no longer exists")
            val check = AssignmentRules.canConfirm(post.postStatus, post.posterId, me.uid, assigneeId)
            if (!check.allowed) throw RuleViolation(check.reason ?: "Not allowed")

            tx.set(asgRef(postId), mapOf(
                "postId" to postId,
                "posterId" to me.uid,
                "posterName" to me.fullName,
                "assigneeId" to assigneeId,
                "assigneeName" to assigneeName,
                "participantIds" to listOf(me.uid, assigneeId),
                "status" to "confirmed",
                "type" to post.type,
                "subject" to post.subject,
                "className" to post.className,
                "room" to post.room,
                "startAt" to post.startAt,
                "endAt" to post.endAt,
                "confirmedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.update(postRef(postId), mapOf(
                "status" to PostStatus.ASSIGNED.value,
                "assignedToId" to assigneeId,
                "assignedToName" to assigneeName,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(
                notifRef(NotificationFactory.assignmentId(postId, assigneeId, NotificationFactory.TYPE_CONFIRMED)),
                NotificationFactory.build(
                    assigneeId, me.uid, me.fullName, NotificationFactory.TYPE_CONFIRMED,
                    "Lecture assigned to you",
                    "${me.fullName} confirmed you for ${post.subject} (${post.className}).",
                    "post", postId
                )
            )
            null
        }.await()
    }

    suspend fun cancelAssignment(postId: String, me: FacultyUser) {
        db.runTransaction { tx ->
            val post = tx.get(postRef(postId)).toObject(LecturePost::class.java)
                ?: throw RuleViolation("This post no longer exists")
            val check = AssignmentRules.canCancelAssignment(post.postStatus, post.posterId, me.uid)
            if (!check.allowed) throw RuleViolation(check.reason ?: "Not allowed")
            val assignee = post.assignedToId

            tx.update(asgRef(postId), mapOf("status" to "cancelled", "updatedAt" to FieldValue.serverTimestamp()))
            tx.update(postRef(postId), mapOf(
                "status" to PostStatus.AVAILABLE.value,
                "assignedToId" to FieldValue.delete(),
                "assignedToName" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            if (assignee != null) {
                tx.set(
                    notifRef(NotificationFactory.assignmentId(postId, assignee, NotificationFactory.TYPE_CANCELLED)),
                    NotificationFactory.build(
                        assignee, me.uid, me.fullName, NotificationFactory.TYPE_CANCELLED,
                        "Assignment cancelled",
                        "${me.fullName} cancelled your assignment for ${post.subject}.",
                        "post", postId
                    )
                )
            }
            null
        }.await()
    }

    /** Owner may close available or assigned posts; a coordinator may close available posts only. */
    suspend fun closePost(postId: String, me: FacultyUser, isCoordinator: Boolean) {
        db.runTransaction { tx ->
            val post = tx.get(postRef(postId)).toObject(LecturePost::class.java)
                ?: throw RuleViolation("This post no longer exists")
            val check = AssignmentRules.canClose(post.postStatus, post.posterId, me.uid, isCoordinator)
            if (!check.allowed) throw RuleViolation(check.reason ?: "Not allowed")
            val assignee = post.assignedToId

            if (post.postStatus == PostStatus.ASSIGNED) {
                tx.update(asgRef(postId), mapOf("status" to "cancelled", "updatedAt" to FieldValue.serverTimestamp()))
            }
            tx.update(postRef(postId), mapOf("status" to PostStatus.CLOSED.value, "updatedAt" to FieldValue.serverTimestamp()))
            if (post.postStatus == PostStatus.ASSIGNED && assignee != null) {
                tx.set(
                    notifRef(NotificationFactory.assignmentId(postId, assignee, NotificationFactory.TYPE_POST_CLOSED)),
                    NotificationFactory.build(
                        assignee, me.uid, me.fullName, NotificationFactory.TYPE_POST_CLOSED,
                        "Lecture post closed",
                        "${post.subject} was closed by ${me.fullName}; it is no longer on your schedule.",
                        "post", postId
                    )
                )
            }
            null
        }.await()
    }

    /** Confirmed assignments involving [uid]. Past/upcoming split happens on the client. */
    fun observeSchedule(uid: String): Flow<Resource<List<Assignment>>> = callbackFlow {
        trySend(Resource.Loading)
        val reg = db.collection(Col.ASSIGNMENTS)
            .whereArrayContains("participantIds", uid)
            .whereEqualTo("status", "confirmed")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) trySend(Resource.Error(ErrorMapper.message(err)))
                else if (snap != null) trySend(Resource.Success(snap.toObjects(Assignment::class.java), snap.metadata.isFromCache))
            }
        awaitClose { reg.remove() }
    }
}
