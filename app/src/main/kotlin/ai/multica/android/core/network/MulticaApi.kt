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

    @GET("api/workspaces/{id}")
    suspend fun getWorkspace(@Path("id") id: String): Response<Workspace>

    @POST("api/workspaces")
    suspend fun createWorkspace(@Body body: CreateWorkspaceRequest): Response<Workspace>

    @PATCH("api/workspaces/{id}")
    suspend fun updateWorkspace(@Path("id") id: String, @Body body: UpdateWorkspaceRequest): Response<Workspace>

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

    @PUT("api/projects/{id}")
    suspend fun updateProject(@Path("id") id: String, @Body body: UpdateProjectRequest): Response<Project>

    @DELETE("api/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String): Response<Unit>

    @GET("api/projects/search")
    suspend fun searchProjects(
        @Query("q") q: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("include_closed") includeClosed: Boolean? = null,
    ): Response<SearchProjectsResponse>

    @GET("api/workspaces/{id}/members")
    suspend fun listMembers(@Path("id") workspaceId: String): Response<List<MemberWithUser>>

    @PATCH("api/workspaces/{workspaceId}/members/{memberId}")
    suspend fun updateMember(
        @Path("workspaceId") workspaceId: String,
        @Path("memberId") memberId: String,
        @Body body: UpdateMemberRequest,
    ): Response<MemberWithUser>

    @DELETE("api/workspaces/{workspaceId}/members/{memberId}")
    suspend fun deleteMember(
        @Path("workspaceId") workspaceId: String,
        @Path("memberId") memberId: String,
    ): Response<Unit>

    @GET("api/invitations")
    suspend fun listMyInvitations(): Response<List<Invitation>>

    @POST("api/invitations/{id}/accept")
    suspend fun acceptInvitation(@Path("id") id: String): Response<MemberWithUser>

    @POST("api/invitations/{id}/decline")
    suspend fun declineInvitation(@Path("id") id: String): Response<Unit>

    // -------- Agents --------

    @GET("api/agents")
    suspend fun listAgents(@Query("include_archived") includeArchived: Boolean = false): Response<List<Agent>>

    @GET("api/agents/{id}")
    suspend fun getAgent(@Path("id") id: String): Response<Agent>

    @POST("api/agents")
    suspend fun createAgent(@Body body: CreateAgentRequest): Response<Agent>

    @PUT("api/agents/{id}")
    suspend fun updateAgent(@Path("id") id: String, @Body body: UpdateAgentRequest): Response<Agent>

    @POST("api/agents/{id}/archive")
    suspend fun archiveAgent(@Path("id") id: String): Response<Agent>

    @POST("api/agents/{id}/restore")
    suspend fun restoreAgent(@Path("id") id: String): Response<Agent>

    @POST("api/agents/{id}/cancel-tasks")
    suspend fun cancelAgentTasks(@Path("id") id: String): Response<CancelledCountResponse>

    // -------- Squads --------

    @GET("api/squads")
    suspend fun listSquads(): Response<List<Squad>>

    @GET("api/squads/{id}")
    suspend fun getSquad(@Path("id") id: String): Response<Squad>

    @POST("api/squads")
    suspend fun createSquad(@Body body: CreateSquadRequest): Response<Squad>

    @PUT("api/squads/{id}")
    suspend fun updateSquad(@Path("id") id: String, @Body body: UpdateSquadRequest): Response<Squad>

    @DELETE("api/squads/{id}")
    suspend fun deleteSquad(@Path("id") id: String): Response<Unit>

    @GET("api/squads/{id}/members")
    suspend fun listSquadMembers(@Path("id") id: String): Response<List<SquadMember>>

    @POST("api/squads/{id}/members")
    suspend fun addSquadMember(@Path("id") id: String, @Body body: AddSquadMemberRequest): Response<SquadMember>

    /** DELETE with JSON body — uses @HTTP for explicit body support. */
    @HTTP(method = "DELETE", path = "api/squads/{id}/members", hasBody = true)
    suspend fun removeSquadMember(@Path("id") id: String, @Body body: RemoveSquadMemberRequest): Response<Unit>

    @PATCH("api/squads/{id}/members/role")
    suspend fun updateSquadMemberRole(@Path("id") id: String, @Body body: UpdateSquadMemberRoleRequest): Response<SquadMember>

    @GET("api/squads/{id}/members/status")
    suspend fun getSquadMemberStatus(@Path("id") id: String): Response<SquadMemberStatusListResponse>

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

    @DELETE("api/issues/{id}")
    suspend fun deleteIssue(@Path("id") id: String): Response<Unit>

    @POST("api/issues/quick-create")
    suspend fun quickCreateIssue(@Body body: QuickCreateIssueRequest): Response<QuickCreateIssueResponse>

    @POST("api/issues/batch-update")
    suspend fun batchUpdateIssues(@Body body: BatchUpdateIssuesRequest): Response<BatchUpdateIssuesResponse>

    @POST("api/issues/batch-delete")
    suspend fun batchDeleteIssues(@Body body: BatchDeleteIssuesRequest): Response<BatchDeleteIssuesResponse>

    @GET("api/issues/{id}/children")
    suspend fun listChildIssues(@Path("id") id: String): Response<ChildIssuesResponse>

    @GET("api/issues/child-progress")
    suspend fun getChildIssueProgress(): Response<ChildIssueProgressResponse>

    // -------- Issue reactions --------

    @POST("api/issues/{id}/reactions")
    suspend fun addIssueReaction(@Path("id") id: String, @Body body: ReactionRequest): Response<IssueReaction>

    @HTTP(method = "DELETE", path = "api/issues/{id}/reactions", hasBody = true)
    suspend fun removeIssueReaction(@Path("id") id: String, @Body body: ReactionRequest): Response<Unit>

    // -------- Issue subscribers --------

    @GET("api/issues/{id}/subscribers")
    suspend fun listIssueSubscribers(@Path("id") id: String): Response<List<IssueSubscriber>>

    @POST("api/issues/{id}/subscribe")
    suspend fun subscribeToIssue(@Path("id") id: String, @Body body: SubscribeRequest): Response<Unit>

    @POST("api/issues/{id}/unsubscribe")
    suspend fun unsubscribeFromIssue(@Path("id") id: String, @Body body: SubscribeRequest): Response<Unit>

    // -------- Issue labels --------

    @GET("api/issues/{issueId}/labels")
    suspend fun listLabelsForIssue(@Path("issueId") issueId: String): Response<IssueLabelsResponse>

    @POST("api/issues/{issueId}/labels")
    suspend fun attachLabel(@Path("issueId") issueId: String, @Body body: AttachLabelRequest): Response<IssueLabelsResponse>

    @DELETE("api/issues/{issueId}/labels/{labelId}")
    suspend fun detachLabel(
        @Path("issueId") issueId: String,
        @Path("labelId") labelId: String,
    ): Response<IssueLabelsResponse>

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

    // -------- Labels (workspace-scoped CRUD) --------

    @GET("api/labels")
    suspend fun listLabels(): Response<ListLabelsResponse>

    @GET("api/labels/{id}")
    suspend fun getLabel(@Path("id") id: String): Response<Label>

    @POST("api/labels")
    suspend fun createLabel(@Body body: CreateLabelRequest): Response<Label>

    @PUT("api/labels/{id}")
    suspend fun updateLabel(@Path("id") id: String, @Body body: UpdateLabelRequest): Response<Label>

    @DELETE("api/labels/{id}")
    suspend fun deleteLabel(@Path("id") id: String): Response<Unit>

    // -------- Pins --------

    @GET("api/pins")
    suspend fun listPins(): Response<List<PinnedItem>>

    @POST("api/pins")
    suspend fun createPin(@Body body: CreatePinRequest): Response<PinnedItem>

    @DELETE("api/pins/{itemType}/{itemId}")
    suspend fun deletePin(
        @Path("itemType") itemType: String,
        @Path("itemId") itemId: String,
    ): Response<Unit>

    @PUT("api/pins/reorder")
    suspend fun reorderPins(@Body body: ReorderPinsRequest): Response<Unit>

    // -------- Autopilots --------

    @GET("api/autopilots")
    suspend fun listAutopilots(@Query("status") status: String? = null): Response<ListAutopilotsResponse>

    @GET("api/autopilots/{id}")
    suspend fun getAutopilot(@Path("id") id: String): Response<GetAutopilotResponse>

    @POST("api/autopilots")
    suspend fun createAutopilot(@Body body: CreateAutopilotRequest): Response<Autopilot>

    @PATCH("api/autopilots/{id}")
    suspend fun updateAutopilot(@Path("id") id: String, @Body body: UpdateAutopilotRequest): Response<Autopilot>

    @DELETE("api/autopilots/{id}")
    suspend fun deleteAutopilot(@Path("id") id: String): Response<Unit>

    @POST("api/autopilots/{id}/trigger")
    suspend fun triggerAutopilot(@Path("id") id: String): Response<AutopilotRun>

    @GET("api/autopilots/{id}/runs")
    suspend fun listAutopilotRuns(
        @Path("id") id: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<ListAutopilotRunsResponse>

    @POST("api/autopilots/{autopilotId}/triggers")
    suspend fun createAutopilotTrigger(
        @Path("autopilotId") autopilotId: String,
        @Body body: CreateAutopilotTriggerRequest,
    ): Response<AutopilotTrigger>

    @PATCH("api/autopilots/{autopilotId}/triggers/{triggerId}")
    suspend fun updateAutopilotTrigger(
        @Path("autopilotId") autopilotId: String,
        @Path("triggerId") triggerId: String,
        @Body body: UpdateAutopilotTriggerRequest,
    ): Response<AutopilotTrigger>

    @DELETE("api/autopilots/{autopilotId}/triggers/{triggerId}")
    suspend fun deleteAutopilotTrigger(
        @Path("autopilotId") autopilotId: String,
        @Path("triggerId") triggerId: String,
    ): Response<Unit>

    @POST("api/autopilots/{id}/collaborators")
    suspend fun grantAutopilotAccess(@Path("id") id: String, @Body body: CollaboratorRequest): Response<AutopilotCollaboratorsResponse>

    @DELETE("api/autopilots/{id}/collaborators/{userId}")
    suspend fun revokeAutopilotAccess(
        @Path("id") id: String,
        @Path("userId") userId: String,
    ): Response<AutopilotCollaboratorsResponse>
}
