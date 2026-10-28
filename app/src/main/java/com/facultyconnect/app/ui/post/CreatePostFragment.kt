package com.facultyconnect.app.ui.post

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.facultyconnect.app.R
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.databinding.FragmentCreatePostBinding
import com.facultyconnect.app.domain.AssignmentRules
import com.facultyconnect.app.domain.LectureField
import com.facultyconnect.app.domain.LectureInput
import com.facultyconnect.app.domain.LectureValidator
import com.facultyconnect.app.model.PostType
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.str
import com.facultyconnect.app.util.toast
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class CreatePostFragment : Fragment(R.layout.fragment_create_post) {
    private var _b: FragmentCreatePostBinding? = null
    private val b get() = _b!!
    private val vm: CreatePostViewModel by viewModels()

    private var date: LocalDate? = null
    private var start: LocalTime? = null
    private var end: LocalTime? = null
    private var populated = false
    private val editId: String? get() = arguments?.getString("postId")

    private val dateFmt get() = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.getDefault())
    private val timeFmt get() = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentCreatePostBinding.bind(view)
        (activity as? com.facultyconnect.app.MainActivity)?.setToolbarTitle(if (editId != null) "Edit request" else "New lecture request")
        b.btnSubmit.text = if (editId != null) "Save changes" else "Publish"

        b.etDate.setOnClickListener { pickDate() }
        b.etStart.setOnClickListener { pickTime(true) }
        b.etEnd.setOnClickListener { pickTime(false) }
        b.btnCancel.setOnClickListener { findNavController().popBackStack() }
        b.btnSubmit.setOnClickListener { submit() }

        editId?.let { vm.loadForEdit(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.editPost.collect { p -> if (p != null && !populated) populate(p) } }
                launch { vm.loadError.collect { e -> if (e != null) { toast(e); findNavController().popBackStack() } } }
                launch { vm.state.collect { render(it) } }
            }
        }
    }

    private fun populate(p: com.facultyconnect.app.model.LecturePost) {
        val me = Session.me
        val check = AssignmentRules.canEdit(p.postStatus, p.posterId, me?.uid.orEmpty())
        if (!check.allowed) { toast(check.reason ?: "Not allowed"); findNavController().popBackStack(); return }
        populated = true
        b.toggleType.check(if (p.postType == PostType.EXTRA) R.id.btnExtra else R.id.btnReplacement)
        b.etSubject.setText(p.subject); b.etClass.setText(p.className)
        b.etRoom.setText(p.room); b.etReason.setText(p.reason); b.etTeamsLink.setText(p.teamsLink)
        p.startAt?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.let {
            date = it.toLocalDate(); start = it.toLocalTime().withSecond(0).withNano(0)
        }
        p.endAt?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.let { end = it.toLocalTime().withSecond(0).withNano(0) }
        showPicked()
    }

    private fun showPicked() {
        b.etDate.setText(date?.format(dateFmt).orEmpty())
        b.etStart.setText(start?.format(timeFmt).orEmpty())
        b.etEnd.setText(end?.format(timeFmt).orEmpty())
    }

    private fun pickDate() {
        val picker = MaterialDatePicker.Builder.datePicker().setTitleText("Lecture date").build()
        picker.addOnPositiveButtonClickListener { sel ->
            date = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
            showPicked()
        }
        picker.show(childFragmentManager, "date")
    }

    private fun pickTime(isStart: Boolean) {
        val current = (if (isStart) start else end) ?: LocalTime.of(9, 0)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(current.hour).setMinute(current.minute)
            .setTitleText(if (isStart) "Start time" else "End time")
            .build()
        picker.addOnPositiveButtonClickListener {
            val t = LocalTime.of(picker.hour, picker.minute)
            if (isStart) start = t else end = t
            showPicked()
        }
        picker.show(childFragmentManager, "time")
    }

    private fun submit() {
        val input = LectureInput(
            b.etSubject.str(), b.etClass.str(), b.etRoom.str(), b.etReason.str(), date, start, end
        )
        val result = LectureValidator.validate(input, LocalDateTime.now())
        b.tilSubject.error = result.errors[LectureField.SUBJECT]
        b.tilClass.error = result.errors[LectureField.CLASS]
        b.tilRoom.error = result.errors[LectureField.ROOM]
        b.tilReason.error = result.errors[LectureField.REASON]
        b.tilDate.error = result.errors[LectureField.DATE]
        b.tilStart.error = result.errors[LectureField.START]
        b.tilEnd.error = result.errors[LectureField.END]
        val teamsLink = b.etTeamsLink.str()
        b.tilTeamsLink.error = if (teamsLink.isNotEmpty() &&
            !(teamsLink.startsWith("https://") && Patterns.WEB_URL.matcher(teamsLink).matches()))
            "Enter a valid https:// Teams meeting link" else null
        if (!result.isValid || b.tilTeamsLink.error != null) return

        val d = date ?: return
        val st = start ?: return
        val en = end ?: return
        val zone = ZoneId.systemDefault()
        val startAt = Timestamp(Date.from(LocalDateTime.of(d, st).atZone(zone).toInstant()))
        val endAt = Timestamp(Date.from(LocalDateTime.of(d, en).atZone(zone).toInstant()))
        val type = if (b.toggleType.checkedButtonId == R.id.btnExtra) PostType.EXTRA.value else PostType.REPLACEMENT.value
        vm.submit(editId, type, input.subject, input.className, input.room, input.reason, teamsLink, startAt, endAt)
    }

    private fun render(s: SubmitState) {
        val busy = s is SubmitState.Submitting
        b.progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        b.btnSubmit.isEnabled = !busy
        b.btnCancel.isEnabled = !busy
        b.tvError.show(s is SubmitState.Error)
        when (s) {
            is SubmitState.Error -> b.tvError.text = s.message
            is SubmitState.Done -> {
                toast(if (s.edited) "Post updated" else "Request published")
                findNavController().popBackStack()
            }
            else -> Unit
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
