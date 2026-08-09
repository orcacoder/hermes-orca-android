package com.eatmoore.hermesorca

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object OrcaNotifications {
    private const val CHANNEL = "orca_signals"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL,
                "ORCA actionable signals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ARMED, ENTER, KILLED, EXPIRED and material ORCA signal updates."
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun signal(context: Context, title: String, body: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.eatmoore.hermesorca.R.drawable.ic_stat_orca)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(4201, notification)
    }
}
