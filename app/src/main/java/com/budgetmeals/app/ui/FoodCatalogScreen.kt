@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.budgetmeals.app.ui

import com.budgetmeals.app.ui.icons.AppIcons

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.theme.Motion
import com.budgetmeals.app.ui.theme.Spacing
import com.budgetmeals.app.ui.theme.extendedColors

@Composable
fun FoodCatalogScreen(
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (FoodCatalogItem) -> Unit,
    onDeleteItem: (FoodCatalogItem) -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    onCopyAiPrompt: () -> Unit,
) {
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var selectedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<ItemCategory?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<FoodCatalogItem?>(null) }

    val filteredItems = remember(snapshot.foodCatalog, searchQuery, selectedCategory) {
        val query = searchQuery.trim().lowercase()
        snapshot.foodCatalog.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.name.lowercase().contains(query) ||
                item.notes.lowercase().contains(query) ||
                item.portionUnit.lowercase().contains(query) ||
                item.stockUnit.lowercase().contains(query)
            val matchesCategory = selectedCategory == null || item.category == selectedCategory
            matchesQuery && matchesCategory
        }.sortedBy { it.name.lowercase() }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.bottomClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            ScreenHeader(
                title = "Food items",
                subtitle = if (filteredItems.size == snapshot.foodCatalog.size) {
                    "${snapshot.foodCatalog.size} items for meal building & stock"
                } else {
                    "${filteredItems.size} of ${snapshot.foodCatalog.size} items"
                },
                onBack = onBack,
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onAddItem) {
                            Icon(
                                AppIcons.Add,
                                contentDescription = "Add food item",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    AppIcons.MoreVert,
                                    contentDescription = "More actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import JSON") },
                                    onClick = {
                                        showMoreMenu = false
                                        onImportJson()
                                    },
                                    leadingIcon = {
                                        Icon(AppIcons.FileUpload, contentDescription = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export JSON") },
                                    onClick = {
                                        showMoreMenu = false
                                        onExportJson()
                                    },
                                    leadingIcon = {
                                        Icon(AppIcons.FileDownload, contentDescription = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy AI Prompt Instructions") },
                                    onClick = {
                                        showMoreMenu = false
                                        onCopyAiPrompt()
                                    },
                                    leadingIcon = {
                                        Icon(AppIcons.Restaurant, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search food items, units, or notes...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        AppIcons.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                AppIcons.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardSurfaceColor(),
                    unfocusedContainerColor = cardSurfaceColor(),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.extendedColors.cardBorder,
                ),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${snapshot.foodCatalog.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                val categoryCounts = remember(snapshot.foodCatalog) {
                    snapshot.foodCatalog.groupingBy { it.category }.eachCount()
                }
                ItemCategory.entries.forEach { category ->
                    val count = categoryCounts[category] ?: 0
                    if (count > 0 || selectedCategory == category) {
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            label = {
                                Text("${category.label} ($count)")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item {
                SoftCard(contentPadding = Spacing.xl) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        IconBadge(
                            icon = if (searchQuery.isNotBlank() || selectedCategory != null) AppIcons.Search else AppIcons.Restaurant,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 52.dp,
                            iconSize = 28.dp,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            if (searchQuery.isNotBlank() || selectedCategory != null) "No matching food items" else "No food items yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (searchQuery.isNotBlank() || selectedCategory != null) {
                                "Try searching for a different name or clear your filters."
                            } else {
                                "Add ingredients like eggs, tomatoes, or bread to calculate meal costs and stock."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        if (searchQuery.isNotBlank() || selectedCategory != null) {
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedCategory = null
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Clear filters")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                OutlinedButton(
                                    onClick = onAddItem,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add item")
                                }
                                OutlinedButton(
                                    onClick = onImportJson,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(AppIcons.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import JSON")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredItems, key = { it.id }, contentType = { "catalog_item" }) { item ->
                FoodCatalogItemCard(
                    item = item,
                    snapshot = snapshot,
                    onEdit = { onEditItem(item) },
                    onDelete = { itemToDelete = item },
                )
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    "Delete \"${item.name}\"?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this food item? Meals that use this ingredient will keep their logged records, but meal planning and cost estimation will no longer auto-fill from this item.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(item)
                        itemToDelete = null
                    },
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun categoryIcon(category: ItemCategory): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    ItemCategory.FOOD_STAPLE -> AppIcons.RiceBowl
    ItemCategory.FOOD_FRESH -> AppIcons.Restaurant
    ItemCategory.FOOD_STREET -> AppIcons.Restaurant
    ItemCategory.SNACK -> AppIcons.Fastfood
    ItemCategory.HOUSEHOLD -> AppIcons.Home
    ItemCategory.OTHER -> AppIcons.Inventory2
}

@Composable
private fun FoodCatalogItemCard(
    item: FoodCatalogItem,
    snapshot: AppSnapshot,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val onHandPortions = snapshot.catalogAvailablePortions[item.id] ?: 0.0
    val hasDetails = (item.conversionLabel.isNotBlank() && item.conversionLabel != "1 piece = 1 piece") ||
        item.priceOptions.isNotEmpty() ||
        (item.purchasePrice != null && item.purchasePrice > 0.0) ||
        item.mealUsage.isNotBlank() ||
        item.cleanUserNotes.isNotBlank() ||
        !item.hasKnownPrice

    SoftCard(
        modifier = modifier.animateContentSize(Motion.CardExpandTween),
        onClick = {
            if (hasDetails) {
                isExpanded = !isExpanded
            } else {
                onEdit()
            }
        },
        contentPadding = Spacing.md,
    ) {
        // Main row: IconBadge + Name/Badges + Price + Expand/Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = categoryIcon(item.category),
                tint = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatusPill(
                        text = item.category.label,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    val hasStock = onHandPortions > 0.001
                    StatusPill(
                        text = if (hasStock) "${onHandPortions.cleanNumber()} ${item.portionUnit} in stock" else "0 ${item.portionUnit} in stock",
                        color = if (hasStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Price per portion on the right
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = BudgetMath.money(item.defaultCostPerPortion),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "per ${item.portionUnit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (hasDetails) {
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        if (isExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = if (isExpanded) "Show less" else "Show more details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        AppIcons.Edit,
                        contentDescription = "Edit item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Expanded Details (Progressive Disclosure)
        if (isExpanded && hasDetails) {
            HorizontalDivider(
                color = MaterialTheme.extendedColors.cardBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
            )

            // Conversion / Ratio Details
            if (item.conversionLabel.isNotBlank() && item.conversionLabel != "1 piece = 1 piece") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        AppIcons.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = item.conversionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Warning / Price Options / Pack Purchase
            if (!item.hasKnownPrice) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        AppIcons.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Price needs confirmation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else if (item.priceOptions.isNotEmpty()) {
                Text(
                    text = "Sizes: " + item.priceOptions.joinToString(" · ") { "${it.name} ${BudgetMath.money(it.price)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (item.purchasePrice != null && item.purchasePrice > 0.0) {
                Text(
                    text = "Pack purchase: ${BudgetMath.money(item.purchasePrice)} for ${item.purchaseLabel ?: item.stockUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Meal usage & notes
            if (item.mealUsage.isNotBlank()) {
                Text(
                    text = "Used in: ${item.mealUsage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (item.cleanUserNotes.isNotBlank()) {
                Text(
                    text = item.cleanUserNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) {
                    Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
