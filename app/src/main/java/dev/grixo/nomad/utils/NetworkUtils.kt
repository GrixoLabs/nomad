package dev.grixo.nomad.utils

import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object NetworkUtils {

    fun <T> handleApiCall(call: () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null || response.code() == 204) {
                    Result.success(body as T)
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
        } catch (e: UnknownHostException) {
            Result.failure(Exception("No internet connection or server unreachable"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out"))
        } catch (e: IOException) {
            Result.failure(Exception("Network error occurred"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // For suspend calls returning Response
    suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                // Handle Unit/Void cases where body is null but success is true
                if (body != null || response.code() == 204 || response.code() == 201 || (body == null && T::class == Unit::class)) {
                    Result.success(body as T)
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
        } catch (e: UnknownHostException) {
            Result.failure(Exception("No internet connection or server unreachable"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out"))
        } catch (e: IOException) {
            Result.failure(Exception("Network error occurred"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
