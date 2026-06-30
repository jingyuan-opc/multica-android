package ai.multica.android.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.workspacePrefStore by preferencesDataStore(name = "workspace_pref_store")

/**
 * Persists the user's last-selected workspace across app launches.
 * The server scopes most requests to a single workspace, so we
 * must know which one to use BEFORE the first API call fires.
 *
 * The InboxViewModel / ProjectsViewModel / IssuesViewModel all
 * `init { refresh() }` — without this pref, the very first
 * `loadWorkspaces` round-trip would race with their first
 * `listInbox` / `listProjects` / `listIssues` calls, which then
 * go out without an `X-Workspace-Slug` header and the server
 * rejects with "workspace_id or workspace_slug is required".
 *
 * On launch, [WorkspacePreference.lastActive] is read and pushed
 * into [WorkspaceStore] synchronously, so AuthInterceptor adds
 * the header on the very first request.
 */
@Singleton
class WorkspacePreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val lastActiveFlow: Flow<Pair<String?, String?>> = context.workspacePrefStore.data
        .map { prefs ->
            prefs[KEY_SLUG] to prefs[KEY_ID]
        }

    suspend fun getLastActive(): Pair<String?, String?> =
        context.workspacePrefStore.data.first().let { prefs ->
            prefs[KEY_SLUG] to prefs[KEY_ID]
        }

    suspend fun setLastActive(slug: String?, id: String?) {
        context.workspacePrefStore.edit { prefs ->
            if (slug == null) {
                prefs.remove(KEY_SLUG)
                prefs.remove(KEY_ID)
            } else {
                prefs[KEY_SLUG] = slug
                if (id != null) prefs[KEY_ID] = id
            }
        }
    }

    private companion object {
        val KEY_SLUG = stringPreferencesKey("last_active_slug")
        val KEY_ID = stringPreferencesKey("last_active_id")
    }
}
