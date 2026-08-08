package dev.grixo.nomad.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_signals")
data class SignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gpsTimestampUtc: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val altitudeM: Double,
    val speedMps: Float,
    val bearingDeg: Float,
    val batteryPercent: Int,
    val charging: Boolean,
    val batteryTemperature: Float,
    val networkType: String,
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val screenOn: Boolean,
    val powerSaveMode: Boolean
)
