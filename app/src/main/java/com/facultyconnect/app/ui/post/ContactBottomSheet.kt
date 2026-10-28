package com.facultyconnect.app.ui.post

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.facultyconnect.app.R
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.data.UserRepository
import com.facultyconnect.app.databinding.SheetContactBinding
import com.facultyconnect.app.domain.ConversationIds
import com.facultyconnect.app.model.FacultyUser
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.util.TimeFormat
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/** The "Contact Faculty" sheet: in-app chat (preferred), email, and an optional Teams / contact link. */
class ContactBottomSheet : BottomSheetDialogFragment() {
    private var _b: SheetContactBinding? = null
    private val b get() = _b!!
    private var poster: FacultyUser? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetContactBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val postId = args.getString("postId").orEmpty()
        val posterId = args.getString("posterId").orEmpty()
        val posterName = args.getString("posterName").orEmpty()
        val subject = args.getString("subject").orEmpty()
        val typeLabel = args.getString("typeLabel").orEmpty()
        val dateLabel = args.getString("dateLabel").orEmpty()
        val postTeamsLink = args.getString("teamsLink").orEmpty()

        b.tvSheetTitle.text = "Contact $posterName"
        b.tvSheetSub.text = "$subject  |  $dateLabel"
        b.btnEmail.isEnabled = false
        b.btnTeams.isEnabled = false

        b.btnChat.setOnClickListener {
            val me = Session.me ?: return@setOnClickListener
            val convId = ConversationIds.forPost(me.uid, posterId, postId)
            val nav = requireActivity().findNavController(R.id.nav_host)
            dismiss()
            nav.navigate(R.id.chatFragment, bundleOf("convId" to convId, "otherUid" to posterId, "postId" to postId))
        }

        b.sheetProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                poster = UserRepository().getUser(posterId)
                val p = poster
                val email = p?.email.orEmpty()
                b.btnEmail.isEnabled = Patterns.EMAIL_ADDRESS.matcher(email).matches()
                val link = postTeamsLink.takeIf(::isValidLink) ?: p?.teamsLink.orEmpty()
                b.btnTeams.isEnabled = isValidLink(link)
                b.tvSheetNote.text = when {
                    p == null -> "Could not load contact details. In-app chat is still available."
                    !b.btnTeams.isEnabled -> "This faculty member has not added a Teams / contact link."
                    else -> ""
                }
                b.btnEmail.setOnClickListener { sendEmail(email, "FacultyConnect: $typeLabel - $subject ($dateLabel)") }
                b.btnTeams.setOnClickListener { openLink(link) }
            } catch (e: Exception) {
                b.tvSheetNote.text = ErrorMapper.message(e)
            } finally {
                _b?.sheetProgress?.visibility = View.INVISIBLE
            }
        }
    }

    private fun isValidLink(link: String) =
        link.startsWith("https://") && Patterns.WEB_URL.matcher(link).matches()

    private fun sendEmail(address: String, subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        try { startActivity(intent) } catch (e: ActivityNotFoundException) {
            requireContext().toast("No email app is installed on this device.")
        }
    }

    private fun openLink(link: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } catch (e: ActivityNotFoundException) {
            requireContext().toast("No app can open this link.")
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(p: LecturePost) = ContactBottomSheet().apply {
            arguments = bundleOf(
                "postId" to p.id, "posterId" to p.posterId, "posterName" to p.posterName,
                "subject" to p.subject, "teamsLink" to p.teamsLink, "typeLabel" to p.postType.label,
                "dateLabel" to TimeFormat.date(p.startAt)
            )
        }
    }
}
