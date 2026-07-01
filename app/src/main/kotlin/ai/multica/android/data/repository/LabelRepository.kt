package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.CreateLabelRequest
import ai.multica.android.data.dto.UpdateLabelRequest
import ai.multica.android.data.model.Label
import ai.multica.android.data.model.ListLabelsResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(): ApiResult<ListLabelsResponse> =
        apiCall(NetworkFactory.json) { api.listLabels() }

    suspend fun get(id: String): ApiResult<Label> =
        apiCall(NetworkFactory.json) { api.getLabel(id) }

    suspend fun create(name: String, color: String = "#3b82f6"): ApiResult<Label> =
        apiCall(NetworkFactory.json) {
            api.createLabel(CreateLabelRequest(name = name.trim(), color = color))
        }

    suspend fun update(id: String, name: String? = null, color: String? = null): ApiResult<Label> =
        apiCall(NetworkFactory.json) {
            api.updateLabel(id, UpdateLabelRequest(name = name?.trim(), color = color))
        }

    suspend fun delete(id: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.deleteLabel(id) }
}
