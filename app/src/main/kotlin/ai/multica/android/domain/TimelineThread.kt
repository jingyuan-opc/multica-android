package ai.multica.android.domain

import ai.multica.android.data.model.TimelineEntry
import ai.multica.android.data.model.TimelineRow

/**
 * Timeline thread builder — **MIRROR** of
 * `apps/mobile/lib/timeline-thread.ts::buildTimelineRows`.
 *
 * ## What it does
 *
     * Converts a flat `List<TimelineEntry>` (returned by
     * `GET /api/issues/{id}/timeline`) into a list of
     * [TimelineRow]s, where each row bundles a top-level entry
     * with all its descendant replies (chronologically flattened —
     * see [collectRepliesChrono]). The thread UI renders each row
     * as a single card.
 *
 * ## Why it matters
 *
 * Without this, a comment thread with 3 replies would either
 * render as 4 separate rows (losing the visual thread) or
 * vanish entirely if a reply's `parent_id` isn't in the loaded
 * batch (orphaned reply). The 2026-05-09 incident writeup
 * explicitly requires mirroring this.
 *
 * ## Rules
 *
 *  1. **Top-level** = entry with `parent_id == null` (and not a
 *     comment that points to a non-existent parent — see rule 3).
 *  2. **Reply** = entry with `parent_id != null` whose parent
 *     is somewhere in the input batch. Replies are attached to
 *     their root, in BFS order.
 *  3. **Orphan rescue**: a reply whose `parent_id` is not in the
 *     batch is promoted to top-level. Without this, replies
 *     silently disappear when the parent is filtered or not yet
 *     loaded.
 *  4. **Activity / status_change / progress_update / system**
 *     entries (i.e. non-comment type) are always top-level — they
 *     have no `parent_id`. The "replies" array is empty for them.
 */
object TimelineThread {

    fun build(entries: List<TimelineEntry>): List<TimelineRow> {
        if (entries.isEmpty()) return emptyList()

        val byId = entries.associateBy { it.id }
        val childrenOf = linkedMapOf<String, MutableList<TimelineEntry>>()
        val topLevel = mutableListOf<TimelineEntry>()

        for (entry in entries) {
            val parentId = entry.parentId
            if (parentId == null) {
                topLevel.add(entry)
            } else if (byId.containsKey(parentId)) {
                childrenOf.getOrPut(parentId) { mutableListOf() }.add(entry)
            } else {
                // Orphan rescue — promote to top-level.
                topLevel.add(entry)
            }
        }

        return topLevel.map { root ->
            val replies = collectRepliesChrono(root.id, childrenOf)
            TimelineRow(root = root, replies = replies)
        }
    }

    /**
     * Walk the parent_id graph rooted at [rootId] and return every descendant
     * in CHRONOLOGICAL order (created_at ASC, id tie-break). Mirror of the web
     * `collectThreadReplies` (packages/views/issues/components/thread-utils.ts).
     *
     * Chronological, not depth-first: agent replies are forced to nest under
     * the comment that triggered them, so a depth-first walk would let a slow
     * agent's late reply render BEFORE earlier sibling replies (#3691). The
     * server's --thread output the agent reads is already chronological; this
     * keeps the UI on the same order.
     */
    private fun collectRepliesChrono(
        rootId: String,
        childrenOf: Map<String, List<TimelineEntry>>,
    ): List<TimelineEntry> {
        val out = mutableListOf<TimelineEntry>()
        fun walk(id: String) {
            val direct = childrenOf[id].orEmpty()
            for (child in direct) {
                out.add(child)
                walk(child.id)
            }
        }
        walk(rootId)
        return out.sortedWith(
            compareBy({ it.createdAt }, { it.id }),
        )
    }
}

/**
 * A thread's resolution, derived purely from `resolved_at`. Mirror of the web
 * `deriveThreadResolution` (packages/views/issues/components/thread-utils.ts).
 *
 * Two user actions write the same field:
 *  - "Resolve thread" sets resolved_at on the ROOT → whole thread folds.
 *  - "Resolve thread with comment" sets resolved_at on a REPLY → that reply is
 *    the resolution; the others fold around it.
 *
 * The derivation is total so the UI never shows two resolutions and never
 * crashes on any combination (older / concurrent writes can resolve more than
 * one): root wins; otherwise the reply with the latest resolved_at is THE
 * resolution.
 */
sealed interface ThreadResolution {
    data object None : ThreadResolution
    data object Root : ThreadResolution
    data class Reply(val resolutionId: String) : ThreadResolution
}

fun deriveThreadResolution(
    root: TimelineEntry,
    replies: List<TimelineEntry>,
): ThreadResolution {
    if (root.resolvedAt != null) return ThreadResolution.Root
    var chosen: TimelineEntry? = null
    for (reply in replies) {
        val r = reply.resolvedAt ?: continue
        if (chosen == null || r > chosen.resolvedAt!!) chosen = reply
    }
    return chosen?.let { ThreadResolution.Reply(it.id) } ?: ThreadResolution.None
}
