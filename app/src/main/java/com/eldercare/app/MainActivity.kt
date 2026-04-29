package com.eldercare.app

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eldercare.app.collector.*
import com.eldercare.app.service.MonitorService
import com.eldercare.app.upload.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startMonitorService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onRequestPermissions = { requestPermissions() },
                    onStartService = { startMonitorService() },
                    onStopService = { MonitorService.stop(this) }
                )
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startMonitorService() {
        requestIgnoreBatteryOptimization()
        MonitorService.start(this)
    }

    private fun requestIgnoreBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // 忽略
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("elder_care", Context.MODE_PRIVATE) }
    var elderName by remember { mutableStateOf(prefs.getString("elder_name", "") ?: "") }
    var batteryInfo by remember { mutableStateOf<BatteryInfo?>(null) }
    var networkInfo by remember { mutableStateOf<NetworkInfo?>(null) }
    var audioInfo by remember { mutableStateOf<AudioInfo?>(null) }
    var permissionInfo by remember { mutableStateOf<PermissionInfo?>(null) }
    var isServiceRunning by remember { mutableStateOf(MonitorService.isRunning) }

    LaunchedEffect(Unit) {
        batteryInfo = BatteryStateCollector.collect(context)
        networkInfo = NetworkStateCollector.collect(context)
        audioInfo = AudioStateCollector.collect(context)
        permissionInfo = PermissionStateCollector.collect(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("老人关怀 App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 设置
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("设备设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = elderName,
                        onValueChange = { newValue ->
                            elderName = newValue
                            prefs.edit().putString("elder_name", newValue).apply()
                        },
                        label = { Text("老人姓名/备注") },
                        placeholder = { Text("例如：奶奶、爸爸") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // 服务状态
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("服务状态", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (isServiceRunning) "✅ 监控服务运行中" else "❌ 监控服务已停止")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onStartService()
                            isServiceRunning = true
                        }) {
                            Text("启动服务")
                        }
                        OutlinedButton(onClick = {
                            onStopService()
                            isServiceRunning = false
                        }) {
                            Text("停止服务")
                        }
                    }
                }
            }

            // 权限状态
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("权限状态", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    permissionInfo?.let { info ->
                        Text("定位权限: ${if (info.locationPermissionGranted) "✅" else "❌"}")
                        Text("定位服务: ${if (info.locationServiceEnabled) "✅" else "❌"}")
                        Text("通知权限: ${if (info.notificationEnabled) "✅" else "❌"}")
                        Text("电池优化: ${if (info.ignoreBatteryOptimization) "✅ 已忽略" else "❌ 未忽略"}")
                    } ?: Text("加载中...")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestPermissions) {
                        Text("申请权限")
                    }
                }
            }

            // 电量信息
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("电量信息", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    batteryInfo?.let { info ->
                        Text("电量: ${info.percent}%")
                        Text("充电中: ${if (info.isCharging) "是" else "否"}")
                        Text("省电模式: ${if (info.powerSaveMode) "是" else "否"}")
                    } ?: Text("加载中...")
                }
            }

            // 网络信息
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("网络信息", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    networkInfo?.let { info ->
                        Text("网络状态: ${info.networkStatus}")
                        Text("Wi-Fi 开关: ${if (info.wifiEnabled) "开" else "关"}")
                        Text("Wi-Fi 连接: ${if (info.wifiConnected) "是" else "否"}")
                        if (info.wifiConnected) Text("Wi-Fi 名称: ${info.wifiSsid}")
                        Text("飞行模式: ${if (info.airplaneMode) "开" else "关"}")
                    } ?: Text("加载中...")
                }
            }

            // 音量信息
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("音量信息", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    audioInfo?.let { info ->
                        Text("铃声音量: ${info.ringVolume}/${info.ringVolumeMax}")
                        Text("媒体音量: ${info.mediaVolume}/${info.mediaVolumeMax}")
                        Text("响铃模式: ${info.ringerMode}")
                        Text("勿扰模式: ${info.zenMode}")
                    } ?: Text("加载中...")
                }
            }

            // 刷新并上报按钮
            var isUploading by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    isUploading = true
                    // 刷新本地状态
                    batteryInfo = BatteryStateCollector.collect(context)
                    networkInfo = NetworkStateCollector.collect(context)
                    audioInfo = AudioStateCollector.collect(context)
                    permissionInfo = PermissionStateCollector.collect(context)

                    // 上报到服务器
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                            val prefs = context.getSharedPreferences("elder_care", Context.MODE_PRIVATE)
                            val deviceId = prefs.getString("device_id", null)
                                ?: run {
                                    val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                                    prefs.edit().putString("device_id", id).apply()
                                    id
                                }

                            val battery = batteryInfo
                            val network = networkInfo
                            val audio = audioInfo

                            val json = JSONObject().apply {
                                put("deviceId", deviceId)
                                put("elderName", prefs.getString("elder_name", "未设置") ?: "未设置")
                                put("appVersion", "1.0.0")
                                put("androidVersion", Build.VERSION.SDK_INT.toString())
                                put("brand", Build.BRAND)
                                put("model", Build.MODEL)
                                put("clientReportTime", dateFormat.format(Date()))
                                put("heartbeatSeq", 0)
                                put("batteryPercent", battery?.percent ?: 0)
                                put("isCharging", battery?.isCharging ?: false)
                                put("powerSaveMode", battery?.powerSaveMode ?: false)
                                put("networkStatus", network?.networkStatus ?: "NONE")
                                put("wifiEnabled", network?.wifiEnabled ?: false)
                                put("wifiConnected", network?.wifiConnected ?: false)
                                put("wifiSsid", network?.wifiSsid ?: "")
                                put("airplaneMode", network?.airplaneMode ?: false)
                                put("ringVolume", audio?.ringVolume ?: 0)
                                put("ringVolumeMax", audio?.ringVolumeMax ?: 0)
                                put("mediaVolume", audio?.mediaVolume ?: 0)
                                put("mediaVolumeMax", audio?.mediaVolumeMax ?: 0)
                                put("ringerMode", audio?.ringerMode ?: "UNKNOWN")
                                put("zenMode", audio?.zenMode ?: "OFF")
                                put("latitude", JSONObject.NULL)
                                put("longitude", JSONObject.NULL)
                                put("accuracy", JSONObject.NULL)
                                put("locationTime", JSONObject.NULL)
                                put("locationProvider", JSONObject.NULL)
                                put("locationPermissionGranted", permissionInfo?.locationPermissionGranted ?: false)
                                put("locationServiceEnabled", permissionInfo?.locationServiceEnabled ?: false)
                                put("notificationEnabled", permissionInfo?.notificationEnabled ?: false)
                                put("ignoreBatteryOptimization", permissionInfo?.ignoreBatteryOptimization ?: false)
                                put("appMonitorRunning", permissionInfo?.appMonitorRunning ?: false)
                            }

                            UploadManager.uploadHeartbeat(context, json.toString())
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Upload failed", e)
                        }
                        withContext(Dispatchers.Main) {
                            isUploading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                Text(if (isUploading) "上报中..." else "刷新并上报状态")
            }
        }
    }
}
