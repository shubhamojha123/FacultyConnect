package com.facultyconnect.app.domain

import com.facultyconnect.app.model.PostStatus

data class Eligibility(val allowed: Boolean, val reason: String? = null) {
    companion object {
        fun ok() = Eligibility(true)
        fun no(reason: String) = Eligibility(false, reason)
    }
}

/** Allowed status transitions: Available <-> Assigned, and either -> Closed. Closed is final. */
object StatusRules {
    fun canTransition(from: PostStatus, to: PostStatus): Boolean = when (from) {
        PostStatus.AVAILABLE -> to == PostStatus.ASSIGNED || to == PostStatus.CLOSED
        PostStatus.ASSIGNED -> to == PostStatus.AVAILABLE || to == PostStatus.CLOSED
        PostStatus.CLOSED -> false
    }
}

/** Client-side mirror of the checks enforced by firestore.rules (the rules are the real authority). */
object AssignmentRules {

    fun canConfirm(status: PostStatus, posterId: String, actorUid: String, assigneeUid: String): Eligibility = when {
        actorUid != posterId -> Eligibility.no("Only the person who posted this lecture can confirm an assignment")
        assigneeUid.isBlank() -> Eligibility.no("Choose a faculty member to assign")
        assigneeUid == posterId -> Eligibility.no("You cannot assign a lecture to yourself")
        status == PostStatus.ASSIGNED -> Eligibility.no("This lecture is already assigned")
        status == PostStatus.CLOSED -> Eligibility.no("This post is closed")
        else -> Eligibility.ok()
    }

    fun canEdit(status: PostStatus, posterId: String, actorUid: String): Eligibility = when {
        actorUid != posterId -> Eligibility.no("Only the creator can edit this post")
        status != PostStatus.AVAILABLE -> Eligibility.no("Only available posts can be edited. Cancel the assignment first")
        else -> Eligibility.ok()
    }

    fun canClose(status: PostStatus, posterId: String, actorUid: String, isCoordinator: Boolean): Eligibility = when {
        status == PostStatus.CLOSED -> Eligibility.no("This post is already closed")
        actorUid == posterId -> Eligibility.ok()
        isCoordinator && status == PostStatus.AVAILABLE -> Eligibility.ok()
        isCoordinator -> Eligibility.no("The poster must cancel the assignment before a coordinator can close this post")
        else -> Eligibility.no("Only the creator or a department coordinator can close this post")
    }

    fun canCancelAssignment(status: PostStatus, posterId: String, actorUid: String): Eligibility = when {
        actorUid != posterId -> Eligibility.no("Only the creator can cancel an assignment")
        status != PostStatus.ASSIGNED -> Eligibility.no("This lecture is not currently assigned")
        else -> Eligibility.ok()
    }
}
