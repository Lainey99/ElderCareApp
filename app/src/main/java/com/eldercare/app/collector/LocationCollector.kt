package com.eldercare.app.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
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

    suspend fun collect(context: Context): LocationInfo {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val serviceEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!permissionGranted || !serviceEnabled) {
            return LocationInfo(
                latitude = null, longitude = null, accuracy = null,
                locationTime = null, locationProvider = null,
                permissionGranted = permissionGranted, serviceEnabled = serviceEnabled
            )
        }

        val location = getLastKnownLocation(context)

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
            val cancellationToken = CancellationTokenSource()

            suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken.token)
                    .addOnSuccessListener { location ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            }
        } catch (e: SecurityException) {
            null
        }
    }
}
