package com.facultyconnect.app.data

import com.facultyconnect.app.model.LecturePost
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class FeedQuery(val status: String?, val type: String?, val limit: Long, val refresh: Int = 0)
data class FeedResult(val posts: List<LecturePost>, val fromCache: Boolean, val requestedLimit: Long)

class PostRepository {
    private val posts = Fb.db.collection(Col.POSTS)

    /** Live feed. Needs the composite indexes listed in firebase/firestore.indexes.json when status/type are set. */
    fun observePosts(q: FeedQuery): Flow<Resource<FeedResult>> = callbackFlow {
        trySend(Resource.Loading)
        var query: Query = posts
        q.status?.let { query = query.whereEqualTo("status", it) }
        q.type?.let { query = query.whereEqualTo("type", it) }
        query = query.orderBy("createdAt", Query.Direction.DESCENDING).limit(q.limit)
        val reg = query.addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) {
                trySend(Resource.Error(ErrorMapper.message(err)))
            } else if (snap != null) {
                trySend(Resource.Success(FeedResult(snap.toObjects(LecturePost::class.java), snap.metadata.isFromCache, q.limit)))
            }
        }
        awaitClose { reg.remove() }
    }

    fun observePost(id: String): Flow<Resource<LecturePost?>> = callbackFlow {
        trySend(Resource.Loading)
        val reg = posts.document(id).addSnapshotListener { snap, err ->
            if (err != null) trySend(Resource.Error(ErrorMapper.message(err)))
            else if (snap != null) trySend(Resource.Success(if (snap.exists()) snap.toObject(LecturePost::class.java) else null))
        }
        awaitClose { reg.remove() }
    }

    suspend fun getPost(id: String): LecturePost? {
        val s = posts.document(id).get().await()
        return if (s.exists()) s.toObject(LecturePost::class.java) else null
    }

    fun newPostId(): String = posts.document().id

    /** Uses a caller-supplied id so retrying after a timeout cannot create a second post. */
    suspend fun createPost(id: String, data: Map<String, Any?>) {
        withTimeout(15_000) { posts.document(id).set(data).await() }
    }

    suspend fun updateDetails(id: String, details: Map<String, Any>) {
        withTimeout(15_000) { posts.document(id).update(details + ("updatedAt" to FieldValue.serverTimestamp())).await() }
    }
}
