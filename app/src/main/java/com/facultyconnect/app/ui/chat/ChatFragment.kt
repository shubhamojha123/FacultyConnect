package com.facultyconnect.app.ui.chat

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facultyconnect.app.MainActivity
import com.facultyconnect.app.R
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.databinding.FragmentChatBinding
import com.facultyconnect.app.domain.AssignmentRules
import com.facultyconnect.app.model.Conversation
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.ui.common.StateView
import com.facultyconnect.app.util.NetworkUtils
import com.facultyconnect.app.util.StatusStyle
import com.facultyconnect.app.util.TimeFormat
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ChatFragment : Fragment(R.layout.fragment_chat) {
    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!
    private val vm: ChatViewModel by viewModels()
    private lateinit var stateView: StateView
    private var adapter: MessageAdapter? = null
    private var firstList = true

    private val convId get() = arguments?.getString("convId").orEmpty()
    private val otherUid get() = arguments?.getString("otherUid").orEmpty()
    private val postId get() = arguments?.getString("postId")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentChatBinding.bind(view)
        stateView = StateView(b.stateView)
        val me = Session.me ?: return
        if (convId.isEmpty() || otherUid.isEmpty()) { toast("Conversation not found"); findNavController().popBackStack(); return }

        adapter = MessageAdapter(me.uid, otherUid) { vm.retry(it) }
        val lm = LinearLayoutManager(requireContext()).apply { reverseLayout = true }
        b.rvMessages.layoutManager = lm
        b.rvMessages.adapter = adapter
        b.rvMessages.itemAnimator = null
        b.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // reverseLayout: older messages are at the END of the list.
                if (lm.findLastVisibleItemPosition() >= (adapter?.itemCount ?: 0) - 5) vm.loadMore()
            }
        })

        b.btnSend.setOnClickListener {
            val text = b.etMessage.text?.toString().orEmpty()
            if (text.isNotBlank()) { vm.send(text); b.etMessage.setText("") }
        }
        vm.start(convId, otherUid, postId, arguments?.getBoolean("requestToTake") == true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.conversation.collect { onConversation(it) } }
                launch { vm.ui.collect { render(it) } }
                launch { vm.post.collect { bindLecture(it) } }
                launch { vm.events.collect { toast(it) } }
                launch { vm.confirming.collect { b.btnConfirm.isEnabled = !it } }
            }
        }
    }

    private fun onConversation(r: Resource<Conversation>) {
        val me = Session.me ?: return
        if (r is Resource.Success) {
            val name = r.data.participantNames[r.data.otherUid(me.uid)] ?: "Chat"
            (activity as? MainActivity)?.setToolbarTitle(name)
        }
    }

    private fun render(s: ChatUi) {
        b.tvOffline.show(!NetworkUtils.isOnline(requireContext()))
        adapter?.submitList(s.messages) {
            // Keep the newest message in view when something arrives or is sent.
            if (s.messages.isNotEmpty() && (firstList || (b.rvMessages.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() <= 1)) {
                b.rvMessages.scrollToPosition(0)
            }
            if (s.messages.isNotEmpty()) firstList = false
        }
        when {
            s.loading && s.messages.isEmpty() -> stateView.loading()
            s.error != null -> stateView.error(s.error) { vm.reload(convId, otherUid, postId) }
            s.messages.isEmpty() -> stateView.empty("No messages yet", "Say hello and coordinate the lecture.")
            else -> stateView.hide()
        }
        if (s.error == null) vm.onMessagesShown(s.messages)
    }

    private fun bindLecture(p: LecturePost?) {
        val me = Session.me ?: return
        b.cardLecture.show(p != null)
        if (p == null) return
        b.tvLectureTitle.text = p.subject
        b.tvLectureMeta.text = "${p.postType.label}  |  ${p.className}  |  ${TimeFormat.date(p.startAt)}"
        StatusStyle.bindStatus(b.tvLectureStatus, p.postStatus)
        b.btnViewPost.setOnClickListener { findNavController().navigate(R.id.postDetailFragment, bundleOf("postId" to p.id)) }

        val c = (vm.conversation.value as? Resource.Success)?.data
        val otherName = c?.participantNames?.get(c.otherUid(me.uid)) ?: "this faculty member"
        val canConfirm = AssignmentRules.canConfirm(p.postStatus, p.posterId, me.uid, otherUid).allowed
        b.btnConfirm.show(canConfirm)
        b.btnConfirm.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm assignment?")
                .setMessage("Assign \"${p.subject}\" on ${TimeFormat.date(p.startAt)} to $otherName? Both of you will see it in your schedule.")
                .setNegativeButton("Back", null)
                .setPositiveButton("Confirm") { _, _ -> vm.confirmAssignment() }
                .show()
        }
    }

    override fun onDestroyView() {
        adapter = null
        _b = null
        super.onDestroyView()
    }
}
