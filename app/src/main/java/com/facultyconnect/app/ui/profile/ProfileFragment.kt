package com.facultyconnect.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.facultyconnect.app.R
import com.facultyconnect.app.data.AuthRepository
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.data.UserRepository
import com.facultyconnect.app.databinding.FragmentProfileBinding
import com.facultyconnect.app.ui.auth.LoginActivity
import com.facultyconnect.app.util.Avatar
import com.facultyconnect.app.util.StatusStyle
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!
    private val users = UserRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentProfileBinding.bind(view)
        b.btnEdit.setOnClickListener { findNavController().navigate(R.id.editProfileFragment) }
        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Log out?")
                .setMessage("You will stop receiving push notifications on this device.")
                .setNegativeButton("Cancel", null).setPositiveButton("Log out") { _, _ -> logout() }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun bind() {
        val me = Session.me ?: return
        Avatar.bind(b.ivAvatar, me.fullName, me.photoUrl)
        b.tvName.text = me.fullName
        b.tvEmail.text = me.email
        b.tvDept.text = me.department
        b.tvDesignation.text = me.designation
        b.tvSubjects.text = me.subjects.joinToString(", ").ifEmpty { "-" }
        StatusStyle.bindPill(b.tvVerified, "Verified faculty", R.color.status_assigned)
        b.tvRole.show(Session.isCoordinator)
        StatusStyle.bindPill(b.tvRole, Session.role, R.color.type_extra)

        // Preference switches: set state BEFORE attaching listeners.
        b.switchMessages.setOnCheckedChangeListener(null)
        b.switchAssignments.setOnCheckedChangeListener(null)
        b.switchMessages.isChecked = me.notifyMessages
        b.switchAssignments.isChecked = me.notifyAssignments
        b.switchMessages.setOnCheckedChangeListener { _, on -> savePref("notifyMessages", on) }
        b.switchAssignments.setOnCheckedChangeListener { _, on -> savePref("notifyAssignments", on) }
    }

    private fun savePref(key: String, value: Boolean) {
        val me = Session.me ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                users.updateProfile(me.uid, mapOf(key to value))
                Session.me = if (key == "notifyMessages") me.copy(notifyMessages = value) else me.copy(notifyAssignments = value)
            } catch (e: Exception) {
                toast(ErrorMapper.message(e)); bind()
            }
        }
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Stop pushes to this device for this account before signing out.
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                users.removeDeviceToken(token)
                FirebaseMessaging.getInstance().deleteToken().await()
            }
            AuthRepository().signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
