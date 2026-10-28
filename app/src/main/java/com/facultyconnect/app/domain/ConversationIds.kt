package com.facultyconnect.app.domain

/**
 * Stable conversation ids so the same two people never get duplicate conversations.
 * Direct chat:  <uidA>_<uidB>            (uids sorted)
 * Lecture chat: <uidA>_<uidB>__<postId>
 */
object ConversationIds {
    fun direct(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")
    fun forPost(a: String, b: String, postId: String): String = direct(a, b) + "__" + postId
}
