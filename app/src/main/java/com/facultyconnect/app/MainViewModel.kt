package com.facultyconnect.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.ChatRepository
import com.facultyconnect.app.data.NotificationRepository
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Badge counts for the bottom navigation. */
class MainViewModel : ViewModel() {
    private val uid = Session.me?.uid.orEmpty()

    val unreadNotifications: StateFlow<Int> = NotificationRepository().observeUnreadCount(uid)
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unreadMessages: StateFlow<Int> = ChatRepository().observeConversations(uid)
        .map { r ->
            if (r is Resource.Success) r.data.sumOf { (it.unread[uid] ?: 0L).toInt() } else 0
        }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
