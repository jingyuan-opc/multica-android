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
 * with all its descendant replies (BFS-flattened). The mobile
 * thread UI renders each row as a single bubble.
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
            val replies = collectRepliesBfs(root.id, childrenOf)
            TimelineRow(root = root, replies = replies)
        }
    }

    private fun collectRepliesBfs(
        rootId: String,
        childrenOf: Map<String, List<TimelineEntry>>,
    ): List<TimelineEntry> {
        val out = mutableListOf<TimelineEntry>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            val direct = childrenOf[parent].orEmpty()
            for (entry in direct) {
                out.add(entry)
                // A reply's replies (rare in multica but allowed by
                // the schema) are flattened into the same row at
                // BFS depth.
                queue.add(entry.id)
            }
        }
        return out
    }
}
