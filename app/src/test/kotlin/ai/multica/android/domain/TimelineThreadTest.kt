package ai.multica.android.domain

import ai.multica.android.data.model.CommentAuthorType
import ai.multica.android.data.model.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineThreadTest {

    @Test
    fun `groups top-level comments with their replies`() {
        val c1 = comment(id = "c1", parentId = null, createdAt = "2026-06-28T10:00:00Z")
        val c1r1 = comment(id = "c1r1", parentId = "c1", createdAt = "2026-06-28T10:01:00Z")
        val c1r2 = comment(id = "c1r2", parentId = "c1", createdAt = "2026-06-28T10:02:00Z")
        val c2 = comment(id = "c2", parentId = null, createdAt = "2026-06-28T10:03:00Z")

        val rows = TimelineThread.build(listOf(c1, c1r1, c1r2, c2))
        assertEquals(2, rows.size)

        val row1 = rows[0]
        assertEquals("c1", row1.root.id)
        assertEquals(listOf("c1r1", "c1r2"), row1.replies.map { it.id })

        val row2 = rows[1]
        assertEquals("c2", row2.root.id)
        assertTrue(row2.replies.isEmpty())
    }

    @Test
    fun `orphan replies are promoted to top-level`() {
        val c1 = comment(id = "c1", parentId = null, createdAt = "2026-06-28T10:00:00Z")
        val orphan = comment(id = "orphan", parentId = "c-does-not-exist", createdAt = "2026-06-28T10:01:00Z")

        val rows = TimelineThread.build(listOf(c1, orphan))
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.root.id == "orphan" })
    }

    @Test
    fun `flattens reply chains into single root row`() {
        val c1 = comment(id = "c1", parentId = null, createdAt = "2026-06-28T10:00:00Z")
        val c1r1 = comment(id = "c1r1", parentId = "c1", createdAt = "2026-06-28T10:01:00Z")
        val c1r1r1 = comment(id = "c1r1r1", parentId = "c1r1", createdAt = "2026-06-28T10:02:00Z")

        val rows = TimelineThread.build(listOf(c1, c1r1, c1r1r1))
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].replies.size)
    }

    @Test
    fun `non-comment entries are always top-level`() {
        val activity = activity(id = "a1", createdAt = "2026-06-28T10:00:00Z")
        val status = statusChange(id = "s1", createdAt = "2026-06-28T10:01:00Z")
        val rows = TimelineThread.build(listOf(activity, status))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.replies.isEmpty() })
    }

    @Test
    fun `replies are sorted chronologically regardless of input order`() {
        // Mirrors web collectThreadReplies: chronological (created_at ASC),
        // NOT depth-first. A late agent reply must not jump ahead of earlier
        // sibling replies (#3691).
        val c1 = comment(id = "c1", parentId = null, createdAt = "2026-06-28T10:00:00Z")
        val c1r2 = comment(id = "c1r2", parentId = "c1", createdAt = "2026-06-28T10:02:00Z")
        val c1r1 = comment(id = "c1r1", parentId = "c1", createdAt = "2026-06-28T10:01:00Z")
        val c1r3 = comment(id = "c1r3", parentId = "c1", createdAt = "2026-06-28T10:03:00Z")

        val rows = TimelineThread.build(listOf(c1, c1r2, c1r1, c1r3))
        assertEquals(1, rows.size)
        assertEquals(
            listOf("c1r1", "c1r2", "c1r3"),
            rows[0].replies.map { it.id },
        )
    }

    @Test
    fun `nested replies are flattened and sorted chronologically`() {
        val c1 = comment(id = "c1", parentId = null, createdAt = "2026-06-28T10:00:00Z")
        val c1r1 = comment(id = "c1r1", parentId = "c1", createdAt = "2026-06-28T10:01:00Z")
        val c1r1r1 = comment(id = "c1r1r1", parentId = "c1r1", createdAt = "2026-06-28T09:30:00Z")
        // Even though c1r1r1 is nested deeper, its earlier created_at puts it first.
        val rows = TimelineThread.build(listOf(c1, c1r1, c1r1r1))
        assertEquals(listOf("c1r1r1", "c1r1"), rows[0].replies.map { it.id })
    }

    // ---- ThreadResolution derivation ----

    @Test
    fun `root resolved_at yields Root resolution`() {
        val root = comment(id = "c1", parentId = null, createdAt = "t").copy(resolvedAt = "2026-06-28T11:00:00Z")
        assertEquals(ThreadResolution.Root, deriveThreadResolution(root, emptyList()))
    }

    @Test
    fun `latest resolved reply wins over earlier ones`() {
        val root = comment(id = "c1", parentId = null, createdAt = "t")
        val r1 = comment(id = "r1", parentId = "c1", createdAt = "t1").copy(resolvedAt = "2026-06-28T11:00:00Z")
        val r2 = comment(id = "r2", parentId = "c1", createdAt = "t2").copy(resolvedAt = "2026-06-28T12:00:00Z")
        assertEquals(
            ThreadResolution.Reply("r2"),
            deriveThreadResolution(root, listOf(r1, r2)),
        )
    }

    @Test
    fun `root resolution wins over reply resolution`() {
        val root = comment(id = "c1", parentId = null, createdAt = "t").copy(resolvedAt = "2026-06-28T10:00:00Z")
        val r1 = comment(id = "r1", parentId = "c1", createdAt = "t1").copy(resolvedAt = "2026-06-28T12:00:00Z")
        assertEquals(ThreadResolution.Root, deriveThreadResolution(root, listOf(r1)))
    }

    @Test
    fun `no resolution when nothing resolved`() {
        val root = comment(id = "c1", parentId = null, createdAt = "t")
        val r1 = comment(id = "r1", parentId = "c1", createdAt = "t1")
        assertEquals(ThreadResolution.None, deriveThreadResolution(root, listOf(r1)))
    }

    // ---- helpers ----

    private fun comment(
        id: String,
        parentId: String?,
        createdAt: String,
    ): TimelineEntry = TimelineEntry(
        type = "comment",
        id = id,
        actorType = CommentAuthorType.MEMBER,
        actorId = "user-1",
        content = "test",
        parentId = parentId,
        createdAt = createdAt,
    )

    private fun activity(id: String, createdAt: String): TimelineEntry = TimelineEntry(
        type = "activity",
        id = id,
        actorType = CommentAuthorType.AGENT,
        actorId = "agent-1",
        action = "agent_run",
        createdAt = createdAt,
    )

    private fun statusChange(id: String, createdAt: String): TimelineEntry = TimelineEntry(
        type = "status_change",
        id = id,
        actorType = CommentAuthorType.MEMBER,
        actorId = "user-1",
        action = "status_changed",
        createdAt = createdAt,
    )
}
