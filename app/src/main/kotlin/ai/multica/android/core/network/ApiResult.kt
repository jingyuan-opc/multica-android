package ai.multica.android.core.network

/**
 * Sealed result type for repository / API calls. Repositories return
 * this so ViewModels can map cleanly to a UiState with Loading / Error
 * / Data branches.
 *
 * - [Success] wraps a 2xx body.
 * - [HttpError] wraps a non-2xx response with the parsed error message
 *   (if any). Carries status code for branching (401 → kick to login).
 * - [NetworkError] wraps an IOException (no network, DNS failure, etc.).
 * - [Unknown] wraps anything else.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class HttpError(
        val code: Int,
        val message: String,
    ) : ApiResult<Nothing>()
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>()
    data class Unknown(val cause: Throwable) : ApiResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is HttpError -> this
        is NetworkError -> this
        is Unknown -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data

    inline fun onSuccess(block: (T) -> Unit): ApiResult<T> {
        if (this is Success) block(data)
        return this
    }
}
