package com.facultyconnect.app.push

import com.facultyconnect.app.data.Fb
import com.facultyconnect.app.data.UserRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (Fb.auth.currentUser == null) return
        scope.launch { runCatching { UserRepository().saveDeviceToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        NotificationHelper.show(applicationContext, message.data)
    }
}
