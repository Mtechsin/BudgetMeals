package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors

// ============================================================================
// Shared building blocks for the redesign. New and reworked screens should be
// composed from these rather than hand-rolling Card + BorderStroke + padding.
// ============================================================================

/** Card fill that lifts off the page in both themes (white on paper, a step above charcoal in dark). */
@Composable
fun cardSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

/**
 * The one card used across the app: soft fill, hairline border, 20dp radius.
 * Pass [onClick] to make the whole card a single large touch target.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = cardSurfaceColor(),
    borderColor: Color = MaterialTheme.extendedColors.cardBorder,
    contentPadding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            content = inner,
        )
    }
}

/** Small rounded status label such as "Eaten", "Low", "Planned". Always at least 12sp. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(99.dp),
        color = if (filled) color else color.copy(alpha = 0.14f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.surface else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Section heading with an optional trailing slot (link, count, etc). */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Tinted rounded-square icon holder used at the start of rows. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = tint.copy(alpha = 0.14f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/** Single row inside the quick-add sheet. */
@Composable
private fun QuickAddRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
) {
    SoftCard(onClick = onClick, contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Content of the quick-add sheet opened from the floating button on Home. */
@Composable
fun QuickAddSheetContent(
    onPurchase: () -> Unit,
    onExpense: () -> Unit,
    onShoppingItem: () -> Unit,
    onMeal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            "What happened?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        QuickAddRow(
            icon = AppIcons.ShoppingBasket,
            title = "I bought food",
            subtitle = "Adds to stock and spending",
            tint = MaterialTheme.colorScheme.primary,
            onClick = onPurchase,
        )
        QuickAddRow(
            icon = AppIcons.ReceiptLong,
            title = "I spent on something else",
            subtitle = "Internet, calling, household, custom",
            tint = MaterialTheme.colorScheme.tertiary,
            onClick = onExpense,
        )
        QuickAddRow(
            icon = AppIcons.ShoppingCart,
            title = "Remind me to buy",
            subtitle = "Add to the shopping list",
            tint = MaterialTheme.colorScheme.secondary,
            onClick = onShoppingItem,
        )
        QuickAddRow(
            icon = AppIcons.Restaurant,
            title = "Create a meal",
            subtitle = "Build a reusable meal from foods",
            tint = AccentPurple,
            onClick = onMeal,
        )
    }
}
