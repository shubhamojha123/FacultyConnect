package com.facultyconnect.app.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.AssignmentRepository
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.PostRepository
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.model.LecturePost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModel : ViewModel() {
    private val posts = PostRepository()
    private val assignments = AssignmentRepository()
    private val postId = MutableStateFlow<String?>(null)

    val post: StateFlow<Resource<LecturePost?>> = postId.filterNotNull()
        .flatMapLatest { posts.observePost(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    fun load(id: String) { postId.value = id }

    fun close(p: LecturePost) = run("Post closed") {
        assignments.closePost(p.id, Session.me!!, Session.isCoordinator)
    }

    fun cancelAssignment(p: LecturePost) = run("Assignment cancelled. The post is available again.") {
        assignments.cancelAssignment(p.id, Session.me!!)
    }

    private fun run(success: String, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block(); _events.emit(success) } catch (e: Exception) { _events.emit(ErrorMapper.message(e)) }
            finally { _busy.value = false }
        }
    }
}
