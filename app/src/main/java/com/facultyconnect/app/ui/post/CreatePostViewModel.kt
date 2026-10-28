package com.facultyconnect.app.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.PostRepository
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.model.PostStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SubmitState {
    object Idle : SubmitState()
    object Submitting : SubmitState()
    data class Done(val edited: Boolean) : SubmitState()
    data class Error(val message: String) : SubmitState()
}

class CreatePostViewModel : ViewModel() {
    private val repo = PostRepository()
    /** Stable for the life of the form: a retry after a timeout re-uses it, so no duplicate post is possible. */
    private val newId = repo.newPostId()

    private val _state = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val state: StateFlow<SubmitState> = _state

    private val _editPost = MutableStateFlow<LecturePost?>(null)
    val editPost: StateFlow<LecturePost?> = _editPost
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    fun loadForEdit(id: String) {
        if (_editPost.value != null) return
        viewModelScope.launch {
            try {
                _editPost.value = repo.getPost(id) ?: run { _loadError.value = "Post not found"; null }
            } catch (e: Exception) { _loadError.value = ErrorMapper.message(e) }
        }
    }

    fun submit(
        editId: String?, type: String, subject: String, className: String, room: String,
        reason: String, teamsLink: String, startAt: Timestamp, endAt: Timestamp
    ) {
        if (_state.value is SubmitState.Submitting) return          // duplicate-submission guard
        val me = Session.me ?: return
        _state.value = SubmitState.Submitting
        viewModelScope.launch {
            try {
                val details = mapOf(
                    "type" to type, "subject" to subject.trim(), "className" to className.trim(),
                    "room" to room.trim(), "reason" to reason.trim(), "teamsLink" to teamsLink.trim(),
                    "startAt" to startAt, "endAt" to endAt
                )
                if (editId != null) {
                    repo.updateDetails(editId, details)
                } else {
                    repo.createPost(newId, details + mapOf(
                        "posterId" to me.uid,
                        "posterName" to me.fullName,
                        "posterDepartment" to me.department,
                        "posterPhotoUrl" to me.photoUrl,
                        "status" to PostStatus.AVAILABLE.value,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
                _state.value = SubmitState.Done(editId != null)
            } catch (e: Exception) {
                val msg = ErrorMapper.message(e)
                _state.value = SubmitState.Error(
                    if (e is kotlinx.coroutines.TimeoutCancellationException)
                        "Could not confirm with the server. If you are offline it will publish when you reconnect; check Home before trying again."
                    else msg
                )
            }
        }
    }
}
