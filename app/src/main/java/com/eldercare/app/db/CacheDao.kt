package com.eldercare.app.db

import androidx.room.*

@Dao
interface CacheDao {

    @Insert
    suspend fun insertHeartbeat(entity: HeartbeatEntity): Long

    @Query("SELECT * FROM heartbeat_queue WHERE uploadStatus = 0 ORDER BY createTime ASC")
    suspend fun getPendingHeartbeats(): List<HeartbeatEntity>

    @Update
    suspend fun updateHeartbeat(entity: HeartbeatEntity)

    @Delete
    suspend fun deleteHeartbeat(entity: HeartbeatEntity)

    @Query("DELETE FROM heartbeat_queue WHERE uploadStatus = 2")
    suspend fun deleteUploadedHeartbeats()

    @Insert
    suspend fun insertEvent(entity: EventEntity): Long

    @Query("SELECT * FROM event_queue WHERE uploadStatus = 0 ORDER BY createTime ASC")
    suspend fun getPendingEvents(): List<EventEntity>

    @Update
    suspend fun updateEvent(entity: EventEntity)

    @Delete
    suspend fun deleteEvent(entity: EventEntity)

    @Query("DELETE FROM event_queue WHERE uploadStatus = 2")
    suspend fun deleteUploadedEvents()
}
