package com.budgetmeals.app.ui

import com.budgetmeals.app.ui.icons.AppIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodMeasurementCodec
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.ui.theme.extendedColors


@Composable
fun FoodItemFormSheet(
    existing: FoodCatalogItem?,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    val state = remember(existing?.id) { FoodItemFormState(existing) }
    with(state) {
        val cleanQuery = name.trim().lowercase()
        val isAutoFilled = appliedPresetName != null && name.equals(appliedPresetName, ignoreCase = true)
        val showSuggestions = cleanQuery.isNotBlank() && !isSuggestionsDismissed && !isAutoFilled

        val matchingPresets = remember(cleanQuery) {
            if (cleanQuery.isBlank()) emptyList()
            else FoodMeasurementCodec.presets.filter {
                it.name.lowercase().contains(cleanQuery) || it.title.lowercase().contains(cleanQuery)
            }
        }
        val matchingCatalogItems = remember(cleanQuery, snapshot.foodCatalog) {
            if (cleanQuery.isBlank()) emptyList()
            else snapshot.foodCatalog.filter {
                it.name.lowercase().contains(cleanQuery) &&
                    matchingPresets.none { p -> p.name.equals(it.name, ignoreCase = true) }
            }
        }

        SheetBody(
        if (existing == null) "Add food item" else "Edit food item",
        "Set portion measurements, spoons-to-grams conversion, or snack sizes.",
        onDismiss,
    ) {
        BudgetTextField(
            value = name,
            onValueChange = {
                name = it
                isSuggestionsDismissed = false
                if (appliedPresetName != null && !it.equals(appliedPresetName, ignoreCase = true)) {
                    appliedPresetName = null
                }
            },
            label = "Food name",
            placeholder = "e.g. Baladi bread, Eggs, Feta cheese...",
            trailingIcon = if (name.isNotBlank()) {
                {
                    IconButton(onClick = {
                        name = ""
                        appliedPresetName = null
                        isSuggestionsDismissed = false
                    }) {
                        Icon(
                            AppIcons.Close,
                            contentDescription = "Clear food name",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else null,
        )

        // Live suggestions popup when typing
        if (showSuggestions && (matchingPresets.isNotEmpty() || matchingCatalogItems.isNotEmpty())) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
                tonalElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Suggestions (tap to auto-fill)",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentMintLight,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier
                                .clickable { isSuggestionsDismissed = true }
                                .padding(4.dp),
                        )
                    }

                    matchingPresets.take(5).forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    applyPreset(preset)
                                    appliedPresetName = preset.name
                                    isSuggestionsDismissed = true
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryIcon(
                                    iconName = preset.category.icon,
                                    modifier = Modifier.padding(end = 8.dp).size(24.dp),
                                    tint = AccentMintLight,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        preset.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                    val priceDesc = if (preset.portionsPerStockUnit > 1.0) {
                                        "${(preset.defaultCostPerPortion * preset.portionsPerStockUnit).cleanNumber()} EGP / ${preset.stockUnit}"
                                    } else {
                                        "${preset.defaultCostPerPortion.cleanNumber()} EGP / ${preset.portionUnit}"
                                    }
                                    Text(
                                        "$priceDesc • ${preset.category.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentMint.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "Select",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentMintLight,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }

                    matchingCatalogItems.take(3).forEach { catItem ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    applyCatalogItem(catItem)
                                    appliedPresetName = catItem.name
                                    isSuggestionsDismissed = true
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryIcon(
                                    iconName = catItem.category.icon,
                                    modifier = Modifier.padding(end = 8.dp).size(24.dp),
                                    tint = AccentMintLight,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        catItem.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        "${catItem.defaultCostPerPortion.cleanNumber()} EGP / ${catItem.portionUnit} • In catalog",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "In catalog",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto-filled confirmation badge
        if (isAutoFilled) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = AccentMint.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Auto-filled from preset: $name",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentMintLight,
                        )
                        Text(
                            text = "Measurements and default prices filled. Adjust any field below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    TextButton(onClick = {
                        appliedPresetName = null
                        isSuggestionsDismissed = false
                    }) {
                        Text("Custom", style = MaterialTheme.typography.labelSmall, color = AccentMintLight)
                    }
                }
            }
        }

        // Quick browse chips when name is blank
        if (name.isBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FoodMeasurementCodec.menuCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedMenuCategory == cat,
                        onClick = { selectedMenuCategory = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentMint.copy(alpha = 0.25f),
                            selectedLabelColor = AccentMintLight,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val visiblePresets = remember(selectedMenuCategory) {
                if (selectedMenuCategory == "All") FoodMeasurementCodec.presets
                else FoodMeasurementCodec.presets.filter { it.menuCategory == selectedMenuCategory }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                visiblePresets.forEach { preset ->
                    TagChip(
                        text = preset.title,
                        leadingIcon = when (preset.category) {
                            ItemCategory.FOOD_STAPLE -> AppIcons.RiceBowl
                            ItemCategory.FOOD_FRESH -> AppIcons.Nutrition
                            ItemCategory.FOOD_STREET -> AppIcons.Restaurant
                            ItemCategory.SNACK -> AppIcons.Cookie
                            ItemCategory.HOUSEHOLD -> AppIcons.Home
                            ItemCategory.OTHER -> AppIcons.Category
                        },
                        selected = false,
                        onClick = {
                            applyPreset(preset)
                            appliedPresetName = preset.name
                            isSuggestionsDismissed = true
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        DropdownField(
            label = "Category",
            value = category,
            values = ItemCategory.entries,
            labelOf = { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelected = { category = it },
        )

        Spacer(Modifier.height(8.dp))
        FormSectionTitle("How is it sold?")
        val kindOptions = listOf(
            Triple("single", "Single", AppIcons.LunchDining),
            Triple("pack", "Pack", AppIcons.Inventory2),
            Triple("weight", "By weight", AppIcons.Scale),
            Triple("spoons", "Spoon jar", AppIcons.Flatware),
            Triple("tiered", "Sizes", AppIcons.Sell),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kindOptions.take(3).forEach { (kind, label, icon) ->
                    val isSelected = selectedKind == kind
                    Surface(
                        modifier = Modifier.weight(1f).clickable { selectKind(kind) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentMint.copy(alpha = 0.18f) else DarkSurfaceLow,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) AccentMint else DarkBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(icon, contentDescription = null, tint = if (isSelected) AccentMintLight else TextSecondary, modifier = Modifier.size(24.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (isSelected) AccentMintLight else TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kindOptions.drop(3).forEach { (kind, label, icon) ->
                    val isSelected = selectedKind == kind
                    Surface(
                        modifier = Modifier.weight(1f).clickable { selectKind(kind) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentMint.copy(alpha = 0.18f) else DarkSurfaceLow,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) AccentMint else DarkBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(icon, contentDescription = null, tint = if (isSelected) AccentMintLight else TextSecondary, modifier = Modifier.size(24.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (isSelected) AccentMintLight else TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        when (selectedKind) {
            "spoons" -> SpoonJarSection(
                spoonUnit = spoonUnitName,
                gramsPerSpoon = gramsPerSpoon,
                container = stockUnit,
                containerGrams = containerGrams,
                containerPrice = containerPrice,
                perSpoon = defaultCost,
                showTuning = showSpoonTuning,
                onSpoonUnitChange = { spoonUnitName = it; portionUnit = it.ifBlank { "spoon" } },
                onGramsChange = { raw ->
                    gramsPerSpoon = raw
                    val gps = raw.asDouble().coerceAtLeast(1.0)
                    val jg = containerGrams.asDouble()
                    if (jg > 0) {
                        portionsPerStockUnit = (jg / gps).cleanNumber()
                        val cp = containerPrice.asDouble()
                        if (cp > 0) defaultCost = (cp / (jg / gps)).cleanNumber()
                    }
                },
                onContainerChange = { stockUnit = it },
                onContainerGramsChange = { raw ->
                    containerGrams = raw
                    val gps = gramsPerSpoon.asDouble().coerceAtLeast(1.0)
                    val jg = raw.asDouble()
                    if (jg > 0) {
                        portionsPerStockUnit = (jg / gps).cleanNumber()
                        val cp = containerPrice.asDouble()
                        if (cp > 0) defaultCost = (cp / (jg / gps)).cleanNumber()
                    }
                },
                onContainerPriceChange = { raw ->
                    containerPrice = raw
                    val cp = raw.asDouble()
                    val spoons = portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
                    if (cp > 0) defaultCost = (cp / spoons).cleanNumber()
                },
                onPerSpoonChange = { defaultCost = it },
                onToggleTuning = { showSpoonTuning = !showSpoonTuning },
            )
            "tiered" -> TieredPriceSection(
                small = smallPrice, medium = mediumPrice, large = largePrice,
                onSmall = { smallPrice = it }, onMedium = { mediumPrice = it }, onLarge = { largePrice = it },
            )
            "single" -> SinglePriceSection(
                portionUnit = portionUnit,
                defaultCost = defaultCost,
                showMore = showMoreSingleUnits,
                onUnitSelected = { u -> portionUnit = u; stockUnit = u; portionsPerStockUnit = "1" },
                onPortionUnitChange = { portionUnit = it; stockUnit = it; portionsPerStockUnit = "1" },
                onCostChange = { defaultCost = it },
                onToggleMore = { showMoreSingleUnits = !showMoreSingleUnits },
            )
            "pack" -> PackPriceSection(
                stockUnit = stockUnit,
                portionUnit = portionUnit,
                count = portionsPerStockUnit,
                packPrice = packagePrice,
                unitCost = defaultCost,
                showMore = showMorePackUnits,
                onStockChange = { stockUnit = it },
                onPortionChange = { portionUnit = it },
                onCountChange = { raw ->
                    portionsPerStockUnit = raw
                    val count = raw.asDouble()
                    val pkg = packagePrice.asDouble()
                    if (count > 0 && pkg > 0) defaultCost = (pkg / count).cleanNumber()
                },
                onPackPriceChange = { raw ->
                    packagePrice = raw
                    val pkg = raw.asDouble()
                    val count = portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
                    if (pkg > 0) defaultCost = (pkg / count).cleanNumber()
                },
                onUnitCostChange = { raw ->
                    defaultCost = raw
                    val unitCost = raw.asDouble()
                    val count = portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
                    if (unitCost > 0) packagePrice = (unitCost * count).cleanNumber()
                },
                onToggleMore = { showMorePackUnits = !showMorePackUnits },
            )
            else -> WeightPriceSection(
                itemName = name,
                portionUnit = portionUnit,
                count = portionsPerStockUnit,
                kgPrice = packagePrice,
                unitCost = defaultCost,
                showMore = showMoreWeightUnits,
                onProduceClick = { resolved ->
                    portionUnit = resolved
                    stockUnit = "kg"
                    if (portionsPerStockUnit.isBlank() || portionsPerStockUnit == "1" || portionsPerStockUnit == "1000" || portionsPerStockUnit == "100") portionsPerStockUnit = "6"
                    val count = portionsPerStockUnit.asDouble()
                    val kgPrice = packagePrice.asDouble()
                    if (count > 0 && kgPrice > 0) defaultCost = (kgPrice / count).cleanNumber()
                },
                onBulkClick = {
                    portionUnit = "g"
                    stockUnit = "kg"
                    portionsPerStockUnit = "1000"
                    val kgPrice = packagePrice.asDouble()
                    if (kgPrice > 0) defaultCost = (kgPrice / 1000.0).cleanNumber()
                },
                onCupsClick = {
                    portionUnit = "cup"
                    stockUnit = "kg"
                    portionsPerStockUnit = "5"
                    val kgPrice = packagePrice.asDouble()
                    if (kgPrice > 0) defaultCost = (kgPrice / 5.0).cleanNumber()
                },
                onUnitChange = { u ->
                    portionUnit = u
                    stockUnit = "kg"
                    if (u == "g") portionsPerStockUnit = "1000"
                    else if (portionsPerStockUnit == "1000") portionsPerStockUnit = "6"
                    val count = portionsPerStockUnit.asDouble()
                    val kgPrice = packagePrice.asDouble()
                    if (count > 0 && kgPrice > 0) defaultCost = (kgPrice / count).cleanNumber()
                },
                onCountChange = { raw ->
                    portionsPerStockUnit = raw
                    val count = raw.asDouble()
                    val kgPrice = packagePrice.asDouble()
                    if (count > 0 && kgPrice > 0) defaultCost = (kgPrice / count).cleanNumber()
                },
                onKgPriceChange = { raw ->
                    packagePrice = raw
                    val kgPrice = raw.asDouble()
                    val count = portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
                    if (kgPrice > 0) defaultCost = (kgPrice / count).cleanNumber()
                },
                onUnitCostChange = { raw ->
                    defaultCost = raw
                    val unitPrice = raw.asDouble()
                    val count = portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
                    if (unitPrice > 0) packagePrice = (unitPrice * count).cleanNumber()
                },
                onToggleMore = { showMoreWeightUnits = !showMoreWeightUnits },
            )
        }

        Spacer(Modifier.height(8.dp))
        PurchaseNotesSection(
            expanded = showAdvanced,
            purchasePrice = purchasePrice,
            purchaseQty = purchaseQuantity,
            mealUsage = mealUsage,
            notes = notes,
            onToggle = { showAdvanced = !showAdvanced },
            onPrice = { purchasePrice = it },
            onQty = { purchaseQuantity = it },
            onMealUsage = { mealUsage = it },
            onNotes = { notes = it },
        )

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            if (existing == null) "Save food item" else "Save changes",
            {
                viewModel.saveFoodCatalogItem(buildCatalogItem(existing?.id))
                onDismiss()
            },
            enabled = isValid,
            icon = AppIcons.Check,
        )

        if (existing != null) {
            OutlinedButton(
                onClick = {
                    viewModel.deleteFoodCatalogItem(existing)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ErrorBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
            ) {
                Icon(AppIcons.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete item")
            }
        }
    }
    }
}
