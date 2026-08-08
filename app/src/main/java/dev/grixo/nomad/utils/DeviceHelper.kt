package dev.grixo.nomad.utils

import android.os.Build
import dev.grixo.nomad.BuildConfig

object DeviceHelper {
    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
    fun getManufacturer(): String = Build.MANUFACTURER
    fun getModel(): String = Build.MODEL
    fun getAndroidVersion(): String = Build.VERSION.RELEASE
    fun getAppVersion(): String = BuildConfig.VERSION_NAME
}
