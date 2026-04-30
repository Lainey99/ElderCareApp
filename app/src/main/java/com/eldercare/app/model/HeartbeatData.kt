package com.eldercare.app.model

data class HeartbeatData(
    val deviceId: String,
    val elderName: String,
    val appVersion: String,
    val androidVersion: String,
    val brand: String,
    val model: String,
    val clientReportTime: String,
    val heartbeatSeq: Int,

    // 电量
    val batteryPercent: Int,
    val isCharging: Boolean,
    val powerSaveMode: Boolean,

    // 网络
    val networkStatus: String,
    val wifiEnabled: Boolean,
    val wifiConnected: Boolean,
    val wifiSsid: String,
    val airplaneMode: Boolean,
    val mobileDataEnabled: Boolean,

    // 音量
    val ringVolume: Int,
    val ringVolumeMax: Int,
    val mediaVolume: Int,
    val mediaVolumeMax: Int,
    val ringerMode: String,
    val zenMode: String,

    // 位置
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val locationTime: String?,
    val locationProvider: String?,

    // 权限状态
    val locationPermissionGranted: Boolean,
    val locationServiceEnabled: Boolean,
    val notificationEnabled: Boolean,
    val ignoreBatteryOptimization: Boolean,
    val appMonitorRunning: Boolean
)
