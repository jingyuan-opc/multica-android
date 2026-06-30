package ai.multica.android.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ai.multica.android.R
import ai.multica.android.data.model.Reaction

/**
 * Displays the reactions on a single comment as a horizontal flow
 * of pills, with a trailing `+` button that opens the emoji picker.
 *
 * The bar's contract:
 * - Click an existing reaction pill: if I have reacted, removes it
 *   (toggle). If I haven't, adds it.
 * - Click `+`: opens the emoji picker.
 * - The picker returns the chosen emoji; the VM handles add/remove.
 */
@Composable
fun ReactionsBar(
    reactions: List<Reaction>,
    currentActorId: String?,
    onToggle: (emoji: String) -> Unit,
    onAdd: (emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    // Aggregate same-emoji reactions into a (emoji, count, mine) row.
    val grouped = remember(reactions, currentActorId) {
        reactions
            .groupBy { it.emoji }
            .map { (emoji, list) ->
                Triple(emoji, list.size, list.any { it.actorId == currentActorId })
            }
            .sortedByDescending { it.second }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        grouped.forEach { (emoji, count, mine) ->
            ReactionPill(
                emoji = emoji,
                count = count,
                highlighted = mine,
                onClick = { onToggle(emoji) },
            )
        }
        SmallFloatingActionButton(
            onClick = { pickerOpen = true },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AddReaction,
                contentDescription = "Add reaction",
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (pickerOpen) {
        EmojiPickerDialog(
            onPick = {
                pickerOpen = false
                onAdd(it)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ReactionPill(
    emoji: String,
    count: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * A small set of common reaction emojis. The web client shows a much
 * larger picker (emoji-mart) — this is a v1 approximation that covers
 * the most common cases (👍 ❤️ 🎉 😄 🤔 👀 🔥).
 */
private val QUICK_EMOJI = listOf("👍", "❤️", "🎉", "😄", "🤔", "👀", "🔥", "🚀", "✅", "❌")

@Composable
private fun EmojiPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Add reaction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 5 per row grid
                QUICK_EMOJI.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onPick(emoji) }
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
