package com.facultyconnect.app.ui.directory

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.facultyconnect.app.R
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.databinding.FragmentDirectoryBinding
import com.facultyconnect.app.domain.ConversationIds
import com.facultyconnect.app.model.FacultyUser
import com.facultyconnect.app.ui.common.StateView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DirectoryFragment : Fragment(R.layout.fragment_directory) {
    private var _b: FragmentDirectoryBinding? = null
    private val b get() = _b!!
    private val vm: DirectoryViewModel by viewModels()
    private lateinit var stateView: StateView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentDirectoryBinding.bind(view)
        stateView = StateView(b.stateView)
        val myUid = Session.me?.uid.orEmpty()

        val adapter = FacultyAdapter(myUid, onMessage = { startChat(it, myUid) }, onOpen = { showProfile(it, myUid) })
        b.rvFaculty.layoutManager = LinearLayoutManager(requireContext())
        b.rvFaculty.adapter = adapter
        b.etSearch.doAfterTextChanged { vm.setQuery(it?.toString().orEmpty()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    adapter.submitList(s.shown)
                    when {
                        s.loading -> stateView.loading()
                        s.error != null -> stateView.error(s.error) { vm.load() }
                        s.shown.isEmpty() -> stateView.empty("No faculty found", "Try a different name, department or subject.")
                        else -> stateView.hide()
                    }
                }
            }
        }
    }

    private fun startChat(u: FacultyUser, myUid: String) {
        findNavController().navigate(R.id.chatFragment, bundleOf(
            "convId" to ConversationIds.direct(myUid, u.uid), "otherUid" to u.uid))
    }

    /** Only professional fields are shown; nothing private is ever stored in users/{uid}. */
    private fun showProfile(u: FacultyUser, myUid: String) {
        val msg = "${u.designation}\n${u.department}\n\nSubjects: ${u.subjects.joinToString(", ").ifEmpty { "-" }}\nEmail: ${u.email}"
        val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(u.fullName).setMessage(msg)
            .setNegativeButton("Close", null)
        if (u.uid != myUid) builder.setPositiveButton("Message") { _, _ -> startChat(u, myUid) }
        builder.show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
