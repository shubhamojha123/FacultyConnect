package com.facultyconnect.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.ChatRepository
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.model.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class MessagesUi(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<Conversation> = emptyList(),
    val fromCache: Boolean = false
)

class MessagesViewModel : ViewModel() {
    private val uid = Session.me?.uid.orEmpty()
    private val query = MutableStateFlow("")
    private val retry = MutableStateFlow(0)

    val state: StateFlow<MessagesUi> = combine(
        ChatRepository().observeConversations(uid), query, retry
    ) { res, q, _ ->
        when (res) {
            is Resource.Loading -> MessagesUi(loading = true)
            is Resource.Error -> MessagesUi(loading = false, error = res.message)
            is Resource.Success -> {
                val needle = q.trim().lowercase()
                MessagesUi(
                    loading = false, fromCache = res.fromCache,
                    items = if (needle.isEmpty()) res.data else res.data.filter {
                        (it.participantNames[it.otherUid(uid)] ?: "").lowercase().contains(needle) ||
                            (it.postSubject ?: "").lowercase().contains(needle) ||
                            it.lastMessage.lowercase().contains(needle)
                    }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MessagesUi())

    fun setQuery(q: String) { query.value = q }
    fun retry() = retry.update { it + 1 }
}
