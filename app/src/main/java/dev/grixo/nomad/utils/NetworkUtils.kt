package dev.grixo.nomad.utils

import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object NetworkUtils {

    suspend inline fun <reified T> safeApiCall(call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null || response.code() == 204 || response.code() == 201 || T::class == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    Result.success((body ?: Unit) as T)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorMsg = when (response.code()) {
                    in 400..499 -> "Client error: ${response.code()}"
                    in 500..599 -> "Server error: ${response.code()}"
                    else -> "Unexpected error: ${response.code()}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (_: UnknownHostException) {
            Result.failure(Exception("No internet connection or server unreachable"))
        } catch (_: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out"))
        } catch (_: IOException) {
            Result.failure(Exception("Network error occurred"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
