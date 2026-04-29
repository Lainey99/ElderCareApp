package com.eldercare.app.collector

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.app.NotificationManager

data class AudioInfo(
    val ringVolume: Int,
    val ringVolumeMax: Int,
    val mediaVolume: Int,
    val mediaVolumeMax: Int,
    val ringerMode: String,  // NORMAL, VIBRATE, SILENT
    val zenMode: String      // OFF, PRIORITY, TOTAL_SILENCE, ALARMS
)

object AudioStateCollector {

    fun collect(context: Context): AudioInfo {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val ringVolumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)

        val mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaVolumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            else -> "UNKNOWN"
        }

        val zenMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            when (notificationManager.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "OFF"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "TOTAL_SILENCE"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS"
                else -> "OFF"
            }
        } else {
            "OFF"
        }

        return AudioInfo(
            ringVolume = ringVolume,
            ringVolumeMax = ringVolumeMax,
            mediaVolume = mediaVolume,
            mediaVolumeMax = mediaVolumeMax,
            ringerMode = ringerMode,
            zenMode = zenMode
        )
    }
}
