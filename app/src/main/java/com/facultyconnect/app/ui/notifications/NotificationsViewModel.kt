package com.facultyconnect.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.NotificationRepository
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.model.AppNotification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUi(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<AppNotification> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel : ViewModel() {
    private val repo = NotificationRepository()
    private val uid = Session.me?.uid.orEmpty()
    private val retry = MutableStateFlow(0)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    val state: StateFlow<NotificationsUi> = retry
        .flatMapLatest { repo.observe(uid) }
        .map { res ->
            when (res) {
                is Resource.Loading -> NotificationsUi(loading = true)
                is Resource.Error -> NotificationsUi(loading = false, error = res.message)
                is Resource.Success -> NotificationsUi(loading = false, items = res.data)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationsUi())

    fun retry() = retry.update { it + 1 }

    fun markRead(n: AppNotification) {
        if (n.read) return
        viewModelScope.launch { runCatching { repo.markRead(n.id) } }
    }

    fun markAllRead() {
        val ids = state.value.items.filter { !it.read }.map { it.id }
        viewModelScope.launch {
            try { repo.markAllRead(ids) } catch (e: Exception) { _events.emit(com.facultyconnect.app.data.ErrorMapper.message(e)) }
        }
    }
}
