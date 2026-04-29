package com.eldercare.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import com.eldercare.app.upload.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val hasNetwork = capabilities != null

            Log.d("NetworkReceiver", "Network changed, hasNetwork: $hasNetwork")

            if (hasNetwork) {
                // 网络恢复，触发补传
                CoroutineScope(Dispatchers.IO).launch {
                    UploadManager.retryFailedUploads(context)
                }
            }
        }
    }
}
