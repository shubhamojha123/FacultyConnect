package com.facultyconnect.app

import com.facultyconnect.app.domain.AssignmentRules
import com.facultyconnect.app.domain.ConversationIds
import com.facultyconnect.app.domain.StatusRules
import com.facultyconnect.app.model.PostStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentRulesTest {
    private val A = PostStatus.AVAILABLE
    private val S = PostStatus.ASSIGNED
    private val C = PostStatus.CLOSED

    // ---- status transitions ----
    @Test fun allowedTransitions() {
        assertTrue(StatusRules.canTransition(A, S))
        assertTrue(StatusRules.canTransition(A, C))
        assertTrue(StatusRules.canTransition(S, A))   // assignment cancelled
        assertTrue(StatusRules.canTransition(S, C))
    }

    @Test fun closedIsFinalAndNoSelfTransitions() {
        assertFalse(StatusRules.canTransition(C, A))
        assertFalse(StatusRules.canTransition(C, S))
        assertFalse(StatusRules.canTransition(A, A))
        assertFalse(StatusRules.canTransition(S, S))
    }

    // ---- confirm ----
    @Test fun onlyPosterCanConfirm() {
        assertTrue(AssignmentRules.canConfirm(A, "poster", "poster", "other").allowed)
        assertFalse(AssignmentRules.canConfirm(A, "poster", "other", "other").allowed)
    }

    @Test fun cannotAssignTwiceOrToSelfOrWhenClosed() {
        assertFalse(AssignmentRules.canConfirm(S, "poster", "poster", "x").allowed)      // duplicate assignment
        assertFalse(AssignmentRules.canConfirm(C, "poster", "poster", "x").allowed)
        assertFalse(AssignmentRules.canConfirm(A, "poster", "poster", "poster").allowed)
        assertFalse(AssignmentRules.canConfirm(A, "poster", "poster", "").allowed)
    }

    // ---- edit / close / cancel ----
    @Test fun onlyAvailablePostsCanBeEditedByCreator() {
        assertTrue(AssignmentRules.canEdit(A, "p", "p").allowed)
        assertFalse(AssignmentRules.canEdit(S, "p", "p").allowed)
        assertFalse(AssignmentRules.canEdit(A, "p", "q").allowed)
    }

    @Test fun closeRules() {
        assertTrue(AssignmentRules.canClose(A, "p", "p", false).allowed)
        assertTrue(AssignmentRules.canClose(S, "p", "p", false).allowed)
        assertFalse(AssignmentRules.canClose(C, "p", "p", false).allowed)
        assertFalse(AssignmentRules.canClose(A, "p", "q", false).allowed)
        assertTrue(AssignmentRules.canClose(A, "p", "q", true).allowed)    // coordinator closes available post
        assertFalse(AssignmentRules.canClose(S, "p", "q", true).allowed)   // but not an assigned one
    }

    @Test fun cancelAssignmentRules() {
        assertTrue(AssignmentRules.canCancelAssignment(S, "p", "p").allowed)
        assertFalse(AssignmentRules.canCancelAssignment(A, "p", "p").allowed)
        assertFalse(AssignmentRules.canCancelAssignment(S, "p", "q").allowed)
    }

    // ---- conversation ids (duplicate prevention) ----
    @Test fun conversationIdIsSymmetricAndPostSpecific() {
        assertEquals(ConversationIds.direct("u1", "u2"), ConversationIds.direct("u2", "u1"))
        assertEquals(ConversationIds.forPost("u1", "u2", "p9"), ConversationIds.forPost("u2", "u1", "p9"))
        assertNotEquals(ConversationIds.direct("u1", "u2"), ConversationIds.forPost("u1", "u2", "p9"))
        assertNotEquals(ConversationIds.forPost("u1", "u2", "p9"), ConversationIds.forPost("u1", "u2", "p8"))
    }
}
