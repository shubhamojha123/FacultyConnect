package com.facultyconnect.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.AssignmentRepository
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.model.Assignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ScheduleUi(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<Assignment> = emptyList(),
    val upcomingTab: Boolean = true
)

class ScheduleViewModel : ViewModel() {
    private val uid = Session.me?.uid.orEmpty()
    private val upcoming = MutableStateFlow(true)

    val state: StateFlow<ScheduleUi> = combine(AssignmentRepository().observeSchedule(uid), upcoming) { res, up ->
        when (res) {
            is Resource.Loading -> ScheduleUi(loading = true, upcomingTab = up)
            is Resource.Error -> ScheduleUi(loading = false, error = res.message, upcomingTab = up)
            is Resource.Success -> {
                val now = System.currentTimeMillis()
                // Confirmed only; one document per post id, so no duplicates are possible.
                val confirmed = res.data.filter { it.status == "confirmed" }
                val list = if (up) confirmed.filter { (it.endAt?.toDate()?.time ?: 0L) >= now }
                    .sortedBy { it.startAt?.toDate()?.time ?: 0L }
                else confirmed.filter { (it.endAt?.toDate()?.time ?: 0L) < now }
                    .sortedByDescending { it.startAt?.toDate()?.time ?: 0L }
                ScheduleUi(loading = false, items = list, upcomingTab = up)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUi())

    fun showUpcoming(v: Boolean) { upcoming.value = v }
}
