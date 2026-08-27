package ru.mugalimov.volthome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mugalimov.volthome.core.theme.VhColors
import ru.mugalimov.volthome.core.theme.VhDimens
import ru.mugalimov.volthome.core.theme.VhSpacing
import ru.mugalimov.volthome.core.theme.UiStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VhTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val t = VhColors.tokens
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = t.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            when {
                navigationIcon != null -> navigationIcon()
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Назад",
                        tint = t.textSecondary
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = t.bg,
            scrolledContainerColor = t.surface,
            titleContentColor = t.textPrimary,
            navigationIconContentColor = t.textSecondary,
            actionIconContentColor = t.textSecondary
        )
    )
}

@Composable
fun VhSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(VhSpacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = VhColors.tokens.textPrimary
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(VhSpacing.xxs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VhColors.tokens.textSecondary
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun VhListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val t = VhColors.tokens
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = t.surfaceAlt,
        border = BorderStroke(1.dp, t.divider)
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = VhSpacing.md, vertical = VhSpacing.sm)
                .heightIn(min = VhDimens.controlMinHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VhSpacing.sm)
        ) {
            icon?.let {
                Surface(shape = MaterialTheme.shapes.medium, color = t.primarySurface) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = t.primary,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                }
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = t.textMuted
                )
            }
        }
    }
}

@Composable
fun VhInlineNotice(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    status: UiStatus = UiStatus.INFO,
    icon: ImageVector? = null
) {
    val t = VhColors.tokens
    val accent = VhColors.status(status)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = VhColors.statusSurface(status),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(VhSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(VhSpacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(VhSpacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                if (!description.isNullOrBlank()) {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = t.textSecondary)
                }
            }
        }
    }
}

@Composable
fun VhMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = VhColors.tokens.textMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = VhColors.tokens.textPrimary)
        if (!supportingText.isNullOrBlank()) {
            Text(supportingText, style = MaterialTheme.typography.bodySmall, color = VhColors.tokens.textSecondary)
        }
    }
}

@Composable
fun VhPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = VhDimens.controlMinHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = VhColors.tokens.primary,
            contentColor = VhColors.tokens.textOnAccent
        )
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(VhSpacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VhSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = VhDimens.controlMinHeight),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, VhColors.tokens.border)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VhLoadingState(
    modifier: Modifier = Modifier,
    message: String = "Загружаем данные…"
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = VhColors.tokens.primary
            )
            Spacer(Modifier.height(VhSpacing.sm))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = VhColors.tokens.textSecondary)
        }
    }
}

@Composable
fun VhEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) = VhMessageState(
    modifier = modifier,
    icon = icon,
    title = title,
    description = description,
    actionLabel = actionLabel,
    onAction = onAction,
    isError = false
)

@Composable
fun VhErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) = VhMessageState(
    modifier = modifier,
    icon = Icons.Rounded.ErrorOutline,
    title = "Не удалось загрузить данные",
    description = message,
    actionLabel = if (onRetry != null) "Повторить" else null,
    onAction = onRetry,
    isError = true
)

@Composable
private fun VhMessageState(
    icon: ImageVector,
    title: String,
    description: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    isError: Boolean,
    modifier: Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(VhSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VhSpacing.sm)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (isError) VhColors.tokens.errorSurface else VhColors.tokens.surfaceAlt
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isError) VhColors.tokens.error else VhColors.tokens.textSecondary,
                    modifier = Modifier.padding(VhSpacing.sm).size(28.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = VhColors.tokens.textPrimary,
                textAlign = TextAlign.Center
            )
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VhColors.tokens.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onAction != null) {
                VhSecondaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun VhStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) VhColors.tokens.primarySurface else VhColors.tokens.surfaceAlt,
        border = BorderStroke(
            1.dp,
            if (emphasized) VhColors.tokens.primary.copy(alpha = 0.65f) else VhColors.tokens.border
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) VhColors.tokens.primary else VhColors.tokens.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
