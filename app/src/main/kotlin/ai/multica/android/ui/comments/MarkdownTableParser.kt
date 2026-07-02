package ai.multica.android.ui.comments

/**
 * Result of splitting a markdown document into ordered segments so that
 * GFM tables can be rendered with a real grid layout instead of relying on
 * Markwon's single-TextView table simulation (which misaligns when cell
 * content wraps or columns vary in width).
 *
 * [text] segments are rendered via Markwon (all other markdown); [table]
 * segments are rendered via [MarkdownTableView].
 */
sealed class MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment()
    data class Table(val header: List<String>, val aligns: List<TableAlign>, val rows: List<List<String>>) : MarkdownSegment()
}

enum class TableAlign { LEFT, CENTER, RIGHT }

object MarkdownTableParser {

    /**
     * Split [markdown] into an ordered list of [MarkdownSegment]s. Consecutive
     * GFM table rows (header + delimiter + body) are collapsed into a single
     * [MarkdownSegment.Table]; everything else becomes [MarkdownSegment.Text]
     * (preserving original line breaks and blank lines between blocks).
     */
    fun parse(markdown: String): List<MarkdownSegment> {
        val lines = markdown.split("\n")
        val segments = mutableListOf<MarkdownSegment>()
        val textBuffer = StringBuilder()
        var i = 0

        fun flushText() {
            if (textBuffer.isNotEmpty()) {
                segments.add(MarkdownSegment.Text(textBuffer.toString().trimEnd('\n')))
                textBuffer.clear()
            }
        }

        while (i < lines.size) {
            val table = tryParseTable(lines, i)
            if (table != null) {
                flushText()
                segments.add(table.segment)
                i = table.nextIndex
            } else {
                textBuffer.append(lines[i]).append('\n')
                i++
            }
        }
        flushText()
        return segments
    }

    private data class ParsedTable(val segment: MarkdownSegment.Table, val nextIndex: Int)

    private fun tryParseTable(lines: List<String>, start: Int): ParsedTable? {
        // A GFM table needs at least a header row and a delimiter row.
        if (start + 1 >= lines.size) return null
        val headerLine = lines[start].trim()
        val delimLine = lines[start + 1].trim()
        if (!headerLine.startsWith("|") && !headerLine.endsWith("|")) return null
        val headerCells = splitRow(headerLine)
        if (headerCells.isEmpty()) return null

        // Delimiter row: cells like :---, :--:, ---:, --- (only dashes/colons).
        val aligns = splitRow(delimLine).map { cell ->
            val c = cell.trim()
            val left = c.startsWith(":")
            val right = c.endsWith(":")
            when {
                left && right -> TableAlign.CENTER
                right -> TableAlign.RIGHT
                else -> TableAlign.LEFT
            }
        }
        // Validate the delimiter cells contain only dashes/colons.
        if (aligns.isEmpty() || splitRow(delimLine).any { it.replace(":", "").replace("-", "").isNotEmpty() }) {
            return null
        }

        val rows = mutableListOf<List<String>>()
        var j = start + 2
        while (j < lines.size) {
            val row = lines[j].trim()
            if (row.isEmpty() || (!row.startsWith("|") && !row.endsWith("|"))) break
            rows.add(splitRow(row))
            j++
        }
        return ParsedTable(MarkdownSegment.Table(headerCells, aligns, rows), j)
    }

    /** Split a table row into cells, trimming the leading/trailing pipe. */
    private fun splitRow(row: String): List<String> {
        var r = row.trim()
        if (r.startsWith("|")) r = r.substring(1)
        if (r.endsWith("|")) r = r.substring(0, r.length - 1)
        return r.split("|").map { it.trim() }
    }
}
