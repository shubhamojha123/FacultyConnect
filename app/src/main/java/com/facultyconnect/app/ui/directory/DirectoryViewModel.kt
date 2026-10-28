package com.facultyconnect.app.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.UserRepository
import com.facultyconnect.app.model.FacultyUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DirectoryUi(
    val loading: Boolean = true,
    val error: String? = null,
    val all: List<FacultyUser> = emptyList(),
    val query: String = ""
) {
    val shown: List<FacultyUser> get() {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.fullName.lowercase().contains(q) || it.department.lowercase().contains(q) ||
                it.designation.lowercase().contains(q) || it.subjects.any { s -> s.lowercase().contains(q) }
        }
    }
}

class DirectoryViewModel : ViewModel() {
    private val repo = UserRepository()
    private val _state = MutableStateFlow(DirectoryUi())
    val state: StateFlow<DirectoryUi> = _state

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val users = repo.listVerified().sortedBy { it.fullName.lowercase() }
                _state.value = _state.value.copy(loading = false, all = users)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = ErrorMapper.message(e))
            }
        }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }
}
