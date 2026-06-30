package ai.multica.android.domain

import ai.multica.android.data.model.TimelineEntry
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Timeline coalescing — **MIRROR** of
 * `apps/mobile/lib/timeline-coalesce.ts::coalesceTimeline`.
 *
 * ## Why this matters
 *
 * The backend's `GET /api/issues/{id}/timeline` returns every event
 * (agent run starts, status changes, task progresses, comments,
 * reactions) as a separate row. The web app and iOS app merge
 * consecutive identical events from the same actor into a single
 * UI row — "agent X ran task 3 times" rather than 3 rows of
 * "agent X ran task". Skipping this on Android produces a wall of
 * redundant rows; the 2026-05-09 incident writeup explicitly
 * requires mirroring it.
 *
 * ## Rules
 *
 * - Two adjacent entries coalesce when:
 *   1. Same `action`, same `actor_type`, same `actor_id`, AND
 *   2. (a) `action` is in the always-coalesce set
 *          (`task_completed`, `task_failed`), OR
 *      (b) `created_at` gap ≤ 2 minutes
 * - `squad_leader_evaluated` is **never** coalesced.
 * - `comment`-type entries (where `type == "comment"`) are **never**
 *   coalesced — every comment is its own row.
 * - Activities whose `actor_type` or `actor_id` differ do not
 *   coalesce.
 *
 * Output preserves the order of the first occurrence of each
 * group; the merged entry keeps the **latest** `created_at` so the
 * timeline still scrolls to the freshest event.
 */
object TimelineCoalesce {

    private val ALWAYS_COALESCE = setOf("task_completed", "task_failed")
    private const val NEVER_COALESCE = "squad_leader_evaluated"
    private const val COALESCE_WINDOW_MS = 2L * 60L * 1000L

    /**
     * Run the canonical coalesce pipeline. Returns a list with the
     * same order as input, but consecutive mergeable events merged.
     */
    fun coalesce(entries: List<TimelineEntry>): List<TimelineEntry> {
        if (entries.isEmpty()) return entries
        val out = mutableListOf<TimelineEntry>()
        var i = 0
        while (i < entries.size) {
            val current = entries[i]
            // Comments never coalesce.
            if (current.type == "comment") {
                out.add(current)
                i++
                continue
            }
            // Always-merge actions coalesce forward as far as possible.
            // Otherwise only the very next matching event in the
            // 2-minute window is merged — we don't keep scanning
            // because the window is anchored to the prior event, not
            // to the original.
            val canStart = canCoalesce(current)
            if (!canStart) {
                out.add(current)
                i++
                continue
            }
            var j = i + 1
            val newest = current.createdAt
            var latestInstant = parseCreatedAt(newest)
            while (j < entries.size && canCoalesce(entries[j])) {
                if (!sameKey(current, entries[j])) break
                if (!ALWAYS_COALESCE.contains(current.action)) {
                    val jInstant = parseCreatedAt(entries[j].createdAt)
                    // entries[j] is later in time than latestInstant (since
                    // j>i and timeline is sorted ascending). The gap is
                    // jInstant - latestInstant, but the entries might not
                    // be perfectly sorted — use the absolute difference.
                    val gap = kotlin.math.abs(jInstant - latestInstant)
                    if (gap > COALESCE_WINDOW_MS) break
                }
                // advance latest
                val t = parseCreatedAt(entries[j].createdAt)
                if (t > latestInstant) latestInstant = t
                j++
            }
            if (j == i + 1) {
                out.add(current)
            } else {
                // Bump the merged entry's createdAt to the latest
                // sub-event so the scroll position still points at
                // the freshest action.
                out.add(current.copy(createdAt = formatCreatedAt(latestInstant)))
            }
            i = j
        }
        return out
    }

    private fun canCoalesce(entry: TimelineEntry): Boolean {
        if (entry.type == "comment") return false
        val action = entry.action ?: return false
        if (action == NEVER_COALESCE) return false
        return true
    }

    private fun sameKey(a: TimelineEntry, b: TimelineEntry): Boolean =
        a.action == b.action && a.actorType == b.actorType && a.actorId == b.actorId

    private fun parseCreatedAt(value: String): Long = try {
        Instant.parse(value).toEpochMilliseconds()
    } catch (e: Throwable) {
        0L
    }

    private fun formatCreatedAt(epochMs: Long): String =
        Instant.fromEpochMilliseconds(epochMs).toString()
}
