package com.eldercare.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.eldercare.app.model.EventType
import com.eldercare.app.upload.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class StateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val eventType = when (action) {
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> EventType.AIRPLANE_MODE_CHANGED
            "android.media.RINGER_MODE_CHANGED" -> EventType.RINGER_MODE_CHANGED
            Intent.ACTION_BATTERY_LOW -> EventType.BATTERY_LOW
            Intent.ACTION_BATTERY_OKAY -> "BATTERY_OKAY"
            Intent.ACTION_POWER_CONNECTED -> EventType.CHARGING_STATE_CHANGED
            Intent.ACTION_POWER_DISCONNECTED -> EventType.CHARGING_STATE_CHANGED
            "android.net.wifi.WIFI_STATE_CHANGED" -> EventType.WIFI_STATE_CHANGED
            "android.net.wifi.STATE_CHANGE" -> EventType.WIFI_CONNECTION_CHANGED
            "android.location.PROVIDERS_CHANGED" -> EventType.LOCATION_SERVICE_CHANGED
            else -> return
        }

        Log.d("StateReceiver", "State changed: $eventType")

        CoroutineScope(Dispatchers.IO).launch {
            recordEvent(context, eventType.toString(), action)
        }
    }

    private suspend fun recordEvent(context: Context, eventType: String, action: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val eventId = "evt_${System.currentTimeMillis()}_${(1000..9999).random()}"

        val deviceId = context.getSharedPreferences("elder_care", Context.MODE_PRIVATE)
            .getString("device_id", "unknown") ?: "unknown"

        val eventJson = JSONObject().apply {
            put("eventId", eventId)
            put("deviceId", deviceId)
            put("eventType", eventType)
            put("eventTime", dateFormat.format(Date()))
            put("oldValue", "")
            put("newValue", action)
            put("cached", false)
            put("reportTime", dateFormat.format(Date()))
        }

        UploadManager.uploadEvent(context, eventJson.toString())
    }
}
