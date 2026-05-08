package com.eldercare.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eldercare.app.ElderCareApplication
import com.eldercare.app.MainActivity
import com.eldercare.app.R
import com.eldercare.app.worker.HeartbeatWorker

class MonitorService : Service() {

    companion object {
        const val TAG = "MonitorService"
        const val NOTIFICATION_ID = 1001
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())
        // 注册 WorkManager 周期心跳任务
        HeartbeatWorker.enqueuePeriodic(this)
        Log.d(TAG, "MonitorService created, periodic heartbeat work registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 每次 Service 启动时也重新注册，确保 WorkManager 任务不会丢失
        HeartbeatWorker.enqueuePeriodic(this)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        releaseWakeLock()
        Log.d(TAG, "MonitorService destroyed")
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ElderCareApplication.CHANNEL_ID)
            .setContentTitle("安心守护")
            .setContentText("正在监控设备状态")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ElderCare::HeartbeatWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10分钟超时，避免泄漏
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
