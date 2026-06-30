package ai.multica.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// Enums (mirror packages/core/types/* literal unions exactly)
// ============================================================
//
// `@SerialName(...)` overrides the default enum name serialization
// (which is the Kotlin identifier, e.g. "IN_PROGRESS") to the wire
// format the server expects (lowercase snake_case per
// server/internal/handler/issue.go).

@Serializable
enum class IssueStatus {
    @SerialName("backlog") BACKLOG,
    @SerialName("todo") TODO,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("in_review") IN_REVIEW,
    @SerialName("done") DONE,
    @SerialName("blocked") BLOCKED,
    @SerialName("cancelled") CANCELLED;

    companion object {
        /**
         * Statuses shown as board columns (excludes cancelled).
         * Mirrors packages/core/issues/config/status.ts::BOARD_STATUSES.
         */
        val BOARD: List<IssueStatus> = listOf(
            BACKLOG, TODO, IN_PROGRESS, IN_REVIEW, DONE, BLOCKED
        )

        val ALL: List<IssueStatus> = entries
    }
}

@Serializable
enum class IssuePriority {
    @SerialName("urgent") URGENT,
    @SerialName("high") HIGH,
    @SerialName("medium") MEDIUM,
    @SerialName("low") LOW,
    @SerialName("none") NONE;

    companion object {
        val ORDER: List<IssuePriority> = listOf(URGENT, HIGH, MEDIUM, LOW, NONE)
    }
}

@Serializable
enum class IssueAssigneeType {
    @SerialName("member") MEMBER,
    @SerialName("agent") AGENT,
    @SerialName("squad") SQUAD
}

@Serializable
enum class ProjectStatus {
    @SerialName("planned") PLANNED,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("paused") PAUSED,
    @SerialName("completed") COMPLETED,
    @SerialName("cancelled") CANCELLED
}

@Serializable
enum class ProjectPriority {
    @SerialName("urgent") URGENT,
    @SerialName("high") HIGH,
    @SerialName("medium") MEDIUM,
    @SerialName("low") LOW,
    @SerialName("none") NONE;

    companion object {
        val ORDER: List<ProjectPriority> = listOf(URGENT, HIGH, MEDIUM, LOW, NONE)
    }
}

@Serializable
enum class ProjectLeadType {
    @SerialName("member") MEMBER,
    @SerialName("agent") AGENT
}

@Serializable
enum class MemberRole {
    @SerialName("owner") OWNER,
    @SerialName("admin") ADMIN,
    @SerialName("member") MEMBER
}

@Serializable
enum class InboxSeverity {
    @SerialName("action_required") ACTION_REQUIRED,
    @SerialName("attention") ATTENTION,
    @SerialName("info") INFO
}

@Serializable
enum class InboxRecipientType {
    @SerialName("member") MEMBER,
    @SerialName("agent") AGENT
}

@Serializable
enum class InboxActorType {
    @SerialName("member") MEMBER,
    @SerialName("agent") AGENT,
    @SerialName("system") SYSTEM
}

@Serializable
enum class InboxItemType {
    @SerialName("issue_assigned") ISSUE_ASSIGNED,
    @SerialName("issue_subscribed") ISSUE_SUBSCRIBED,
    @SerialName("unassigned") UNASSIGNED,
    @SerialName("assignee_changed") ASSIGNEE_CHANGED,
    @SerialName("status_changed") STATUS_CHANGED,
    @SerialName("priority_changed") PRIORITY_CHANGED,
    @SerialName("start_date_changed") START_DATE_CHANGED,
    @SerialName("due_date_changed") DUE_DATE_CHANGED,
    @SerialName("new_comment") NEW_COMMENT,
    @SerialName("mentioned") MENTIONED,
    @SerialName("review_requested") REVIEW_REQUESTED,
    @SerialName("task_completed") TASK_COMPLETED,
    @SerialName("task_failed") TASK_FAILED,
    @SerialName("agent_blocked") AGENT_BLOCKED,
    @SerialName("agent_completed") AGENT_COMPLETED,
    @SerialName("reaction_added") REACTION_ADDED,
    @SerialName("quick_create_done") QUICK_CREATE_DONE,
    @SerialName("quick_create_failed") QUICK_CREATE_FAILED
}

@Serializable
enum class CommentType {
    @SerialName("comment") COMMENT,
    @SerialName("status_change") STATUS_CHANGE,
    @SerialName("progress_update") PROGRESS_UPDATE,
    @SerialName("system") SYSTEM
}

@Serializable
enum class CommentAuthorType {
    @SerialName("member") MEMBER,
    @SerialName("agent") AGENT,
    @SerialName("system") SYSTEM
}

@Serializable
enum class InvitationStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
    @SerialName("declined") DECLINED,
    @SerialName("expired") EXPIRED
}

@Serializable
enum class IssueSortBy {
    @SerialName("position") POSITION,
    @SerialName("priority") PRIORITY,
    @SerialName("title") TITLE,
    @SerialName("created_at") CREATED_AT,
    @SerialName("start_date") START_DATE,
    @SerialName("due_date") DUE_DATE
}

@Serializable
enum class SortDirection {
    @SerialName("asc") ASC,
    @SerialName("desc") DESC
}
