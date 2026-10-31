package com.facultyconnect.app.data

import com.facultyconnect.app.model.AppNotification
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {
    private val col = Fb.db.collection(Col.NOTIFICATIONS)

    /**
     * Filter on the automatically indexed recipient field, then sort locally. This avoids requiring a
     * deployed composite index just to open Alerts (which otherwise leaves the screen unusable until an
     * administrator deploys firestore.indexes.json).
     */
    fun observe(uid: String): Flow<Resource<List<AppNotification>>> = callbackFlow {
        trySend(Resource.Loading)
        val reg = col.whereEqualTo("toUid", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) trySend(Resource.Error(ErrorMapper.message(err)))
                else if (snap != null) {
                    val items = snap.toObjects(AppNotification::class.java)
                        .sortedWith(
                            compareByDescending<AppNotification> { it.createdAt?.seconds ?: Long.MIN_VALUE }
                                .thenByDescending { it.createdAt?.nanoseconds ?: 0 }
                        )
                    trySend(Resource.Success(items, snap.metadata.isFromCache))
                }
            }
        awaitClose { reg.remove() }
    }

    fun observeUnreadCount(uid: String): Flow<Int> = callbackFlow {
        val reg = col.whereEqualTo("toUid", uid).whereEqualTo("read", false).limit(99)
            .addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.size()) }
        awaitClose { reg.remove() }
    }

    suspend fun markRead(id: String) {
        col.document(id).update("read", true).await()
    }

    suspend fun markAllRead(ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = Fb.db.batch()
        ids.take(400).forEach { batch.update(col.document(it), "read", true) }
        batch.commit().await()
    }
}
