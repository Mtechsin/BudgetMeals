package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.data.Units
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import com.budgetmeals.app.ui.theme.extendedColors
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay

private val FOOD_CLASSIFICATIONS = listOf(
    "ready_to_eat" to "Ready-to-eat",
    "street_food" to "Street food",
    "snack" to "Snack",
    "groceries" to "Groceries",
    "restaurant" to "Restaurant",
    "other" to "Other",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogFoodSheet(
    snapshot: AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    val editorIdentity = "log_food"
    val draft = rememberSheetDraft(editorIdentity, onDismiss)
    val draftKey = draft.draftKey

    var amount by rememberSaveable(key = "${draftKey}_amount") { mutableStateOf("") }
    var foodName by rememberSaveable(key = "${draftKey}_name") { mutableStateOf("") }
    var isGroceries by rememberSaveable(key = "${draftKey}_groceries") { mutableStateOf(false) }
    var quantity by rememberSaveable(key = "${draftKey}_qty") { mutableStateOf("1") }
    var unit by rememberSaveable(key = "${draftKey}_unit") { mutableStateOf("pack") }
    var date by rememberSaveable(key = "${draftKey}_date", stateSaver = localDateSaver) {
        mutableStateOf(snapshot.today)
    }
    var classification by rememberSaveable(key = "${draftKey}_class") {
        mutableStateOf(if (isGroceries) "groceries" else "ready_to_eat")
    }
    var notes by rememberSaveable(key = "${draftKey}_notes") { mutableStateOf("") }
    var showMoreDetails by rememberSaveable(key = "${draftKey}_more") { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focusRequester.requestFocus() }
    }

    // Match catalog item when name is typed
    val matchedCatalog = remember(foodName, snapshot.foodCatalogByName) {
        val trimmed = foodName.trim().lowercase(Locale.US)
        if (trimmed.isNotBlank()) snapshot.foodCatalogByName[trimmed] else null
    }

    // Update classification and unit when switching to/from groceries
    LaunchedEffect(isGroceries) {
        if (isGroceries) {
            if (classification == "ready_to_eat") classification = "groceries"
            if (matchedCatalog != null) {
                unit = matchedCatalog.stockUnit.ifBlank { "pack" }
            } else if (unit.isBlank() || unit == "piece") {
                unit = "pack"
            }
        } else {
            if (classification == "groceries") classification = "ready_to_eat"
        }
    }

    // When catalog matches, update unit if appropriate
    LaunchedEffect(matchedCatalog) {
        if (isGroceries && matchedCatalog != null) {
            unit = matchedCatalog.stockUnit.ifBlank { matchedCatalog.baseUnit }
        }
    }

    // Up to two recent purchases as shortcuts
    val recentShortcuts = remember(snapshot.expenses) {
        val foodCategoryIds = snapshot.categories.filter { it.isFood }.map { it.id }.toSet()
        snapshot.expenses
            .filter {
                it.categoryId in foodCategoryIds &&
                    !it.isBudgetTransfer &&
                    it.description.isNotBlank() &&
                    it.description != "Food" &&
                    !it.description.startsWith("Bought ")
            }
            .sortedByDescending { it.date }
            .distinctBy { it.description.lowercase(Locale.US) }
            .take(2)
    }

    val parsedAmount = amount.asDouble()
    val parsedQty = quantity.asDouble()
    val isAmountValid = parsedAmount > 0.0 && amount.isNonNegativeNumber()
    val isNameValid = !isGroceries || foodName.isNotBlank()
    val isQtyValid = !isGroceries || (parsedQty > 0.0 && quantity.isNonNegativeNumber())
    val canSave = isAmountValid && isNameValid && isQtyValid && !draft.submission.isSaving

    SheetBody(
        title = "Log food",
        subtitle = "Record what you spent on food today",
        onClose = draft.dismiss,
    ) {
        // Amount field: automatic focus
        NumberField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount",
            prefix = "EGP ",
            modifier = Modifier.focusRequester(focusRequester),
            isError = amount.isNotBlank() && !isAmountValid,
            supportingText = if (amount.isNotBlank() && !isAmountValid) ({
                Text("Enter an amount greater than 0 EGP.")
            }) else null,
        )

        Spacer(Modifier.height(12.dp))

        // Food name (optional for ready-to-eat; required for groceries)
        BudgetTextField(
            value = foodName,
            onValueChange = { foodName = it },
            label = if (isGroceries) "Item name (required for stock)" else "Food or place (optional)",
            placeholder = if (isGroceries) "e.g. Eggs, Rice, Milk" else "e.g. Falafel, Koshary, Cafe",
            modifier = Modifier.fillMaxWidth(),
            isError = isGroceries && foodName.isBlank() && amount.isNotBlank(),
            supportingText = if (isGroceries && foodName.isBlank() && amount.isNotBlank()) ({
                Text("Groceries for later need an item name so stock stays organized.")
            }) else null,
        )

        // Recent purchase shortcuts
        if (recentShortcuts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                recentShortcuts.forEach { shortcut ->
                    Surface(
                        onClick = {
                            amount = shortcut.amount.cleanNumber()
                            foodName = shortcut.description
                            shortcut.foodClassification?.let { classification = it }
                            if (shortcut.foodClassification == "groceries") {
                                isGroceries = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = shortcut.description,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = BudgetMath.money(shortcut.amount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // "Groceries for later" switch
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isGroceries) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (isGroceries) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.extendedColors.cardBorder,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Groceries for later",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isGroceries) "Updates spending and adds to kitchen stock" else "Records meal spending without creating pantry stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isGroceries,
                    onCheckedChange = { isGroceries = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary,
                    ),
                )
            }
        }

        // When Groceries for later is enabled: show quantity and unit
        AnimatedVisibility(visible = isGroceries) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NumberField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = "Quantity",
                        modifier = Modifier.weight(1f),
                    )
                    BudgetTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = "Unit",
                        modifier = Modifier.weight(1f),
                        placeholder = "pack",
                    )
                }

                val isConversionKnown = matchedCatalog?.hasUsableConversion == true
                if (foodName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (matchedCatalog != null) {
                            if (isConversionKnown) {
                                "Reusing catalog: ${matchedCatalog.name} (${matchedCatalog.conversionLabel})"
                            } else {
                                "Unfamiliar conversions for ${matchedCatalog.name}: flagged as unknown conversion."
                            }
                        } else {
                            "Unfamiliar item: default to 1 pack with conversion flagged as unknown."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isConversionKnown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // More details toggle
        OutlinedButton(
            onClick = { showMoreDetails = !showMoreDetails },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showMoreDetails) "Hide details" else "More details (date, type, notes)",
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = if (showMoreDetails) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // More details section
        AnimatedVisibility(visible = showMoreDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Date picker
                Column {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = date == snapshot.today,
                            onClick = { date = snapshot.today },
                            label = { Text("Today") },
                        )
                        FilterChip(
                            selected = date == snapshot.today.minusDays(1),
                            onClick = { date = snapshot.today.minusDays(1) },
                            label = { Text("Yesterday") },
                        )
                        if (date != snapshot.today && date != snapshot.today.minusDays(1)) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text(BudgetMath.formatDate(date)) },
                            )
                        }
                    }
                }

                // Food classification
                Column {
                    Text(
                        text = "Classification",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FOOD_CLASSIFICATIONS.forEach { (key, label) ->
                            FilterChip(
                                selected = classification == key,
                                onClick = { classification = key },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                // Notes
                BudgetTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes (optional)",
                    placeholder = "Any extra context",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Save button
        PrimaryButton(
            text = "Save",
            onClick = {
                if (!canSave) return@PrimaryButton
                val trimmedName = foodName.trim()
                val parsedCost = parsedAmount
                val chosenDate = date

                draft.submission.submit { complete ->
                    if (isGroceries) {
                        // Saving groceries updates spending and stock together
                        val catalog = matchedCatalog
                        val rawUnit = unit.trim().ifBlank { "pack" }
                        val convertedQty = if (catalog != null && catalog.hasUsableConversion) {
                            Units.convert(parsedQty, rawUnit, catalog.baseUnit, catalog)
                        } else null
                        val isKnownConversion = convertedQty != null
                        val storedUnit = if (isKnownConversion && catalog != null) catalog.baseUnit else rawUnit
                        val finalQuantity = convertedQty ?: parsedQty

                        val stockItem = StockItem(
                            id = UUID.randomUUID().toString(),
                            name = trimmedName,
                            category = catalog?.category ?: ItemCategory.FOOD_STAPLE,
                            unit = storedUnit,
                            totalQuantity = finalQuantity,
                            totalPrice = parsedCost,
                            purchaseDate = chosenDate,
                            batchType = BatchType.CUSTOM,
                            notes = notes.trim(),
                            catalogId = catalog?.id,
                            conversionKnown = isKnownConversion,
                        )

                        viewModel.recordPurchase(
                            item = stockItem,
                            rememberForNextShop = false,
                            catalogItem = catalog,
                            onComplete = complete,
                        )
                    } else {
                        // Saving ready-to-eat food records spending without creating pantry stock
                        viewModel.recordFoodExpense(
                            amount = parsedCost,
                            date = chosenDate,
                            name = trimmedName,
                            classification = classification,
                            notes = notes.trim(),
                            onComplete = complete,
                        )
                    }
                }
            },
            enabled = canSave,
            icon = AppIcons.Check,
        )
    }
}
