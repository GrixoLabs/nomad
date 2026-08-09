package dev.grixo.nomad.utils

import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object AuthErrorMapper {

    fun friendlyNetworkMessage(error: Throwable): String = when (error) {
        is UnknownHostException ->
            "Can't reach Nomad servers. Check your internet connection and try again."
        is SocketTimeoutException ->
            "Connection timed out. Try again."
        is IOException ->
            "Network error. Check your connection and try again."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
    }

    fun fromResponse(response: Response<*>, fallback: String): String {
        val raw = try {
            response.errorBody()?.string()?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (raw.isEmpty()) return "$fallback (${response.code()})"
        return try {
            val obj = JSONObject(raw)
            when (val detail = obj.opt("detail")) {
                is String -> detail
                is JSONArray -> {
                    val first = detail.optJSONObject(0)
                    first?.optString("msg")?.takeIf { it.isNotBlank() }
                        ?: detail.toString().take(200)
                }
                else -> raw.take(220)
            }
        } catch (_: Exception) {
            // Retrofit/OkHttp often surface UnknownHost as the message body itself.
            if (raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("No address associated with hostname", ignoreCase = true)
            ) {
                "Can't reach Nomad servers. Check your internet connection and try again."
            } else {
                raw.take(220)
            }
        }
    }
}
