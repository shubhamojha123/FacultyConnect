package com.facultyconnect.app.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.Fb
import com.facultyconnect.app.databinding.ActivitySplashBinding
import com.facultyconnect.app.util.show
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private lateinit var b: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (!Fb.isConfigured(this)) {
            // Honest failure: no fake data is ever substituted.
            b.splashProgress.show(false)
            b.splashStatus.show(true)
            b.splashStatus.text = "Firebase is not configured.\n\nAdd your google-services.json to the app/ folder " +
                "and rebuild (see README, Step 2)."
            return
        }
        b.btnRetry.setOnClickListener { route() }
        lifecycleScope.launch { delay(900); route() }
    }

    private fun route() {
        b.btnRetry.show(false); b.splashStatus.show(false); b.splashProgress.show(true)
        lifecycleScope.launch {
            try {
                AuthFlow.route(this@SplashActivity)
            } catch (e: Exception) {
                b.splashProgress.show(false)
                b.splashStatus.show(true)
                b.splashStatus.text = ErrorMapper.message(e)
                b.btnRetry.show(true)
            }
        }
    }
}
