package com.facultyconnect.app.ui.post

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.facultyconnect.app.R
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.databinding.FragmentPostDetailBinding
import com.facultyconnect.app.domain.AssignmentRules
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.ui.common.StateView
import com.facultyconnect.app.util.Avatar
import com.facultyconnect.app.util.StatusStyle
import com.facultyconnect.app.util.TimeFormat
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class PostDetailFragment : Fragment(R.layout.fragment_post_detail) {
    private var _b: FragmentPostDetailBinding? = null
    private val b get() = _b!!
    private val vm: PostDetailViewModel by viewModels()
    private lateinit var stateView: StateView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentPostDetailBinding.bind(view)
        stateView = StateView(b.stateView)
        val id = arguments?.getString("postId")
        if (id == null) { findNavController().popBackStack(); return }
        vm.load(id)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.post.collect { render(it) } }
                launch { vm.busy.collect { busy ->
                    b.progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
                    b.btnClose.isEnabled = !busy; b.btnCancelAssign.isEnabled = !busy; b.btnEdit.isEnabled = !busy
                } }
                launch { vm.events.collect { toast(it) } }
            }
        }
    }

    private fun render(r: Resource<LecturePost?>) {
        when (r) {
            is Resource.Loading -> { b.detailScroll.show(false); stateView.loading() }
            is Resource.Error -> { b.detailScroll.show(false); stateView.error(r.message) { vm.load(arguments?.getString("postId").orEmpty()) } }
            is Resource.Success -> {
                val p = r.data
                if (p == null) {
                    b.detailScroll.show(false)
                    stateView.empty("Post not found", "It may have been removed.")
                } else { stateView.hide(); b.detailScroll.show(true); bind(p) }
            }
        }
    }

    private fun bind(p: LecturePost) {
        val me = Session.me ?: return
        Avatar.bind(b.ivAvatar, p.posterName, p.posterPhotoUrl)
        b.tvName.text = p.posterName
        b.tvDept.text = p.posterDepartment
        StatusStyle.bindStatus(b.tvStatus, p.postStatus)
        StatusStyle.bindType(b.tvType, p.postType)
        b.tvSubject.text = p.subject
        b.tvClass.text = "Class: ${p.className}"
        b.tvDate.text = "Date: ${TimeFormat.date(p.startAt)}"
        b.tvTime.text = "Time: ${TimeFormat.range(p.startAt, p.endAt)}"
        b.tvRoom.text = "Room: ${p.room}"
        b.tvReason.text = p.reason
        b.tvPosted.text = "Posted ${TimeFormat.relative(p.createdAt)}"
        b.tvAssigned.show(p.assignedToName != null)
        b.tvAssigned.text = "Assigned to ${p.assignedToName}"

        val mine = p.posterId == me.uid
        b.btnContact.show(!mine)
        b.btnContact.setOnClickListener { ContactBottomSheet.newInstance(p).show(childFragmentManager, "contact") }
        val hasPostTeamsLink = p.teamsLink.startsWith("https://") && Patterns.WEB_URL.matcher(p.teamsLink).matches()
        b.btnTeams.show(hasPostTeamsLink)
        b.btnTeams.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.teamsLink))) }
            catch (_: ActivityNotFoundException) { toast("No app can open this Teams link.") }
        }

        b.btnEdit.show(AssignmentRules.canEdit(p.postStatus, p.posterId, me.uid).allowed)
        b.btnEdit.setOnClickListener { findNavController().navigate(R.id.createPostFragment, bundleOf("postId" to p.id)) }

        b.btnCancelAssign.show(AssignmentRules.canCancelAssignment(p.postStatus, p.posterId, me.uid).allowed)
        b.btnCancelAssign.setOnClickListener {
            confirm("Cancel assignment?", "${p.assignedToName} will be notified and removed from the schedule. The post becomes available again.") { vm.cancelAssignment(p) }
        }

        b.btnClose.show(AssignmentRules.canClose(p.postStatus, p.posterId, me.uid, Session.isCoordinator).allowed)
        b.btnClose.setOnClickListener {
            confirm("Close this post?", "Closed posts cannot be claimed or reopened.") { vm.close(p) }
        }
    }

    private fun confirm(title: String, msg: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(msg)
            .setNegativeButton("Back", null).setPositiveButton("Confirm") { _, _ -> onYes() }.show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
