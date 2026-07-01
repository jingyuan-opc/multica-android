package ai.multica.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Unified card surface used across list rows, detail sections, and resource
 * pickers. Establishes one visual treatment for elevated content so the app
 * reads as cohesive rather than ad-hoc per-screen.
 *
 * Design direction (Linear / Primer inspired, brand = vivid blue):
 *   - 1dp hairline border tinted to the surface outline (not a heavy shadow)
 *   - brand medium corner radius (10dp, matches web --radius)
 *   - neutral surface fill; subtle elevation only when [elevated]
 *   - consistent inner padding via [contentPadding]
 *
 * Prefer this over raw Material3 [androidx.compose.material3.Card] so border
 * treatment stays uniform. For clickable rows use [MulticaClickableCard].
 */
@Composable
fun MulticaCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (elevated) 1.dp else 0.dp,
        shadowElevation = if (elevated) 1.dp else 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
