package com.eldercare.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heartbeat_queue")
data class HeartbeatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val createTime: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val uploadStatus: Int = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_UPLOADING = 1
        const val STATUS_UPLOADED = 2
        const val STATUS_FAILED = 3
    }
}

@Entity(tableName = "event_queue")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val createTime: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val uploadStatus: Int = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_UPLOADING = 1
        const val STATUS_UPLOADED = 2
        const val STATUS_FAILED = 3
    }
}
