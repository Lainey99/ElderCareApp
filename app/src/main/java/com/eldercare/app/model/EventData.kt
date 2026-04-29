package com.eldercare.app.model

data class EventData(
    val eventId: String,
    val deviceId: String,
    val eventType: String,
    val eventTime: String,
    val oldValue: String,
    val newValue: String,
    val cached: Boolean,
    val reportTime: String
)

enum class EventType {
    BOOT_COMPLETED,
    NETWORK_CHANGED,
    WIFI_STATE_CHANGED,
    WIFI_CONNECTION_CHANGED,
    AIRPLANE_MODE_CHANGED,
    RINGER_MODE_CHANGED,
    RING_VOLUME_CHANGED,
    ZEN_MODE_CHANGED,
    BATTERY_LOW,
    CHARGING_STATE_CHANGED,
    LOCATION_SERVICE_CHANGED,
    NETWORK_RECOVERED
}
