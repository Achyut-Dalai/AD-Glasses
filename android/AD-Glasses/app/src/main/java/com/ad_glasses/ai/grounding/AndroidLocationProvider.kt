package com.ad_glasses.ai.grounding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class GeoFix(
    val point: GeoPoint,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
)

/** Fetches location only for a user turn that actually needs spatial context. */
class AndroidLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun currentFix(): GeoFix? {
        if (!hasPermission()) return null
        val recent = withTimeoutOrNull(LAST_LOCATION_TIMEOUT_MS) { fused.lastLocation.awaitNullable() }
        if (recent != null && isFreshEnough(recent)) return recent.toFix()

        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .awaitNullable()
                    ?.toFix()
            }
        } finally {
            cancellation.cancel()
        }
    }

    private fun isFreshEnough(location: Location): Boolean {
        val ageMs = if (location.elapsedRealtimeNanos > 0L) {
            (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L
        } else {
            Long.MAX_VALUE
        }
        return ageMs <= MAX_LOCATION_AGE_MS && (!location.hasAccuracy() || location.accuracy <= MAX_ACCEPTABLE_ACCURACY_METERS)
    }

    private fun Location.toFix(): GeoFix = GeoFix(
        point = GeoPoint(latitude = latitude, longitude = longitude),
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        bearingDegrees = bearing.takeIf { hasBearing() },
    )

    private suspend fun <T> Task<T>.awaitNullable(): T? = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
    }

    private companion object {
        const val MAX_LOCATION_AGE_MS = 120_000L
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 200f
        const val LAST_LOCATION_TIMEOUT_MS = 800L
        const val CURRENT_LOCATION_TIMEOUT_MS = 4_000L
    }
}
