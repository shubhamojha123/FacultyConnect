package com.facultyconnect.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.facultyconnect.app.MainActivity
import com.facultyconnect.app.data.AuthRepository
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.Fb
import com.facultyconnect.app.data.Gate
import com.facultyconnect.app.databinding.ActivityPendingBinding
import com.facultyconnect.app.util.show
import com.facultyconnect.app.util.toast
import kotlinx.coroutines.launch

/** Shown when the email is not yet verified, or the account is waiting for administrator approval. */
class PendingActivity : AppCompatActivity() {
    private lateinit var b: ActivityPendingBinding
    private val auth by lazy { AuthRepository() }
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPendingBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnContinue.setOnClickListener { refresh() }
        b.btnResend.setOnClickListener {
            lifecycleScope.launch {
                try { auth.sendVerificationEmail(); toast("Verification email sent. Check your inbox and spam folder.") }
                catch (e: Exception) { toast(ErrorMapper.message(e)) }
            }
        }
        b.btnSignOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) refresh()
    }

    private fun loading(v: Boolean) {
        b.progress.visibility = if (v) View.VISIBLE else View.INVISIBLE
        b.btnContinue.isEnabled = !v
    }

    private fun refresh() {
        if (checking) return
        checking = true
        loading(true)
        lifecycleScope.launch {
            try {
                val gate = auth.resolveGate()
                when (gate) {
                    Gate.READY -> {
                        startActivity(Intent(this@PendingActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                    Gate.LOGIN -> {
                        startActivity(Intent(this@PendingActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                    Gate.EMAIL_UNVERIFIED -> {
                        b.tvTitle.text = "Verify your email"
                        b.tvMessage.text = "We sent a link to ${Fb.auth.currentUser?.email}. Open it, then tap \"Check again\"."
                        b.btnResend.show(true)
                    }
                    Gate.PENDING_APPROVAL -> {
                        b.tvTitle.text = "Awaiting approval"
                        b.tvMessage.text = "Your email is confirmed. An administrator must approve your faculty account before you can use the app. Tap \"Check again\" after they approve it."
                        b.btnResend.show(false)
                    }
                }
            } catch (e: Exception) {
                b.tvTitle.text = "Could not check status"
                b.tvMessage.text = ErrorMapper.message(e)
            } finally {
                checking = false
                loading(false)
            }
        }
    }
}
