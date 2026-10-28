package com.facultyconnect.app.ui.home

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.facultyconnect.app.R
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.databinding.DialogFilterBinding
import com.facultyconnect.app.databinding.FragmentHomeBinding
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.model.PostStatus
import com.facultyconnect.app.model.PostType
import com.facultyconnect.app.domain.ConversationIds
import com.facultyconnect.app.ui.common.StateView
import com.facultyconnect.app.util.NetworkUtils
import com.facultyconnect.app.util.TimeFormat
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.str
import com.facultyconnect.app.util.toast
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val vm: FeedViewModel by viewModels()
    private lateinit var stateView: StateView
    private lateinit var adapter: PostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentHomeBinding.bind(view)
        stateView = StateView(b.stateView)
        val myUid = Session.me?.uid.orEmpty()

        adapter = PostAdapter(
            myUid = myUid,
            onRequest = { openPostChat(it, requestToTake = true) },
            onMessage = { openPostChat(it, requestToTake = false) },
            onManage = { openDetail(it) },
            onTeams = { post ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.teamsLink))) }
                catch (_: ActivityNotFoundException) { toast("No app can open this Teams link.") }
            },
            onOpen = { openDetail(it) }
        )
        b.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        b.rvPosts.adapter = adapter
        b.rvPosts.isNestedScrollingEnabled = false
        b.scrollContent.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { scrollView, _, scrollY, _, _ ->
            val content = scrollView.getChildAt(0)
            if (content != null && scrollY + scrollView.height >= content.height - 240) vm.loadMore()
        })

        b.swipe.setOnRefreshListener { vm.refresh() }
        b.fabNew.setOnClickListener { findNavController().navigate(R.id.createPostFragment) }
        b.btnFilter.setOnClickListener { showFilterDialog() }
        b.etSearch.setText(vm.filter.query)
        b.etSearch.doAfterTextChanged { vm.setSearch(it?.toString().orEmpty()) }

        b.chipStatus.check(when (vm.currentStatus) {
            PostStatus.AVAILABLE.value -> R.id.chipAvailable
            PostStatus.ASSIGNED.value -> R.id.chipAssigned
            PostStatus.CLOSED.value -> R.id.chipClosed
            else -> R.id.chipAll
        })
        b.chipStatus.setOnCheckedStateChangeListener { _, ids ->
            vm.setStatus(when (ids.firstOrNull()) {
                R.id.chipAvailable -> PostStatus.AVAILABLE.value
                R.id.chipAssigned -> PostStatus.ASSIGNED.value
                R.id.chipClosed -> PostStatus.CLOSED.value
                else -> null
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun render(s: FeedUi) {
        if (!s.loading) b.swipe.isRefreshing = false
        b.tvOffline.show(s.fromCache && !NetworkUtils.isOnline(requireContext()))
        adapter.submitList(s.posts)
        when {
            s.loading && s.posts.isEmpty() -> stateView.loading()
            s.error != null -> stateView.error(s.error) { vm.refresh() }
            s.posts.isEmpty() -> stateView.empty(
                "No lecture requests found",
                "Try another filter, or tap New request to post one."
            )
            else -> stateView.hide()
        }
    }

    private fun openDetail(p: LecturePost) =
        findNavController().navigate(R.id.postDetailFragment, bundleOf("postId" to p.id))

    private fun openPostChat(p: LecturePost, requestToTake: Boolean) {
        val myUid = Session.me?.uid ?: return
        val convId = ConversationIds.forPost(myUid, p.posterId, p.id)
        findNavController().navigate(R.id.chatFragment, bundleOf(
            "convId" to convId,
            "otherUid" to p.posterId,
            "postId" to p.id,
            "requestToTake" to requestToTake
        ))
    }

    private fun showFilterDialog() {
        val d = DialogFilterBinding.inflate(LayoutInflater.from(requireContext()))
        var date: LocalDate? = vm.filter.date
        fun showDate() { d.tvFilterDate.text = date?.let { TimeFormat.date(com.google.firebase.Timestamp(
            java.util.Date.from(it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))) } ?: "Any date" }
        showDate()
        d.chipTypeGroup.check(when (vm.currentType) {
            PostType.REPLACEMENT.value -> R.id.chipTypeReplacement
            PostType.EXTRA.value -> R.id.chipTypeExtra
            else -> R.id.chipTypeAny
        })
        d.etFilterDept.setText(vm.filter.department)
        d.etFilterSubject.setText(vm.filter.subject)
        d.btnPickDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker().setTitleText("Lecture date").build()
            picker.addOnPositiveButtonClickListener { sel ->
                date = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate(); showDate()
            }
            picker.show(childFragmentManager, "datePicker")
        }
        d.btnClearDate.setOnClickListener { date = null; showDate() }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filters")
            .setView(d.root)
            .setNegativeButton("Reset") { _, _ ->
                vm.setType(null); vm.setClientFilters("", "", null)
            }
            .setPositiveButton("Apply") { _, _ ->
                vm.setType(when (d.chipTypeGroup.checkedChipId) {
                    R.id.chipTypeReplacement -> PostType.REPLACEMENT.value
                    R.id.chipTypeExtra -> PostType.EXTRA.value
                    else -> null
                })
                vm.setClientFilters(d.etFilterDept.str(), d.etFilterSubject.str(), date)
            }
            .show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
