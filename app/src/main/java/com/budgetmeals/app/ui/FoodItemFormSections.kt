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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.state.BudgetMath


@Composable
fun QuickSelectionRow(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val isSelected = selected.equals(option, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentMint.copy(alpha = 0.25f),
                        selectedLabelColor = AccentMintLight,
                    ),
                )
            }
        }
    }
}
@Composable
fun FoodKindSummary(text: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = AccentMint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, AccentMint.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AccentMintLight, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SinglePriceSection(
    portionUnit: String,
    defaultCost: String,
    showMore: Boolean,
    onUnitSelected: (String) -> Unit,
    onPortionUnitChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onToggleMore: () -> Unit,
) {
    FormSectionTitle("Buy & use (same unit)")
    Text("1 piece used as 1 piece — no pack math.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    QuickSelectionRow(
        label = "Unit:",
        selected = portionUnit,
        options = if (showMore) listOf("piece", "loaf", "portion", "plate", "can", "egg", "cup", "slice", "g", "ml") else listOf("piece", "loaf", "portion", "plate", "can"),
        onSelect = onUnitSelected,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        BudgetTextField(value = portionUnit, onValueChange = onPortionUnitChange, label = "Unit", modifier = Modifier.weight(1f))
        NumberField(value = defaultCost, onValueChange = onCostChange, label = "Price", prefix = "EGP ", modifier = Modifier.weight(1f))
    }
    TextButton(onClick = onToggleMore) {
        Text(if (showMore) "Fewer units" else "More units", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
    FoodKindSummary("1 $portionUnit = ${com.budgetmeals.app.state.BudgetMath.money(defaultCost.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)}", icon = AppIcons.LunchDining)
}

@Composable
fun PackPriceSection(
    stockUnit: String,
    portionUnit: String,
    count: String,
    packPrice: String,
    unitCost: String,
    showMore: Boolean,
    onStockChange: (String) -> Unit,
    onPortionChange: (String) -> Unit,
    onCountChange: (String) -> Unit,
    onPackPriceChange: (String) -> Unit,
    onUnitCostChange: (String) -> Unit,
    onToggleMore: () -> Unit,
) {
    FormSectionTitle("Buy pack / use pieces")
    Text("E.g. carton of 30 eggs — buy pack, use pieces.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BudgetTextField(value = stockUnit, onValueChange = onStockChange, label = "Buy: pack", modifier = Modifier.weight(1f))
        BudgetTextField(value = portionUnit, onValueChange = onPortionChange, label = "Use: inside", modifier = Modifier.weight(1f))
    }
    if (!showMore) {
        QuickSelectionRow(label = "Pack:", selected = stockUnit, options = listOf("carton", "box", "bag", "pack"), onSelect = onStockChange)
        QuickSelectionRow(label = "Inside:", selected = portionUnit, options = listOf("egg", "piece", "loaf", "cup"), onSelect = onPortionChange)
    } else {
        QuickSelectionRow(label = "Pack:", selected = stockUnit, options = listOf("carton", "box", "bag", "pack", "tub", "bottle", "can"), onSelect = onStockChange)
        QuickSelectionRow(label = "Inside:", selected = portionUnit, options = listOf("egg", "piece", "portion", "loaf", "can", "cup", "slice"), onSelect = onPortionChange)
    }
    TextButton(onClick = onToggleMore) {
        Text(if (showMore) "Fewer options" else "More options", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
    QuickSelectionRow(label = "Count in 1 $stockUnit:", selected = count, options = listOf("6", "12", "24", "30"), onSelect = onCountChange)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        NumberField(value = count, onValueChange = onCountChange, label = "Count", modifier = Modifier.weight(1f))
        NumberField(value = packPrice, onValueChange = onPackPriceChange, label = "Pack price", prefix = "EGP ", modifier = Modifier.weight(1f))
    }
    NumberField(value = unitCost, onValueChange = onUnitCostChange, label = "Per $portionUnit (auto)", prefix = "EGP ", modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    FoodKindSummary("1 $stockUnit = ${count.ifBlank { "1" }} ${portionUnit}s · ${com.budgetmeals.app.state.BudgetMath.money(unitCost.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)} per $portionUnit", icon = AppIcons.Inventory2)
}

@Composable
fun WeightPriceSection(
    itemName: String,
    portionUnit: String,
    count: String,
    kgPrice: String,
    unitCost: String,
    showMore: Boolean,
    onProduceClick: (String) -> Unit,
    onBulkClick: () -> Unit,
    onCupsClick: () -> Unit,
    onUnitChange: (String) -> Unit,
    onCountChange: (String) -> Unit,
    onKgPriceChange: (String) -> Unit,
    onUnitCostChange: (String) -> Unit,
    onToggleMore: () -> Unit,
) {
    FormSectionTitle("Buy kg / use pieces")
    val lower = itemName.lowercase()
    val isBulk = lower.contains("sugar") || lower.contains("flour") || lower.contains("salt") || lower.contains("rice")
    if (!isBulk) {
        Text("Count pieces per kg. Grams and spoons are optional.", style = MaterialTheme.typography.bodySmall, color = AccentMintLight, modifier = Modifier.padding(bottom = 6.dp))
    }
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TagChip(text = "Produce", leadingIcon = AppIcons.Nutrition, selected = portionUnit != "g" && count != "1000" && portionUnit != "cup" && portionUnit != "spoon", onClick = {
            val resolved = when {
                lower.contains("tomato") -> "tomato"
                lower.contains("cucumber") -> "cucumber"
                lower.contains("potato") -> "potato"
                lower.contains("onion") -> "onion"
                lower.contains("lemon") -> "lemon"
                else -> "piece"
            }
            onProduceClick(resolved)
        })
        TagChip(text = "Bulk (g)", leadingIcon = AppIcons.Scale, selected = portionUnit == "g" && count == "1000", onClick = onBulkClick)
        TagChip(text = "Cups", leadingIcon = AppIcons.RiceBowl, selected = portionUnit == "cup", onClick = onCupsClick)
    }
    if (!showMore) {
        QuickSelectionRow(label = "Used as:", selected = portionUnit, options = listOf("piece", "tomato", "onion", "potato", "lemon", "g"), onSelect = onUnitChange)
        QuickSelectionRow(label = "Count in 1 kg:", selected = count, options = listOf("4", "6", "8", "12", "16"), onSelect = onCountChange)
    } else {
        QuickSelectionRow(label = "Used as:", selected = portionUnit, options = listOf("piece", "g", "cup", "spoon", "portion", "tomato", "cucumber", "potato", "onion", "lemon"), onSelect = onUnitChange)
        QuickSelectionRow(label = "Count in 1 kg:", selected = count, options = listOf("4", "5", "6", "8", "10", "12", "16", "100", "1000"), onSelect = onCountChange)
    }
    TextButton(onClick = onToggleMore) {
        Text(if (showMore) "Fewer options" else "More units", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BudgetTextField(value = portionUnit, onValueChange = onUnitChange, label = "Use unit", modifier = Modifier.weight(1f))
        NumberField(value = count, onValueChange = onCountChange, label = "Per kg", prefix = "~", modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        NumberField(value = kgPrice, onValueChange = onKgPriceChange, label = "Buy: kg price", prefix = "EGP ", modifier = Modifier.weight(1f))
        NumberField(value = unitCost, onValueChange = onUnitCostChange, label = "Use: each", prefix = "EGP ", modifier = Modifier.weight(1f))
    }
    FoodKindSummary("1 kg ≈ ${count.ifBlank { "1" }} ${portionUnit}s · ${com.budgetmeals.app.state.BudgetMath.money(unitCost.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)} per $portionUnit", icon = AppIcons.Scale)
}

@Composable
fun SpoonJarSection(
    spoonUnit: String,
    gramsPerSpoon: String,
    container: String,
    containerGrams: String,
    containerPrice: String,
    perSpoon: String,
    showTuning: Boolean,
    onSpoonUnitChange: (String) -> Unit,
    onGramsChange: (String) -> Unit,
    onContainerChange: (String) -> Unit,
    onContainerGramsChange: (String) -> Unit,
    onContainerPriceChange: (String) -> Unit,
    onPerSpoonChange: (String) -> Unit,
    onToggleTuning: () -> Unit,
) {
    FormSectionTitle("Buy jar / use spoons")
    Text("For tahina, honey, oil — buy a jar, use spoons.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetTextField(value = spoonUnit, onValueChange = onSpoonUnitChange, label = "Spoon unit", modifier = Modifier.weight(1f))
        NumberField(value = gramsPerSpoon, onValueChange = onGramsChange, label = "Grams / spoon", prefix = "g ", modifier = Modifier.weight(1f))
    }
    QuickSelectionRow(label = "Grams per spoon:", selected = gramsPerSpoon, options = listOf("5", "10", "15", "20"), onSelect = onGramsChange)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetTextField(value = container, onValueChange = onContainerChange, label = "Buy: container", modifier = Modifier.weight(1f))
        NumberField(value = containerGrams, onValueChange = onContainerGramsChange, label = "Net weight", prefix = "g ", modifier = Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(value = containerPrice, onValueChange = onContainerPriceChange, label = "Buy price", prefix = "EGP ", modifier = Modifier.weight(1f))
        NumberField(value = perSpoon, onValueChange = onPerSpoonChange, label = "Use price", prefix = "EGP ", modifier = Modifier.weight(1f))
    }
    TextButton(onClick = onToggleTuning) {
        Text(if (showTuning) "Hide container options" else "Tune container / weights", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Icon(if (showTuning) AppIcons.ExpandLess else AppIcons.ExpandMore, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
    if (showTuning) {
        QuickSelectionRow(label = "Container:", selected = container, options = listOf("jar", "tub", "bottle", "kg", "pack", "can"), onSelect = onContainerChange)
        QuickSelectionRow(label = "Net weight:", selected = containerGrams, options = listOf("200", "340", "380", "500", "700", "1000"), onSelect = onContainerGramsChange)
    }
    FoodKindSummary("1 $container (${containerGrams.ifBlank { "380" }}g) ≈ ${(containerGrams.trim().replace(',', '.').toDoubleOrNull() ?: 0.0).let { jg -> (gramsPerSpoon.trim().replace(',', '.').toDoubleOrNull() ?: 10.0).let { gps -> if (gps > 0) (jg / gps).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } else "1" } }} ${spoonUnit}s · ${com.budgetmeals.app.state.BudgetMath.money(perSpoon.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)} / $spoonUnit", icon = AppIcons.Flatware)
}

@Composable
fun TieredPriceSection(small: String, medium: String, large: String, onSmall: (String) -> Unit, onMedium: (String) -> Unit, onLarge: (String) -> Unit) {
    FormSectionTitle("Size prices")
    Text("Only for street snacks with Small / Medium / Large.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(value = small, onValueChange = onSmall, label = "Small", prefix = "EGP ", modifier = Modifier.weight(1f))
        NumberField(value = medium, onValueChange = onMedium, label = "Medium", prefix = "EGP ", modifier = Modifier.weight(1f))
        NumberField(value = large, onValueChange = onLarge, label = "Large", prefix = "EGP ", modifier = Modifier.weight(1f))
    }
    FoodKindSummary("Small: ${small.ifBlank { "0" }} EGP · Medium: ${medium.ifBlank { "0" }} EGP · Large: ${large.ifBlank { "0" }} EGP", icon = AppIcons.Sell)
}

@Composable
fun PurchaseNotesSection(
    expanded: Boolean,
    purchasePrice: String,
    purchaseQty: String,
    mealUsage: String,
    notes: String,
    onToggle: () -> Unit,
    onPrice: (String) -> Unit,
    onQty: (String) -> Unit,
    onMealUsage: (String) -> Unit,
    onNotes: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = DarkSurfaceLow, border = BorderStroke(1.dp, DarkBorder)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Purchase & notes (optional)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                    Text(
                        if (purchasePrice.isNotBlank() || mealUsage.isNotBlank() || notes.isNotBlank()) "Has details — tap to edit" else "Tap for pack price, meals, notes",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Icon(if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore, contentDescription = null, tint = TextSecondary)
            }
            if (expanded) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(value = purchasePrice, onValueChange = onPrice, label = "Pack price", placeholder = "0", prefix = "EGP ", modifier = Modifier.weight(1f))
                    NumberField(value = purchaseQty, onValueChange = onQty, label = "Qty", placeholder = "1", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text("Suitable for meals (tap to select):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val mealOptions = listOf("Any", "Breakfast", "Lunch", "Dinner", "Snack")
                    mealOptions.forEach { m ->
                        val isSelected = if (m == "Any") mealUsage.isBlank() || mealUsage.equals("Any", ignoreCase = true) else mealUsage.contains(m, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (m == "Any") onMealUsage("Any") else {
                                    val parts = mealUsage.split(",").map { it.trim() }.filter { it.isNotBlank() && !it.equals("Any", ignoreCase = true) }.toMutableList()
                                    if (parts.any { it.equals(m, ignoreCase = true) }) parts.removeAll { it.equals(m, ignoreCase = true) } else parts.add(m)
                                    onMealUsage(if (parts.isEmpty()) "Any" else parts.joinToString(", "))
                                }
                            },
                            label = { Text(m, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentMint.copy(alpha = 0.25f), selectedLabelColor = AccentMintLight),
                        )
                    }
                }
                BudgetTextField(value = mealUsage, onValueChange = onMealUsage, label = "Suitable for", placeholder = "e.g. Any, Breakfast", singleLine = false, modifier = Modifier.padding(top = 4.dp))
                BudgetTextField(value = notes, onValueChange = onNotes, label = "Notes (optional)", singleLine = false, modifier = Modifier.padding(top = 8.dp))
                Text("Leave price at 0 when it still needs checking.", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
