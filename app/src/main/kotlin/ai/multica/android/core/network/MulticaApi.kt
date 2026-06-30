package ai.multica.android.core.network

import ai.multica.android.data.dto.*
import ai.multica.android.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the Multica backend.
 *
 * Conventions:
 * - All workspace-scoped routes expect `X-Workspace-Slug` to be set
 *   by AuthInterceptor (read from WorkspaceStore).
 * - All routes use Bearer auth (also set by AuthInterceptor).
 * - Routes that return a bare JSON array use List<...> as the return
 *   type; those returning an object use the corresponding *Response type.
 * - Endpoints that return 204 use Response<Unit>.
 */
interface MulticaApi {

    // -------- Auth (no auth, no workspace header) --------

    @POST("auth/send-code")
    suspend fun sendCode(@Body body: SendCodeRequest): Response<SendCodeResponse>

    @POST("auth/verify-code")
    suspend fun verifyCode(@Body body: VerifyCodeRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<LogoutResponse>

    // -------- Me --------

    @GET("api/me")
    suspend fun getMe(): Response<User>

    // -------- Workspaces --------

    @GET("api/workspaces")
    suspend fun listWorkspaces(): Response<List<Workspace>>

    // -------- Inbox --------

    /** Returns a bare JSON array — the spec response shape is `InboxItem[]`. */
    @GET("api/inbox")
    suspend fun listInbox(): Response<List<InboxItem>>

    @GET("api/inbox/unread/count")
    suspend fun countUnreadInbox(): Response<InboxUnreadCountResponse>

    /** Cross-workspace summary — workspace header not required. */
    @GET("api/inbox/unread-summary")
    suspend fun inboxUnreadSummary(): Response<List<InboxWorkspaceUnread>>

    @POST("api/inbox/{id}/read")
    suspend fun markInboxRead(@Path("id") id: String): Response<InboxItem>

    @POST("api/inbox/{id}/archive")
    suspend fun archiveInboxItem(@Path("id") id: String): Response<InboxItem>

    @POST("api/inbox/mark-all-read")
    suspend fun markAllInboxRead(): Response<ArchiveCountResponse>

    @POST("api/inbox/archive-all-read")
    suspend fun archiveAllReadInbox(): Response<ArchiveCountResponse>

    // -------- Projects --------

    @GET("api/projects")
    suspend fun listProjects(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
    ): Response<ListProjectsResponse>

    @GET("api/projects/{id}")
    suspend fun getProject(@Path("id") id: String): Response<Project>

    @POST("api/projects")
    suspend fun createProject(@Body body: ai.multica.android.data.dto.CreateProjectRequest): Response<Project>

    @GET("api/workspaces/{id}/members")
    suspend fun listMembers(@Path("id") workspaceId: String): Response<List<MemberWithUser>>

    @GET("api/agents")
    suspend fun listAgents(@Query("include_archived") includeArchived: Boolean = false): Response<List<Agent>>

    @GET("api/squads")
    suspend fun listSquads(): Response<List<Squad>>

    // -------- Issues --------

    @GET("api/issues")
    suspend fun listIssues(
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("assignee_id") assigneeId: String? = null,
        @Query("creator_id") creatorId: String? = null,
        @Query("project_id") projectId: String? = null,
        @Query("involves_user_id") involvesUserId: String? = null,
        @Query("open_only") openOnly: Boolean? = null,
        @Query("scheduled") scheduled: Boolean? = null,
        @Query("date_field") dateField: String? = null,
        @Query("date_start") dateStart: String? = null,
        @Query("date_end") dateEnd: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null,
    ): Response<ListIssuesResponse>

    @GET("api/issues/search")
    suspend fun searchIssues(
        @Query("q") q: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<SearchIssuesResponse>

    @GET("api/issues/grouped")
    suspend fun listGroupedIssues(
        @Query("group_by") groupBy: String = "assignee",
        @Query("project_id") projectId: String? = null,
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("assignee_id") assigneeId: String? = null,
    ): Response<GroupedIssuesResponse>

    @GET("api/issues/{id}")
    suspend fun getIssue(@Path("id") id: String): Response<Issue>

    @POST("api/issues")
    suspend fun createIssue(@Body body: ai.multica.android.data.dto.CreateIssueRequest): Response<Issue>

    @PUT("api/issues/{id}")
    suspend fun updateIssue(
        @Path("id") id: String,
        @Body body: UpdateIssueRequest,
    ): Response<Issue>

    // -------- Timeline / Comments --------

    @GET("api/issues/{id}/timeline")
    suspend fun listTimeline(@Path("id") issueId: String): Response<List<TimelineEntry>>

    @POST("api/issues/{id}/comments")
    suspend fun createComment(
        @Path("id") issueId: String,
        @Body body: CreateCommentRequest,
    ): Response<Comment>

    @PUT("api/comments/{id}")
    suspend fun updateComment(
        @Path("id") id: String,
        @Body body: UpdateCommentRequest,
    ): Response<Comment>

    @DELETE("api/comments/{id}")
    suspend fun deleteComment(@Path("id") id: String): Response<Unit>

    @POST("api/comments/{id}/resolve")
    suspend fun resolveComment(@Path("id") id: String): Response<Comment>

    @DELETE("api/comments/{id}/resolve")
    suspend fun unresolveComment(@Path("id") id: String): Response<Comment>

    @POST("api/comments/{id}/reactions")
    suspend fun addCommentReaction(
        @Path("id") commentId: String,
        @Body body: ReactionRequest,
    ): Response<Reaction>

    @DELETE("api/comments/{id}/reactions")
    suspend fun removeCommentReaction(
        @Path("id") commentId: String,
        @Body body: ReactionRequest,
    ): Response<Unit>
}
