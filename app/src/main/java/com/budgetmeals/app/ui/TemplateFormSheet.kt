@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.budgetmeals.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealDataCodec
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.BudgetViewModel
import com.budgetmeals.app.state.MealBuilderDraft
import com.budgetmeals.app.ui.icons.AppIcons
import java.time.DayOfWeek
import java.util.UUID

private enum class MealPriceMode { INGREDIENTS, MANUAL }

private val mealTypeSaver = Saver<MealType, String>(
    save = { it.name }, restore = { runCatching { MealType.valueOf(it) }.getOrDefault(MealType.LUNCH) },
)
private val daySaver = Saver<DayOfWeek?, String>(
    save = { it?.name.orEmpty() }, restore = { it.takeIf(String::isNotBlank)?.let { value -> runCatching { DayOfWeek.valueOf(value) }.getOrNull() } },
)
private val componentsSaver = Saver<List<MealComponent>, String>(
    save = { MealDataCodec.encodeComponents(it) }, restore = { MealDataCodec.decodeComponents(it) },
)
private val priceModeSaver = Saver<MealPriceMode, String>(
    save = { it.name }, restore = { runCatching { MealPriceMode.valueOf(it) }.getOrDefault(MealPriceMode.INGREDIENTS) },
)

@Composable
internal fun TemplateFormSheet(
    existing: MealTemplate?,
    snapshot: com.budgetmeals.app.data.AppSnapshot,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    onDismissGuardChanged: ((() -> Unit)?) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val draft by viewModel.builderDraft.collectAsState()
    val initialComponents = remember(existing?.id) {
        existing?.let { it.components.ifEmpty { MealDataCodec.legacyComponents(it.notes) } } ?: draft.components
    }
    val initialIngredientTotal = initialComponents.sumOf { it.estimatedCost }
    val existingHasManualTotal = existing != null && existing.cost != initialIngredientTotal
    val newHasManualDraft = existing == null && draft.cost.asDoubleOrNull()?.let { it > 0.0 } == true
    var newId by rememberSaveable(existing?.id) { mutableStateOf(existing?.id ?: UUID.randomUUID().toString()) }

    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: draft.name) }
    var type by rememberSaveable(existing?.id, stateSaver = mealTypeSaver) { mutableStateOf(existing?.mealType ?: draft.mealType) }
    var manualCost by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { if (it.cost == 0.0) "" else it.cost.cleanNumber() } ?: draft.cost)
    }
    var recurring by rememberSaveable(existing?.id) { mutableStateOf(existing?.isRecurring ?: draft.isRecurring) }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes ?: draft.notes) }
    var day by rememberSaveable(existing?.id, stateSaver = daySaver) { mutableStateOf(if (existing != null) existing.dayOfWeek else draft.dayOfWeek) }
    var components by rememberSaveable(existing?.id, stateSaver = componentsSaver) { mutableStateOf(initialComponents) }
    var pricingMode by rememberSaveable(existing?.id, stateSaver = priceModeSaver) {
        mutableStateOf(if (existingHasManualTotal || newHasManualDraft) MealPriceMode.MANUAL else MealPriceMode.INGREDIENTS)
    }
    var catalogItems by remember(existing?.id) { mutableStateOf(snapshot.foodCatalog) }
    var query by rememberSaveable(existing?.id) { mutableStateOf("") }
    var addingIngredients by rememberSaveable(existing?.id) { mutableStateOf(initialComponents.isEmpty()) }
    var invalidComponents by rememberSaveable(existing?.id) { mutableStateOf(emptySet<String>()) }
    var isSaving by remember(existing?.id) { mutableStateOf(false) }
    var isDeleting by remember(existing?.id) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var saveError by rememberSaveable(existing?.id) { mutableStateOf<String?>(null) }
    val editingEnabled = !isSaving && !isDeleting

    LaunchedEffect(snapshot.foodCatalog) {
        val optimisticItems = catalogItems.filter { local -> snapshot.foodCatalog.none { it.id == local.id } }
        catalogItems = (snapshot.foodCatalog + optimisticItems).distinctBy { it.id }
    }

    LaunchedEffect(name, type, manualCost, recurring, notes, day, components, pricingMode) {
        if (existing == null) viewModel.updateBuilderDraft(
            MealBuilderDraft(name, type, components, manualCost, recurring, day, notes, hasStarted = true),
        )
    }

    val ingredientTotal = components.sumOf { it.estimatedCost }
    val parsedManualPrice = manualCost.asDoubleOrNull()
    val manualPriceValid = manualCost.isBlank() || (parsedManualPrice != null && parsedManualPrice.isFinite() && parsedManualPrice >= 0.0)
    val effectivePrice = if (pricingMode == MealPriceMode.INGREDIENTS) ingredientTotal else (parsedManualPrice ?: 0.0)
    val priceIncomplete = components.any { comp ->
        comp.costPerUnit <= 0.0
    }
    val validName = name.isNotBlank()
    val hasValidIngredients = components.all {
        it.name.isNotBlank() && it.quantity.isFinite() && it.quantity > 0.0 && it.costPerUnit.isFinite() &&
            it.costPerUnit >= 0.0 && it.estimatedCost.isFinite()
    }
    val canSave = validName && (pricingMode != MealPriceMode.MANUAL || manualPriceValid) && effectivePrice.isFinite() && effectivePrice >= 0.0 &&
        hasValidIngredients && invalidComponents.isEmpty() && !isSaving && !isDeleting
    val editing = existing != null
    val hasUnsavedChanges = if (existing != null) {
        name != existing.name || type != existing.mealType || recurring != existing.isRecurring || notes != existing.notes || day != existing.dayOfWeek ||
            components != initialComponents || invalidComponents.isNotEmpty() || pricingMode != (if (existingHasManualTotal) MealPriceMode.MANUAL else MealPriceMode.INGREDIENTS) ||
            (pricingMode == MealPriceMode.MANUAL && (parsedManualPrice ?: 0.0) != existing.cost)
    } else {
        name.isNotBlank() || components.isNotEmpty() || invalidComponents.isNotEmpty() || manualCost.isNotBlank() || notes.isNotBlank() || type != MealType.LUNCH ||
            !recurring || day != null || pricingMode != (if (newHasManualDraft) MealPriceMode.MANUAL else MealPriceMode.INGREDIENTS)
    }

    fun requestDismiss() {
        if (isSaving || isDeleting) return
        if (hasUnsavedChanges) showDiscardDialog = true else onDismiss()
    }

    val latestRequestDismiss by rememberUpdatedState<() -> Unit>({ requestDismiss() })
    val stableGuard = remember { { latestRequestDismiss() } }
    val latestGuardChanged by rememberUpdatedState(onDismissGuardChanged)
    DisposableEffect(Unit) {
        onDispose { latestGuardChanged(null) }
    }
    SideEffect { onDismissGuardChanged(if (hasUnsavedChanges || isSaving || isDeleting) stableGuard else null) }

    fun addCatalogFood(food: FoodCatalogItem) {
        val found = components.indexOfFirst { it.catalogId == food.id }
        if (found >= 0) {
            val old = components[found]
            components = components.toMutableList().also { it[found] = old.copy(quantity = old.quantity + 1.0) }
        } else {
            val component = when (val measurement = food.measurement) {
                is FoodMeasurementType.SpoonsToGrams -> MealComponent(
                    catalogId = food.id, name = food.name, quantity = 2.0, unit = measurement.spoonUnitName,
                    costPerUnit = BudgetMath.catalogCostPerPortion(snapshot, food).takeIf { it > 0.0 } ?: food.defaultCostPerPortion,
                )
                is FoodMeasurementType.TieredSizes -> {
                    val size = measurement.sizes.getOrNull(1) ?: measurement.sizes.firstOrNull()
                    MealComponent(catalogId = food.id, name = food.name, quantity = 1.0, unit = size?.name ?: food.portionUnit,
                        costPerUnit = size?.price ?: food.defaultCostPerPortion)
                }
                FoodMeasurementType.Standard -> MealComponent(
                    catalogId = food.id, name = food.name, quantity = 1.0, unit = food.portionUnit,
                    costPerUnit = BudgetMath.catalogCostPerPortion(snapshot, food),
                )
            }
            components = components + component
        }
        query = ""
    }

    fun addCustomFood(raw: String) {
        val foodName = raw.trim()
        if (foodName.isBlank()) return
        val match = catalogItems.firstOrNull { it.name.equals(foodName, true) }
        if (match != null) addCatalogFood(match) else {
            components = components + MealComponent(name = foodName, quantity = 1.0, unit = "piece", costPerUnit = 0.0, useStock = false)
            query = ""
        }
    }

    fun createCatalogFood(raw: String) {
        val foodName = raw.trim()
        if (foodName.isBlank()) return
        catalogItems.firstOrNull { it.name.equals(foodName, ignoreCase = true) }?.let { existingFood ->
            addCatalogFood(existingFood)
            return
        }
        val item = FoodCatalogItem(name = foodName, category = ItemCategory.FOOD_FRESH, stockUnit = "piece", portionUnit = "piece")
        catalogItems = (catalogItems + item).distinctBy { it.name.lowercase() }
        viewModel.saveFoodCatalogItem(item)
        addCatalogFood(item)
    }

    fun saveMeal() {
        if (!canSave) return
        isSaving = true
        saveError = null
        val saved = MealTemplate(
            id = newId, name = name.trim(), mealType = type, cost = effectivePrice,
            isRecurring = recurring, dayOfWeek = day, notes = notes.trim(), isCustom = existing?.isCustom ?: true,
            components = components,
        )
        viewModel.saveTemplate(saved) { success ->
            isSaving = false
            if (success) {
                if (!editing) viewModel.clearBuilderDraft()
                onDismiss()
            } else saveError = "Couldn't save this meal. Your edits are still here; try again."
        }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().blockSheetDragWhenScrolled().testTag("meal_editor_header").padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (editing) "Edit meal" else "Build a meal", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            }
            IconButton(onClick = ::requestDismiss, enabled = !isSaving && !isDeleting) {
                Icon(AppIcons.Close, contentDescription = "Close editor", tint = TextSecondary)
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).testTag("meal_editor_scroll").sheetVerticalScroll(scrollState).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorCard(title = "Meal details") {
                BudgetTextField(name, { name = it }, "Meal name", modifier = Modifier.testTag("meal_name"), placeholder = "e.g. Lentil soup with bread", enabled = editingEnabled)
                if (name.isBlank()) Text("Enter a name to save this meal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text("Meal type", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MealType.entries.forEach { mealType ->
                        FilterChip(
                            selected = type == mealType, onClick = { type = mealType }, enabled = editingEnabled,
                            label = { Text(mealType.label) },
                            leadingIcon = if (type == mealType) ({ Icon(AppIcons.Check, null, Modifier.size(16.dp)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentMint.copy(alpha = .16f), selectedLabelColor = AccentMintLight),
                        )
                    }
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ingredients (${components.size})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    TextButton(onClick = {
                        if (addingIngredients) { query = ""; focusManager.clearFocus() }
                        addingIngredients = !addingIngredients
                    }, enabled = editingEnabled, modifier = Modifier.heightIn(min = 48.dp).testTag("toggle_add_ingredients")) {
                        Icon(if (addingIngredients) AppIcons.Close else AppIcons.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (addingIngredients) "Done" else "Add")
                    }
                }
                if (addingIngredients) {
                    BudgetTextField(
                        query, { query = it }, "Find an ingredient", modifier = Modifier.testTag("catalog_search"), placeholder = "Search by food name", enabled = editingEnabled,
                        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }, enabled = editingEnabled) { Icon(AppIcons.Close, "Clear search") } }) else null,
                    )
                    val matches = if (query.isBlank()) emptyList() else catalogItems.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(6)
                    if (query.isNotBlank()) {
                        if (matches.isEmpty()) {
                            Text("No catalog matches. Add \"${query.trim()}\" as a custom ingredient or save it to your catalog.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        } else {
                            Text("Catalog matches", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            matches.forEach { food ->
                                CatalogSearchResult(food, snapshot, enabled = editingEnabled, onClick = { addCatalogFood(food) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { addCustomFood(query) }, enabled = editingEnabled, modifier = Modifier.weight(1f)) { Text("Add custom") }
                            Button(onClick = { createCatalogFood(query) }, enabled = editingEnabled, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentMint, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Save to catalog") }
                        }
                    } else if (catalogItems.isNotEmpty()) {
                        val quickPicks = catalogItems.filter { food -> components.none { it.catalogId == food.id } }.take(10)
                        if (quickPicks.isNotEmpty()) {
                            Text("Quick picks", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                quickPicks.forEach { food ->
                                    AssistChip(onClick = { addCatalogFood(food) }, enabled = editingEnabled, label = { Text(food.name) }, leadingIcon = { Icon(AppIcons.Add, null, Modifier.size(16.dp)) })
                                }
                            }
                        }
                    } else {
                        EmptyStateCard("Your food catalog is empty", "Type an ingredient above to add it to this meal or save it to the catalog.")
                    }
                    if (components.isNotEmpty()) TextButton(
                        onClick = { components = emptyList(); invalidComponents = emptySet() },
                        enabled = editingEnabled, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.align(Alignment.End),
                    ) { Text("Clear ingredients") }
                }

                if (components.isEmpty()) {
                    EmptyStateCard("No ingredients yet", "Add ingredients to track portions, stock, and a recipe total.")
                } else {
                    components.forEach { component ->
                        key(component.id) {
                            val food = catalogItems.firstOrNull { it.id == component.catalogId }
                            MealIngredientCard(
                                component = component,
                                catalog = food,
                                availablePortions = food?.let { snapshot.catalogAvailablePortions[it.id] },
                                onChange = { changed -> components = components.map { if (it.id == changed.id) changed else it } },
                                onRemove = {
                                    components = components.filterNot { it.id == component.id }
                                    invalidComponents = invalidComponents - component.id
                                },
                                onInputValidityChanged = { isValid ->
                                    invalidComponents = if (isValid) invalidComponents - component.id else invalidComponents + component.id
                                },
                                enabled = editingEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (invalidComponents.isNotEmpty()) Text("Finish or correct the highlighted ingredient fields before saving.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            EditorCard(title = "Price") {
                PriceModeOption(
                    selected = pricingMode == MealPriceMode.INGREDIENTS,
                    title = "Ingredient total",
                    detail = "${BudgetMath.money(ingredientTotal)} from the recipe",
                    onClick = { pricingMode = MealPriceMode.INGREDIENTS },
                    enabled = editingEnabled,
                    modifier = Modifier.testTag("price_mode_ingredients"),
                )
                PriceModeOption(
                    selected = pricingMode == MealPriceMode.MANUAL,
                    title = "Manual total",
                    detail = "Set the full price for this meal",
                    onClick = { pricingMode = MealPriceMode.MANUAL },
                    enabled = editingEnabled,
                    modifier = Modifier.testTag("price_mode_manual"),
                )
                if (pricingMode == MealPriceMode.MANUAL) {
                    OutlinedTextField(
                        value = manualCost,
                        onValueChange = { manualCost = it },
                        modifier = Modifier.fillMaxWidth().testTag("manual_price"),
                        enabled = editingEnabled,
                        label = { Text("Manual meal total") },
                        placeholder = { Text("0") },
                        prefix = { Text("EGP ") },
                        singleLine = true,
                        isError = !manualPriceValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = if (!manualPriceValid) ({ Text("Enter a finite price of zero or more.") }) else null,
                        shape = RoundedCornerShape(14.dp),
                        colors = budgetTextFieldColors(),
                    )
                    if (manualCost.isBlank()) Text("No manual price entered; this meal will save as EGP 0.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                if (priceIncomplete) Text(if (pricingMode == MealPriceMode.MANUAL) "Some ingredient prices are missing. This meal uses your manual total." else "Some ingredient prices are missing. Add missing costs or choose a manual total.", style = MaterialTheme.typography.bodySmall, color = AccentAmber)
            }

            EditorCard(title = "Planning") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it }, enabled = editingEnabled, colors = sheetCheckboxColors())
                    Text("Use in weekly auto-fill", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                }
                Text("Preferred day", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    FilterChip(selected = day == null, onClick = { day = null }, enabled = editingEnabled, label = { Text("Any day") })
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(selected = day == d, onClick = { day = if (day == d) null else d }, enabled = editingEnabled, label = { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                BudgetTextField(notes, { notes = it }, "Recipe or notes (optional)", placeholder = "Instructions, sides, reminders...", singleLine = false, enabled = editingEnabled)
            }

            if (editing) {
                OutlinedButton(onClick = { showDeleteDialog = true }, enabled = !isSaving && !isDeleting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_meal"), border = BorderStroke(1.dp, ErrorRed.copy(alpha = .45f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                    Icon(AppIcons.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Delete meal")
                }
            } else if (hasUnsavedChanges) {
                TextButton(onClick = {
                    name = ""; type = MealType.LUNCH; manualCost = ""; recurring = true; notes = ""; day = null
                    components = emptyList(); invalidComponents = emptySet(); pricingMode = MealPriceMode.INGREDIENTS; query = ""; addingIngredients = true
                    viewModel.clearBuilderDraft()
                }, enabled = editingEnabled, modifier = Modifier.align(Alignment.End)) { Text("Reset meal", color = ErrorRed) }
            }
            saveError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(6.dp))
        }

        Surface(color = DarkSurface, border = BorderStroke(1.dp, DarkBorder), modifier = Modifier.fillMaxWidth().blockSheetDragWhenScrolled().testTag("meal_editor_footer")) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compactFooter = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
                val totalBlock: @Composable () -> Unit = {
                    Column {
                        Text(if (pricingMode == MealPriceMode.INGREDIENTS) "INGREDIENT TOTAL" else "MANUAL TOTAL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = TextMuted)
                        Text(BudgetMath.money(effectivePrice), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = AccentMintLight)
                        if (priceIncomplete) Text("Includes unpriced ingredients", style = MaterialTheme.typography.labelSmall, color = AccentAmber)
                    }
                }
                val saveButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                    Button(
                        onClick = { saveMeal() },
                        enabled = canSave,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentMint, contentColor = MaterialTheme.colorScheme.onPrimary, disabledContainerColor = DarkSurfaceHigh, disabledContentColor = TextMuted),
                        modifier = buttonModifier.heightIn(min = 50.dp).testTag("save_meal"),
                    ) {
                        if (isSaving) Text("Saving...", fontWeight = FontWeight.Bold) else {
                            Icon(AppIcons.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save meal", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (compactFooter) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            totalBlock()
                            if (saveError != null) TextButton(onClick = { requestDismiss() }, enabled = editingEnabled) { Text("Cancel") }
                        }
                        saveButton(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { totalBlock() }
                        if (saveError != null) TextButton(onClick = { requestDismiss() }, enabled = editingEnabled) { Text("Cancel") }
                        saveButton(Modifier)
                    }
                }
            }
        }
    }

    if (showDiscardDialog) AlertDialog(
        onDismissRequest = { showDiscardDialog = false },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Your changes to this meal will be lost.") },
        confirmButton = { TextButton(onClick = { showDiscardDialog = false; if (!editing) viewModel.clearBuilderDraft(); onDismiss() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") } },
    )
    if (showDeleteDialog && existing != null) AlertDialog(
        onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
        title = { Text("Delete ${existing.name}?") },
        text = { Text("This meal shortcut will be removed. This can't be undone.") },
        confirmButton = {
            TextButton(enabled = !isDeleting, modifier = Modifier.testTag("confirm_delete"), onClick = {
                isDeleting = true; saveError = null
                viewModel.deleteTemplate(existing) { success ->
                    isDeleting = false
                    if (success) onDismiss() else { showDeleteDialog = false; saveError = "Couldn't delete this meal. Please try again." }
                }
            }) { Text(if (isDeleting) "Deleting..." else "Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !isDeleting, modifier = Modifier.testTag("cancel_delete"), onClick = { showDeleteDialog = false }) { Text("Cancel") } },
    )
}

@Composable
private fun EditorCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceLow),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = .75f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (subtitle != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            }
            content()
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, description: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = DarkSurface, border = BorderStroke(1.dp, DarkBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun CatalogSearchResult(food: FoodCatalogItem, snapshot: com.budgetmeals.app.data.AppSnapshot, enabled: Boolean, onClick: () -> Unit) {
    val cost = BudgetMath.catalogCostPerPortion(snapshot, food)
    val secondary = when {
        !food.priceKnown || !food.hasKnownPrice || cost <= 0.0 -> "Price not set"
        else -> "${BudgetMath.money(cost)} / ${food.portionUnit}"
    }
    Surface(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).testTag("catalog_result_${food.id}"), shape = RoundedCornerShape(15.dp),
        color = DarkSurface, border = BorderStroke(1.dp, DarkBorder),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = if (secondary == "Price not set") AccentAmber else AccentMintLight)
            }
            Icon(AppIcons.Add, "Add ${food.name}", tint = AccentMint)
        }
    }
}

@Composable
private fun PriceModeOption(selected: Boolean, title: String, detail: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentMint.copy(alpha = .10f) else DarkSurface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) AccentMint.copy(alpha = .7f) else DarkBorder),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
