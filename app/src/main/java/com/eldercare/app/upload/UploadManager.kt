package com.eldercare.app.upload

import android.content.Context
import android.util.Log
import com.eldercare.app.db.AppDatabase
import com.eldercare.app.db.EventEntity
import com.eldercare.app.db.HeartbeatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object UploadManager {

    private const val TAG = "UploadManager"
    private const val BASE_URL = "http://10.32.10.130:3000/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    suspend fun uploadHeartbeat(context: Context, payload: String) {
        val db = AppDatabase.getDatabase(context)
        val entity = HeartbeatEntity(payload = payload)

        try {
            db.cacheDao().insertHeartbeat(entity)
            performUpload(db)
        } catch (e: Exception) {
            Log.e(TAG, "Upload heartbeat failed", e)
        }
    }

    suspend fun uploadEvent(context: Context, payload: String) {
        val db = AppDatabase.getDatabase(context)
        val entity = EventEntity(payload = payload)

        try {
            db.cacheDao().insertEvent(entity)
            performUpload(db)
        } catch (e: Exception) {
            Log.e(TAG, "Upload event failed", e)
        }
    }

    suspend fun performUpload(db: AppDatabase) = withContext(Dispatchers.IO) {
        // 上传心跳
        val pendingHeartbeats = db.cacheDao().getPendingHeartbeats()
        for (heartbeat in pendingHeartbeats) {
            try {
                db.cacheDao().updateHeartbeat(heartbeat.copy(uploadStatus = HeartbeatEntity.STATUS_UPLOADING))
                val request = Request.Builder()
                    .url("$BASE_URL/device/heartbeat")
                    .post(heartbeat.payload.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    db.cacheDao().updateHeartbeat(heartbeat.copy(uploadStatus = HeartbeatEntity.STATUS_UPLOADED))
                    Log.d(TAG, "Heartbeat uploaded: ${heartbeat.id}")
                } else {
                    db.cacheDao().updateHeartbeat(heartbeat.copy(
                        uploadStatus = HeartbeatEntity.STATUS_FAILED,
                        retryCount = heartbeat.retryCount + 1
                    ))
                }
            } catch (e: Exception) {
                db.cacheDao().updateHeartbeat(heartbeat.copy(
                    uploadStatus = HeartbeatEntity.STATUS_FAILED,
                    retryCount = heartbeat.retryCount + 1
                ))
                Log.e(TAG, "Upload heartbeat error", e)
            }
        }

        // 上传事件
        val pendingEvents = db.cacheDao().getPendingEvents()
        for (event in pendingEvents) {
            try {
                db.cacheDao().updateEvent(event.copy(uploadStatus = EventEntity.STATUS_UPLOADING))
                val request = Request.Builder()
                    .url("$BASE_URL/device/event")
                    .post(event.payload.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    db.cacheDao().updateEvent(event.copy(uploadStatus = EventEntity.STATUS_UPLOADED))
                    Log.d(TAG, "Event uploaded: ${event.id}")
                } else {
                    db.cacheDao().updateEvent(event.copy(
                        uploadStatus = EventEntity.STATUS_FAILED,
                        retryCount = event.retryCount + 1
                    ))
                }
            } catch (e: Exception) {
                db.cacheDao().updateEvent(event.copy(
                    uploadStatus = EventEntity.STATUS_FAILED,
                    retryCount = event.retryCount + 1
                ))
                Log.e(TAG, "Upload event error", e)
            }
        }

        // 清理已上传的数据
        db.cacheDao().deleteUploadedHeartbeats()
        db.cacheDao().deleteUploadedEvents()
    }

    suspend fun retryFailedUploads(context: Context) {
        val db = AppDatabase.getDatabase(context)
        // 重置失败状态
        val failedHeartbeats = db.cacheDao().getPendingHeartbeats()
        for (h in failedHeartbeats.filter { it.retryCount >= 3 }) {
            db.cacheDao().deleteHeartbeat(h)
        }
        performUpload(db)
    }
}
