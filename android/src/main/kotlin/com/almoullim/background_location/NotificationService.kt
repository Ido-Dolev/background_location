package com.almoullim.background_location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.almoullim.background_location.AlarmReceiver

class NotificationHandler(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "alarm_plugin_channel"
        private const val CHANNEL_NAME = "Alarm Notification"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // We need to use [Resources.getIdentifier] because resources are registered by Flutter.
    @SuppressLint("DiscouragedApi")
    fun buildNotification(
        fullScreen: Boolean,
        pendingIntent: PendingIntent,
        alarmId: Int,
        notification_title: String,
        notification_body: String,
        stop_button_text: String
    ): Notification {
        val defaultIconResId =
            context.packageManager.getApplicationInfo(context.packageName, 0).icon
        val iconResId = defaultIconResId

//        val iconResId = if (notificationSettings.icon != null) {
//            val resId = context.resources.getIdentifier(
//                notificationSettings.icon,
//                "drawable",
//                context.packageName
//            )
//            if (resId != 0) resId else defaultIconResId
//        } else {
//            defaultIconResId
//        }

        val stopIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_STOP
            putExtra("id", alarmId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconResId)
            .setContentTitle(notification_title)
            .setContentText(notification_body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSound(null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(stopPendingIntent)

        notificationBuilder.setFullScreenIntent(pendingIntent, true)
        notificationBuilder.addAction(0, stop_button_text, stopPendingIntent)

        return notificationBuilder.build()
    }
}