package com.facultyconnect.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.facultyconnect.app.data.AuthRepository
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.databinding.ActivityLoginBinding
import com.facultyconnect.app.util.normalizedEmail
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.str
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding
    private val repo by lazy { AuthRepository() }
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnLogin.setOnClickListener { login() }
        b.btnRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        b.btnForgot.setOnClickListener { sendPasswordResetLink() }
    }

    private fun validEmail(): String? {
        val email = b.etEmail.str().normalizedEmail()
        b.tilEmail.error = null
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tilEmail.error = "Enter a valid email address"; return null
        }
        return email
    }

    private fun setBusy(v: Boolean) {
        busy = v
        b.progress.visibility = if (v) android.view.View.VISIBLE else android.view.View.INVISIBLE
        b.btnLogin.isEnabled = !v; b.btnRegister.isEnabled = !v; b.btnForgot.isEnabled = !v
    }

    private fun login() {
        if (busy) return
        b.tvError.show(false)
        b.tvStatus.show(false)
        val email = validEmail() ?: return
        val password = b.etPassword.text?.toString().orEmpty()
        b.tilPassword.error = null
        if (password.isEmpty()) { b.tilPassword.error = "Enter your password"; return }

        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.signIn(email, password)
                AuthFlow.route(this@LoginActivity, repo)
            } catch (e: Exception) {
                setBusy(false)
                b.tvError.show(true); b.tvError.text = ErrorMapper.message(e)
            }
        }
    }

    private fun sendPasswordResetLink() {
        if (busy) return
        b.tvError.show(false)
        b.tvStatus.show(false)
        val email = validEmail() ?: return
        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.sendPasswordReset(email)
                b.tvStatus.text = "If an account exists for this email, Firebase has sent a password reset link. Check your inbox and spam folder."
                b.tvStatus.show(true)
            } catch (error: Exception) {
                val authError = generateSequence(error as Throwable) { it.cause }
                    .filterIsInstance<FirebaseAuthException>()
                    .firstOrNull()
                if (authError?.errorCode == "ERROR_USER_NOT_FOUND") {
                    b.tvStatus.text = "If an account exists for this email, Firebase has sent a password reset link. Check your inbox and spam folder."
                    b.tvStatus.show(true)
                } else {
                    b.tvError.text = ErrorMapper.message(error)
                    b.tvError.show(true)
                }
            } finally {
                setBusy(false)
            }
        }
    }

}
