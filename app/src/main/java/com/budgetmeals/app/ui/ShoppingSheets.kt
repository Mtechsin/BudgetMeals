package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalDate
import java.util.UUID

private val COMMON_UNITS = listOf("piece", "kg", "g", "pack", "can", "bottle", "liter")

@Composable
internal fun ShoppingFormSheet(
    existing: ShoppingItem?,
    snapshot: AppSnapshot? = null,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    val itemId = rememberSaveable(existing?.id) { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(existing?.quantity?.cleanNumber() ?: "1") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: "piece") }
    var isPriceKnown by rememberSaveable { mutableStateOf(existing?.priceKnown ?: true) }
    var price by rememberSaveable {
        mutableStateOf(
            existing?.let { if (it.priceKnown) it.estimatedPrice.cleanNumber() else "" } ?: ""
        )
    }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: ItemCategory.FOOD_STAPLE) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    val submission = rememberFormSubmission(onDismiss)

    val catalogItems = snapshot?.foodCatalog.orEmpty()
    val normalizedQuery = name.trim().lowercase()

    // Filter catalog suggestions matching input or top items if blank
    val matchingCatalogItems = remember(catalogItems, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            catalogItems.take(8)
        } else {
            catalogItems.filter { it.name.lowercase().contains(normalizedQuery) }.take(6)
        }
    }

    val parsedQty = quantity.asDouble()
    val parsedPrice = price.asDouble()
    val isValid = name.isNotBlank() && parsedQty > 0.0 &&
        (!isPriceKnown || price.isNonNegativeNumber(allowBlank = true))

    SheetBody(
        title = if (existing == null) "Add to shopping" else "Edit item",
        subtitle = "Plan what to pick up in your next market run",
        onClose = onDismiss,
    ) {
        // 1. Food Catalog Suggestions
        if (matchingCatalogItems.isNotEmpty() && (existing == null || name.isBlank())) {
            Text(
                text = if (name.isBlank()) "Quick suggestions from pantry catalog" else "Matches in catalog",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                matchingCatalogItems.forEach { food ->
                    val estimatedItemPrice = food.purchasePrice ?: (if (food.defaultCostPerPortion > 0.0) food.defaultCostPerPortion * food.portionsPerStockUnit else null)
                    Surface(
                        onClick = {
                            name = food.name
                            unit = food.baseUnit.ifBlank { "piece" }
                            category = food.category
                            if (estimatedItemPrice != null && estimatedItemPrice > 0.0) {
                                price = (estimatedItemPrice * parsedQty.coerceAtLeast(1.0)).cleanNumber()
                                isPriceKnown = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                shoppingCategoryIcon(food.category),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = shoppingCategoryColor(food.category),
                            )
                            Text(
                                text = food.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        // 2. Item Name
        BudgetTextField(
            value = name,
            onValueChange = { name = it },
            label = "Item name",
            placeholder = "e.g. Baladi bread, eggs, olive oil",
            modifier = Modifier.fillMaxWidth(),
        )

        // 3. Quantity & Stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Decrement button
                    Surface(
                        onClick = {
                            val current = parsedQty
                            if (current > 1.0) {
                                quantity = (current - 1.0).cleanNumber()
                            } else if (current > 0.25) {
                                quantity = (current - 0.25).cleanNumber()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(18.dp))
                        }
                    }

                    NumberField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = "Qty",
                        modifier = Modifier.weight(1f),
                    )

                    // Increment button
                    Surface(
                        onClick = {
                            val current = parsedQty
                            quantity = (current + 1.0).cleanNumber()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            BudgetTextField(
                value = unit,
                onValueChange = { unit = it },
                label = "Unit",
                modifier = Modifier.weight(1f),
            )
        }

        // Common unit chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            COMMON_UNITS.forEach { commonUnit ->
                val isSelected = unit.equals(commonUnit, ignoreCase = true)
                Surface(
                    onClick = { unit = commonUnit },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.cardBorder,
                    ),
                ) {
                    Text(
                        text = commonUnit,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // 4. Category Selector
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ItemCategory.entries.forEach { cat ->
                val isSelected = category == cat
                Surface(
                    onClick = { category = cat },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) shoppingCategoryColor(cat).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) shoppingCategoryColor(cat) else MaterialTheme.extendedColors.cardBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            shoppingCategoryIcon(cat),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (isSelected) shoppingCategoryColor(cat) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 5. Price Estimate Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Estimated price",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = { isPriceKnown = true },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPriceKnown) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (isPriceKnown) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.cardBorder),
                ) {
                    Text(
                        text = "Price known",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPriceKnown) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Surface(
                    onClick = {
                        isPriceKnown = false
                        price = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isPriceKnown) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (!isPriceKnown) MaterialTheme.colorScheme.secondary else MaterialTheme.extendedColors.cardBorder),
                ) {
                    Text(
                        text = "Check in store",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isPriceKnown) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        if (isPriceKnown) {
            NumberField(
                value = price,
                onValueChange = { price = it },
                label = "Expected total price",
                prefix = "EGP ",
                placeholder = "0.00",
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            ) {
                Text(
                    text = "You can record the exact price paid at the store when you check off this item.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // 6. Optional Note
        BudgetTextField(
            value = note,
            onValueChange = { note = it },
            label = "Note (optional)",
            placeholder = "e.g. Specific brand, size, stall in market",
            modifier = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.height(20.dp))

        // 7. Save CTA
        PrimaryButton(
            text = if (existing == null) "Add to shopping list" else "Save changes",
            onClick = {
                val hasPrice = isPriceKnown && price.isNonNegativeNumber()
                val finalPrice = if (hasPrice) parsedPrice else 0.0
                submission.submit { complete ->
                    viewModel.saveShopping(
                        ShoppingItem(
                            id = itemId,
                            name = name.trim(),
                            quantity = parsedQty,
                            unit = unit.trim().ifBlank { "piece" },
                            estimatedPrice = finalPrice,
                            category = category,
                            isChecked = existing?.isChecked ?: false,
                            createdDate = existing?.createdDate ?: LocalDate.now(),
                            sourceItemId = existing?.sourceItemId,
                            note = note.trim(),
                            purchaseExpenseId = existing?.purchaseExpenseId,
                            purchaseStockId = existing?.purchaseStockId,
                            priceKnown = hasPrice,
                        ),
                        onComplete = complete,
                    )
                }
            },
            enabled = isValid && !submission.isSaving,
            icon = AppIcons.Check,
        )

        // 8. Delete option when editing
        if (existing != null) {
            OutlinedButton(
                onClick = {
                    viewModel.deleteShopping(existing)
                    onDismiss()
                },
                enabled = !submission.isSaving,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 48.dp),
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove from shopping list", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Buy / Purchase checkout sheet.
 * Confirms actual price paid, displays budget impact, and adds to stock automatically.
 */
@Composable
internal fun BuyShoppingSheet(
    item: ShoppingItem,
    snapshot: AppSnapshot? = null,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    var price by remember(item.id) {
        mutableStateOf(
            if (item.priceKnown) item.estimatedPrice.cleanNumber() else ""
        )
    }
    var rememberForNextShop by remember(item.id) { mutableStateOf(false) }
    val submission = rememberFormSubmission(onDismiss)

    val parsedPrice = price.asDouble()
    val isValid = price.isNonNegativeNumber()

    // Budget impact calculation
    val todayFoodBudgetRemaining = snapshot?.todayFoodRemaining ?: 0.0
    val expenseCategoryId = when (item.category) {
        ItemCategory.HOUSEHOLD -> "household"
        ItemCategory.OTHER -> "other"
        else -> "food"
    }
    val countsAsFood = snapshot?.categories?.firstOrNull { it.id == expenseCategoryId }?.isFood
        ?: (expenseCategoryId == "food")
    val budgetAfterPurchase = todayFoodBudgetRemaining - if (countsAsFood) parsedPrice else 0.0

    SheetBody(
        title = "Buy ${item.name}",
        subtitle = "Mark item as purchased and add to pantry stock",
        onClose = onDismiss,
    ) {
        // 1. Item Summary Card
        SoftCard(
            contentPadding = 14.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = shoppingCategoryIcon(item.category),
                    tint = shoppingCategoryColor(item.category),
                    size = 42.dp,
                    iconSize = 22.dp,
                )

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${item.quantity.compact()} ${item.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "· ${item.category.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (item.priceKnown && item.estimatedPrice > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Estimated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = BudgetMath.money(item.estimatedPrice),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // 2. Quick Estimate Fill Shortcut
        if (item.priceKnown && item.estimatedPrice > 0.0 && price != item.estimatedPrice.cleanNumber()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    onClick = { price = item.estimatedPrice.cleanNumber() },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = "Use estimate: ${BudgetMath.money(item.estimatedPrice)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // 3. Actual Price Input
        FormSectionTitle("Actual price paid")
        NumberField(
            value = price,
            onValueChange = { price = it },
            label = "Total price paid",
            prefix = "EGP ",
            placeholder = "0.00",
        )

        // 4. Budget Impact Preview
        if (snapshot != null && isValid && countsAsFood) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (budgetAfterPurchase >= 0.0) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                },
                border = BorderStroke(
                    1.dp,
                    if (budgetAfterPurchase >= 0.0) MaterialTheme.extendedColors.cardBorder else MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Today's food budget after purchase",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (budgetAfterPurchase >= 0.0) {
                                "${BudgetMath.money(budgetAfterPurchase)} remaining today"
                            } else {
                                "${BudgetMath.money(-budgetAfterPurchase)} over today's budget"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (budgetAfterPurchase >= 0.0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = if (budgetAfterPurchase >= 0.0) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        },
                    ) {
                        Text(
                            text = if (budgetAfterPurchase >= 0.0) "Within budget" else "Over budget",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (budgetAfterPurchase >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // 5. Repeat & Stock Notes
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Checkbox(
                checked = rememberForNextShop,
                onCheckedChange = { rememberForNextShop = it },
                colors = sheetCheckboxColors(),
            )
            Text(
                text = "Keep item on shopping list for next time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(10.dp))
        AnimatedSaveHint(if (countsAsFood) {
            "This purchase will be counted in today's food spending and added to your pantry stock."
        } else {
            "This purchase will be counted in spending and added to your stock."
        })

        Spacer(Modifier.height(18.dp))

        // 6. Confirm CTA
        PrimaryButton(
            text = if (isValid) "Confirm purchase · ${BudgetMath.money(parsedPrice)}" else "Confirm purchase",
            onClick = {
                submission.submit { complete ->
                    viewModel.buyShoppingItem(item, parsedPrice, rememberForNextShop, complete)
                }
            },
            enabled = isValid && !submission.isSaving,
            icon = AppIcons.Check,
        )
    }
}
