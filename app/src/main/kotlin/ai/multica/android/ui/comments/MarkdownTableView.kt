package ai.multica.android.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Renders a GFM table as a real grid using Compose Column/Row. Each column
 * gets an equal weight so cell contents align vertically regardless of their
 * width, and wrapping cells expand row height instead of misaligning columns.
 *
 * This replaces Markwon's single-TextView table simulation for correctness.
 * Inline markdown inside cells (e.g. `**bold**`, `` `code` ``) is rendered via
 * [MarkdownText].
 */
@Composable
fun MarkdownTableView(
    table: MarkdownSegment.Table,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    codeColor: Color = Color.Unspecified,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
    val colCount = maxOf(table.header.size, table.aligns.size, table.rows.maxOfOrNull { it.size } ?: 1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(1.dp),
    ) {
        // Header row.
        Row(modifier = Modifier.fillMaxWidth().background(headerBackground)) {
            for (c in 0 until colCount) {
                TableCell(
                    text = table.header.getOrElse(c) { "" },
                    align = table.aligns.getOrElse(c) { TableAlign.LEFT },
                    isHeader = true,
                    textColor = textColor,
                    linkColor = linkColor,
                    codeColor = codeColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(color = borderColor, thickness = 1.dp)
        // Body rows.
        table.rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until colCount) {
                    TableCell(
                        text = row.getOrElse(c) { "" },
                        align = table.aligns.getOrElse(c) { TableAlign.LEFT },
                        isHeader = false,
                        textColor = textColor,
                        linkColor = linkColor,
                        codeColor = codeColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (rowIndex < table.rows.lastIndex) {
                HorizontalDivider(color = borderColor, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    align: TableAlign,
    isHeader: Boolean,
    textColor: Color,
    linkColor: Color,
    codeColor: Color,
    modifier: Modifier = Modifier,
) {
    val textAlign = when (align) {
        TableAlign.LEFT -> TextAlign.Start
        TableAlign.CENTER -> TextAlign.Center
        TableAlign.RIGHT -> TextAlign.End
    }
    val cellText = text.ifBlank { " " }
    Box(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = when (align) {
            TableAlign.LEFT -> Alignment.TopStart
            TableAlign.CENTER -> Alignment.TopCenter
            TableAlign.RIGHT -> Alignment.TopEnd
        },
    ) {
        if (isHeader) {
            Text(
                text = cellText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = textAlign,
            )
        } else {
            // Inline-markdown cells (bold/code/links) via Markwon; plain cells
            // still render fine through the same MarkdownText path.
            MarkdownText(
                text = cellText,
                textColor = textColor,
                linkColor = linkColor,
                codeColor = codeColor,
            )
        }
    }
}
