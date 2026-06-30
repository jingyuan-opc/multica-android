package ai.multica.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.multica.android.R
import ai.multica.android.core.network.ApiResult

/**
 * Shared error UI. The three [ApiResult] non-success branches map
 * to distinct visuals:
 *
 * - [ApiResult.NetworkError]  → "Network error" with offline icon
 * - [ApiResult.HttpError]     → server message + status code
 * - [ApiResult.Unknown]       → "Unexpected error" with warning icon
 *
 * The action slot always offers a Retry button.
 */
@Composable
fun ErrorState(
    result: ApiResult<*>,
    onRetry: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val (icon, title, description) = when (result) {
        is ApiResult.NetworkError -> Triple(
            Icons.Filled.CloudOff,
            "Can't reach server",
            result.cause.message ?: "Check your connection and try again.",
        )
        is ApiResult.HttpError -> Triple(
            Icons.Filled.Error,
            "Server error (${result.code})",
            result.message,
        )
        is ApiResult.Unknown -> Triple(
            Icons.Filled.Warning,
            "Unexpected error",
            result.cause.message ?: "Something went wrong.",
        )
        else -> Triple(
            Icons.Filled.Warning,
            "Something went wrong",
            "",
        )
    }
    ErrorState(
        icon = icon,
        title = title,
        description = description,
        onRetry = onRetry,
        modifier = modifier,
    )
}

@Composable
fun ErrorState(
    icon: ImageVector,
    title: String,
    description: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Text(androidx.compose.ui.res.stringResource(R.string.common_retry))
            }
        }
    }
}
