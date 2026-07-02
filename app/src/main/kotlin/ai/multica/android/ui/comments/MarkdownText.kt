package ai.multica.android.ui.comments

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.spans.CodeSpan
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Renders a Markdown string as native Compose text via a Markwon-
 * populated TextView (AndroidView interop).
 *
 * The [Markwon] instance is built once per [Context] (cached in
 * [remember]) so we don't re-create the renderer on every recompose.
 * It applies the current Material 3 colors so the markdown feels
 * native to the surface.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    codeColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val markwon = remember(context) { buildMarkwon(context) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                // Pre-set link / code colors so first-paint is correct.
                setLinkTextColor(linkColor.toArgb())
            }
        },
        update = { tv ->
            val parsed = markwon.parse(text) as org.commonmark.node.Node
            val builder = SpannableStringBuilder()
            // Markwon returns Spanned from render; we set the text and apply spans.
            val rendered = markwon.render(parsed)
            tv.setText(rendered, TextView.BufferType.SPANNABLE)
            tv.setTextColor(textColor.toArgb())
            tv.setLinkTextColor(linkColor.toArgb())

            // Apply the code color to all CodeSpans.
            (tv.text as? Spannable)?.let { spannable ->
                spannable.getSpans(0, spannable.length, CodeSpan::class.java).forEach { span ->
                    spannable.setSpan(
                        ForegroundColorSpan(codeColor.toArgb()),
                        spannable.getSpanStart(span),
                        spannable.getSpanEnd(span),
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE,
                    )
                }
            }
        },
    )
}

private fun buildMarkwon(context: Context): Markwon {
    return Markwon.builder(context)
        .usePlugin(CorePlugin.create())
        .usePlugin(LinkifyPlugin.create())
        // Note: GFM tables are NOT registered here. Markwon's single-TextView
        // table simulation misaligns when cells wrap, so tables are parsed out
        // by MarkdownTableParser and rendered by MarkdownTableView instead.
        .build()
}

/**
 * Full markdown renderer that splits the document into text and table blocks.
 * Text blocks (everything except GFM tables) go through Markwon; table blocks
 * are rendered as a real Compose grid via [MarkdownTableView] so columns stay
 * aligned even when cell content wraps.
 *
 * This is the entry point issue descriptions / comments should use when the
 * content may contain tables. [MarkdownText] (Markwon only) remains available
 * for inline fragments known not to contain tables (e.g. a single table cell).
 */
@Composable
fun MarkdownRichText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    codeColor: Color = Color.Unspecified,
) {
    val segments = remember(text) { MarkdownTableParser.parse(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    if (segment.content.isNotBlank()) {
                        MarkdownText(
                            text = segment.content,
                            textColor = textColor,
                            linkColor = linkColor,
                            codeColor = codeColor,
                        )
                    }
                }
                is MarkdownSegment.Table -> MarkdownTableView(
                    table = segment,
                    textColor = textColor,
                    linkColor = linkColor,
                    codeColor = codeColor,
                )
            }
        }
    }
}
