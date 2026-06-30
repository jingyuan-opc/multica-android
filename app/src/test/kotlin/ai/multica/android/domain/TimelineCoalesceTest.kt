package ai.multica.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ai.multica.android.data.model.TimelineEntry

class TimelineCoalesceTest {

    @Test
    fun `consecutive task_completed from same actor merge`() {
        val a = entry(action = "task_completed", actor = "A1", createdAt = "2026-06-28T10:00:00Z")
        val b = entry(action = "task_completed", actor = "A1", createdAt = "2026-06-28T10:00:30Z")
        val result = TimelineCoalesce.coalesce(listOf(a, b))
        assertEquals(1, result.size)
    }

    @Test
    fun `task_completed 10 minutes apart still merges (always-merge rule)`() {
        val a = entry(action = "task_completed", actor = "A1", createdAt = "2026-06-28T10:00:00Z")
        val b = entry(action = "task_completed", actor = "A1", createdAt = "2026-06-28T10:10:00Z")
        val result = TimelineCoalesce.coalesce(listOf(a, b))
        assertEquals(1, result.size)
    }

    @Test
    fun `consecutive agent_run within 2min merges`() {
        val a = entry(action = "agent_run", actor = "A1", createdAt = "2026-06-28T10:00:00Z")
        val b = entry(action = "agent_run", actor = "A1", createdAt = "2026-06-28T10:01:00Z")
        val result = TimelineCoalesce.coalesce(listOf(a, b))
        assertEquals(1, result.size)
    }

    @Test
    fun `consecutive agent_run 5min apart does not merge`() {
        val a = entry(action = "agent_run", actor = "A1", createdAt = "2026-06-28T10:00:00Z")
        val b = entry(action = "agent_run", actor = "A1", createdAt = "2026-06-28T10:05:00Z")
        val result = TimelineCoalesce.coalesce(listOf(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun `different actors do not merge`() {
        val a = entry(action = "agent_run", actor = "A1", createdAt = "2026-06-28T10:00:00Z")
        val b = entry(action = "agent_run", actor = "A2", createdAt = "2026-06-28T10:00:30Z")
        val result = TimelineCoalesce.coalesce(listOf(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun `squad_leader_evaluated never coalesces`() {
        val a = entry(action = "squad_leader_evaluated", actor = "A1", createdAt = "2026-06-28T10:00:00Z")
        val b = entry(action = "squad_leader_evaluated", actor = "A1", createdAt = "2026-06-28T10:00:30Z")
        val result = TimelineCoalesce.coalesce(listOf(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun `comments never coalesce`() {
        val a = entry(type = "comment", content = "first", createdAt = "2026-06-28T10:00:00Z")
        val b = entry(type = "comment", content = "second", createdAt = "2026-06-28T10:00:30Z")
        val result = TimelineCoalesce.coalesce(listOf(a, b))
        assertEquals(2, result.size)
    }

    private fun entry(
        type: String = "activity",
        action: String? = "agent_run",
        actor: String = "A1",
        createdAt: String,
        content: String? = null,
    ): TimelineEntry = TimelineEntry(
        type = type,
        id = "$actor-$action-$createdAt",
        actorType = ai.multica.android.data.model.CommentAuthorType.AGENT,
        actorId = actor,
        content = content,
        action = action,
        createdAt = createdAt,
    )
}
