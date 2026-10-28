package com.facultyconnect.app.ui.schedule

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
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.databinding.FragmentScheduleBinding
import com.facultyconnect.app.ui.common.StateView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {
    private var _b: FragmentScheduleBinding? = null
    private val b get() = _b!!
    private val vm: ScheduleViewModel by viewModels()
    private lateinit var stateView: StateView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentScheduleBinding.bind(view)
        stateView = StateView(b.stateView)
        val adapter = ScheduleAdapter(Session.me?.uid.orEmpty()) { a ->
            findNavController().navigate(R.id.postDetailFragment, bundleOf("postId" to a.postId))
        }
        b.rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        b.rvSchedule.adapter = adapter

        b.tabs.getTabAt(if (vm.state.value.upcomingTab) 0 else 1)?.select()
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { vm.showUpcoming(tab.position == 0) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    adapter.submitList(s.items)
                    when {
                        s.loading -> stateView.loading()
                        s.error != null -> stateView.error(s.error) { vm.showUpcoming(s.upcomingTab) }
                        s.items.isEmpty() -> stateView.message(
                            R.drawable.ic_schedule,
                            if (s.upcomingTab) "No upcoming lectures" else "No past lectures",
                            "Confirmed lecture assignments appear here."
                        )
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
