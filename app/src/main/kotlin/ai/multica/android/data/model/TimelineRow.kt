package ai.multica.android.data.model

import kotlinx.serialization.Serializable

/**
 * A single row in the rendered comment thread.
 * Built from a flat list of [TimelineEntry]s by `domain/TimelineThread.kt`.
 *
 * - [root] is a top-level entry (a comment OR a non-comment activity).
 * - [replies] are flattened descendants of [root], BFS order.
 *   Replies preserve their [TimelineEntry.parentId] pointer for UI
 *   attribution but the rendering layer treats them as one bubble.
 */
@Serializable
data class TimelineRow(
    val root: TimelineEntry,
    val replies: List<TimelineEntry> = emptyList(),
)
