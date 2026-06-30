package ai.multica.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.core.theme.PriorityColors

/**
 * Linear stack of 4 horizontal bars (filled / empty) representing
 * issue priority. Mirrors the web app's `PRIORITY_CONFIG.bars`:
 *
 * - urgent: 4 filled
 * - high:   3 filled
 * - medium: 2 filled
 * - low:    1 filled
 * - none:   0 filled
 *
 * Bars are 2dp wide and 2dp apart, 12dp tall.
 */
@Composable
fun PriorityBars(
    priority: IssuePriority,
    modifier: Modifier = Modifier,
) {
    val color = PriorityColors.forPriority(priority)
    val filled = when (priority) {
        IssuePriority.URGENT -> 4
        IssuePriority.HIGH -> 3
        IssuePriority.MEDIUM -> 2
        IssuePriority.LOW -> 1
        IssuePriority.NONE -> 0
    }
    val empty = 4 - filled

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(filled) {
            PriorityBar(color, filled = true)
        }
        repeat(empty) {
            PriorityBar(color.copy(alpha = 0.25f), filled = false)
        }
    }
}

@Composable
private fun PriorityBar(color: Color, filled: Boolean) {
    Canvas(modifier = Modifier.size(width = 2.dp, height = 12.dp)) {
        if (filled) {
            drawRect(color = color, topLeft = Offset.Zero, size = size)
        } else {
            drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = size,
            )
        }
    }
}
