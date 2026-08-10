package dev.grixo.nomad.data.location

import android.location.Location
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LiveLocationEvent(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val altitudeM: Double?,
    val batteryPercent: Int?,
    val networkType: String,
    val uploaded: Boolean,
    /** Degrees clockwise from north when known. */
    val bearingDeg: Float? = null
)

@Singleton
class LocationBus @Inject constructor() {
    private val _events = MutableSharedFlow<LiveLocationEvent>(replay = 1, extraBufferCapacity = 16)
    val events: SharedFlow<LiveLocationEvent> = _events.asSharedFlow()

    suspend fun publish(event: LiveLocationEvent) {
        _events.emit(event)
    }

    fun tryPublish(event: LiveLocationEvent) {
        _events.tryEmit(event)
    }

    fun fromLocation(
        location: Location,
        batteryPercent: Int?,
        networkType: String,
        uploaded: Boolean
    ): LiveLocationEvent = LiveLocationEvent(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyM = location.accuracy,
        altitudeM = if (location.hasAltitude()) location.altitude else null,
        batteryPercent = batteryPercent,
        networkType = networkType,
        uploaded = uploaded,
        bearingDeg = if (location.hasBearing()) location.bearing else null
    )
}
