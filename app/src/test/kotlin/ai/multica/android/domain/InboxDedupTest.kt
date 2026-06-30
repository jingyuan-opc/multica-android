package ai.multica.android.domain

import ai.multica.android.data.model.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for InboxDedup — these mirror the test cases the iOS
 * app uses for `apps/mobile/lib/inbox-display.ts`. The point is to
 * lock the behavior in place: any change here must produce a
 * matching change in `packages/core/inbox/queries.ts` first.
 *
 * (We don't ship JUnit in the runtime classpath; tests live under
 * `src/test/java` and run via `./gradlew :app:testDebugUnitTest`.)
 */
class InboxDedupTest {

    @Test
    fun `drops archived items`() {
        val items = listOf(
            item(id = "1", archived = false, createdAt = "2026-06-28T10:00:00Z"),
            item(id = "2", archived = true, createdAt = "2026-06-28T11:00:00Z"),
            item(id = "3", archived = false, createdAt = "2026-06-28T12:00:00Z"),
        )
        val result = InboxDedup.deduplicate(items)
        assertEquals(2, result.size)
        assertTrue(result.none { it.archived })
    }

    @Test
    fun `groups by issue_id and keeps newest`() {
        val items = listOf(
            item(id = "1", issueId = "ISS-1", createdAt = "2026-06-28T10:00:00Z"),
            item(id = "2", issueId = "ISS-1", createdAt = "2026-06-28T12:00:00Z"),
            item(id = "3", issueId = "ISS-1", createdAt = "2026-06-28T11:00:00Z"),
        )
        val result = InboxDedup.deduplicate(items)
        assertEquals(1, result.size)
        assertEquals("2", result[0].id)
    }

    @Test
    fun `falls back to id when issue_id is null`() {
        val items = listOf(
            item(id = "1", issueId = null, createdAt = "2026-06-28T10:00:00Z"),
            item(id = "2", issueId = "ISS-1", createdAt = "2026-06-28T11:00:00Z"),
        )
        val result = InboxDedup.deduplicate(items)
        assertEquals(2, result.size)
    }

    @Test
    fun `inherits comment_id from sibling when newest has none`() {
        // Two rows on the same issue: older has a comment_id, newer
        // doesn't. Result should be the newer row, but with the
        // comment_id patched on so deep-link to comment works.
        val older = item(
            id = "1",
            issueId = "ISS-1",
            createdAt = "2026-06-28T10:00:00Z",
            commentId = "COMMENT-42",
        )
        val newer = item(
            id = "2",
            issueId = "ISS-1",
            createdAt = "2026-06-28T12:00:00Z",
            commentId = null,
        )
        val result = InboxDedup.deduplicate(listOf(older, newer))
        assertEquals(1, result.size)
        assertEquals("2", result[0].id)
        assertEquals("COMMENT-42", result[0].commentId)
    }

    @Test
    fun `keeps newest comment_id when newest has one`() {
        val items = listOf(
            item(id = "1", issueId = "ISS-1", createdAt = "2026-06-28T10:00:00Z", commentId = "COMMENT-1"),
            item(id = "2", issueId = "ISS-1", createdAt = "2026-06-28T12:00:00Z", commentId = "COMMENT-2"),
        )
        val result = InboxDedup.deduplicate(items)
        assertEquals(1, result.size)
        assertEquals("COMMENT-2", result[0].commentId)
    }

    @Test
    fun `sorts merged by created_at desc`() {
        val items = listOf(
            item(id = "1", issueId = "ISS-1", createdAt = "2026-06-28T10:00:00Z"),
            item(id = "2", issueId = "ISS-2", createdAt = "2026-06-28T15:00:00Z"),
            item(id = "3", issueId = "ISS-3", createdAt = "2026-06-28T12:00:00Z"),
        )
        val result = InboxDedup.deduplicate(items)
        assertEquals(listOf("2", "3", "1"), result.map { it.id })
    }

    @Test
    fun `countUnread is consistent with rendered list`() {
        // 3 raw rows on the same issue: 1 unread → after dedup, the
        // merged row should reflect the newest's `read` state, and
        // countUnread should be 1.
        val items = listOf(
            item(id = "1", issueId = "ISS-1", createdAt = "2026-06-28T10:00:00Z", read = true),
            item(id = "2", issueId = "ISS-1", createdAt = "2026-06-28T11:00:00Z", read = true),
            item(id = "3", issueId = "ISS-1", createdAt = "2026-06-28T12:00:00Z", read = false),
        )
        assertEquals(1, InboxDedup.countUnread(items))
    }

    @Test
    fun `countUnread on all-archived returns 0`() {
        val items = listOf(
            item(id = "1", issueId = "ISS-1", createdAt = "2026-06-28T10:00:00Z", read = false, archived = true),
            item(id = "2", issueId = "ISS-1", createdAt = "2026-06-28T11:00:00Z", read = false, archived = true),
        )
        assertEquals(0, InboxDedup.countUnread(items))
    }

    @Test
    fun `hasOtherWorkspaceUnread excludes current workspace`() {
        val summary = listOf(
            ai.multica.android.data.model.InboxWorkspaceUnread(workspaceId = "WS-1", count = 3),
            ai.multica.android.data.model.InboxWorkspaceUnread(workspaceId = "WS-2", count = 0),
        )
        // Current is WS-1 (3 unread). Others = [WS-2: 0]. None > 0. → false.
        assertEquals(false, InboxDedup.hasOtherWorkspaceUnread(summary, currentWsId = "WS-1"))
        // When current is WS-2 (0 unread). Others = [WS-1: 3]. → true.
        assertTrue(InboxDedup.hasOtherWorkspaceUnread(summary, currentWsId = "WS-2"))
    }

    // ---- helper ----

    private fun item(
        id: String,
        issueId: String? = null,
        createdAt: String,
        read: Boolean = false,
        archived: Boolean = false,
        commentId: String? = null,
    ): InboxItem {
        val details = commentId?.let {
            kotlinx.serialization.json.JsonObject(mapOf("comment_id" to kotlinx.serialization.json.JsonPrimitive(it)))
        }
        return InboxItem(
            id = id,
            workspaceId = "WS-1",
            recipientType = ai.multica.android.data.model.InboxRecipientType.MEMBER,
            recipientId = "U-1",
            type = ai.multica.android.data.model.InboxItemType.ISSUE_ASSIGNED,
            severity = ai.multica.android.data.model.InboxSeverity.INFO,
            issueId = issueId,
            title = "Test",
            body = null,
            issueStatus = null,
            read = read,
            archived = archived,
            createdAt = createdAt,
            actorType = null,
            actorId = null,
            details = details,
        )
    }
}
