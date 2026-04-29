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
import com.eldercare.app.collector.*
import com.eldercare.app.db.AppDatabase
import com.eldercare.app.model.HeartbeatData
import com.eldercare.app.upload.UploadManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MonitorService : Service() {

    companion object {
        const val TAG = "MonitorService"
        const val NOTIFICATION_ID = 1001
        var isRunning = false
            private set

        private const val HEARTBEAT_INTERVAL = 30 * 60 * 1000L // 30分钟

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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var heartbeatSeq = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())
        startHeartbeatLoop()
        Log.d(TAG, "MonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        heartbeatJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        Log.d(TAG, "MonitorService destroyed")
    }

    private fun startHeartbeatLoop() {
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    collectAndUploadHeartbeat()
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error", e)
                }
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }

    private suspend fun collectAndUploadHeartbeat() {
        heartbeatSeq++
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        val batteryInfo = BatteryStateCollector.collect(this)
        val networkInfo = NetworkStateCollector.collect(this)
        val audioInfo = AudioStateCollector.collect(this)
        val locationInfo = LocationCollector.collect(this)
        val permissionInfo = PermissionStateCollector.collect(this)

        val heartbeat = HeartbeatData(
            deviceId = getDeviceUniqueId(),
            elderName = getPrefs().getString("elder_name", "未设置") ?: "未设置",
            appVersion = "1.0.0",
            androidVersion = Build.VERSION.SDK_INT.toString(),
            brand = Build.BRAND,
            model = Build.MODEL,
            clientReportTime = dateFormat.format(Date()),
            heartbeatSeq = heartbeatSeq,
            batteryPercent = batteryInfo.percent,
            isCharging = batteryInfo.isCharging,
            powerSaveMode = batteryInfo.powerSaveMode,
            networkStatus = networkInfo.networkStatus,
            wifiEnabled = networkInfo.wifiEnabled,
            wifiConnected = networkInfo.wifiConnected,
            wifiSsid = networkInfo.wifiSsid,
            airplaneMode = networkInfo.airplaneMode,
            ringVolume = audioInfo.ringVolume,
            ringVolumeMax = audioInfo.ringVolumeMax,
            mediaVolume = audioInfo.mediaVolume,
            mediaVolumeMax = audioInfo.mediaVolumeMax,
            ringerMode = audioInfo.ringerMode,
            zenMode = audioInfo.zenMode,
            latitude = locationInfo.latitude,
            longitude = locationInfo.longitude,
            accuracy = locationInfo.accuracy,
            locationTime = locationInfo.locationTime,
            locationProvider = locationInfo.locationProvider,
            locationPermissionGranted = permissionInfo.locationPermissionGranted,
            locationServiceEnabled = permissionInfo.locationServiceEnabled,
            notificationEnabled = permissionInfo.notificationEnabled,
            ignoreBatteryOptimization = permissionInfo.ignoreBatteryOptimization,
            appMonitorRunning = permissionInfo.appMonitorRunning
        )

        val json = JSONObject().apply {
            put("deviceId", heartbeat.deviceId)
            put("elderName", heartbeat.elderName)
            put("appVersion", heartbeat.appVersion)
            put("androidVersion", heartbeat.androidVersion)
            put("brand", heartbeat.brand)
            put("model", heartbeat.model)
            put("clientReportTime", heartbeat.clientReportTime)
            put("heartbeatSeq", heartbeat.heartbeatSeq)
            put("batteryPercent", heartbeat.batteryPercent)
            put("isCharging", heartbeat.isCharging)
            put("powerSaveMode", heartbeat.powerSaveMode)
            put("networkStatus", heartbeat.networkStatus)
            put("wifiEnabled", heartbeat.wifiEnabled)
            put("wifiConnected", heartbeat.wifiConnected)
            put("wifiSsid", heartbeat.wifiSsid)
            put("airplaneMode", heartbeat.airplaneMode)
            put("ringVolume", heartbeat.ringVolume)
            put("ringVolumeMax", heartbeat.ringVolumeMax)
            put("mediaVolume", heartbeat.mediaVolume)
            put("mediaVolumeMax", heartbeat.mediaVolumeMax)
            put("ringerMode", heartbeat.ringerMode)
            put("zenMode", heartbeat.zenMode)
            put("latitude", heartbeat.latitude ?: JSONObject.NULL)
            put("longitude", heartbeat.longitude ?: JSONObject.NULL)
            put("accuracy", heartbeat.accuracy ?: JSONObject.NULL)
            put("locationTime", heartbeat.locationTime ?: JSONObject.NULL)
            put("locationProvider", heartbeat.locationProvider ?: JSONObject.NULL)
            put("locationPermissionGranted", heartbeat.locationPermissionGranted)
            put("locationServiceEnabled", heartbeat.locationServiceEnabled)
            put("notificationEnabled", heartbeat.notificationEnabled)
            put("ignoreBatteryOptimization", heartbeat.ignoreBatteryOptimization)
            put("appMonitorRunning", heartbeat.appMonitorRunning)
        }

        UploadManager.uploadHeartbeat(this, json.toString())
        Log.d(TAG, "Heartbeat #$heartbeatSeq uploaded")
    }

    private fun getPrefs() = getSharedPreferences("elder_care", MODE_PRIVATE)

    private fun getDeviceUniqueId(): String {
        val prefs = getPrefs()
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing
        // 生成UUID作为设备ID，每次安装唯一，卸载后重置
        val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        prefs.edit().putString("device_id", id).apply()
        Log.d(TAG, "Generated device_id: $id")
        return id
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ElderCareApplication.CHANNEL_ID)
            .setContentTitle("老人关怀服务")
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
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
