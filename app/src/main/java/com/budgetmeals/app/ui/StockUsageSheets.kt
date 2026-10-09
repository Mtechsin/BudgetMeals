package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.DatedUsage
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.data.Units
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.LocalDate
import java.util.UUID

@Composable
fun StockFormSheet(
    existing: StockItem?,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    val existingId = existing?.id
    val newStockId = remember(existingId) { existingId ?: UUID.randomUUID().toString() }
    var isSaving by remember(existingId) { mutableStateOf(false) }
    val existingCatalogId = existing?.catalogId
    var name by remember(existingId) { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember(existingId) { mutableStateOf(existing?.category ?: ItemCategory.FOOD_STAPLE) }
    var unit by remember(existingId) { mutableStateOf(existing?.unit ?: "piece") }
    var quantity by remember(existingId) { mutableStateOf(existing?.totalQuantity?.cleanNumber() ?: "") }
    var price by remember(existingId) { mutableStateOf(existing?.totalPrice?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "") }
    var batchType by remember(existingId) { mutableStateOf(existing?.batchType ?: BatchType.WEEKLY) }
    var usage by remember(existingId) { mutableStateOf(existing?.estimatedUsagePerDay?.cleanNumber() ?: "") }
    var notes by remember(existingId) { mutableStateOf(existing?.notes.orEmpty()) }
    var catalogId by remember(existingId) { mutableStateOf(existingCatalogId) }
    var catalogItems by remember(existingId) { mutableStateOf(snapshot.foodCatalog) }
    var catalogQuery by remember(existingId) { mutableStateOf("") }
    var portionUnit by remember(existingId) {
        mutableStateOf(snapshot.foodCatalog.firstOrNull { it.id == existingCatalogId }?.portionUnit ?: unit)
    }
    var rememberForNextShop by remember(existingId) { mutableStateOf(false) }
    // What one package of *this batch* holds, in the food's base unit. It belongs to the
    // batch: buying a different package must not reinterpret food already in the kitchen.
    var packageSizeInput by remember(existingId) {
        mutableStateOf(existing?.packageSize?.takeIf { it > 0.0 }?.cleanNumber() ?: "")
    }
    val parsedQuantity = quantity.asDouble()
    val parsedPrice = price.asDouble()
    val parsedUsage = usage.asDouble()
    val valid = name.isNotBlank() && parsedQuantity > 0.0 && parsedPrice >= 0.0
    val effectiveUsage = if (batchType == BatchType.SINGLE_MEAL && parsedUsage <= 0.0) parsedQuantity else parsedUsage
    val previewFood = catalogItems.firstOrNull { it.id == catalogId }
    val previewUnit = unit.trim().ifBlank { previewFood?.baseUnit ?: "piece" }
    val previewPackageSize = packageSizeInput.asDouble()
    val previewIsPackageEntry = previewFood != null && isPackageUnit(previewFood, previewUnit, previewPackageSize)
    val previewPackageFactor = if (previewIsPackageEntry) {
        if (previewPackageSize > 0.0) previewPackageSize else previewFood?.let { Units.basePerUnit(previewUnit, it) }
    } else null
    val previewQuantityInStoredUnit = when {
        previewFood == null -> parsedQuantity
        previewIsPackageEntry && previewPackageFactor != null -> parsedQuantity * previewPackageFactor
        else -> Units.convert(parsedQuantity, previewUnit, previewFood.baseUnit, previewFood)
    }
    val previewStoredUnit = if (previewQuantityInStoredUnit != null) previewFood?.baseUnit ?: previewUnit else previewUnit
    val previewConsumed = when {
        existing == null -> 0.0
        previewFood == null -> existing.consumedQuantity
        else -> Units.convert(existing.consumedQuantity, existing.unit, previewStoredUnit, previewFood)
            ?: existing.consumedQuantity
    }
    val previewTotal = previewQuantityInStoredUnit ?: parsedQuantity
    val remainingForEstimate = (previewTotal - previewConsumed).coerceAtLeast(0.0)
    val days = if (effectiveUsage > 0.0) remainingForEstimate / effectiveUsage else 999.0
    val costPerUnit = if (previewTotal > 0.0) parsedPrice / previewTotal else 0.0

    SheetBody(
        title = if (existing == null) "Bought something" else "Edit stock item",
        subtitle = if (existing == null) "Enter the block once. The calculator handles the rest." else "${existing.remainingQuantity.cleanNumber()} ${existing.unit} left · ${existing.daysLabel}",
        onClose = onDismiss,
    ) {
        if (existing?.isUsageEstimated == true && !existing.isFinished) {
            Surface(
                modifier = Modifier.padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = AccentMint.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        existing.daysLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentMintLight,
                    )
                    Text(
                        "· Estimated rate until usage is logged on 2+ days",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
        FormSectionTitle("Item")
        BudgetTextField(name, { name = it }, label = "What did you buy?")
        Spacer(Modifier.height(8.dp))
        FormSectionTitle("Category")
        if (existing == null) {
            DropdownField(
                label = "Category",
                value = category,
                values = ItemCategory.entries,
                labelOf = { it.label },
                onSelected = {
                    category = it
                    if (it == ItemCategory.HOUSEHOLD || it == ItemCategory.OTHER) {
                        catalogId = null
                        catalogQuery = ""
                    }
                },
            )
        } else {
            BudgetTextField(
                value = category.label,
                onValueChange = {},
                label = "Category",
                enabled = false,
            )
        }
        if (category != ItemCategory.HOUSEHOLD && category != ItemCategory.OTHER) {
            FormSectionTitle("Food catalog")
            val selectedFood = catalogItems.firstOrNull { it.id == catalogId }
            FoodPickerField(
                component = MealComponent(id = "stock-catalog", name = selectedFood?.name ?: name),
                snapshot = snapshot,
                catalogItems = catalogItems,
                query = catalogQuery,
                onQueryChanged = { catalogQuery = it },
                onSelected = { food ->
                    catalogId = food.id
                    category = food.category
                    name = food.name
                    unit = food.stockUnit
                    portionUnit = food.portionUnit
                    // A different food resets the batch's package size to what its catalog states.
                    packageSizeInput = if (existing == null) {
                        food.baseUnitsPerStockUnit
                            .takeIf { food.packageSize != null && it > 0.0 }
                            ?.cleanNumber()
                            .orEmpty()
                    } else {
                        packageSizeInput
                    }
                    catalogQuery = food.name
                },
                onCreate = { typed ->
                    val food = FoodCatalogItem(
                        name = typed.trim(),
                        category = category,
                        stockUnit = unit.ifBlank { "piece" },
                        portionUnit = unit.ifBlank { "piece" },
                        portionsPerStockUnit = 1.0,
                    )
                    catalogItems = catalogItems + food
                    catalogId = food.id
                    name = food.name
                    portionUnit = food.portionUnit
                    catalogQuery = food.name
                },
            )
            Text(
                "Linking lets meal quantities reduce this stock automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (selectedFood != null) {
                val spoonsM = selectedFood.measurement as? FoodMeasurementType.SpoonsToGrams
                if (spoonsM != null) {
                    var buyMode by remember(selectedFood.id) {
                        mutableStateOf(if (unit == "g") "grams" else "jar")
                    }
                    val packageGramsDefault = (selectedFood.baseUnitsPerStockUnit.takeIf { it > 0 } ?: 380.0).cleanNumber()

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = buyMode == "jar",
                            onClick = {
                                buyMode = "jar"
                                unit = selectedFood.stockUnit
                                packageSizeInput = packageSizeInput
                                    .takeIf { it.isNotBlank() }
                                    ?: packageGramsDefault
                            },
                            label = { Text("Buy ${selectedFood.stockUnit.replaceFirstChar { it.uppercase() }}") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = buyMode == "grams",
                            onClick = {
                                buyMode = "grams"
                                unit = "g"
                                packageSizeInput = ""
                            },
                            label = { Text("Buy by Grams") },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (buyMode == "jar") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            NumberField(
                                value = packageSizeInput,
                                onValueChange = { raw -> packageSizeInput = raw },
                                label = "${selectedFood.stockUnit} size (grams)",
                                placeholder = "e.g. 380",
                                prefix = "g ",
                                modifier = Modifier.weight(1f),
                            )
                            BudgetTextField(
                                value = portionUnit,
                                onValueChange = {},
                                label = "Portion unit",
                                modifier = Modifier.weight(1f),
                                enabled = false,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(250, 350, 380, 450, 500, 850, 1000).forEach { grams ->
                                TagChip(
                                    text = "${grams}g",
                                    selected = packageSizeInput == grams.toString(),
                                    onClick = {
                                        packageSizeInput = grams.toString()
                                    },
                                )
                            }
                        }

                        val jg = packageSizeInput.asDouble()
                            .takeIf { it > 0.0 }
                            ?: selectedFood.baseUnitsPerStockUnit.takeIf { it > 0.0 }
                            ?: 380.0
                        val spoonsInJar = jg / spoonsM.gramsPerSpoon
                        val totalSpoons = (parsedQuantity.coerceAtLeast(1.0)) * spoonsInJar
                        val spoonCost = if (parsedPrice > 0 && totalSpoons > 0) parsedPrice / totalSpoons else 0.0
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = AccentMint.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.3f)),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Jar spoon estimate",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = AccentMintLight,
                                )
                                Text(
                                    "1 ${selectedFood.stockUnit} (${jg.cleanNumber()}g) ≈ ${spoonsInJar.cleanNumber()} ${spoonsM.spoonUnitName}s · ${if (spoonCost > 0) "${BudgetMath.money(spoonCost)} / ${spoonsM.spoonUnitName}" else "Enter price below"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else {
                        val totalSpoons = (parsedQuantity / spoonsM.gramsPerSpoon).coerceAtLeast(0.0)
                        val spoonCost = if (parsedPrice > 0 && totalSpoons > 0) parsedPrice / totalSpoons else 0.0
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = AccentMint.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.3f)),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Grams spoon estimate",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = AccentMintLight,
                                )
                                Text(
                                    "${parsedQuantity.cleanNumber()}g ≈ ${totalSpoons.cleanNumber()} ${spoonsM.spoonUnitName}s · ${if (spoonCost > 0) "${BudgetMath.money(spoonCost)} / ${spoonsM.spoonUnitName}" else "Enter price below"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                } else {
                    val hasGenericConversion = selectedFood.portionsPerStockUnit > 1.0 &&
                        !selectedFood.stockUnit.equals(selectedFood.portionUnit, ignoreCase = true)
                    var buyInPortion by remember(selectedFood.id) {
                        mutableStateOf(unit.equals(selectedFood.portionUnit, ignoreCase = true))
                    }
                    if (hasGenericConversion) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !buyInPortion,
                                onClick = {
                                    buyInPortion = false
                                    unit = selectedFood.stockUnit
                                },
                                label = { Text("Buy in ${selectedFood.stockUnit}") },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentMint.copy(alpha = 0.2f),
                                    selectedLabelColor = AccentMintLight,
                                ),
                            )
                            FilterChip(
                                selected = buyInPortion,
                                onClick = {
                                    buyInPortion = true
                                    unit = selectedFood.portionUnit
                                },
                                label = { Text("Buy in ${selectedFood.portionUnit}") },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentMint.copy(alpha = 0.2f),
                                    selectedLabelColor = AccentMintLight,
                                ),
                            )
                        }

                        val pps = selectedFood.baseUnitsPerPortion.coerceAtLeast(0.0001)
                        val conversionText = if (buyInPortion) {
                            val equivBase = parsedQuantity * pps
                            val unitCost = if (parsedPrice > 0 && parsedQuantity > 0) parsedPrice / parsedQuantity else 0.0
                            "${parsedQuantity.cleanNumber()} ${selectedFood.portionUnit} ≈ ${equivBase.cleanNumber()} ${selectedFood.baseUnit} · Tracked as ${equivBase.cleanNumber()} ${selectedFood.baseUnit} in stock${if (unitCost > 0) " (${BudgetMath.money(unitCost)} / ${selectedFood.portionUnit})" else ""}"
                        } else {
                            val equivPortions = selectedFood.baseUnitsPerStockUnit.takeIf { it > 0.0 }
                                ?.let { parsedQuantity * selectedFood.baseUnitsPerStockUnit / pps }
                                ?: (parsedQuantity / pps)
                            val unitCost = if (parsedPrice > 0 && equivPortions > 0) parsedPrice / equivPortions else 0.0
                            "${parsedQuantity.cleanNumber()} $unit ≈ ${equivPortions.cleanNumber()} ${selectedFood.portionUnit}${if (unitCost > 0) " · ${BudgetMath.money(unitCost)} / ${selectedFood.portionUnit}" else ""}"
                        }

                        FoodKindSummary(conversionText, icon = AppIcons.Sync)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        NumberField(
                            value = packageSizeInput,
                            onValueChange = { packageSizeInput = it },
                            label = "This batch: one package holds (${selectedFood.baseUnit})",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            NumberField(quantity, { quantity = it }, "Quantity", Modifier.weight(1f))
            BudgetTextField(
                unit,
                { unit = it },
                label = "Unit",
                modifier = Modifier.weight(1f),
                enabled = catalogId == null,
            )
        }
        val selectedFoodForPackage = catalogItems.firstOrNull { it.id == catalogId }
        if (selectedFoodForPackage != null) {
            NumberField(
                value = packageSizeInput,
                onValueChange = { packageSizeInput = it },
                label = "One package holds (${selectedFoodForPackage.baseUnit})",
                placeholder = "optional",
                modifier = Modifier.padding(top = 12.dp),
                prefix = "${selectedFoodForPackage.baseUnit} ",
            )
            Text(
                purchasePreview(selectedFoodForPackage, unit, parsedQuantity, packageSizeInput.asDouble()),
                style = MaterialTheme.typography.bodySmall,
                color = if (storePreviewIsKnown(selectedFoodForPackage, unit, packageSizeInput.asDouble())) AccentMintLight else ErrorRed,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        NumberField(
            value = price,
            onValueChange = { price = it },
            label = "Price paid",
            placeholder = "0",
            modifier = Modifier.padding(top = 12.dp),
            prefix = "EGP ",
        )
        Spacer(Modifier.height(8.dp))
        FormSectionTitle("How long will it last?")
        DropdownField(
            label = "Batch type",
            value = batchType,
            values = BatchType.entries,
            labelOf = { it.label },
            onSelected = { batchType = it },
        )
        if (batchType != BatchType.SINGLE_MEAL) {
            val usageUnit = catalogItems.firstOrNull { it.id == catalogId }?.baseUnit ?: unit.ifBlank { "unit" }
            NumberField(
                usage,
                { usage = it },
                "Rough use per day ($usageUnit)",
                Modifier.padding(top = 12.dp),
                prefix = "about ",
            )
        } else {
            Text(
                "One-sitting items are used up on the day they are logged.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        BudgetTextField(notes, { notes = it }, label = "Note (optional)", modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(14.dp))
        AnimatedSaveHint(
            if (valid) {
                val hintUnit = if (previewQuantityInStoredUnit != null) {
                    previewFood?.baseUnit ?: unit.ifBlank { "unit" }
                } else {
                    previewUnit
                }
                "${BudgetMath.money(costPerUnit, 2)} per $hintUnit · ${if (days >= 999) "days left needs a daily use" else "${days.toInt()} days left"} · ${BudgetMath.money(costPerUnit * effectiveUsage, 1)}/day"
            } else {
                "Name, quantity, and price are enough to start."
            },
        )
        if (existing == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Checkbox(checked = rememberForNextShop, onCheckedChange = { rememberForNextShop = it }, colors = sheetCheckboxColors())
                Column(Modifier.weight(1f)) {
                    Text("Remember for the next shop", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text("It will appear on the shopping list automatically.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Checkbox(checked = rememberForNextShop, onCheckedChange = { rememberForNextShop = it }, colors = sheetCheckboxColors())
                Text("Add this item to the next shop", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = if (existing == null) "Save purchase" else "Save changes",
            onClick = {
                if (isSaving) return@PrimaryButton
                isSaving = true
                val food = catalogItems.firstOrNull { it.id == catalogId }
                // Everything is stored in the food's base unit, using the batch's own package
                // size when the user stated one. The catalog is never rewritten from here.
                val typedUnit = unit.trim().ifBlank { food?.baseUnit ?: "piece" }
                val typedPackageSize = packageSizeInput.asDouble()
                val isPackageEntry = food != null && isPackageUnit(food, typedUnit, typedPackageSize)
                val packageFactor = if (isPackageEntry) {
                    if (typedPackageSize > 0.0) typedPackageSize else food?.let { Units.basePerUnit(typedUnit, it) }
                } else null

                val totalBase = when {
                    food == null -> parsedQuantity
                    isPackageEntry && packageFactor != null -> parsedQuantity * packageFactor
                    else -> Units.convert(parsedQuantity, typedUnit, food.baseUnit, food)
                        ?: parsedQuantity
                }
                val isSingleMeal = batchType == BatchType.SINGLE_MEAL
                val inputConverted = food == null || (isPackageEntry && packageFactor != null) ||
                    Units.convert(parsedQuantity, typedUnit, food.baseUnit, food) != null
                val storedUnit = if (inputConverted) food?.baseUnit ?: typedUnit else typedUnit
                val consumedBase = when {
                    food == null -> existing?.consumedQuantity ?: 0.0
                    existing == null -> 0.0
                    else -> Units.convert(existing.consumedQuantity, existing.unit, storedUnit, food)
                        ?: existing.consumedQuantity
                }
                val consumedConverted = food == null || existing == null ||
                    Units.convert(existing.consumedQuantity, existing.unit, storedUnit, food) != null
                val convertedUsageHistory = existing?.usageHistory.orEmpty().map { amount ->
                    if (food == null || existing == null) amount
                    else Units.convert(amount, existing.unit, storedUnit, food)
                }
                val convertedDatedUsageHistory = existing?.datedUsageHistory.orEmpty().map { dated ->
                    val amount = if (food == null || existing == null) dated.amount
                    else Units.convert(dated.amount, existing.unit, storedUnit, food)
                    amount?.let { dated.copy(amount = it) }
                }
                val historiesConverted = convertedUsageHistory.all { it != null } &&
                    convertedDatedUsageHistory.all { it != null }
                val finalConsumed = if (isSingleMeal) totalBase else consumedBase
                val converted = inputConverted && consumedConverted && historiesConverted

                val batchPackageLabel = when {
                    !isPackageEntry -> ""
                    typedPackageSize > 0.0 && typedPackageSize != food.baseUnitsPerStockUnit ->
                        "${Units.containerOf(typedUnit) ?: typedUnit} (${typedPackageSize.cleanNumber()}${food.baseUnit})"
                    else -> food?.packageLabel?.takeIf { it.isNotBlank() } ?: typedUnit
                }
                val batchPackageSize = if (isPackageEntry) (packageFactor ?: 0.0) else 0.0
                val usageHistory = when {
                    existing == null && isSingleMeal -> listOf(totalBase)
                    existing == null -> emptyList()
                    historiesConverted -> convertedUsageHistory.mapNotNull { it }
                    else -> existing.usageHistory
                }
                val datedUsageHistory = when {
                    existing == null && isSingleMeal -> listOf(DatedUsage(existing?.purchaseDate ?: LocalDate.now(), totalBase))
                    existing == null -> emptyList()
                    historiesConverted -> convertedDatedUsageHistory.mapNotNull { it }
                    else -> existing.datedUsageHistory
                }

                val item = StockItem(
                    id = newStockId,
                    name = name.trim(),
                    category = category,
                    unit = storedUnit,
                    totalQuantity = totalBase.coerceAtLeast(finalConsumed),
                    totalPrice = parsedPrice,
                    purchaseDate = existing?.purchaseDate ?: LocalDate.now(),
                    expiryDate = existing?.expiryDate,
                    batchType = batchType,
                    estimatedUsagePerDay = effectiveUsage,
                    consumedQuantity = finalConsumed,
                    usageHistory = usageHistory,
                    datedUsageHistory = datedUsageHistory,
                    notes = notes.trim(),
                    catalogId = food?.id ?: catalogId,
                    packageLabel = batchPackageLabel,
                    packageSize = batchPackageSize,
                    conversionKnown = converted,
                )
                if (existing == null) {
                    viewModel.recordPurchase(item, rememberForNextShop)
                } else {
                    viewModel.saveStock(item, rememberForNextShop)
                }
                onDismiss()
            },
            enabled = valid && !isSaving,
            icon = AppIcons.Check,
        )
        if (existing != null) {
            OutlinedButton(
                onClick = {
                    viewModel.deleteStock(existing)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ErrorBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 50.dp),
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete stock item", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Plain-language preview of what saving will store, e.g. "2 carton (30) adds 60 egg".
 * Package labels are read as sizes here, never matched as text.
 */
private fun purchasePreview(
    food: FoodCatalogItem,
    typedUnit: String,
    quantity: Double,
    packageSize: Double,
): String {
    if (quantity <= 0.0) return "Enter how much you bought."
    val unit = typedUnit.trim().ifBlank { food.baseUnit }
    val isPackageEntry = isPackageUnit(food, unit, packageSize)
    if (isPackageEntry) {
        val factor = if (packageSize > 0.0) packageSize else (Units.basePerUnit(unit, food) ?: food.baseUnitsPerStockUnit)
        val base = quantity * factor
        return "${quantity.cleanNumber()} $unit adds ${base.cleanNumber()} ${food.baseUnit}"
    }
    val converted = Units.convert(quantity, unit, food.baseUnit, food)
        ?: return "We cannot convert $unit to ${food.baseUnit} yet, so this batch is kept as entered and flagged."
    return if (converted == quantity) {
        "${quantity.cleanNumber()} ${food.baseUnit} of ${food.name} will be tracked."
    } else {
        "${quantity.cleanNumber()} $unit adds ${converted.cleanNumber()} ${food.baseUnit}"
    }
}

private fun storePreviewIsKnown(food: FoodCatalogItem, typedUnit: String, packageSize: Double): Boolean {
    val unit = typedUnit.trim().ifBlank { food.baseUnit }
    if (isPackageUnit(food, unit, packageSize)) return true
    return Units.convert(1.0, unit, food.baseUnit, food) != null
}

private fun isPackageUnit(food: FoodCatalogItem, unit: String, packageSize: Double): Boolean {
    if (Units.sameUnit(unit, food.baseUnit)) return false

    if (packageSize > 0.0) {
        val typed = Units.normalize(unit)
        val baseNorm = Units.normalize(food.baseUnit)
        if (typed != baseNorm) return true
    }
    val typed = Units.normalize(unit)
    val packageLabel = food.packageLabel
    if (packageLabel != null && typed == Units.normalize(packageLabel)) return true
    if (Units.sameUnit(unit, food.stockUnit) && (Units.containerOf(food.stockUnit) != null || food.packageSize != null)) return true
    val container = Units.containerOf(food.stockUnit)
    return container != null && container == typed
}

@Composable
fun UsageFormSheet(
    item: StockItem,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    val catalog = snapshot.foodCatalog.firstOrNull { it.id == item.catalogId }
        ?: snapshot.foodCatalog.firstOrNull { it.name.equals(item.name, ignoreCase = true) }
    // Portions only exist when the food has a real conversion and this batch is stored in
    // the same base unit the catalog converts to.
    val perPortion = catalog?.baseUnitsPerPortion?.takeIf { it > 0.0 }
    val hasConversion = catalog != null && perPortion != null &&
        item.conversionKnown && Units.sameUnit(item.unit, catalog.baseUnit)
    var usePortionUnit by remember(item.id) { mutableStateOf(hasConversion) }
    var amount by remember(item.id, usePortionUnit) { mutableStateOf("") }
    val value = amount.asDouble()

    val currentUnit = if (usePortionUnit) catalog?.portionUnit ?: item.unit else item.unit
    val remainingInCurrentUnit = if (usePortionUnit && perPortion != null) {
        item.remainingQuantity / perPortion
    } else {
        item.remainingQuantity
    }
    val valid = value > 0.0 && value <= remainingInCurrentUnit + 0.001

    val convertedSubtitle = if (hasConversion) {
        catalog?.let { food ->
            perPortion?.let { portionSize ->
                val totalPortions = item.remainingQuantity / portionSize
                "${item.name} has ${item.remainingQuantity.cleanNumber()} ${item.unit} (≈ ${totalPortions.cleanNumber()} ${food.portionUnit}) left · ${item.daysLabel}"
            }
        }
    } else {
        null
    }
    val subtitle = convertedSubtitle
        ?: "${item.name} has ${item.remainingQuantity.cleanNumber()} ${item.unit} left · ${item.daysLabel}"

    SheetBody("Log use", subtitle, onDismiss) {
        if (item.isUsageEstimated && !item.isFinished) {
            Surface(
                modifier = Modifier.padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = AccentMint.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        item.daysLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentMintLight,
                    )
                    Text(
                        "· Estimated rate until usage is logged on 2+ days",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
        if (hasConversion) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = usePortionUnit,
                    onClick = { usePortionUnit = true },
                    label = { Text("Use: ${catalog?.portionUnit ?: item.unit}") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentMint.copy(alpha = 0.2f),
                        selectedLabelColor = AccentMintLight,
                    ),
                )
                FilterChip(
                    selected = !usePortionUnit,
                    onClick = { usePortionUnit = false },
                    label = { Text("Stock: ${item.unit}") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentMint.copy(alpha = 0.2f),
                        selectedLabelColor = AccentMintLight,
                    ),
                )
            }
        }
        FormSectionTitle("Date")
        var usageDate by remember { mutableStateOf(LocalDate.now()) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = usageDate == LocalDate.now(),
                onClick = { usageDate = LocalDate.now() },
                label = { Text("Today") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = usageDate == LocalDate.now().minusDays(1),
                onClick = { usageDate = LocalDate.now().minusDays(1) },
                label = { Text("Yesterday") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = usageDate == LocalDate.now().minusDays(2),
                onClick = { usageDate = LocalDate.now().minusDays(2) },
                label = { Text("2 days ago") },
                modifier = Modifier.weight(1f),
            )
        }
        FormSectionTitle("How much?")
        // Universal slider: use-units on top, exact field as override. Same math on save.
        val maxVal = remainingInCurrentUnit.coerceAtLeast(0.0)
        val sliderVal = value.coerceIn(0.0, maxVal).toFloat()
        val pctUsed = if (maxVal > 0.0) value / maxVal else 0.0
        val useLabel = when {
            perPortion != null && usePortionUnit -> {
                val baseEquiv = value * perPortion
                "${value.cleanNumber()} ${currentUnit} (≈ ${baseEquiv.cleanNumber()} ${item.unit}) · ${(pctUsed * 100).toInt()}% of remaining"
            }
            else -> "${value.cleanNumber()} ${currentUnit} · ${(pctUsed * 100).toInt()}% of remaining"
        }
        Slider(
            value = sliderVal,
            onValueChange = { v -> amount = v.toDouble().cleanNumber() },
            valueRange = 0f..maxVal.toFloat().coerceAtLeast(0.001f),
            enabled = maxVal > 0.0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(useLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(maxVal.cleanNumber(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        NumberField(amount, { amount = it }, "Exact amount (optional)", prefix = "$currentUnit ")
        Spacer(Modifier.height(10.dp))
        val newRemaining = (remainingInCurrentUnit - value).coerceAtLeast(0.0)
        val saveHint = if (valid) {
            val portionForHint = if (usePortionUnit) perPortion else null
            if (portionForHint != null) {
                val newBase = (item.remainingQuantity - value * portionForHint).coerceAtLeast(0.0)
                "New remaining: ${newRemaining.cleanNumber()} $currentUnit (≈ ${newBase.cleanNumber()} ${item.unit})."
            } else {
                "New remaining: ${newRemaining.cleanNumber()} ${item.unit}. This helps the next estimate."
            }
        } else {
            "Use a number up to ${remainingInCurrentUnit.cleanNumber()}."
        }
        AnimatedSaveHint(saveHint)
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Save usage", {
            val stockAmount = if (usePortionUnit && perPortion != null) {
                value * perPortion
            } else {
                value
            }
            viewModel.logUsage(item, stockAmount, usageDate)
            onDismiss()
        }, enabled = valid, icon = AppIcons.Check)
    }
}
