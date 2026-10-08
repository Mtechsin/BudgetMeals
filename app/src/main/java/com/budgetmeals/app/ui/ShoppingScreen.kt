@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.budgetmeals.app.ui

import com.budgetmeals.app.ui.icons.AppIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.state.BudgetMath

@Composable
fun ShoppingScreen(
    snapshot: AppSnapshot,
    onAddItem: () -> Unit,
    onEditItem: (ShoppingItem) -> Unit,
    onBuyItem: (ShoppingItem) -> Unit,
    onToggleItem: (ShoppingItem) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onUndoPurchase: (ShoppingItem) -> Unit = onToggleItem,
    onBuyAgain: (ShoppingItem) -> Unit = {},
) {
    val open = remember(snapshot.shopping) { snapshot.shopping.filter { !it.isChecked } }
    val checked = remember(snapshot.shopping) { snapshot.shopping.filter { it.isChecked } }
    val estimated = remember(open) { open.filter { it.priceKnown }.sumOf { it.estimatedPrice } }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Shopping",
                subtitle = "The list updates itself from your buying habit",
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkBorder),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = AccentMint.copy(alpha = 0.12f),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                AppIcons.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = AccentMintLight,
                            )
                            Text(
                                open.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AccentMintLight,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Next shop",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        if (open.isEmpty()) {
                            Text(
                                "Nothing waiting. Nice and simple.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${open.size} ${if (open.size == 1) "item" else "items"} Â· ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                                Text(
                                    "about ${BudgetMath.money(estimated)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = AccentMintLight,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (open.isEmpty()) {
            item {
                EmptyState(
                    title = "Your list is clear",
                    message = "New purchases can be remembered for next time automatically.",
                    icon = AppIcons.ShoppingCart,
                    actionLabel = "Add something",
                    onAction = onAddItem,
                )
            }
        } else {
            item { SectionHeader("To buy") }
            items(open, key = { it.id }, contentType = { "shopping_item" }) { item ->
                ShoppingRow(
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    item = item,
                    onToggle = { onToggleItem(item) },
                    onEdit = { onEditItem(item) },
                    onBuy = { onBuyItem(item) },
                    onDelete = { onDeleteItem(item) },
                )
            }
        }

        if (checked.isNotEmpty()) {
            item { SectionHeader("Done") }
            items(checked, key = { it.id }, contentType = { "shopping_item" }) { item ->
                ShoppingRow(
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    item = item,
                    onToggle = { onToggleItem(item) },
                    onEdit = { onEditItem(item) },
                    onBuy = { onBuyItem(item) },
                    onDelete = { onDeleteItem(item) },
                    onUndo = { onUndoPurchase(item) },
                    onBuyAgain = { onBuyAgain(item) },
                )
            }
        }
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onBuy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: (() -> Unit)? = null,
    onBuyAgain: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isChecked) DarkSurfaceLow.copy(alpha = 0.6f) else DarkSurface,
        ),
        border = BorderStroke(
            1.dp,
            if (item.isChecked) DarkBorder.copy(alpha = 0.4f) else DarkBorder,
        ),
    ) {
        Column(Modifier.padding(start = 6.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentMint,
                        checkmarkColor = DarkBgBase,
                        uncheckedColor = TextMuted,
                    ),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.isChecked) TextMuted else TextPrimary,
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${item.quantity.compact()} ${item.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isChecked) TextMuted else TextSecondary,
                        )
                        Text(
                            "Â·",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        if (!item.priceKnown) {
                            Text(
                                "Price not set",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        } else {
                            Text(
                                BudgetMath.money(item.estimatedPrice),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (item.isChecked) TextMuted else AccentMintLight,
                            )
                        }
                    }
                }
                if (!item.isChecked) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onBuy,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AccentMint.copy(alpha = 0.1f),
                            contentColor = AccentMintLight,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "Buy",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
            if (item.note.isNotBlank()) {
                Text(
                    item.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isChecked) TextMuted else AccentIndigo,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                )
            }
            if (item.isChecked) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onBuyAgain?.invoke() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AccentMint.copy(alpha = 0.1f),
                            contentColor = AccentMintLight,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Buy again", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    OutlinedButton(
                        onClick = { onUndo?.invoke() ?: onToggle() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = TextMuted.copy(alpha = 0.1f),
                            contentColor = TextSecondary,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Undo purchase", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = "Edit shopping item",
                            modifier = Modifier.size(16.dp),
                            tint = TextSecondary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = "Delete shopping item",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = "Edit shopping item",
                            modifier = Modifier.size(18.dp),
                            tint = TextSecondary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = "Delete shopping item",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
