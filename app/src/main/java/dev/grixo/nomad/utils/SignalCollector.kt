package dev.grixo.nomad.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import dev.grixo.nomad.data.database.entity.SignalEntity
import java.time.Instant
import java.time.format.DateTimeFormatter

object SignalCollector {

    fun collect(context: Context, location: Location): SignalEntity {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryPercent = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
        } ?: -1

        val chargingStatus = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            chargingStatus == BatteryManager.BATTERY_STATUS_FULL

        val batteryTemp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val networkType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            else -> "UNKNOWN"
        }

        val wifiEnabled = try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled
        } catch (_: Exception) {
            networkType == "WIFI"
        }

        val bluetoothEnabled = try {
            val manager = context.getSystemService(BluetoothManager::class.java)
            val adapter: BluetoothAdapter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager?.adapter
            } else {
                @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            }
            adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

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
            wifiEnabled = wifiEnabled,
            bluetoothEnabled = bluetoothEnabled,
            screenOn = powerManager.isInteractive,
            powerSaveMode = powerManager.isPowerSaveMode
        )
    }
}
