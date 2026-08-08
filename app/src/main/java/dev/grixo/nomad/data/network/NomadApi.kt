package dev.grixo.nomad.data.network

import dev.grixo.nomad.data.network.model.AuthMessageResponse
import dev.grixo.nomad.data.network.model.AuthRegisterRequest
import dev.grixo.nomad.data.network.model.AuthRegisterResponse
import dev.grixo.nomad.data.network.model.DeviceRegistrationRequest
import dev.grixo.nomad.data.network.model.DeviceRegistrationResponse
import dev.grixo.nomad.data.network.model.HistoryResponse
import dev.grixo.nomad.data.network.model.JournalCreateRequest
import dev.grixo.nomad.data.network.model.JournalEntryResponse
import dev.grixo.nomad.data.network.model.MapConfigResponse
import dev.grixo.nomad.data.network.model.NearbyPlacesResponse
import dev.grixo.nomad.data.network.model.PlaceResolveResponse
import dev.grixo.nomad.data.network.model.SendEmailOtpRequest
import dev.grixo.nomad.data.network.model.SendSmsOtpRequest
import dev.grixo.nomad.data.network.model.SignalRequest
import dev.grixo.nomad.data.network.model.VerifyEmailOtpRequest
import dev.grixo.nomad.data.network.model.VerifySmsOtpRequest
import dev.grixo.nomad.data.network.model.WeatherResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NomadApi {

    @GET("api/v1/health")
    suspend fun checkHealth(): Response<Map<String, String>>

    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>

    @POST("api/v1/auth/register")
    suspend fun authRegister(@Body request: AuthRegisterRequest): Response<AuthRegisterResponse>

    @POST("api/v1/auth/send-email-otp")
    suspend fun sendEmailOtp(@Body request: SendEmailOtpRequest): Response<AuthMessageResponse>

    @POST("api/v1/auth/verify-email-otp")
    suspend fun verifyEmailOtp(@Body request: VerifyEmailOtpRequest): Response<AuthMessageResponse>

    @POST("api/v1/auth/send-sms-otp")
    suspend fun sendSmsOtp(@Body request: SendSmsOtpRequest): Response<AuthMessageResponse>

    @POST("api/v1/auth/verify-sms-otp")
    suspend fun verifySmsOtp(@Body request: VerifySmsOtpRequest): Response<AuthMessageResponse>

    @POST("api/v1/signals")
    suspend fun sendSignal(@Body request: SignalRequest): Response<ResponseBody>

    @GET("api/v1/places/resolve")
    suspend fun resolvePlace(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<PlaceResolveResponse>

    @GET("api/v1/weather")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<WeatherResponse>

    @GET("api/v1/places/nearby")
    suspend fun nearbyPlaces(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 10,
        @Query("sort") sort: String = "popularity"
    ): Response<NearbyPlacesResponse>

    @GET("api/v1/map/config")
    suspend fun mapConfig(): Response<MapConfigResponse>

    @POST("api/v1/journal")
    suspend fun createJournal(@Body request: JournalCreateRequest): Response<JournalEntryResponse>

    @GET("api/v1/history")
    suspend fun getHistory(
        @Query("device_uuid") deviceUuid: String,
        @Query("days") days: Int
    ): Response<HistoryResponse>

    companion object {
        // Production edge. If this 502s, nomad.service is up but reverse-proxy isn't routing.
        const val BASE_URL = "https://nomad.grixo.dev/"
    }
}
