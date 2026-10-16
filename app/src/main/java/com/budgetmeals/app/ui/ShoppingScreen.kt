package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.Motion
import com.budgetmeals.app.ui.theme.extendedColors

@Composable
fun ShoppingScreen(
    snapshot: AppSnapshot,
    onEditItem: (ShoppingItem) -> Unit,
    onBuyItem: (ShoppingItem) -> Unit,
    onToggleItem: (ShoppingItem) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onUndoPurchase: (ShoppingItem) -> Unit = onToggleItem,
    onBuyAgain: (ShoppingItem) -> Unit = {},
    onAddItem: (() -> Unit)? = null,
) {
    var isPurchasedHistoryExpanded by rememberSaveable { mutableStateOf(false) }

    val allShopping = snapshot.shopping
    val open = remember(allShopping) { allShopping.filter { !it.isChecked } }
    val bought = remember(allShopping) { allShopping.filter { it.isChecked } }

    val estimatedOpen = remember(open) { open.filter { it.priceKnown }.sumOf { it.estimatedPrice } }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Clean Minimal Header
        item(key = "header") {
            ScreenHeader(
                title = "Shopping",
                subtitle = if (open.isNotEmpty()) {
                    val countText = "${open.size} ${if (open.size == 1) "item" else "items"}"
                    if (estimatedOpen > 0.0) {
                        "$countText · ${BudgetMath.money(estimatedOpen)}"
                    } else {
                        countText
                    }
                } else {
                    "List clear"
                },
                action = null,
            )
        }

        // 2. Open items or Empty State
        if (open.isEmpty()) {
            item(key = "empty_list") {
                EmptyState(
                    title = "Your shopping list is clear",
                    message = "Nothing waiting to buy. Tap the + button below to add groceries.",
                    icon = AppIcons.ShoppingCart,
                )
            }
        } else {
            items(open, key = { it.id }, contentType = { "shopping_item" }) { item ->
                ShoppingItemCard(
                    modifier = Modifier.animateItem(
                        fadeInSpec = Motion.FadeTween,
                        placementSpec = Motion.PlacementSpring,
                        fadeOutSpec = Motion.FadeTween,
                    ),
                    item = item,
                    onBuy = { onBuyItem(item) },
                    onEdit = { onEditItem(item) },
                    onDelete = { onDeleteItem(item) },
                )
            }
        }

        // 3. Discreet Previously Bought History
        if (bought.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isPurchasedHistoryExpanded = !isPurchasedHistoryExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Previously bought (${bought.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (isPurchasedHistoryExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                            contentDescription = if (isPurchasedHistoryExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    TextButton(
                        onClick = { bought.forEach { onDeleteItem(it) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Clear all",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            if (isPurchasedHistoryExpanded) {
                items(bought, key = { it.id }, contentType = { "bought_item" }) { item ->
                    BoughtItemCard(
                        modifier = Modifier.animateItem(
                            fadeInSpec = Motion.FadeTween,
                            placementSpec = Motion.PlacementSpring,
                            fadeOutSpec = Motion.FadeTween,
                        ),
                        item = item,
                        onUndo = { onUndoPurchase(item) },
                        onBuyAgain = { onBuyAgain(item) },
                        onDelete = { onDeleteItem(item) },
                    )
                }
            }
        }
    }
}

/**
 * Clean, unambiguous shopping card.
 * Category icon badge on the left (NO fake selection circles).
 * Clear details in the center.
 * Dedicated "Buy" action and delete button on the right.
 */
@Composable
private fun ShoppingItemCard(
    item: ShoppingItem,
    onBuy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftCard(
        modifier = modifier,
        onClick = onEdit,
        contentPadding = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category icon badge (clear visual identity, NOT a checkbox)
            IconBadge(
                icon = shoppingCategoryIcon(item.category),
                tint = shoppingCategoryColor(item.category),
                size = 40.dp,
                iconSize = 20.dp,
            )

            Spacer(Modifier.width(12.dp))

            // Main details
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = "${item.quantity.compact()} ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                    if (!item.priceKnown) {
                        Text(
                            text = "Price not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    } else {
                        Text(
                            text = if (item.estimatedPrice == 0.0) "Free" else BudgetMath.money(item.estimatedPrice),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (item.note.isNotBlank()) {
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Unambiguous "Buy" button (opens purchase sheet to confirm price)
            FilledTonalButton(
                onClick = onBuy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Text(
                    text = "Buy",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            // Discreet delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = "Delete item",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/**
 * Minimal card for previously bought items in the history section.
 */
@Composable
private fun BoughtItemCard(
    item: ShoppingItem,
    onUndo: () -> Unit,
    onBuyAgain: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
        borderColor = MaterialTheme.extendedColors.cardBorder.copy(alpha = 0.35f),
        contentPadding = 10.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.quantity.compact()} ${item.unit}" +
                        if (item.priceKnown && item.estimatedPrice > 0.0) " · ${BudgetMath.money(item.estimatedPrice)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // Quick re-buy
            Surface(
                onClick = onBuyAgain,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "Buy again",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.width(6.dp))

            // Undo (restore to shopping list)
            Surface(
                onClick = onUndo,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = "Delete item",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** Icon for an ItemCategory in shopping flows. */
@Composable
internal fun shoppingCategoryIcon(category: ItemCategory): ImageVector = when (category) {
    ItemCategory.FOOD_STAPLE -> AppIcons.RiceBowl
    ItemCategory.FOOD_FRESH -> AppIcons.Nutrition
    ItemCategory.FOOD_STREET -> AppIcons.Restaurant
    ItemCategory.SNACK -> AppIcons.Cookie
    ItemCategory.HOUSEHOLD -> AppIcons.Home
    ItemCategory.OTHER -> AppIcons.Category
}

/** Semantic color for an ItemCategory in shopping flows. */
@Composable
internal fun shoppingCategoryColor(category: ItemCategory): Color = when (category) {
    ItemCategory.FOOD_STAPLE -> MaterialTheme.colorScheme.secondary
    ItemCategory.FOOD_FRESH -> MaterialTheme.colorScheme.primary
    ItemCategory.FOOD_STREET -> MaterialTheme.colorScheme.tertiary
    ItemCategory.SNACK -> MaterialTheme.colorScheme.secondary
    ItemCategory.HOUSEHOLD -> MaterialTheme.colorScheme.tertiary
    ItemCategory.OTHER -> MaterialTheme.colorScheme.outline
}
