package com.facultyconnect.app.ui.profile

import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.facultyconnect.app.R
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.data.UserRepository
import com.facultyconnect.app.databinding.FragmentEditProfileBinding
import com.facultyconnect.app.ui.auth.DESIGNATIONS
import com.facultyconnect.app.util.Avatar
import com.facultyconnect.app.util.ImageUtils
import com.facultyconnect.app.util.parseSubjects
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.str
import com.facultyconnect.app.util.toast
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {
    private var _b: FragmentEditProfileBinding? = null
    private val b get() = _b!!
    private val users = UserRepository()
    private var newPhoto: Uri? = null
    private var saving = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { newPhoto = uri; b.ivAvatar.setImageURI(uri) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentEditProfileBinding.bind(view)
        val me = Session.me ?: return
        Avatar.bind(b.ivAvatar, me.fullName, me.photoUrl)
        b.etName.setText(me.fullName)
        b.etDepartment.setText(me.department)
        b.actDesignation.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, DESIGNATIONS))
        b.actDesignation.setText(me.designation, false)
        b.etSubjects.setText(me.subjects.joinToString(", "))
        b.etTeams.setText(me.teamsLink)
        b.btnChangePhoto.setOnClickListener { pickImage.launch("image/*") }
        b.ivAvatar.setOnClickListener { pickImage.launch("image/*") }
        b.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        if (saving) return
        val me = Session.me ?: return
        val name = b.etName.str(); val dept = b.etDepartment.str()
        val designation = b.actDesignation.str(); val subjects = parseSubjects(b.etSubjects.str())
        val teams = b.etTeams.str()

        var ok = true
        fun err(til: com.google.android.material.textfield.TextInputLayout, msg: String?) { til.error = msg; if (msg != null) ok = false }
        err(b.tilName, if (name.length < 2) "Enter your full name" else null)
        err(b.tilDepartment, if (dept.isEmpty()) "Enter your department" else null)
        err(b.tilDesignation, if (designation.isEmpty()) "Choose a designation" else null)
        err(b.tilSubjects, if (subjects.isEmpty()) "Enter at least one subject" else null)
        err(b.tilTeams, if (teams.isNotEmpty() && !(teams.startsWith("https://") && Patterns.WEB_URL.matcher(teams).matches()))
            "Must be a valid https:// link" else null)
        if (!ok) return

        saving = true
        b.progress.visibility = View.VISIBLE; b.btnSave.isEnabled = false; b.tvError.show(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var photoUrl = me.photoUrl
                newPhoto?.let { uri ->
                    val bytes = ImageUtils.compress(requireContext(), uri)
                    photoUrl = users.uploadAvatar(me.uid, bytes)
                }
                users.updateProfile(me.uid, mapOf(
                    "fullName" to name, "department" to dept, "designation" to designation,
                    "subjects" to subjects, "photoUrl" to photoUrl, "teamsLink" to teams
                ))
                Session.me = me.copy(fullName = name, department = dept, designation = designation,
                    subjects = subjects, photoUrl = photoUrl, teamsLink = teams)
                toast("Profile updated")
                findNavController().popBackStack()
            } catch (e: Exception) {
                b.tvError.show(true)
                b.tvError.text = if (e is IllegalArgumentException) (e.message ?: "Invalid image") else ErrorMapper.message(e)
            } finally {
                saving = false
                _b?.progress?.visibility = View.INVISIBLE; _b?.btnSave?.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
