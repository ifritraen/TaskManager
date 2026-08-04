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
import kotlinx.coroutines.*
import java.io.File

class TaskNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Task Manager Stats", "Initializing top processes...", emptyList())
        startForeground(NOTIFICATION_ID, notification)

        if (!isRunning) {
            isRunning = true
            serviceScope.launch {
                while (isActive && isRunning) {
                    val cpuUsage = getCpuUsage()
                    val ramUsage = getRamUsage()
                    val topProcesses = getTop5Processes()
                    val title = "System CPU: $cpuUsage% | RAM: $ramUsage%"
                    val updatedNotification = createNotification(title, "Top 5 processes", topProcesses)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)

                    delay(3000L) // 3-second update interval
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
                description = "Shows real-time CPU and RAM stats with top 5 processes"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String, topProcs: List<ProcessInfoStat>): Notification {
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)

        if (topProcs.isNotEmpty()) {
            topProcs.forEach { proc ->
                style.addLine("${proc.name}: CPU ${proc.cpuUsage}% | RAM ${proc.ramMb} MB")
            }
        } else {
            style.addLine(text)
        }

        val contentText = if (topProcs.isNotEmpty()) {
            topProcs.take(2).joinToString(" | ") { "${it.name}: ${it.cpuUsage}%" }
        } else text

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(style)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private data class ProcessInfoStat(
        val pid: Int,
        val name: String,
        val cpuUsage: Int,
        val ramMb: Long
    )

    private fun getTop5Processes(): List<ProcessInfoStat> {
        val procList = mutableListOf<ProcessInfoStat>()
        val totalRamMb = getTotalRamMb()

        val procDir = File("/proc")
        val pidDirs = procDir.listFiles { file -> file.isDirectory && file.name.all { it.isDigit() } } ?: return emptyList()

        for (dir in pidDirs) {
            try {
                val pid = dir.name.toIntOrNull() ?: continue
                val cmdlineFile = File(dir, "cmdline")
                if (!cmdlineFile.exists()) continue
                val rawCmd = cmdlineFile.readText().replace('\u0000', ' ').trim()
                if (rawCmd.isEmpty()) continue

                val name = rawCmd.split(" ").first().substringAfterLast('/')

                val statmFile = File(dir, "statm")
                var ramMb = 0L
                if (statmFile.exists()) {
                    val rssPages = statmFile.readText().split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 0L
                    ramMb = (rssPages * 4096) / (1024 * 1024)
                }

                val statFile = File(dir, "stat")
                var cpuUsage = 0
                if (statFile.exists()) {
                    val statParts = statFile.readText().split("\\s+".toRegex())
                    if (statParts.size >= 15) {
                        val utime = statParts[13].toLongOrNull() ?: 0L
                        val stime = statParts[14].toLongOrNull() ?: 0L
                        val totalTicks = utime + stime
                        cpuUsage = (totalTicks % 100).toInt()
                    }
                }

                procList.add(ProcessInfoStat(pid, name, cpuUsage, ramMb))
            } catch (_: Exception) {}
        }

        return procList.sortedByDescending { it.ramMb + (it.cpuUsage * 10) }.take(5)
    }

    private fun getTotalRamMb(): Long {
        return try {
            val memInfo = File("/proc/meminfo").readText()
            val totalLine = memInfo.lines().firstOrNull { it.startsWith("MemTotal:") } ?: return 4096L
            val kb = totalLine.split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() ?: 4194304L
            kb / 1024
        } catch (_: Exception) {
            4096L
        }
    }

    private fun getCpuUsage(): Int {
        return try {
            val stat = File("/proc/stat").readText()
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
            val memInfo = File("/proc/meminfo").readText()
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
