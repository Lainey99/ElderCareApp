package com.eldercare.app.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.*
import com.eldercare.app.collector.*
import com.eldercare.app.db.AppDatabase
import com.eldercare.app.upload.UploadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val WORK_NAME = "heartbeat_periodic"
        private const val PREFS_NAME = "elder_care"
        private const val KEY_HEARTBEAT_SEQ = "heartbeat_seq"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                30, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).addTag("heartbeat")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Periodic heartbeat work enqueued")
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Periodic heartbeat work cancelled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Heartbeat work starting")

            val db = AppDatabase.getDatabase(applicationContext)

            // 先补发缓存中的离线心跳和事件
            UploadManager.performUpload(db)

            // 收集设备状态
            val batteryInfo = BatteryStateCollector.collect(applicationContext)
            val networkInfo = NetworkStateCollector.collect(applicationContext)
            val audioInfo = AudioStateCollector.collect(applicationContext)
            val locationInfo = LocationCollector.collect(applicationContext)
            val permissionInfo = PermissionStateCollector.collect(applicationContext)

            // 获取并递增 seq
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val seq = prefs.getInt(KEY_HEARTBEAT_SEQ, 0) + 1
            prefs.edit().putInt(KEY_HEARTBEAT_SEQ, seq).apply()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val deviceId = getDeviceUniqueId(prefs)

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("appVersion", "1.0.0")
                put("androidVersion", Build.VERSION.SDK_INT.toString())
                put("brand", Build.BRAND)
                put("model", Build.MODEL)
                put("clientReportTime", dateFormat.format(Date()))
                put("heartbeatSeq", seq)
                put("batteryPercent", batteryInfo.percent)
                put("isCharging", batteryInfo.isCharging)
                put("powerSaveMode", batteryInfo.powerSaveMode)
                put("networkStatus", networkInfo.networkStatus)
                put("wifiEnabled", networkInfo.wifiEnabled)
                put("wifiConnected", networkInfo.wifiConnected)
                put("wifiSsid", networkInfo.wifiSsid)
                put("airplaneMode", networkInfo.airplaneMode)
                put("mobileDataEnabled", networkInfo.mobileDataEnabled)
                put("ringVolume", audioInfo.ringVolume)
                put("ringVolumeMax", audioInfo.ringVolumeMax)
                put("mediaVolume", audioInfo.mediaVolume)
                put("mediaVolumeMax", audioInfo.mediaVolumeMax)
                put("ringerMode", audioInfo.ringerMode)
                put("zenMode", audioInfo.zenMode)
                put("latitude", locationInfo.latitude ?: JSONObject.NULL)
                put("longitude", locationInfo.longitude ?: JSONObject.NULL)
                put("accuracy", locationInfo.accuracy ?: JSONObject.NULL)
                put("locationTime", locationInfo.locationTime ?: JSONObject.NULL)
                put("locationProvider", locationInfo.locationProvider ?: JSONObject.NULL)
                put("locationPermissionGranted", permissionInfo.locationPermissionGranted)
                put("locationServiceEnabled", permissionInfo.locationServiceEnabled)
                put("notificationEnabled", permissionInfo.notificationEnabled)
                put("ignoreBatteryOptimization", permissionInfo.ignoreBatteryOptimization)
                put("appMonitorRunning", permissionInfo.appMonitorRunning)
            }

            UploadManager.uploadHeartbeat(applicationContext, json.toString())
            Log.d(TAG, "Heartbeat #$seq uploaded successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat work failed", e)
            Result.retry()
        }
    }

    private fun getDeviceUniqueId(prefs: android.content.SharedPreferences): String {
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        prefs.edit().putString("device_id", id).apply()
        return id
    }
}
