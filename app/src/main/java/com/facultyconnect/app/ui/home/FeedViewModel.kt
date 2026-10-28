package com.facultyconnect.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.FeedQuery
import com.facultyconnect.app.data.PostRepository
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.model.PostStatus
import com.facultyconnect.app.util.TimeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Filters applied on the device after loading (Firestore cannot do substring search). */
data class ClientFilter(
    val query: String = "",
    val department: String = "",
    val subject: String = "",
    val date: LocalDate? = null
)

data class FeedUi(
    val loading: Boolean = true,
    val error: String? = null,
    val posts: List<LecturePost> = emptyList(),
    val fromCache: Boolean = false,
    val canLoadMore: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel : ViewModel() {
    private val repo = PostRepository()
    private val pageSize = 20L

    // Server-side filters: status (default = Available first), type, page size, manual refresh counter.
    private val serverQuery = MutableStateFlow(FeedQuery(PostStatus.AVAILABLE.value, null, pageSize))
    private val clientFilter = MutableStateFlow(ClientFilter())

    val state: StateFlow<FeedUi> = combine(
        serverQuery.flatMapLatest { repo.observePosts(it) }, clientFilter
    ) { res, cf ->
        when (res) {
            is Resource.Loading -> FeedUi(loading = true)
            is Resource.Error -> FeedUi(loading = false, error = res.message)
            is Resource.Success -> FeedUi(
                loading = false,
                posts = applyFilter(res.data.posts, cf),
                fromCache = res.fromCache,
                canLoadMore = res.data.posts.size >= res.data.requestedLimit
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedUi())

    val currentStatus: String? get() = serverQuery.value.status
    val currentType: String? get() = serverQuery.value.type
    val filter: ClientFilter get() = clientFilter.value

    fun setStatus(status: String?) = serverQuery.update { it.copy(status = status, limit = pageSize) }
    fun setType(type: String?) = serverQuery.update { it.copy(type = type, limit = pageSize) }
    fun setSearch(q: String) = clientFilter.update { it.copy(query = q) }
    fun setClientFilters(dept: String, subject: String, date: LocalDate?) =
        clientFilter.update { it.copy(department = dept, subject = subject, date = date) }

    fun loadMore() {
        val s = state.value
        if (!s.loading && s.canLoadMore) serverQuery.update { it.copy(limit = it.limit + pageSize) }
    }

    fun refresh() = serverQuery.update { it.copy(refresh = it.refresh + 1) }

    private fun applyFilter(posts: List<LecturePost>, f: ClientFilter): List<LecturePost> {
        val q = f.query.trim().lowercase()
        val dept = f.department.trim().lowercase()
        val subj = f.subject.trim().lowercase()
        return posts.filter { p ->
            (q.isEmpty() || p.posterName.lowercase().contains(q) || p.subject.lowercase().contains(q) ||
                p.className.lowercase().contains(q)) &&
                (dept.isEmpty() || p.posterDepartment.lowercase().contains(dept)) &&
                (subj.isEmpty() || p.subject.lowercase().contains(subj)) &&
                (f.date == null || TimeFormat.localDate(p.startAt) == f.date)
        }
    }
}
