package com.facultyconnect.app.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.facultyconnect.app.data.AuthRepository
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.Fb
import com.facultyconnect.app.data.UserRepository
import com.facultyconnect.app.databinding.ActivityRegisterBinding
import com.facultyconnect.app.util.Avatar
import com.facultyconnect.app.util.ImageUtils
import com.facultyconnect.app.util.normalizedEmail
import com.facultyconnect.app.util.parseSubjects
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.str
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

val DESIGNATIONS = listOf(
    "Assistant Professor", "Associate Professor", "Professor",
    "Faculty Member", "Department Coordinator", "Head of Department"
)

class RegisterActivity : AppCompatActivity() {
    private lateinit var b: ActivityRegisterBinding
    private val auth by lazy { AuthRepository() }
    private val users by lazy { UserRepository() }
    private var photoUri: Uri? = null
    private var busy = false
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoUri = uri
            b.ivAvatar.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)
        Avatar.bind(b.ivAvatar, "?", null)
        b.actDesignation.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, DESIGNATIONS))
        b.tvDomainHint.text = "Verify your email after registering. An administrator must approve your account before you can sign in."
        b.ivAvatar.setOnClickListener { pickImage.launch("image/*") }
        b.tvAddPhoto.setOnClickListener { pickImage.launch("image/*") }
        b.btnRegister.setOnClickListener { register() }
        b.btnLogin.setOnClickListener { finish() }
    }

    private fun setBusy(v: Boolean) {
        busy = v
        b.progress.visibility = if (v) View.VISIBLE else View.INVISIBLE
        b.btnRegister.isEnabled = !v
    }

    private fun register() {
        if (busy) return
        b.tvError.show(false)
        val name = b.etName.str()
        val email = b.etEmail.str().normalizedEmail()
        val pw = b.etPassword.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()
        val dept = b.etDepartment.str()
        val designation = b.actDesignation.str()
        val subjects = parseSubjects(b.etSubjects.str())

        var ok = true
        fun err(til: com.google.android.material.textfield.TextInputLayout, msg: String?) {
            til.error = msg; if (msg != null) ok = false
        }
        err(b.tilName, if (name.length < 2) "Enter your full name" else null)
        err(b.tilEmail, when {
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address"
            else -> null
        })
        err(b.tilPassword, if (pw.length < 8) "Minimum 8 characters" else null)
        err(b.tilConfirm, if (confirm != pw) "Passwords do not match" else null)
        err(b.tilDepartment, if (dept.isEmpty()) "Enter your department" else null)
        err(b.tilDesignation, if (designation.isEmpty()) "Choose a designation" else null)
        err(b.tilSubjects, if (subjects.isEmpty()) "Enter at least one subject" else null)
        if (!ok) return

        setBusy(true)
        lifecycleScope.launch {
            var created = false
            try {
                // Compress/validate the photo first so a bad file doesn't leave a half-made account.
                val photoBytes = photoUri?.let { ImageUtils.compress(this@RegisterActivity, it) }
                Fb.auth.createUserWithEmailAndPassword(email, pw).await()
                created = true
                val uid = Fb.auth.currentUser!!.uid
                val photoUrl = if (photoBytes != null) {
                    runCatching { users.uploadAvatar(uid, photoBytes) }.getOrDefault("")
                } else ""
                users.createProfile(uid, email, name, dept, designation, subjects, photoUrl)
                runCatching { auth.sendVerificationEmail() }
                startActivity(Intent(this@RegisterActivity, PendingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } catch (e: Exception) {
                // Roll back the auth account so the user can simply try again.
                if (created) runCatching { auth.deleteCurrentUser() }
                setBusy(false)
                b.tvError.show(true)
                b.tvError.text = if (e is IllegalArgumentException) (e.message ?: "Invalid image") else ErrorMapper.message(e)
            }
        }
    }

}
