package com.facultyconnect.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.facultyconnect.app.data.Fb
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.data.UserRepository
import com.facultyconnect.app.databinding.ActivityMainBinding
import com.facultyconnect.app.ui.auth.SplashActivity
import com.facultyconnect.app.util.DeepLink
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After process death the in-memory Session is gone: go back through the auth gate.
        if (Fb.auth.currentUser == null || Session.me == null) {
            startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        val topLevel = setOf(R.id.homeFragment, R.id.scheduleFragment, R.id.messagesFragment, R.id.notificationsFragment, R.id.profileFragment)
        b.toolbar.setupWithNavController(nav, AppBarConfiguration(topLevel))
        b.bottomNav.setupWithNavController(nav)
        nav.addOnDestinationChangedListener { _, dest, _ ->
            b.bottomNav.visibility = if (dest.id == R.id.chatFragment) android.view.View.GONE else android.view.View.VISIBLE
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.unreadNotifications.collect { setBadge(R.id.notificationsFragment, it) } }
                launch { vm.unreadMessages.collect { setBadge(R.id.messagesFragment, it) } }
            }
        }

        requestNotificationPermission()
        registerPushToken()
        if (savedInstanceState == null) handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::b.isInitialized) handleDeepLink(intent)
    }

    fun setToolbarTitle(title: String) {
        if (::b.isInitialized) b.toolbar.title = title
    }

    private fun setBadge(menuId: Int, count: Int) {
        if (count > 0) b.bottomNav.getOrCreateBadge(menuId).apply { isVisible = true; number = count }
        else b.bottomNav.removeBadge(menuId)
    }

    private fun handleDeepLink(intent: Intent?) {
        val type = intent?.getStringExtra(DeepLink.EXTRA_REF_TYPE) ?: return
        val id = intent.getStringExtra(DeepLink.EXTRA_REF_ID) ?: return
        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        when (type) {
            "conversation" -> nav.navigate(R.id.chatFragment, bundleOf(
                "convId" to id, "otherUid" to intent.getStringExtra(DeepLink.EXTRA_OTHER_UID)))
            "post" -> nav.navigate(R.id.postDetailFragment, bundleOf("postId" to id))
        }
        intent.removeExtra(DeepLink.EXTRA_REF_TYPE)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun registerPushToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch { runCatching { UserRepository().saveDeviceToken(token) } }
        }
    }
}
