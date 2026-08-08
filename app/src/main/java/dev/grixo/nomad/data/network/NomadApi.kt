package dev.grixo.nomad.data.network

import dev.grixo.nomad.data.network.model.DeviceRegistrationRequest
import dev.grixo.nomad.data.network.model.DeviceRegistrationResponse
import dev.grixo.nomad.data.network.model.SignalRequest
import dev.grixo.nomad.data.network.model.UserRegistrationRequest
import dev.grixo.nomad.data.network.model.UserRegistrationResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface NomadApi {

    @GET("api/v1/health")
    suspend fun checkHealth(): Response<Map<String, String>>

    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>

    @POST("api/v1/users/register")
    suspend fun registerUser(@Body request: UserRegistrationRequest): Response<UserRegistrationResponse>

    @POST("api/v1/signals")
    suspend fun sendSignal(@Body request: SignalRequest): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://nomad.grixo.dev/"
    }
}
