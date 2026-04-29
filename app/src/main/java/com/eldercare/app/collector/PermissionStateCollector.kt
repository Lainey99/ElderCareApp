package com.eldercare.app.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.eldercare.app.service.MonitorService

data class PermissionInfo(
    val locationPermissionGranted: Boolean,
    val locationServiceEnabled: Boolean,
    val notificationEnabled: Boolean,
    val ignoreBatteryOptimization: Boolean,
    val appMonitorRunning: Boolean
)

object PermissionStateCollector {

    fun collect(context: Context): PermissionInfo {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val locationServiceEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        val notificationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoreBatteryOptimization = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        val appMonitorRunning = MonitorService.isRunning

        return PermissionInfo(
            locationPermissionGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            locationServiceEnabled = locationServiceEnabled,
            notificationEnabled = notificationEnabled,
            ignoreBatteryOptimization = ignoreBatteryOptimization,
            appMonitorRunning = appMonitorRunning
        )
    }
}
