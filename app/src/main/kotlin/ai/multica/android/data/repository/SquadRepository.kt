package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.model.Squad
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SquadRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(): ApiResult<List<Squad>> =
        apiCall(NetworkFactory.json) { api.listSquads() }
}
