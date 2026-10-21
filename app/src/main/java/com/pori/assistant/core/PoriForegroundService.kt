package com.pori.assistant.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pori.assistant.MainActivity
import com.pori.assistant.PoriApplication

/** Microphone foreground service: Pori keeps listening for "Hey Pori". */
class PoriForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as PoriApplication).controller
        if (intent?.action == ACTION_STOP) {
            controller.stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        controller.startWakeLoop()
        return START_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pori", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PoriForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Pori is listening")
            .setContentText("Say \"Hey Pori\" to wake her")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pori_listening"
        private const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.pori.assistant.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PoriForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PoriForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
