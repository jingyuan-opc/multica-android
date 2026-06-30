# Multica Android

Native Android client for [Multica](https://multica.ai) — the open-source managed agents platform. Built with Kotlin + Jetpack Compose + Material 3, Hilt, and OkHttp.

This is a **read-mostly MVP** focused on the workspace / project / issue / inbox information flow plus comment posting, reactions, and search. Agent task execution, autopilots, skills, runtimes, and squads are out of scope.

## Quick start

```bash
# 1. Build the debug APK
./gradlew :app:assembleDebug

# 2. Install on a connected device or emulator (API 26+)
./gradlew :app:installDebug

# 3. Run parity tests
./gradlew :app:testDebugUnitTest
```

The first launch uses Multica Cloud (`https://multica.ai`) by default. To point at a self-hosted server, open **Settings → Server** in the app after install.

## Customizing the default backend

Add a line to `local.properties` to bake a different default URL into the APK:

```properties
multica.server.url=https://staging.multica.example.com
```

Then re-run `./gradlew :app:assembleDebug`. The new URL is the initial value of the in-app server picker.

## Features

- 🔐 **Email + 6-digit code auth** with secure token storage (EncryptedSharedPreferences)
- 🏢 **Multi-workspace** with top-bar switcher + cross-workspace unread dot
- 📥 **Inbox** with Unread/All filter, mark read, archive, batch operations, WS-driven live updates
- 📁 **Project list + detail** with description, issue counts, project-scoped issue list
- 🐛 **Issue board** (6 columns, drag-friendly) + **list view** with status / priority filters
- 🔍 **Issue search** with debounced `/api/issues/search` calls and matched-snippet display
- 💬 **Issue detail** with full timeline (comments + activity + status changes), reply, resolve, optimistic posting
- 😀 **Emoji reactions** with grouped-by-emoji display and toggle-to-remove
- 🔴 **WebSocket realtime** (Tier 2): inbox + comment events stream in 1-2s
- 🎨 **Light / dark / system** theme with persistent preference
- 🌐 **Cloud + self-hosted** server switcher in Settings
- 🚀 **App shortcuts**: long-press launcher icon → Inbox / Issues / Projects
- 🔗 **Deep links**: `multica://issue/{id}`, `https://multica.ai/issue/{id}`
- ✋ **Pull-to-refresh** on every list
- 🛡️ **Behavioral parity** with the web/iOS apps — three dedicated tests guard the algorithm fidelity

## Architecture

Single-Activity Compose app. Hilt for DI. Coroutines + Flow. Retrofit + OkHttp + kotlinx-serialization. Material 3 with a custom palette derived from multica's brand `oklch` tokens (see `core/theme/OklchConverter.kt`).

```
app/src/main/kotlin/ai/multica/android/
├── core/            Auth (TokenStore, ServerUrlStore, AuthInterceptor)
│                    Network (MulticaApi, ApiResult, NetworkFactory)
│                    Theme (oklch converter, color, type, theme preference)
│                    DI modules
├── data/            Models (mirror TS types 1:1)
│                    DTOs (request/response)
│                    Repositories (Auth, Workspace, Inbox, Project, Issue)
├── domain/          ★ Behavioral parity (3 modules + tests)
│                    InboxDedup — 1:1 with packages/core/inbox/queries.ts
│                    TimelineCoalesce — 1:1 with apps/mobile/lib/timeline-coalesce.ts
│                    TimelineThread — 1:1 with apps/mobile/lib/timeline-thread.ts
├── ui/              Compose screens + ViewModels
│                    login, home, inbox, projects, issues, comments, settings, components
├── realtime/        WsClient, RealtimeManager, AppStateObserver
├── MulticaApp.kt    Application class (@HiltAndroidApp)
├── MainActivity.kt  Single-Activity host + nav + deep link routing
└── BootstrapViewModel.kt  Auth-gate (Login vs Home)
```

## Behavioral parity (the things that MUST match web / iOS)

Multica's `apps/mobile/CLAUDE.md` documents real production incidents where skipping client-side dedup produced different counts and lists across clients. Three parity files in `domain/` must stay in lockstep with the TypeScript source of truth:

| Kotlin | TypeScript | What it does |
|---|---|---|
| `InboxDedup.kt` | `packages/core/inbox/queries.ts::deduplicateInboxItems` | Groups inbox rows by `issue_id`, drops archived, inherits `comment_id` from siblings, sorts by `created_at` desc. Used for both list rendering and unread badge. |
| `TimelineCoalesce.kt` | `apps/mobile/lib/timeline-coalesce.ts` | Merges consecutive identical activity rows from the same actor within 2 minutes (or always for `task_completed` / `task_failed`). Comments never coalesce. |
| `TimelineThread.kt` | `apps/mobile/lib/timeline-thread.ts::buildTimelineRows` | Bundles each top-level entry with its flattened BFS reply chain. Orphans (parent not in batch) are promoted to top-level so they never silently disappear. |

Each has a unit test in `app/src/test/kotlin/...` that locks the behavior. Run them with:

```bash
./gradlew :app:testDebugUnitTest
```

## API endpoints (MVP)

All workspace-scoped requests use `Authorization: Bearer <jwt>` and `X-Workspace-Slug: <slug>`. The interceptor in `core/auth/AuthInterceptor.kt` adds both automatically.

**Auth**: `POST /auth/send-code`, `POST /auth/verify-code`, `POST /auth/logout`
**Me**: `GET /api/me`
**Workspaces**: `GET /api/workspaces`
**Inbox**: `GET /api/inbox`, `GET /api/inbox/unread/count`, `GET /api/inbox/unread-summary`, `POST /api/inbox/{id}/read`, `POST /api/inbox/{id}/archive`, `POST /api/inbox/mark-all-read`, `POST /api/inbox/archive-all-read`
**Projects**: `GET /api/projects?status=&priority=`, `GET /api/projects/{id}`
**Issues**: `GET /api/issues?...`, `GET /api/issues/search?q=`, `GET /api/issues/grouped`, `GET /api/issues/{id}`, `PUT /api/issues/{id}`
**Comments**: `GET /api/issues/{id}/timeline`, `POST /api/issues/{id}/comments`, `PUT/DELETE /api/comments/{id}`, `POST/DELETE /api/comments/{id}/reactions`, `POST/DELETE /api/comments/{id}/resolve`

## Realtime

Tier 2 WebSocket (OkHttp). First-frame auth, automatic workspace + user scope subscription, exponential backoff with full jitter, 10s `auth_ack` deadline, AppState-aware pause/resume. We listen to inbox + comment + issue events.

## Roadmap (post-MVP)

- Issue create form (UI is in place, just needs a screen)
- Attachment upload in comment composer
- FCM push notifications
- Localization (currently English only)
- Offline cache (Room) — explicitly out of MVP scope per the design plan

## License

Same as upstream Multica: see [LICENSE](https://github.com/multica-ai/multica/blob/main/LICENSE).
