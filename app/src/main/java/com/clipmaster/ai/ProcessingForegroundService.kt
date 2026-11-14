package com.clipmaster.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the app process in foreground priority while the existing processing
 * pipeline runs, so minimizing the Activity does not stop Whisper/FFmpeg work.
 * The ongoing notification makes the active processing state visible to users.
 */
class ProcessingForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Proses ClipMaster AI",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status proses pembuatan klip dan caption"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            else -> {
                val notification = Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("ClipMaster AI sedang bekerja")
                    .setContentText("Transkripsi dan ekspor tetap berjalan saat aplikasi diminimize")
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                return START_NOT_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.clipmaster.ai.action.START_PROCESSING"
        const val ACTION_STOP = "com.clipmaster.ai.action.STOP_PROCESSING"
        private const val CHANNEL_ID = "clipmaster_processing"
        private const val NOTIFICATION_ID = 3101
    }
}
