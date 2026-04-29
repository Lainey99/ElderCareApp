package com.eldercare.app.collector

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

data class NetworkInfo(
    val networkStatus: String,  // NONE, WIFI, MOBILE
    val wifiEnabled: Boolean,
    val wifiConnected: Boolean,
    val wifiSsid: String,
    val airplaneMode: Boolean
)

object NetworkStateCollector {

    fun collect(context: Context): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val networkStatus = when {
            capabilities == null -> "NONE"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
            else -> "NONE"
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiEnabled = wifiManager.isWifiEnabled

        // 用 networkStatus 判断 WiFi 是否连接，比 connectionInfo 更准确
        val wifiConnected = networkStatus == "WIFI"

        // 获取 WiFi 名称
        @Suppress("DEPRECATION")
        val wifiSsid = try {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            info?.ssid?.replace("\"", "") ?: ""
        } catch (e: Exception) {
            ""
        }

        val airplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0

        return NetworkInfo(
            networkStatus = networkStatus,
            wifiEnabled = wifiEnabled,
            wifiConnected = wifiConnected,
            wifiSsid = wifiSsid,
            airplaneMode = airplaneMode
        )
    }
}
