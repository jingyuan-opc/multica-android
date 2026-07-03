package ai.multica.android.ui.issues

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.commentCollapseStore by preferencesDataStore(name = "comment_collapse_store")

/**
 * Tracks which comment threads are collapsed, keyed by issue id.
 *
 * Mirror of the web `comment-collapse-store.ts` (Zustand + persist):
 * only collapsed root comment ids are stored — expanded is the default
 * state. Workspace-scoping is implicit: comment ids are server-issued
 * UUIDs unique across workspaces, so a single store keyed by id is safe.
 *
 * A "thread" = a root comment + all its replies. Collapsing folds the
 * whole thread into a single header row (avatar + author + time + 80-char
 * content preview + "N replies"). The toggle is a chevron on each thread
 * header; there is no per-reply collapse and no global collapse-all.
 */
@Singleton
class CommentCollapseStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Load the set of collapsed root comment ids for [issueId].
     * Call once when a ViewModel hydrates (we don't need a hot Flow —
     * the ViewModel already owns the in-memory Set and pushes updates
     * via [setCollapsed]).
     */
    suspend fun collapsedIds(issueId: String): Set<String> {
        val raw = context.commentCollapseStore.data.first()[key(issueId)].orEmpty()
        return raw.toSet()
    }

    /** Replace the persisted collapsed-id set for [issueId]. */
    suspend fun setCollapsed(issueId: String, ids: Set<String>) {
        context.commentCollapseStore.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(key(issueId))
            } else {
                prefs[key(issueId)] = ids
            }
        }
    }

    private fun key(issueId: String) = stringSetPreferencesKey("collapsed:$issueId")
}
