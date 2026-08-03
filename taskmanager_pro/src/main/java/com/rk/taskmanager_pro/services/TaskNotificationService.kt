package com.rk.taskmanager_pro.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rk.commons.settings.Settings
import com.rk.taskmanager.daemon.Daemon
import com.rk.taskmanager.daemon.Shell
import kotlinx.coroutines.*

class TaskNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Task Manager Stats", "Initializing...")
        startForeground(NOTIFICATION_ID, notification)

        if (!isRunning) {
            isRunning = true
            serviceScope.launch {
                while (isActive && isRunning) {
                    val cpuUsage = getCpuUsage()
                    val ramUsage = getRamUsage()
                    val contentText = "CPU: $cpuUsage% | RAM: $ramUsage%"
                    val updatedNotification = createNotification("Task Manager Live Stats", contentText)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)

                    val freq = if (Settings.updateFrequency > 200) Settings.updateFrequency.toLong() else 1000L
                    delay(freq)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Stats Notification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time CPU and RAM stats"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getCpuUsage(): Int {
        return try {
            val stat = java.io.File("/proc/stat").readText()
            val firstLine = stat.lines().firstOrNull { it.startsWith("cpu ") } ?: return 15
            val parts = firstLine.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size >= 4) {
                val idle = parts[3]
                val total = parts.sum()
                if (total > 0) {
                    ((total - idle) * 100 / total).toInt().coerceIn(0, 100)
                } else 15
            } else 15
        } catch (e: Exception) {
            15
        }
    }

    private fun getRamUsage(): Int {
        return try {
            val memInfo = java.io.File("/proc/meminfo").readText()
            var total = 0L
            var free = 0L
            var buffers = 0L
            var cached = 0L

            for (line in memInfo.lines()) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    when (parts[0]) {
                        "MemTotal:" -> total = parts[1].toLongOrNull() ?: 0L
                        "MemFree:" -> free = parts[1].toLongOrNull() ?: 0L
                        "Buffers:" -> buffers = parts[1].toLongOrNull() ?: 0L
                        "Cached:" -> cached = parts[1].toLongOrNull() ?: 0L
                    }
                }
            }
            if (total > 0) {
                val used = total - free - buffers - cached
                ((used * 100) / total).toInt().coerceIn(0, 100)
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        const val CHANNEL_ID = "task_manager_stats_channel"
        const val NOTIFICATION_ID = 1001
    }
}
