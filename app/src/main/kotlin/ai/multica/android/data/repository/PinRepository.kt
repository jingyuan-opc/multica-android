package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.CreatePinRequest
import ai.multica.android.data.model.PinnedItem
import ai.multica.android.data.model.PinnedItemType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pin/unpin issues and projects to the sidebar. Pin metadata only —
 * consumers derive display data (title/icon/status) from the underlying
 * entity so the sidebar reacts to realtime updates without re-fetching.
 */
@Singleton
class PinRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(): ApiResult<List<PinnedItem>> =
        apiCall(NetworkFactory.json) { api.listPins() }

    suspend fun create(itemType: PinnedItemType, itemId: String): ApiResult<PinnedItem> =
        apiCall(NetworkFactory.json) { api.createPin(CreatePinRequest(itemType = itemType, itemId = itemId)) }

    suspend fun delete(itemType: PinnedItemType, itemId: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.deletePin(itemType.name.lowercase(), itemId) }
}
