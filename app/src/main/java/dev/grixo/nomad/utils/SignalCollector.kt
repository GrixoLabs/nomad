package dev.grixo.nomad.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import dev.grixo.nomad.data.database.entity.SignalEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SignalCollector {

    fun collect(context: Context, location: Location): SignalEntity {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryPercent = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            (level * 100 / scale.toFloat()).toInt()
        } ?: -1

        val chargingStatus = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                chargingStatus == BatteryManager.BATTERY_STATUS_FULL

        val batteryTemp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val networkType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            else -> "UNKNOWN"
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isPowerSaveMode = powerManager.isPowerSaveMode
        val isScreenOn = powerManager.isInteractive

        return SignalEntity(
            gpsTimestampUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(location.time)),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = location.accuracy,
            altitudeM = location.altitude,
            speedMps = location.speed,
            bearingDeg = location.bearing,
            batteryPercent = batteryPercent,
            charging = isCharging,
            batteryTemperature = batteryTemp,
            networkType = networkType,
            wifiEnabled = true, // Simplified for phase 1
            bluetoothEnabled = false, // Simplified for phase 1
            screenOn = isScreenOn,
            powerSaveMode = isPowerSaveMode
        )
    }
}
