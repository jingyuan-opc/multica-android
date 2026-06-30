package ai.multica.android.ui.comments

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.spans.CodeSpan
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import io.noties.markwon.ext.tables.TablePlugin
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
    val builder = Markwon.builder(context)
        .usePlugin(CorePlugin.create())
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(TablePlugin.create(context))
    return builder.build()
}
