package ai.multica.android.data.dto

import ai.multica.android.data.model.InboxItem
import ai.multica.android.data.model.InboxUnreadCountResponse
import ai.multica.android.data.model.InboxWorkspaceUnread
import kotlinx.serialization.Serializable

@Serializable
data class ArchiveCountResponse(val count: Long = 0)
