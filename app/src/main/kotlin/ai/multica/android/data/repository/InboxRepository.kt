package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.ArchiveCountResponse
import ai.multica.android.data.model.InboxItem
import ai.multica.android.data.model.InboxUnreadCountResponse
import ai.multica.android.data.model.InboxWorkspaceUnread
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over the inbox endpoints. **All counting and
 * rendering must go through `domain/InboxDedup.kt` BEFORE the
 * ViewModel sees the data** — see the 2026-05-09 incident in
 * `apps/mobile/CLAUDE.md`.
 *
 * This repository returns the raw server response (it doesn't
 * dedup — that's the ViewModel's job, where it's also responsible
 * for caching the deduped result).
 */
@Singleton
class InboxRepository @Inject constructor(
    private val api: MulticaApi,
) {
    /** Returns raw inbox rows. Dedup at the call site. */
    suspend fun list(): ApiResult<List<InboxItem>> =
        apiCall(NetworkFactory.json) { api.listInbox() }

    suspend fun unreadCount(): ApiResult<InboxUnreadCountResponse> =
        apiCall(NetworkFactory.json) { api.countUnreadInbox() }

    /** Cross-workspace summary — drives the workspace-switcher red dot. */
    suspend fun unreadSummary(): ApiResult<List<InboxWorkspaceUnread>> =
        apiCall(NetworkFactory.json) { api.inboxUnreadSummary() }

    suspend fun markRead(id: String): ApiResult<InboxItem> =
        apiCall(NetworkFactory.json) { api.markInboxRead(id) }

    suspend fun archive(id: String): ApiResult<InboxItem> =
        apiCall(NetworkFactory.json) { api.archiveInboxItem(id) }

    suspend fun markAllRead(): ApiResult<ArchiveCountResponse> =
        apiCall(NetworkFactory.json) { api.markAllInboxRead() }

    suspend fun archiveAllRead(): ApiResult<ArchiveCountResponse> =
        apiCall(NetworkFactory.json) { api.archiveAllReadInbox() }
}
