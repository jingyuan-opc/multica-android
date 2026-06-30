package ai.multica.android.domain

import ai.multica.android.data.model.InboxItem
import ai.multica.android.data.model.InboxWorkspaceUnread
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Inbox deduplication — **MIRROR** of
 * `packages/core/inbox/queries.ts::deduplicateInboxItems`.
 *
 * ## Why this matters (the 2026-05-09 incident)
 *
 * The backend `GET /api/inbox` returns RAW rows that include:
 *  1. Archived items
 *  2. Multiple inbox notifications per issue (a comment, a status
 *     change, and an assignment on the same issue each create one
 *     row)
 *
 * The web app, desktop app, and iOS app all run those raw rows
 * through `deduplicateInboxItems` BEFORE rendering and BEFORE
 * counting unread. Mobile is required to do the same; if it skips
 * this step the same inbox will show 3 unread dots on Android vs 1
 * on iOS/Web — a real production incident documented in
 * `apps/mobile/CLAUDE.md`.
 *
 * ## Algorithm (do not change without coordinating with web/iOS)
 *
 *  1. Drop items where `archived == true`.
 *  2. Group by `issue_id` (fall back to `id` when an item has no
 *     `issue_id`).
 *  3. Within each group, sort by `created_at` desc, keep the newest.
 *  4. **Inherit `details.comment_id`**: if the newest row in the
 *     group has no `comment_id` in its `details`, but ANY sibling
 *     does, use that sibling's `comment_id` and patch it onto the
 *     newest row. This makes the row deep-linkable to the comment
 *     even when the comment notification isn't the most recent.
 *  5. Sort the merged list by `created_at` desc.
 *
 * ## Use it for BOTH
 *
 * - Rendering the inbox list (pass the result to the LazyColumn)
 * - Counting unread (`.count { !it.read }` on the result)
 *
 * Never call `.count { !it.read }` on the raw response.
 */
object InboxDedup {

    /**
     * Run the canonical dedup pipeline. Returns items sorted by
     * `created_at` desc, with no archived items, and with at most
     * one entry per `issue_id`.
     */
    fun deduplicate(items: List<InboxItem>): List<InboxItem> {
        val active = items.filter { !it.archived }

        // Group by issue_id (fall back to id when issue_id is null).
        val groups = linkedMapOf<String, MutableList<InboxItem>>()
        for (item in active) {
            val key = item.issueId ?: item.id
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }

        val merged = mutableListOf<InboxItem>()
        for (group in groups.values) {
            group.sortByDescending { parseCreatedAt(it.createdAt) }
            val newest = group.firstOrNull() ?: continue

            val newestCommentId = extractCommentId(newest)
            // Look for a sibling that has a comment_id but the newest doesn't
            val inheritedCommentId = if (newestCommentId == null) {
                group.firstNotNullOfOrNull { extractCommentId(it) }
            } else {
                null
            }

            if (inheritedCommentId != null) {
                val primitive: JsonElement = JsonPrimitive(inheritedCommentId)
                val newMap: MutableMap<String, JsonElement> = (newest.details as? JsonObject)
                    ?.toMutableMap()
                    ?: mutableMapOf()
                newMap["comment_id"] = primitive
                merged.add(newest.copy(details = JsonObject(newMap)))
            } else {
                merged.add(newest)
            }
        }

        merged.sortByDescending { parseCreatedAt(it.createdAt) }
        return merged
    }

    /**
     * Counts UNREAD in the **deduplicated** list. Never call this on
     * the raw response — it would over-count.
     */
    fun countUnread(items: List<InboxItem>): Int =
        deduplicate(items).count { !it.read }

    /**
     * Whether any workspace OTHER than `currentWsId` has unread inbox
     * items. Mirrors `packages/core/inbox/queries.ts::hasOtherWorkspaceUnread`.
     * Drives the workspace-switcher red dot in the top bar.
     */
    fun hasOtherWorkspaceUnread(
        summary: List<InboxWorkspaceUnread>,
        currentWsId: String?,
    ): Boolean = summary.any { it.workspaceId != currentWsId && it.count > 0 }

    /**
     * Set of workspace ids that have unread inbox items, for marking
     * which workspace a pending message lives in. Mirrors
     * `packages/core/inbox/queries.ts::unreadWorkspaceIds`.
     */
    fun unreadWorkspaceIds(summary: List<InboxWorkspaceUnread>): Set<String> =
        summary.filter { it.count > 0 }.map { it.workspaceId }.toSet()

    // ---- helpers ----

    private fun extractCommentId(item: InboxItem): String? {
        val details = item.details as? JsonObject ?: return null
        val value = details["comment_id"] as? JsonPrimitive ?: return null
        return value.contentOrNull
    }

    /**
     * The server returns RFC3339Nano timestamps (e.g.
     * "2026-06-28T12:34:56.789012Z"). kotlinx.datetime parses these
     * reliably across timezones; falling back to `0L` keeps the
     * sort stable if a malformed timestamp slips through.
     */
    private fun parseCreatedAt(value: String): Long = try {
        kotlinx.datetime.Instant.parse(value).toEpochMilliseconds()
    } catch (e: Throwable) {
        0L
    }
}
