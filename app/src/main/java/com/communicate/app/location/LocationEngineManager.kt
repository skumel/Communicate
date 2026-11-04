package com.communicate.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.communicate.app.data.model.TelemetryPacket
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationEngineManager(context: Context) {

    private val TAG = "LocationEngineManager"
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _currentSpeedMph = MutableStateFlow(0f)
    val currentSpeedMph: StateFlow<Float> = _currentSpeedMph.asStateFlow()

    private val _telemetryPacket = MutableStateFlow<TelemetryPacket?>(null)
    val telemetryPacket: StateFlow<TelemetryPacket?> = _telemetryPacket.asStateFlow()

    private var deviceName: String = "Local Rider"
    private var isTracking: Boolean = false

    fun setDeviceName(name: String) {
        this.deviceName = name
    }

    /**
     * Start high-accuracy location tracking for riding speedometer and GPS map overlay.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(intervalMillis: Long = 1000L) {
        if (isTracking) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "Location updates requested successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates", e)
        }
    }

    fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTracking = false
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location updates", e)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            _currentLocation.value = location

            // Convert speed from meters/sec to mph (1 m/s = 2.23694 mph)
            val speedMph = if (location.hasSpeed()) {
                location.speed * 2.23694f
            } else {
                0f
            }
            _currentSpeedMph.value = Math.max(0f, speedMph)

            val packet = TelemetryPacket(
                latitude = location.latitude,
                longitude = location.longitude,
                speedMph = _currentSpeedMph.value,
                bearing = if (location.hasBearing()) location.bearing else 0f,
                riderName = deviceName
            )
            _telemetryPacket.value = packet
        }
    }
}
