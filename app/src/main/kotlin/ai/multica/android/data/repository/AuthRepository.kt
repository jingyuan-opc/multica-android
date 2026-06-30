package ai.multica.android.data.repository

import ai.multica.android.core.auth.TokenStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.LoginResponse
import ai.multica.android.data.dto.LogoutResponse
import ai.multica.android.data.dto.SendCodeRequest
import ai.multica.android.data.dto.SendCodeResponse
import ai.multica.android.data.dto.VerifyCodeRequest
import ai.multica.android.data.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: MulticaApi,
    private val tokenStore: TokenStore,
) {
    suspend fun sendCode(email: String): ApiResult<SendCodeResponse> =
        apiCall(NetworkFactory.json) { api.sendCode(SendCodeRequest(email.trim().lowercase())) }

    suspend fun verifyCode(email: String, code: String): ApiResult<LoginResponse> {
        val result = apiCall(NetworkFactory.json) {
            api.verifyCode(VerifyCodeRequest(email.trim().lowercase(), code.trim()))
        }
        if (result is ApiResult.Success) {
            tokenStore.setToken(result.data.token)
        }
        return result
    }

    /**
     * Bootstrap session: validates the stored token by calling
     * GET /api/me. If it succeeds, return the user; if 401, clear
     * the token and surface the error so the UI can route to login.
     */
    suspend fun getMe(): ApiResult<User> {
        val result = apiCall(NetworkFactory.json) { api.getMe() }
        if (result is ApiResult.HttpError && result.code == 401) {
            tokenStore.clear()
        }
        return result
    }

    suspend fun logout(): ApiResult<LogoutResponse> {
        // Always clear local token, even if the server call fails.
        val result = apiCall(NetworkFactory.json) { api.logout() }
        tokenStore.clear()
        return result
    }
}
