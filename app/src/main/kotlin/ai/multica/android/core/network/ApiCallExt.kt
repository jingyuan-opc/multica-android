package ai.multica.android.core.network

import ai.multica.android.data.dto.ApiErrorBody
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Convert a Retrofit [Response] into an [ApiResult]. Parses the error
 * body if present to surface a human-readable message.
 */
suspend fun <T : Any> apiCall(
    json: Json,
    block: suspend () -> Response<T>,
): ApiResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) {
            ApiResult.Success(body)
        } else {
            // Retrofit returns null for 204 / Unit, treat as Success
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(Unit as T)
        }
    } else {
        val errorBody = response.errorBody()?.string()
        val message = errorBody
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull()?.error }
            ?: defaultErrorMessage(response.code())
        ApiResult.HttpError(response.code(), message)
    }
} catch (e: java.io.IOException) {
    ApiResult.NetworkError(e)
} catch (e: Throwable) {
    ApiResult.Unknown(e)
}

private fun defaultErrorMessage(code: Int): String = when (code) {
    400 -> "Bad request"
    401 -> "Not signed in"
    403 -> "Forbidden"
    404 -> "Not found"
    429 -> "Too many requests — please try again"
    in 500..599 -> "Server error ($code)"
    else -> "Request failed ($code)"
}
