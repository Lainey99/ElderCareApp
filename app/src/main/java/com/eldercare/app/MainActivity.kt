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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

val Purple40 = Color(0xFF6C63FF)
val PurpleLight = Color(0xFF8B83FF)
val PurpleDark = Color(0xFF524BCC)
val Green40 = Color(0xFF4CAF50)
val GreenLight = Color(0xFF81C784)
val Orange40 = Color(0xFFFF9800)
val Red40 = Color(0xFFF44336)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFEEEEEE)
val Gray600 = Color(0xFF757575)

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
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Purple40,
                    onPrimary = Color.White,
                    primaryContainer = PurpleLight,
                    secondary = Green40,
                    surface = Color.White,
                    background = Gray100,
                    onBackground = Color(0xFF1C1B1F),
                    onSurface = Color(0xFF1C1B1F),
                )
            ) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Purple40, PurpleLight)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "安心守护",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 设置
            SectionCard {
                SectionTitle(icon = Icons.Default.Person, title = "设备设置")
                OutlinedTextField(
                    value = elderName,
                    onValueChange = { newValue ->
                        elderName = newValue
                        prefs.edit().putString("elder_name", newValue).apply()
                    },
                    label = { Text("姓名/备注") },
                    placeholder = { Text("例如：奶奶、爸爸") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // 服务状态
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(icon = Icons.Default.Settings, title = "监控服务")
                    StatusChip(
                        active = isServiceRunning,
                        activeText = "运行中",
                        inactiveText = "已停止"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onStartService()
                            isServiceRunning = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green40),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("启动")
                    }
                    OutlinedButton(
                        onClick = {
                            onStopService()
                            isServiceRunning = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止")
                    }
                }
            }

            // 权限状态
            SectionCard {
                SectionTitle(icon = Icons.Default.Lock, title = "权限状态")
                permissionInfo?.let { info ->
                    StatusRow("定位权限", info.locationPermissionGranted)
                    StatusRow("定位服务", info.locationServiceEnabled)
                    StatusRow("通知权限", info.notificationEnabled)
                    StatusRow("电池优化", info.ignoreBatteryOptimization)
                } ?: Text("加载中...", color = Gray600)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        // 先尝试申请权限，如果之前被拒绝过就跳设置页
                        val hasFineLocation = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasFineLocation) {
                            // 权限已授予，可能是通知权限没开
                            onRequestPermissions()
                        } else {
                            // 跳转到应用设置页
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("申请权限")
                }
            }

            // 电量
            SectionCard {
                SectionTitle(icon = Icons.Default.BatteryFull, title = "电量")
                batteryInfo?.let { info ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${info.percent}%",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    info.percent > 50 -> Green40
                                    info.percent > 20 -> Orange40
                                    else -> Red40
                                }
                            )
                            if (info.isCharging) {
                                Text("充电中 ⚡", color = Green40, fontSize = 12.sp)
                            }
                            if (info.powerSaveMode) {
                                Text("省电模式 ⚠️", color = Orange40, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { info.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            info.percent > 50 -> Green40
                            info.percent > 20 -> Orange40
                            else -> Red40
                        },
                        trackColor = Gray200,
                    )
                } ?: Text("加载中...", color = Gray600)
            }

            // 网络
            SectionCard {
                SectionTitle(icon = Icons.Default.Wifi, title = "网络")
                networkInfo?.let { info ->
                    StatusRow("网络状态", info.networkStatus != "NONE", info.networkStatus)
                    StatusRow("WiFi 开关", info.wifiEnabled)
                    StatusRow("WiFi 连接", info.wifiConnected)
                    if (info.wifiConnected && info.wifiSsid.isNotBlank()) {
                        StatusRow("WiFi 名称", true, info.wifiSsid)
                    }
                    StatusRow("蜂窝数据", info.mobileDataEnabled)
                    StatusRow("飞行模式", info.airplaneMode, if (info.airplaneMode) "已开启" else "")
                } ?: Text("加载中...", color = Gray600)
            }

            // 音量
            SectionCard {
                SectionTitle(icon = Icons.Default.VolumeUp, title = "音量")
                audioInfo?.let { info ->
                    VolumeRow("铃声", info.ringVolume, info.ringVolumeMax)
                    VolumeRow("媒体", info.mediaVolume, info.mediaVolumeMax)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("响铃模式", color = Gray600, fontSize = 13.sp)
                        Text(
                            when (info.ringerMode) {
                                "NORMAL" -> "正常"
                                "VIBRATE" -> "振动"
                                "SILENT" -> "静音"
                                else -> info.ringerMode
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } ?: Text("加载中...", color = Gray600)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 上报按钮
            var isUploading by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    isUploading = true
                    batteryInfo = BatteryStateCollector.collect(context)
                    networkInfo = NetworkStateCollector.collect(context)
                    audioInfo = AudioStateCollector.collect(context)
                    permissionInfo = PermissionStateCollector.collect(context)

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                            val p = context.getSharedPreferences("elder_care", Context.MODE_PRIVATE)
                            val deviceId = p.getString("device_id", null)
                                ?: run {
                                    val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                                    p.edit().putString("device_id", id).apply()
                                    id
                                }

                            val battery = batteryInfo
                            val network = networkInfo
                            val audio = audioInfo

                            val json = JSONObject().apply {
                                put("deviceId", deviceId)
                                put("elderName", p.getString("elder_name", "未设置") ?: "未设置")
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
                                put("mobileDataEnabled", network?.mobileDataEnabled ?: false)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    if (isUploading) "上报中..." else "刷新并上报状态",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Purple40,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF333333)
        )
    }
}

@Composable
fun StatusRow(label: String, active: Boolean, extraText: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Gray600, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (extraText.isNotBlank()) {
                Text(extraText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Green40 else Red40)
            )
        }
    }
}

@Composable
fun StatusChip(active: Boolean, activeText: String, inactiveText: String) {
    if (activeText.isBlank() && inactiveText.isBlank()) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Green40.copy(alpha = 0.12f) else Red40.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            if (active) activeText else inactiveText,
            color = if (active) Green40 else Red40,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun VolumeRow(label: String, current: Int, max: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Gray600, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { if (max > 0) current.toFloat() / max else 0f },
                modifier = Modifier
                    .width(80.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (current > 0) Purple40 else Gray200,
                trackColor = Gray200,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "$current/$max",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (current == 0) Red40 else Color.Unspecified
            )
        }
    }
}
