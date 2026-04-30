package com.eldercare.app.collector

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

data class NetworkInfo(
    val networkStatus: String,  // NONE, WIFI, MOBILE
    val wifiEnabled: Boolean,
    val wifiConnected: Boolean,
    val wifiSsid: String,
    val airplaneMode: Boolean,
    val mobileDataEnabled: Boolean
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
        val wifiConnected = networkStatus == "WIFI"

        @Suppress("DEPRECATION")
        val wifiSsid = try {
            val info = wifiManager.connectionInfo
            info?.ssid?.replace("\"", "") ?: ""
        } catch (e: Exception) {
            ""
        }

        val airplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0

        // 检测蜂窝数据开关是否开启
        val mobileDataEnabled = checkMobileDataEnabled(connectivityManager)

        return NetworkInfo(
            networkStatus = networkStatus,
            wifiEnabled = wifiEnabled,
            wifiConnected = wifiConnected,
            wifiSsid = wifiSsid,
            airplaneMode = airplaneMode,
            mobileDataEnabled = mobileDataEnabled
        )
    }

    private fun checkMobileDataEnabled(connectivityManager: ConnectivityManager): Boolean {
        return try {
            // 遍历所有网络，检查是否有蜂窝网络可用
            val allNetworks = connectivityManager.allNetworks
            allNetworks.any { network ->
                val caps = connectivityManager.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            }
        } catch (e: Exception) {
            false
        }
    }
}
