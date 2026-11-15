package com.clipmaster.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors

/**
 * Owns the complete video-processing task. The Activity only submits URIs and
 * observes progress; destroying or minimizing the Activity cannot shut down
 * the worker that runs Whisper and FFmpeg.
 */
class ProcessingForegroundService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var processing = false

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
                executor.shutdownNow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (processing) return START_NOT_STICKY

                val inputValue = intent.getStringExtra(EXTRA_INPUT_URI)
                val outputValue = intent.getStringExtra(EXTRA_OUTPUT_URI)
                val duration = intent.getIntExtra(EXTRA_DURATION, 60)
                val captions = intent.getBooleanExtra(EXTRA_CAPTIONS, true)
                if (inputValue.isNullOrBlank() || outputValue.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                processing = true
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_IS_PROCESSING, true)
                    .putString(PREF_STATUS, "Status: Memproses di latar belakang...")
                    .putBoolean(PREF_SUCCESS, false)
                    .apply()

                val initial = buildNotification("Menyiapkan video dan model Whisper")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        initial,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initial)
                }

                sendProgress(status = "Status: Memproses di latar belakang...", success = false)
                executor.execute {
                    try {
                        val processor = ClipProcessor(
                            applicationContext,
                            duration,
                            captions,
                            { message -> publishLog(message) },
                            { status, success -> publishStatus(status, success) }
                        )
                        val success = processor.process(Uri.parse(inputValue), Uri.parse(outputValue))
                        updateNotification(if (success) "Klip berhasil disimpan" else "Proses gagal")
                    } catch (error: Throwable) {
                        publishLog("Layanan berhenti karena kesalahan: ${error.message}")
                        publishStatus("Status: GAGAL", false)
                    } finally {
                        processing = false
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putBoolean(PREF_IS_PROCESSING, false)
                            .apply()
                        sendProgress(done = true)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
                return START_NOT_STICKY
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
    }

    private fun publishLog(message: String) {
        updateNotification(message.take(100))
        sendProgress(log = message)
    }

    private fun publishStatus(status: String, success: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(PREF_STATUS, status)
            .putBoolean(PREF_SUCCESS, success)
            .apply()
        updateNotification(status.removePrefix("Status: "))
        sendProgress(status = status, success = success)
    }

    private fun sendProgress(
        log: String? = null,
        status: String? = null,
        success: Boolean = false,
        done: Boolean = false
    ) {
        val update = Intent(ACTION_PROGRESS).setPackage(packageName)
        if (log != null) update.putExtra(EXTRA_LOG, log)
        if (status != null) update.putExtra(EXTRA_STATUS, status)
        update.putExtra(EXTRA_SUCCESS, success)
        update.putExtra(EXTRA_DONE, done)
        sendBroadcast(update)
    }

    private fun buildNotification(message: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ClipMaster AI sedang bekerja")
            .setContentText(message)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.clipmaster.ai.action.START_PROCESSING"
        const val ACTION_STOP = "com.clipmaster.ai.action.STOP_PROCESSING"
        const val ACTION_PROGRESS = "com.clipmaster.ai.action.PROCESSING_PROGRESS"

        const val EXTRA_INPUT_URI = "input_uri"
        const val EXTRA_OUTPUT_URI = "output_uri"
        const val EXTRA_DURATION = "duration_seconds"
        const val EXTRA_CAPTIONS = "captions_enabled"
        const val EXTRA_LOG = "log"
        const val EXTRA_STATUS = "status"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_DONE = "done"

        const val PREFS_NAME = "clipmaster_processing"
        const val PREF_IS_PROCESSING = "is_processing"
        const val PREF_STATUS = "status"
        const val PREF_SUCCESS = "success"

        private const val CHANNEL_ID = "clipmaster_processing"
        private const val NOTIFICATION_ID = 3101
    }
}
