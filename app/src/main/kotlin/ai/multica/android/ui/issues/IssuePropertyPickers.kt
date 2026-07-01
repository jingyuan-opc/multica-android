package ai.multica.android.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.multica.android.data.model.Agent
import ai.multica.android.data.model.IssueAssigneeType
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.Label
import ai.multica.android.data.model.MemberWithUser
import ai.multica.android.data.model.Project
import ai.multica.android.data.model.Squad
import ai.multica.android.ui.components.MulticaAvatar
import ai.multica.android.ui.components.PriorityBars
import ai.multica.android.ui.components.StatusChip
import ai.multica.android.ui.components.labelFor as labelForStatus

/**
 * Reusable dropdown property pickers for the issue detail sidebar.
 * Each renders a label + read-only OutlinedTextField with a trailing
 * dropdown arrow; tapping opens a menu of options with the current
 * value checked.
 */

/** A section header inside a picker dropdown. */
@Composable
fun PickerSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// ---------- Concrete pickers ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPickerField(
    current: IssueStatus,
    onPick: (IssueStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(88.dp),
            )
            OutlinedTextField(
                value = labelForStatus(current),
                onValueChange = {},
                readOnly = true,
                leadingIcon = { StatusChip(status = current, modifier = Modifier.padding(start = 8.dp)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.weight(1f).menuAnchor(),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            IssueStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(labelForStatus(status)) },
                    onClick = { onPick(status); expanded = false },
                    leadingIcon = { StatusChip(status = status) },
                    trailingIcon = {
                        if (status == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityPickerField(
    current: IssuePriority,
    onPick: (IssuePriority) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Priority",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(88.dp),
            )
            OutlinedTextField(
                value = current.name.lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                leadingIcon = {
                    Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PriorityBars(priority = current)
                    }
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.weight(1f).menuAnchor(),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            IssuePriority.ORDER.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = { onPick(p); expanded = false },
                    leadingIcon = { PriorityBars(priority = p) },
                    trailingIcon = {
                        if (p == current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssigneePickerField(
    currentType: IssueAssigneeType?,
    currentId: String?,
    members: List<MemberWithUser>,
    agents: List<Agent>,
    squads: List<Squad>,
    onPick: (IssueAssigneeType?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = resolveAssigneeLabel(currentType, currentId, members, agents, squads)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Assignee",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(88.dp),
            )
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                leadingIcon = {
                    assigneeLeadingIcon(currentType, currentId, members, agents, squads, Modifier.padding(start = 8.dp))
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.weight(1f).menuAnchor(),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            DropdownMenuItem(
                text = { Text("Unassigned") },
                onClick = { onPick(null, null); expanded = false },
                trailingIcon = {
                    if (currentType == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                },
            )
            if (members.isNotEmpty()) {
                PickerSectionHeader("Members")
                members.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.name) },
                        onClick = { onPick(IssueAssigneeType.MEMBER, m.userId); expanded = false },
                        leadingIcon = { MulticaAvatar(name = m.name, avatarUrl = m.avatarUrl, size = 24.dp) },
                        trailingIcon = {
                            if (currentType == IssueAssigneeType.MEMBER && currentId == m.userId)
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
            if (agents.isNotEmpty()) {
                PickerSectionHeader("Agents")
                agents.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.name) },
                        onClick = { onPick(IssueAssigneeType.AGENT, a.id); expanded = false },
                        leadingIcon = { Icon(Icons.Filled.SmartToy, null) },
                        trailingIcon = {
                            if (currentType == IssueAssigneeType.AGENT && currentId == a.id)
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
            if (squads.isNotEmpty()) {
                PickerSectionHeader("Squads")
                squads.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = { onPick(IssueAssigneeType.SQUAD, s.id); expanded = false },
                        leadingIcon = { Icon(Icons.Filled.Group, null) },
                        trailingIcon = {
                            if (currentType == IssueAssigneeType.SQUAD && currentId == s.id)
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerField(
    currentProjectId: String?,
    projects: List<Project>,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = projects.firstOrNull { it.id == currentProjectId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Project",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(88.dp),
            )
            OutlinedTextField(
                value = current?.title ?: "None",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.weight(1f).menuAnchor(),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = { onPick(null); expanded = false },
                trailingIcon = {
                    if (currentProjectId == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                },
            )
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.title) },
                    onClick = { onPick(p.id); expanded = false },
                    trailingIcon = {
                        if (p.id == currentProjectId) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}

/** A colored label chip. */
@Composable
fun LabelChip(label: Label, modifier: Modifier = Modifier) {
    val bg = parseHexColor(label.color ?: "#3b82f6")
    val fg = if (luminance(bg) > 0.5) Color.Black else Color.White
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = label.name,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ---------- Helpers ----------

private fun resolveAssigneeLabel(
    type: IssueAssigneeType?,
    id: String?,
    members: List<MemberWithUser>,
    agents: List<Agent>,
    squads: List<Squad>,
): String = when (type) {
    IssueAssigneeType.MEMBER -> members.firstOrNull { it.userId == id }?.name ?: "Member"
    IssueAssigneeType.AGENT -> agents.firstOrNull { it.id == id }?.name ?: "Agent"
    IssueAssigneeType.SQUAD -> squads.firstOrNull { it.id == id }?.name ?: "Squad"
    null -> "Unassigned"
}

@Composable
private fun assigneeLeadingIcon(
    type: IssueAssigneeType?,
    id: String?,
    members: List<MemberWithUser>,
    agents: List<Agent>,
    squads: List<Squad>,
    modifier: Modifier = Modifier,
) {
    when (type) {
        IssueAssigneeType.MEMBER -> {
            val m = members.firstOrNull { it.userId == id }
            MulticaAvatar(name = m?.name ?: "M", avatarUrl = m?.avatarUrl, size = 24.dp, modifier = modifier)
        }
        IssueAssigneeType.AGENT -> Icon(Icons.Filled.SmartToy, null, modifier = modifier)
        IssueAssigneeType.SQUAD -> Icon(Icons.Filled.Group, null, modifier = modifier)
        null -> Icon(Icons.Filled.ArrowDropDown, null, modifier = modifier)
    }
}

/**
 * Parse "#RRGGBB" / "#RGB" / "RRGGBB" → Compose Color.
 * Expands 3-digit shorthand (#fc0 → #ffcc00) before parsing.
 * Falls back to primary on failure.
 */
internal fun parseHexColor(hex: String): Color = try {
    var normalized = hex.removePrefix("#").trim()
    if (normalized.length == 3) {
        // #RGB → #RRGGBB
        normalized = normalized.toCharArray().joinToString("") { c -> "$c$c" }
    }
    require(normalized.length == 6) { "expected 6 hex digits" }
    val rgb = normalized.toLong(16)
    Color(rgb.toULong() or (0xFFUL shl 24))
} catch (e: Throwable) {
    Color(0xFF3B82F6)
}

/**
 * Relative luminance (0..1) using the WCAG linearization for sRGB, so the
 * contrast threshold for black/white foreground text is accurate at all
 * brightnesses (the old gamma-space sum picked the wrong color on amber, etc).
 */
internal fun luminance(color: Color): Float {
    fun linearize(c: Float): Float = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
    return 0.2126f * linearize(color.red) + 0.7152f * linearize(color.green) + 0.0722f * linearize(color.blue)
}
