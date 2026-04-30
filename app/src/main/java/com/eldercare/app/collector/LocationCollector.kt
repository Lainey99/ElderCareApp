package com.eldercare.app.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LocationInfo(
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val locationTime: String?,
    val locationProvider: String?,
    val permissionGranted: Boolean,
    val serviceEnabled: Boolean
)

object LocationCollector {

    private const val TAG = "LocationCollector"

    suspend fun collect(context: Context): LocationInfo {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val permissionGranted = fineLocation || coarseLocation

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val serviceEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        Log.d(TAG, "permission=$permissionGranted, serviceEnabled=$serviceEnabled, " +
                "gps=${locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)}, " +
                "network=${locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)}")

        if (!permissionGranted || !serviceEnabled) {
            return LocationInfo(
                latitude = null, longitude = null, accuracy = null,
                locationTime = null, locationProvider = null,
                permissionGranted = permissionGranted, serviceEnabled = serviceEnabled
            )
        }

        // 1. 先尝试 FusedLocation 缓存
        var location = getLastKnownLocation(context)
        Log.d(TAG, "step1 - lastLocation: lat=${location?.latitude}, lon=${location?.longitude}")

        // 2. 尝试 FusedLocation 主动请求（带超时）
        if (location == null) {
            location = withTimeoutOrNull(5000L) { requestNewLocation(context) }
            Log.d(TAG, "step2 - getCurrentLocation: lat=${location?.latitude}, lon=${location?.longitude}")
        }

        // 3. 用原生 LocationManager 请求单次更新（兜底）
        if (location == null) {
            location = requestSingleUpdate(locationManager)
            Log.d(TAG, "step3 - singleUpdate: lat=${location?.latitude}, lon=${location?.longitude}")
        }

        Log.d(TAG, "final: lat=${location?.latitude}, lon=${location?.longitude}, provider=${location?.provider}")

        return LocationInfo(
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracy = location?.accuracy,
            locationTime = location?.time?.let { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(it) },
            locationProvider = location?.provider,
            permissionGranted = permissionGranted,
            serviceEnabled = serviceEnabled
        )
    }

    private suspend fun getLastKnownLocation(context: Context): Location? {
        return try {
            val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine { continuation ->
                fusedClient.lastLocation
                    .addOnSuccessListener { location -> continuation.resume(location) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "getLastKnownLocation security error", e)
            null
        }
    }

    private suspend fun requestNewLocation(context: Context): Location? {
        return try {
            val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { location -> continuation.resume(location) }
                    .addOnFailureListener { continuation.resume(null) }
                continuation.invokeOnCancellation { cts.cancel() }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "requestNewLocation security error", e)
            null
        }
    }

    private suspend fun requestSingleUpdate(locationManager: LocationManager): Location? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        continuation.resume(loc)
                    }
                    @Deprecated("Deprecated in API")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                // 优先用网络定位（快），不行用GPS
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } else {
                    continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "requestSingleUpdate security error", e)
            null
        }
    }
}
