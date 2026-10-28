package com.facultyconnect.app.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.facultyconnect.app.MainActivity
import com.facultyconnect.app.R
import com.facultyconnect.app.util.DeepLink

object NotificationHelper {
    const val CH_MESSAGES = "messages"
    const val CH_ASSIGNMENTS = "assignments"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New chat messages"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ASSIGNMENTS, "Assignments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Lecture assignment updates"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    /**
     * Shows a push built from an FCM *data* message. Message text is never sent through FCM; the lock-screen
     * (public) version is generic, and the full title is shown only when the device is unlocked.
     */
    @SuppressLint("MissingPermission")
    fun show(ctx: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val isMessage = data["type"] == "message"
        val channel = if (isMessage) CH_MESSAGES else CH_ASSIGNMENTS
        val title = data["title"] ?: "FacultyConnect"
        val body = if (isMessage) "Open FacultyConnect to read it." else data["body"].orEmpty()
        val refId = data["refId"].orEmpty()

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DeepLink.EXTRA_REF_TYPE, data["refType"])
            putExtra(DeepLink.EXTRA_REF_ID, refId)
            putExtra(DeepLink.EXTRA_OTHER_UID, data["fromUid"])
        }
        val pi = PendingIntent.getActivity(ctx, refId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val publicVersion = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("FacultyConnect")
            .setContentText("You have new activity")
            .build()

        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(refId.ifEmpty { title }.hashCode(), n)
    }
}
