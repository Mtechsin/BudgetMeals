package com.budgetmeals.app.ui

import com.budgetmeals.app.ui.icons.AppIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealDataCodec
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.state.MealBuilderDraft
import java.time.DayOfWeek
import java.util.UUID

@Composable
fun TemplateFormSheet(
    existing: MealTemplate?,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
) {
    val draft by viewModel.builderDraft.collectAsState()
    val startingComponents = if (existing != null) {
        existing.components.ifEmpty { MealDataCodec.legacyComponents(existing.notes) }
    } else {
        draft.components
    }
    val existingId = existing?.id
    val newTemplateId = remember(existingId) { existingId ?: UUID.randomUUID().toString() }
    var isSaving by remember(existingId) { mutableStateOf(false) }
    var name by remember(existingId) { mutableStateOf(existing?.name ?: draft.name) }
    var type by remember(existingId) { mutableStateOf(existing?.mealType ?: draft.mealType) }
    var cost by remember(existingId) {
        mutableStateOf(existing?.cost?.let { if (it == 0.0) "" else it.cleanNumber() } ?: draft.cost)
    }
    var recurring by remember(existingId) { mutableStateOf(existing?.isRecurring ?: draft.isRecurring) }
    var notes by remember(existingId) { mutableStateOf(existing?.notes ?: draft.notes) }
    var day by remember(existingId) { mutableStateOf(existing?.dayOfWeek ?: draft.dayOfWeek) }
    var components by remember(existingId) { mutableStateOf(startingComponents) }
    var componentCostInputs by remember(existingId) { mutableStateOf(mapOf<String, String>()) }
    var catalogItems by remember(existingId) { mutableStateOf(snapshot.foodCatalog) }
    var foodSearchQuery by remember { mutableStateOf("") }
    var expandedComponentId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name, type, cost, recurring, notes, day, components) {
        if (existing == null) {
            viewModel.updateBuilderDraft(
                MealBuilderDraft(
                    name = name,
                    mealType = type,
                    components = components,
                    cost = cost,
                    isRecurring = recurring,
                    dayOfWeek = day,
                    notes = notes,
                    hasStarted = true,
                )
            )
        }
    }

    val calculatedCost = components.sumOf { it.estimatedCost }
    val hasCalculatedCost = components.any { it.costPerUnit > 0.0 }
    val effectiveCost = if (components.isNotEmpty() && hasCalculatedCost) calculatedCost else cost.asDouble()
    val valid = name.isNotBlank() && effectiveCost >= 0.0 && components.all { it.name.isNotBlank() && it.quantity > 0.0 }

    fun updateComponent(component: MealComponent) {
        components = components.map { if (it.id == component.id) component else it }
    }

    fun addCatalogFood(food: FoodCatalogItem) {
        val existingIndex = components.indexOfFirst { it.catalogId == food.id }
        if (existingIndex >= 0) {
            val old = components[existingIndex]
            components = components.toMutableList().also {
                it[existingIndex] = old.copy(quantity = old.quantity + 1.0)
            }
        } else {
            val newComp = when (val m = food.measurement) {
                is FoodMeasurementType.SpoonsToGrams -> {
                    val spoonsCost = BudgetMath.catalogCostPerPortion(snapshot, food).takeIf { it > 0.0 } ?: food.defaultCostPerPortion
                    MealComponent(
                        id = UUID.randomUUID().toString(),
                        catalogId = food.id,
                        name = food.name,
                        quantity = 2.0,
                        unit = m.spoonUnitName,
                        costPerUnit = spoonsCost,
                        useStock = true,
                    )
                }
                is FoodMeasurementType.TieredSizes -> {
                    val defaultSize = m.sizes.getOrNull(1) ?: m.sizes.firstOrNull()
                    val price = defaultSize?.price ?: food.defaultCostPerPortion
                    val sizeName = defaultSize?.name ?: "Medium"
                    MealComponent(
                        id = UUID.randomUUID().toString(),
                        catalogId = food.id,
                        name = "${food.name} ($sizeName)",
                        quantity = 1.0,
                        unit = sizeName,
                        costPerUnit = price,
                        useStock = true,
                    )
                }
                FoodMeasurementType.Standard -> {
                    MealComponent(
                        id = UUID.randomUUID().toString(),
                        catalogId = food.id,
                        name = food.name,
                        quantity = 1.0,
                        unit = food.portionUnit,
                        costPerUnit = BudgetMath.catalogCostPerPortion(snapshot, food),
                        useStock = true,
                    )
                }
            }
            components = components + newComp
        }
        foodSearchQuery = ""
    }

    fun addCustomFood(rawName: String) {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return
        val catalogMatch = catalogItems.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (catalogMatch != null) {
            addCatalogFood(catalogMatch)
        } else {
            val newComp = MealComponent(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                quantity = 1.0,
                unit = "piece",
                costPerUnit = 0.0,
                useStock = false,
            )
            components = components + newComp
            foodSearchQuery = ""
        }
    }

    fun createCatalogAndAdd(rawName: String) {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return
        val food = FoodCatalogItem(
            name = trimmed,
            category = ItemCategory.FOOD_FRESH,
            stockUnit = "piece",
            portionUnit = "piece",
            portionsPerStockUnit = 1.0,
        )
        catalogItems = (catalogItems + food).distinctBy { it.name.lowercase() }
        viewModel.saveFoodCatalogItem(food)
        addCatalogFood(food)
    }

    fun updateQuantity(componentId: String, delta: Double) {
        components = components.map { comp ->
            if (comp.id == componentId) {
                val newQ = (comp.quantity + delta).coerceAtLeast(0.1)
                val rounded = kotlin.math.round(newQ * 10.0) / 10.0
                comp.copy(quantity = rounded)
            } else {
                comp
            }
        }
    }

    fun removeComponent(componentId: String) {
        components = components.filterNot { it.id == componentId }
        componentCostInputs = componentCostInputs - componentId
        if (expandedComponentId == componentId) expandedComponentId = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 1. PINNED TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (existing == null) "Build a meal" else "Edit meal",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                )
                Text(
                    "Live ingredient costing & meal recipe",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }

            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDismiss) {
                Icon(AppIcons.Close, contentDescription = "Close", tint = TextMuted)
            }
        }

        // 2. SCROLLABLE CONTENT BODY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
        // Meal Basics
        FormSectionTitle("Meal details")
        BudgetTextField(
            value = name,
            onValueChange = { name = it },
            label = "Meal name",
            placeholder = "e.g. Scrambled Eggs & Toast, Lentil Soup",
        )

        Spacer(Modifier.height(10.dp))
        Text("Meal type", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(6.dp))

        // 4-Card Meal Type Segmented Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealType.entries.forEach { mealType ->
                val isSelected = type == mealType
                val color = when (mealType) {
                    MealType.BREAKFAST -> AccentAmber
                    MealType.LUNCH -> AccentMint
                    MealType.DINNER -> AccentIndigo
                    MealType.SNACK -> AccentPurple
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { type = mealType },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) color.copy(alpha = 0.18f) else DarkSurfaceLow,
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) color else DarkBorder,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MealIcon(mealType, Modifier.size(20.dp), tint = if (isSelected) color else TextSecondary)
                        Text(
                            mealType.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (isSelected) color else TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 3. Ingredients & Foods Section
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormSectionTitle("Ingredients (${components.size})")
                if (existing == null && components.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            components = emptyList()
                            viewModel.clearBuilderDraft()
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Clear all", style = MaterialTheme.typography.labelSmall, color = ErrorRed.copy(alpha = 0.8f))
                    }
                }
            }
            if (hasCalculatedCost) {
                Text(
                    "Total: ${BudgetMath.money(calculatedCost)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = AccentMintLight,
                )
            }
        }

        // Quick Add Search Bar
        BudgetTextField(
            value = foodSearchQuery,
            onValueChange = { foodSearchQuery = it },
            label = "Add ingredient",
            placeholder = "Type name or search food catalog...",
        )

        // Matching catalog items or quick add suggestions
        val filteredCatalog = if (foodSearchQuery.isNotBlank()) {
            val q = foodSearchQuery.trim().lowercase()
            catalogItems.filter { it.name.lowercase().contains(q) }
        } else {
            emptyList()
        }

        if (foodSearchQuery.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (filteredCatalog.isNotEmpty()) {
                    Text("From catalog:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    filteredCatalog.take(4).forEach { food ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addCatalogFood(food) },
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val hasKnownFoodPrice = food.priceKnown && food.hasKnownPrice
                                val costLabel = when (val m = food.measurement) {
                                    is FoodMeasurementType.SpoonsToGrams -> {
                                        val portionCost = BudgetMath.catalogCostPerPortion(snapshot, food)
                                        if (!hasKnownFoodPrice || portionCost <= 0.0) {
                                            "estimate incomplete"
                                        } else {
                                            "${BudgetMath.money(portionCost)}/${m.spoonUnitName} (~${m.gramsPerSpoon.cleanNumber()}g)"
                                        }
                                    }
                                    is FoodMeasurementType.TieredSizes -> {
                                        if (!hasKnownFoodPrice) {
                                            "estimate incomplete"
                                        } else {
                                            m.sizes.joinToString(" Â· ") { "${it.name} ${it.price.cleanNumber()} EGP" }
                                        }
                                    }
                                    FoodMeasurementType.Standard -> {
                                        val portionCost = BudgetMath.catalogCostPerPortion(snapshot, food)
                                        if (!hasKnownFoodPrice || portionCost <= 0.0) {
                                            "estimate incomplete"
                                        } else {
                                            "${BudgetMath.money(portionCost)}/${food.portionUnit}"
                                        }
                                    }
                                }
                                Text(
                                    costLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (costLabel == "estimate incomplete") AccentAmber else AccentMintLight,
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AccentMint)
                            }
                        }
                    }
                }

                // If not an exact match, allow adding as custom or catalog
                val hasExact = catalogItems.any { it.name.equals(foodSearchQuery.trim(), ignoreCase = true) }
                if (!hasExact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { addCustomFood(foodSearchQuery) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                        ) {
                            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add as custom", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { createCatalogAndAdd(foodSearchQuery) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentMint.copy(alpha = 0.18f), contentColor = AccentMintLight),
                        ) {
                            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save to catalog", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else if (catalogItems.isNotEmpty()) {
            // Horizontal scroll of popular catalog items
            Spacer(Modifier.height(4.dp))
            Text("Quick add from catalog:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                catalogItems.take(16).forEach { food ->
                    TagChip(
                        text = "+ ${food.name}",
                        selected = components.any { it.catalogId == food.id },
                        onClick = { addCatalogFood(food) },
                    )
                }
            }
        }

        // Ingredients List
        Spacer(Modifier.height(8.dp))
        if (components.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
                border = BorderStroke(1.dp, DarkBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "No ingredients added yet",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                    Text(
                        "Pick foods from catalog above to track stock and calculate cost, or set a manual price below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                components.forEach { component ->
                    val isExpanded = expandedComponentId == component.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
                        border = BorderStroke(1.dp, if (isExpanded) AccentMint.copy(alpha = 0.4f) else DarkBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val catalog = catalogItems.firstOrNull { it.id == component.catalogId }
                            val measurement = catalog?.measurement ?: FoodMeasurementCodec.decodeMeasurement(
                                rawNotes = "",
                                name = component.name,
                                portionUnit = component.unit,
                            )
                            val displayName = if (measurement is FoodMeasurementType.TieredSizes) {
                                catalog?.name ?: component.name.substringBefore(" (")
                            } else {
                                component.name
                            }

                            // Header Row: Left (Name & Subtitle) + Right (Stepper & Delete)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = TextPrimary,
                                        )
                                        if (component.catalogId != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentMint.copy(alpha = 0.12f),
                                            ) {
                                                Text(
                                                    "STOCK",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = AccentMintLight,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }

                                        // Total amount next to individual ingredient!
                                        if (component.estimatedCost > 0.0) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = AccentMint.copy(alpha = 0.15f),
                                            ) {
                                                Text(
                                                    BudgetMath.money(component.estimatedCost),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                    ),
                                                    color = AccentMintLight,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }

                                    val isUnknownPrice = component.costPerUnit <= 0.0 || (catalog != null && (!catalog.priceKnown || !catalog.hasKnownPrice))
                                    when (measurement) {
                                        is FoodMeasurementType.SpoonsToGrams -> {
                                            val totalGrams = component.quantity * measurement.gramsPerSpoon
                                            val unitLabel = if (component.quantity == 1.0) measurement.spoonUnitName else "${measurement.spoonUnitName}s"
                                            Text(
                                                if (isUnknownPrice) {
                                                    "${component.quantity.cleanNumber()} $unitLabel (~${totalGrams.cleanNumber()}g) Â· estimate incomplete"
                                                } else {
                                                    "${component.quantity.cleanNumber()} $unitLabel (~${totalGrams.cleanNumber()}g) Â· ${BudgetMath.money(component.costPerUnit)}/${measurement.spoonUnitName}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUnknownPrice) AccentAmber else TextSecondary,
                                            )
                                        }
                                        is FoodMeasurementType.TieredSizes -> {
                                            Text(
                                                if (isUnknownPrice) {
                                                    if (component.costPerUnit <= 0.0) "Select size below" else "${component.quantity.cleanNumber()} Ã— ${component.unit} Â· estimate incomplete"
                                                } else {
                                                    "${component.quantity.cleanNumber()} Ã— ${component.unit} (${BudgetMath.money(component.costPerUnit)} each)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUnknownPrice && component.costPerUnit > 0.0) AccentAmber else TextSecondary,
                                            )
                                        }
                                        FoodMeasurementType.Standard -> {
                                            Text(
                                                if (isUnknownPrice) {
                                                    "${component.quantity.cleanNumber()} ${component.unit} Â· estimate incomplete"
                                                } else {
                                                    "${component.quantity.cleanNumber()} ${component.unit} Â· ${BudgetMath.money(component.costPerUnit)}/${component.unit}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUnknownPrice) AccentAmber else TextSecondary,
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // Stepper controls
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { updateQuantity(component.id, -1.0) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkSurfaceHigh,
                                        border = BorderStroke(1.dp, DarkBorder),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(AppIcons.Remove, contentDescription = "Decrease quantity", tint = TextPrimary, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    val stepperText = when (measurement) {
                                        is FoodMeasurementType.SpoonsToGrams -> {
                                            val u = if (component.quantity == 1.0) measurement.spoonUnitName else "${measurement.spoonUnitName}s"
                                            "${component.quantity.cleanNumber()} $u"
                                        }
                                        is FoodMeasurementType.TieredSizes -> {
                                            val u = if (component.quantity == 1.0) "pack" else "packs"
                                            "${component.quantity.cleanNumber()} $u"
                                        }
                                        FoodMeasurementType.Standard -> {
                                            "${component.quantity.cleanNumber()} ${component.unit}"
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .widthIn(min = 46.dp)
                                            .height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkSurface,
                                        border = BorderStroke(1.dp, DarkBorder),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                            Text(
                                                stepperText,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = TextPrimary,
                                                maxLines = 1,
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { updateQuantity(component.id, 1.0) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkSurfaceHigh,
                                        border = BorderStroke(1.dp, DarkBorder),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(AppIcons.Add, contentDescription = "Increase quantity", tint = TextPrimary, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    IconButton(
                                        onClick = { removeComponent(component.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            AppIcons.Delete,
                                            contentDescription = "Remove",
                                            tint = ErrorRed.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            // Full-width Tiered Size Selector (if applicable)
                            if (measurement is FoodMeasurementType.TieredSizes) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    measurement.sizes.forEach { size ->
                                        val isSelected = component.unit.equals(size.name, ignoreCase = true) ||
                                            component.name.contains("(${size.name})", ignoreCase = true) ||
                                            (component.unit.isBlank() && component.costPerUnit == size.price)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) AccentMint.copy(alpha = 0.2f) else DarkSurfaceHigh,
                                            border = BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) AccentMint else DarkBorder,
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    val baseName = catalog?.name ?: component.name.substringBefore(" (")
                                                    componentCostInputs = componentCostInputs + (component.id to (if (size.price == 0.0) "" else size.price.cleanNumber()))
                                                    updateComponent(
                                                        component.copy(
                                                            name = "$baseName (${size.name})",
                                                            unit = size.name,
                                                            costPerUnit = size.price,
                                                        ),
                                                    )
                                                },
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            ) {
                                                Text(
                                                    "${size.name} Â· ${size.price.cleanNumber()} EGP",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    ),
                                                    color = if (isSelected) AccentMintLight else TextSecondary,
                                                    maxLines = 1,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Details toggle row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        expandedComponentId = if (isExpanded) null else component.id
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        if (isExpanded) "Hide price details â–²" else "Edit price & unit â–¼",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                }

                                if (component.catalogId != null) {
                                    val catalogItem = catalogItems.firstOrNull { it.id == component.catalogId }
                                    if (catalogItem != null) {
                                        val available = BudgetMath.catalogAvailablePortions(snapshot, catalogItem)
                                        Text(
                                            "${available.cleanNumber()} ${catalogItem.portionUnit} in stock",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (available >= component.quantity) AccentMintLight else AccentAmber,
                                        )
                                    }
                                }
                            }

                            if (isExpanded) {
                                val costInputValue = componentCostInputs[component.id]
                                    ?: if (component.costPerUnit == 0.0) "" else component.costPerUnit.cleanNumber()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    NumberField(
                                        value = costInputValue,
                                        onValueChange = { raw ->
                                            componentCostInputs = componentCostInputs + (component.id to raw)
                                            updateComponent(component.copy(costPerUnit = raw.asDouble().coerceAtLeast(0.0)))
                                        },
                                        label = "Cost/${component.unit}",
                                        placeholder = "0",
                                        prefix = "EGP ",
                                        modifier = Modifier.weight(1f),
                                    )
                                    BudgetTextField(
                                        value = component.unit,
                                        onValueChange = { updateComponent(component.copy(unit = it.trim())) },
                                        label = "Unit",
                                        modifier = Modifier.weight(1f),
                                        enabled = component.catalogId == null,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = component.useStock,
                                        onCheckedChange = { updateComponent(component.copy(useStock = it)) },
                                        colors = sheetCheckboxColors(),
                                    )
                                    Text(
                                        "Reduce linked stock when eaten",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Meal Price (Manual override when no ingredient costs exist)
        Spacer(Modifier.height(10.dp))
        if (!hasCalculatedCost) {
            FormSectionTitle("Meal price")
            NumberField(
                value = cost,
                onValueChange = { cost = it },
                label = "Total price",
                placeholder = "0",
                prefix = "EGP ",
            )
        }

        // 5. Schedule & Planning Card
        Spacer(Modifier.height(12.dp))
        FormSectionTitle("Planning & Schedule")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
            border = BorderStroke(1.dp, DarkBorder),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = recurring,
                        onCheckedChange = { recurring = it },
                        colors = sheetCheckboxColors(),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Include in auto-fill weekly plans", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        Text("The planner will pick this meal when generating plans", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                Text("Fixed day of the week (optional)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = day == null,
                        onClick = { day = null },
                        label = { Text("Any day") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentMint.copy(alpha = 0.16f),
                            selectedLabelColor = AccentMintLight,
                        ),
                    )
                    DayOfWeek.entries.forEach { d ->
                        val isSelected = day == d
                        FilterChip(
                            selected = isSelected,
                            onClick = { day = if (isSelected) null else d },
                            label = { Text(d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentMint.copy(alpha = 0.16f),
                                selectedLabelColor = AccentMintLight,
                            ),
                        )
                    }
                }

                BudgetTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Recipe or notes (optional)",
                    placeholder = "Instructions, sides, or reminders...",
                    singleLine = false,
                )
            }
        }

            if (existing != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.deleteTemplate(existing)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ErrorBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(AppIcons.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete shortcut", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 3. PINNED BOTTOM ACTION BAR
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "TOTAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = TextMuted,
                    )
                    val isEstimateIncomplete = components.isNotEmpty() && components.any { comp ->
                        val cat = catalogItems.firstOrNull { it.id == comp.catalogId }
                        comp.costPerUnit <= 0.0 || (cat != null && (!cat.priceKnown || !cat.hasKnownPrice))
                    }
                    Text(
                        if (isEstimateIncomplete) "estimate incomplete" else BudgetMath.money(effectiveCost),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isEstimateIncomplete) 16.sp else 20.sp,
                        ),
                        color = if (isEstimateIncomplete) AccentAmber else AccentMintLight,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (existing == null && (components.isNotEmpty() || name.isNotBlank())) {
                        TextButton(
                            onClick = {
                                name = ""
                                cost = ""
                                components = emptyList()
                                notes = ""
                                viewModel.clearBuilderDraft()
                            },
                        ) {
                            Text("Reset", color = ErrorRed, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            viewModel.saveTemplate(
                                MealTemplate(
                                    id = newTemplateId,
                                    name = name.trim(),
                                    mealType = type,
                                    cost = effectiveCost,
                                    isRecurring = recurring,
                                    dayOfWeek = day,
                                    notes = notes.trim(),
                                    isCustom = existing != null,
                                    components = components,
                                ),
                            )
                            if (existing == null) {
                                viewModel.clearBuilderDraft()
                            }
                            onDismiss()
                        },
                        enabled = valid && !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentMint,
                            contentColor = Color.Black,
                            disabledContainerColor = DarkSurfaceHigh,
                            disabledContentColor = TextMuted,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save meal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FoodPickerField(
    component: MealComponent,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    catalogItems: List<FoodCatalogItem>,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSelected: (FoodCatalogItem) -> Unit,
    onCreate: (String) -> Unit,
) {
    var expanded by remember(component.id) { mutableStateOf(false) }
    val normalizedQuery = query.trim().lowercase()
    val matches = catalogItems
        .filter { normalizedQuery.isBlank() || it.name.lowercase().contains(normalizedQuery) }
        .sortedBy { it.name.lowercase() }
    val exactMatch = catalogItems.any { it.name.equals(query.trim(), ignoreCase = true) }
    val selectedCatalog = catalogItems.firstOrNull { it.id == component.catalogId }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BudgetTextField(
            value = query.ifBlank { component.name },
            onValueChange = {
                onQueryChanged(it)
                expanded = true
            },
            label = "Food / Ingredient",
            modifier = Modifier.fillMaxWidth(),
        )

        if (catalogItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                catalogItems.take(12).forEach { food ->
                    val isSelected = food.id == component.catalogId
                    TagChip(
                        text = food.name,
                        selected = isSelected,
                        onClick = {
                            onQueryChanged(food.name)
                            onSelected(food)
                            expanded = false
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (!expanded) onQueryChanged("")
                    expanded = !expanded
                },
            ) {
                Text(if (expanded) "Close catalog list" else "Browse all (${catalogItems.size}) items")
            }

            if (selectedCatalog != null) {
                Text(
                    "${selectedCatalog.portionUnit} Â· ${BudgetMath.money(BudgetMath.catalogCostPerPortion(snapshot, selectedCatalog), 2)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentMintLight,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurface),
        ) {
            matches.forEach { food ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(food.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text(
                                "${food.category.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} Â· ${food.conversionLabel} Â· ${BudgetMath.money(BudgetMath.catalogCostPerPortion(snapshot, food), 2)} / ${food.portionUnit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    },
                    onClick = {
                        onQueryChanged(food.name)
                        onSelected(food)
                        expanded = false
                    },
                )
            }
            if (query.isNotBlank() && !exactMatch) {
                DropdownMenuItem(
                    text = { Text("Add \"$query\" to food items", color = AccentMintLight) },
                    onClick = {
                        onCreate(query)
                        expanded = false
                    },
                )
            }
        }
    }
}
