package com.facultyconnect.app.data

import com.facultyconnect.app.model.ChatMessage
import com.facultyconnect.app.model.Conversation
import com.facultyconnect.app.model.FacultyUser
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.model.SendState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class ChatRepository {
    private val db = Fb.db

    private fun convRef(id: String) = db.collection(Col.CONVERSATIONS).document(id)
    private fun msgCol(convId: String) = convRef(convId).collection(Col.MESSAGES)

    fun newMessageId(convId: String): String = msgCol(convId).document().id

    suspend fun hasMessage(convId: String, messageId: String): Boolean =
        msgCol(convId).document(messageId).get().await().exists()

    /** Needs composite index: conversations (participants CONTAINS, lastMessageAt DESC). */
    fun observeConversations(uid: String): Flow<Resource<List<Conversation>>> = callbackFlow {
        trySend(Resource.Loading)
        val reg = db.collection(Col.CONVERSATIONS)
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) trySend(Resource.Error(ErrorMapper.message(err)))
                else if (snap != null) trySend(Resource.Success(snap.toObjects(Conversation::class.java), snap.metadata.isFromCache))
            }
        awaitClose { reg.remove() }
    }

    /**
     * Returns the existing conversation or creates it. [convId] is deterministic (see ConversationIds), so
     * repeated taps never create duplicates. The conversation only appears in the Messages list after its
     * first message (it is ordered by lastMessageAt).
     */
    suspend fun getOrCreate(convId: String, me: FacultyUser, other: FacultyUser, post: LecturePost?): Conversation {
        val ref = convRef(convId)
        val snap = ref.get().await()
        if (snap.exists()) return snap.toObject(Conversation::class.java)!!

        val data = mutableMapOf<String, Any>(
            "participants" to listOf(me.uid, other.uid),
            "participantNames" to mapOf(me.uid to me.fullName, other.uid to other.fullName),
            "participantPhotos" to mapOf(me.uid to me.photoUrl, other.uid to other.photoUrl),
            "lastMessage" to "",
            "lastSenderId" to "",
            "unread" to mapOf(me.uid to 0L, other.uid to 0L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (post != null) {
            data["postId"] = post.id
            data["postSubject"] = post.subject
        }
        // If the device is offline the write is queued locally; don't block the UI forever.
        withTimeoutOrNull(8_000) { ref.set(data).await() }
        return Conversation(
            id = convId, participants = listOf(me.uid, other.uid),
            participantNames = mapOf(me.uid to me.fullName, other.uid to other.fullName),
            participantPhotos = mapOf(me.uid to me.photoUrl, other.uid to other.photoUrl),
            postId = post?.id, postSubject = post?.subject,
            unread = mapOf(me.uid to 0L, other.uid to 0L)
        )
    }

    suspend fun getConversation(convId: String): Conversation? {
        val s = convRef(convId).get().await()
        return if (s.exists()) s.toObject(Conversation::class.java) else null
    }

    /** Newest first. Pending local writes are reported as SendState.PENDING until the server confirms them. */
    fun observeMessages(convId: String, limit: Long): Flow<Resource<List<ChatMessage>>> = callbackFlow {
        trySend(Resource.Loading)
        val reg = msgCol(convId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    trySend(Resource.Error(ErrorMapper.message(err)))
                } else if (snap != null) {
                    val list = snap.documents.map { toChatMessage(it) }
                    trySend(Resource.Success(list, snap.metadata.isFromCache))
                }
            }
        awaitClose { reg.remove() }
    }

    private fun toChatMessage(d: DocumentSnapshot): ChatMessage = ChatMessage(
        id = d.id,
        senderId = d.getString("senderId").orEmpty(),
        text = d.getString("text").orEmpty(),
        createdAt = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        readBy = (d.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        state = if (d.metadata.hasPendingWrites()) SendState.PENDING else SendState.SENT
    )

    /**
     * Atomically writes the message, updates the conversation summary / recipient unread counter and
     * upserts the recipient's single "new message" notification. [msgId] is chosen by the caller, so
     * retrying a failed send re-uses the same document (no duplicate message).
     * Completes only when the SERVER acknowledges (or throws on rejection); while offline it stays pending.
     */
    suspend fun send(conv: Conversation, me: FacultyUser, msgId: String, text: String) {
        val other = conv.otherUid(me.uid)
        val batch = db.batch()
        batch.set(msgCol(conv.id).document(msgId), mapOf(
            "senderId" to me.uid,
            "text" to text,
            "readBy" to listOf(me.uid),
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.update(convRef(conv.id), mapOf(
            "lastMessage" to text.take(120),
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastSenderId" to me.uid,
            "unread.$other" to FieldValue.increment(1)
        ))
        batch.set(
            db.collection(Col.NOTIFICATIONS).document(NotificationFactory.messageId(conv.id, other)),
            NotificationFactory.build(
                other, me.uid, me.fullName, NotificationFactory.TYPE_MESSAGE,
                "New message from ${me.fullName}", text.take(80), "conversation", conv.id
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
        batch.commit().await()
    }

    /** Marks incoming messages as read and clears my unread counter. */
    suspend fun markRead(convId: String, myUid: String, messageIds: List<String>) {
        val batch = db.batch()
        messageIds.take(400).forEach { id ->
            batch.update(msgCol(convId).document(id), "readBy", FieldValue.arrayUnion(myUid))
        }
        batch.update(convRef(convId), "unread.$myUid", 0L)
        batch.commit().await()
        runCatching {
            db.collection(Col.NOTIFICATIONS).document(NotificationFactory.messageId(convId, myUid))
                .update("read", true).await()
        }
    }
}
