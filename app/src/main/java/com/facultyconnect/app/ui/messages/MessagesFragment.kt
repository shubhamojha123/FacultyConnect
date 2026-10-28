package com.facultyconnect.app.ui.messages

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.facultyconnect.app.R
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.databinding.FragmentMessagesBinding
import com.facultyconnect.app.ui.common.StateView
import com.facultyconnect.app.util.NetworkUtils
import com.facultyconnect.app.util.show
import kotlinx.coroutines.launch

class MessagesFragment : Fragment(R.layout.fragment_messages) {
    private var _b: FragmentMessagesBinding? = null
    private val b get() = _b!!
    private val vm: MessagesViewModel by viewModels()
    private lateinit var stateView: StateView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentMessagesBinding.bind(view)
        stateView = StateView(b.stateView)
        val myUid = Session.me?.uid.orEmpty()

        val adapter = ConversationAdapter(myUid) { c ->
            findNavController().navigate(R.id.chatFragment, bundleOf("convId" to c.id, "otherUid" to c.otherUid(myUid)))
        }
        b.rvConversations.layoutManager = LinearLayoutManager(requireContext())
        b.rvConversations.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        b.rvConversations.adapter = adapter
        b.fabNewChat.setOnClickListener { findNavController().navigate(R.id.directoryFragment) }
        b.etSearch.doAfterTextChanged { vm.setQuery(it?.toString().orEmpty()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    b.tvOffline.show(s.fromCache && !NetworkUtils.isOnline(requireContext()))
                    adapter.submitList(s.items)
                    when {
                        s.loading -> stateView.loading()
                        s.error != null -> stateView.error(s.error) { vm.retry() }
                        s.items.isEmpty() -> stateView.empty("No conversations yet", "Tap New message to find a colleague, or use Contact Faculty on a lecture post.")
                        else -> stateView.hide()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
