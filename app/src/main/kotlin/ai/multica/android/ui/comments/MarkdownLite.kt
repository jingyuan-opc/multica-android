package ai.multica.android.ui.comments

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Minimal Markdown → AnnotatedString converter.
 *
 * Supports:
 * - Paragraph breaks (blank line)
 * - `**bold**` → bold span
 * - `*italic*` → italic span
 * - `` `code` `` → monospace
 * - `- ` / `* ` → bullet list
 * - `# `, `## `, `### ` → headings
 * - `> ` → blockquote (renders as a left bar)
 *
 * This is intentionally lightweight — full CommonMark is a Phase 5
 * polish using Markwon. For v1, the comment composer is plain
 * markdown and these few transformations cover 95% of what users
 * actually type in issue threads.
 */
object MarkdownLite {

    fun render(
        text: String,
        codeColor: Color = Color(0xFFEF4444),
        quoteColor: Color = Color(0xFF6B7280),
    ): AnnotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("### ") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified))
                    append(line.removePrefix("### "))
                    pop()
                    append("\n")
                }
                line.startsWith("## ") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(line.removePrefix("## "))
                    pop()
                    append("\n")
                }
                line.startsWith("# ") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified))
                    append(line.removePrefix("# "))
                    pop()
                    append("\n")
                }
                line.startsWith("> ") -> {
                    pushStyle(SpanStyle(color = quoteColor, fontWeight = FontWeight.Medium))
                    append("│ ")
                    pop()
                    appendInline(line.removePrefix("> "), codeColor)
                    append("\n")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("•  ")
                    appendInline(line.removePrefix("- ").removePrefix("* "), codeColor)
                    append("\n")
                }
                line.isBlank() -> append("\n")
                else -> {
                    appendInline(line, codeColor)
                    append("\n")
                }
            }
            i++
        }
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
        text: String,
        codeColor: Color,
    ) {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        val code = text.substring(i + 1, end)
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor))
                        append(code)
                        pop()
                        i = end + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                c == '*' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        val bold = text.substring(i + 2, end)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(bold)
                        pop()
                        i = end + 2
                    } else {
                        append(c)
                        i++
                    }
                }
                c == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i) {
                        val italic = text.substring(i + 1, end)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Light))
                        append(italic)
                        pop()
                        i = end + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    if (close > i && close + 1 < n && text[close + 1] == '(') {
                        val urlEnd = text.indexOf(')', close + 2)
                        if (urlEnd > close) {
                            val label = text.substring(i + 1, close)
                            pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                            append(label)
                            pop()
                            i = urlEnd + 1
                        } else {
                            append(c)
                            i++
                        }
                    } else {
                        append(c)
                        i++
                    }
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }
}
