package dev.grixo.nomad.data.network

import dev.grixo.nomad.BuildConfig

object ApiConfig {
    const val BASE_URL = "https://nomad.grixo.dev/"
    
    // In a real app, these could be swapped based on build variants
    fun getBaseUrl(): String = BASE_URL
}
