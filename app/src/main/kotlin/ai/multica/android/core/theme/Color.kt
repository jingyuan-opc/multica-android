package ai.multica.android.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Multica brand palette — exact oklch values from
 * packages/ui/styles/tokens.css.
 *
 * Light theme and dark theme both reference the same logical tokens;
 * the lightness curve flips so the brand reads as "blue" in both modes.
 */
object BrandColors {

    // ----- Brand -----
    val brandLight = oklchToArgb(0.55, 0.16, 255.0)        // vivid blue
    val brandLightForeground = oklchToArgb(0.985, 0.0, 0.0)
    val brandDark = oklchToArgb(0.65, 0.16, 255.0)         // brighter blue for dark bg
    val brandDarkForeground = oklchToArgb(0.985, 0.0, 0.0)

    // ----- Surface (light) -----
    val backgroundLight = oklchToArgb(1.0, 0.0, 0.0)                      // pure white
    val foregroundLight = oklchToArgb(0.141, 0.005, 285.823)              // near-black ink
    val cardLight = oklchToArgb(1.0, 0.0, 0.0)
    val cardForegroundLight = oklchToArgb(0.141, 0.005, 285.823)
    val popoverLight = oklchToArgb(1.0, 0.0, 0.0)
    val popoverForegroundLight = oklchToArgb(0.141, 0.005, 285.823)
    val primaryLight = oklchToArgb(0.21, 0.006, 285.885)                  // near-black primary
    val primaryForegroundLight = oklchToArgb(0.985, 0.0, 0.0)
    val secondaryLight = oklchToArgb(0.967, 0.001, 286.375)
    val secondaryForegroundLight = oklchToArgb(0.21, 0.006, 285.885)
    val mutedLight = oklchToArgb(0.967, 0.001, 286.375)
    val mutedForegroundLight = oklchToArgb(0.552, 0.016, 285.938)
    val accentLight = oklchToArgb(0.967, 0.001, 286.375)
    val accentForegroundLight = oklchToArgb(0.21, 0.006, 285.885)
    val destructiveLight = oklchToArgb(0.577, 0.245, 27.325)              // red
    val destructiveForegroundLight = oklchToArgb(0.985, 0.0, 0.0)
    val borderLight = oklchToArgb(0.92, 0.004, 286.32)
    val inputLight = oklchToArgb(0.92, 0.004, 286.32)
    val ringLight = oklchToArgb(0.705, 0.015, 286.067)

    // ----- Surface (dark) -----
    val backgroundDark = oklchToArgb(0.18, 0.005, 285.823)               // near-black
    val foregroundDark = oklchToArgb(0.985, 0.0, 0.0)                     // near-white
    val cardDark = oklchToArgb(0.21, 0.006, 285.885)
    val cardForegroundDark = oklchToArgb(0.985, 0.0, 0.0)
    val popoverDark = oklchToArgb(0.21, 0.006, 285.885)
    val popoverForegroundDark = oklchToArgb(0.985, 0.0, 0.0)
    val primaryDark = oklchToArgb(0.92, 0.004, 286.32)                    // near-white primary
    val primaryForegroundDark = oklchToArgb(0.21, 0.006, 285.885)
    val secondaryDark = oklchToArgb(0.274, 0.006, 286.033)
    val secondaryForegroundDark = oklchToArgb(0.985, 0.0, 0.0)
    val mutedDark = oklchToArgb(0.274, 0.006, 286.033)
    val mutedForegroundDark = oklchToArgb(0.705, 0.015, 286.067)
    val accentDark = oklchToArgb(0.274, 0.006, 286.033)
    val accentForegroundDark = oklchToArgb(0.985, 0.0, 0.0)
    val destructiveDark = oklchToArgb(0.704, 0.191, 22.216)               // softer red on dark
    val destructiveForegroundDark = oklchToArgb(0.985, 0.0, 0.0)
    val borderDark = oklchToArgb(1.0, 0.0, 0.0, alpha = 0.10)
    val inputDark = oklchToArgb(1.0, 0.0, 0.0, alpha = 0.15)
    val ringDark = oklchToArgb(0.552, 0.016, 285.938)

    // ----- Semantic palette -----
    val success = oklchToArgb(0.55, 0.16, 145.0)        // green
    val warning = oklchToArgb(0.75, 0.16, 85.0)         // amber
    val info = oklchToArgb(0.55, 0.18, 250.0)           // cyan-blue
}

// ---------- Compose-friendly Color extensions ----------

val BrandColor: Color = Color(BrandColors.brandLight)
val SuccessColor: Color = Color(BrandColors.success)
val WarningColor: Color = Color(BrandColors.warning)
val InfoColor: Color = Color(BrandColors.info)
val DestructiveColor: Color = Color(BrandColors.destructiveLight)

/**
 * Status pill colors. Used for Issue.status and Project.status chips
 * across the app. Centralized so they match the web app's
 * STATUS_CONFIG iconColor values.
 */
object StatusColors {
    // IssueStatus colors
    val backlog = Color(BrandColors.mutedForegroundLight)
    val todo = Color(BrandColors.mutedForegroundLight)
    val inProgress = Color(BrandColors.brandLight)
    val inReview = Color(BrandColors.info)
    val done = Color(BrandColors.success)
    val blocked = Color(BrandColors.destructiveLight)
    val cancelled = Color(BrandColors.mutedForegroundLight)

    fun forStatus(status: ai.multica.android.data.model.IssueStatus): Color = when (status) {
        ai.multica.android.data.model.IssueStatus.BACKLOG -> backlog
        ai.multica.android.data.model.IssueStatus.TODO -> todo
        ai.multica.android.data.model.IssueStatus.IN_PROGRESS -> inProgress
        ai.multica.android.data.model.IssueStatus.IN_REVIEW -> inReview
        ai.multica.android.data.model.IssueStatus.DONE -> done
        ai.multica.android.data.model.IssueStatus.BLOCKED -> blocked
        ai.multica.android.data.model.IssueStatus.CANCELLED -> cancelled
    }
}

/**
 * Priority bar colors. The web app uses 4 bars for urgent, 3 for high,
 * 2 for medium, 1 for low, 0 for none. Bar fill colors are mapped to
 * the destructive / warning / info / muted palette.
 */
object PriorityColors {
    val urgent = Color(BrandColors.destructiveLight)
    val high = Color(BrandColors.warning)
    val medium = Color(BrandColors.info)
    val low = Color(BrandColors.mutedForegroundLight)
    val none = Color(BrandColors.mutedForegroundLight)

    fun forPriority(priority: ai.multica.android.data.model.IssuePriority): Color = when (priority) {
        ai.multica.android.data.model.IssuePriority.URGENT -> urgent
        ai.multica.android.data.model.IssuePriority.HIGH -> high
        ai.multica.android.data.model.IssuePriority.MEDIUM -> medium
        ai.multica.android.data.model.IssuePriority.LOW -> low
        ai.multica.android.data.model.IssuePriority.NONE -> none
    }

    /**
     * Project priority uses the same value set as IssuePriority
     * (`urgent` / `high` / `medium` / `low` / `none`) but is a
     * different enum. The color mapping is the same.
     */
    fun forProjectPriority(priority: ai.multica.android.data.model.ProjectPriority): Color = when (priority) {
        ai.multica.android.data.model.ProjectPriority.URGENT -> urgent
        ai.multica.android.data.model.ProjectPriority.HIGH -> high
        ai.multica.android.data.model.ProjectPriority.MEDIUM -> medium
        ai.multica.android.data.model.ProjectPriority.LOW -> low
        ai.multica.android.data.model.ProjectPriority.NONE -> none
    }
}

/**
 * Inbox severity colors. Mirrors the visual weight the web app uses
 * in packages/views/inbox.
 */
object SeverityColors {
    val actionRequired = Color(BrandColors.destructiveLight)
    val attention = Color(BrandColors.warning)
    val info = Color(BrandColors.info)

    fun forSeverity(severity: ai.multica.android.data.model.InboxSeverity): Color = when (severity) {
        ai.multica.android.data.model.InboxSeverity.ACTION_REQUIRED -> actionRequired
        ai.multica.android.data.model.InboxSeverity.ATTENTION -> attention
        ai.multica.android.data.model.InboxSeverity.INFO -> info
    }
}
