package com.facultyconnect.app.ui.notifications

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
import com.facultyconnect.app.R
import com.facultyconnect.app.databinding.FragmentNotificationsBinding
import com.facultyconnect.app.model.AppNotification
import com.facultyconnect.app.ui.common.StateView
import com.facultyconnect.app.util.toast
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment(R.layout.fragment_notifications) {
    private var _b: FragmentNotificationsBinding? = null
    private val b get() = _b!!
    private val vm: NotificationsViewModel by viewModels()
    private lateinit var stateView: StateView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentNotificationsBinding.bind(view)
        stateView = StateView(b.stateView)
        val adapter = NotificationAdapter { open(it) }
        b.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        b.rvNotifications.adapter = adapter
        b.btnMarkAll.setOnClickListener { vm.markAllRead() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.events.collect { toast(it) } }
                launch {
                    vm.state.collect { s ->
                        adapter.submitList(s.items)
                        b.btnMarkAll.isEnabled = s.items.any { !it.read }
                        when {
                            s.loading -> stateView.loading()
                            s.error != null -> stateView.error(s.error) { vm.retry() }
                            s.items.isEmpty() -> stateView.message(R.drawable.ic_notifications, "You're all caught up", "New messages and assignment updates appear here.")
                            else -> stateView.hide()
                        }
                    }
                }
            }
        }
    }

    private fun open(n: AppNotification) {
        vm.markRead(n)
        when (n.refType) {
            "conversation" -> findNavController().navigate(R.id.chatFragment, bundleOf("convId" to n.refId, "otherUid" to n.fromUid))
            "post" -> findNavController().navigate(R.id.postDetailFragment, bundleOf("postId" to n.refId))
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
