package com.budgetmeals.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.Units
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.ui.icons.AppIcons
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealIngredientCard(
    component: MealComponent,
    catalog: FoodCatalogItem?,
    availablePortions: Double?,
    onChange: (MealComponent) -> Unit,
    onRemove: () -> Unit,
    onInputValidityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var quantityText by rememberSaveable(component.id) { mutableStateOf(component.quantity.cleanNumber()) }
    var priceText by rememberSaveable(component.id) { mutableStateOf(component.costPerUnit.cleanNumber()) }
    var unitText by rememberSaveable(component.id) { mutableStateOf(component.unit) }
    var optionsExpanded by rememberSaveable(component.id) { mutableStateOf(false) }

    // Preserve raw text for our own valid model updates, while reflecting parent-side edits.
    var lastObserved by remember(component.id) { mutableStateOf(component) }
    var pendingEmission by remember(component.id) { mutableStateOf<MealComponent?>(null) }
    SideEffect {
        if (component != lastObserved) {
            if (component != pendingEmission) {
                quantityText = component.quantity.cleanNumber()
                priceText = component.costPerUnit.cleanNumber()
                unitText = component.unit
            }
            lastObserved = component
            pendingEmission = null
        }
    }

    val quantity = quantityText.asDoubleOrNull()
    val validQuantity = quantity != null && quantity > 0.0
    val price = priceText.asDoubleOrNull()
    val validPrice = price != null && price >= 0.0
    val validUnit = unitText.isNotBlank()
    val estimatedCostOverflow = !component.estimatedCost.isFinite()
    val allValid = validQuantity && validPrice && validUnit && !estimatedCostOverflow
    LaunchedEffect(allValid) { onInputValidityChanged(allValid) }
    LaunchedEffect(validPrice, validUnit) {
        if (!validPrice || !validUnit) optionsExpanded = true
    }

    fun emit(updated: MealComponent) {
        pendingEmission = updated
        onChange(updated)
    }

    val tieredSizes = (catalog?.measurement as? FoodMeasurementType.TieredSizes)?.sizes.orEmpty()
    val nameTierName = tieredSizes.firstOrNull { size ->
        component.name.trimEnd().endsWith("(${size.name})", ignoreCase = true)
    }?.name
    val selectedTierName = tieredSizes.firstOrNull { component.unit.equals(it.name, ignoreCase = true) }?.name
        ?: nameTierName
    // Buttons always adjust one unit. Never invent a fractional minimum when the
    // next decrement would reach zero; removing an ingredient has its own action.
    // Decimal arithmetic keeps explicitly entered fractions stable across +/-.
    val decimalQuantity = quantity?.takeIf { it > 0.0 }?.let(BigDecimal::valueOf)
    val decreasedQuantity = decimalQuantity?.subtract(BigDecimal.ONE)?.takeIf {
        it.signum() > 0 && it.toDouble() < quantity!!
    }
    val increasedQuantity = decimalQuantity?.add(BigDecimal.ONE)?.takeIf {
        it.toDouble().isFinite() && it.toDouble() > quantity!!
    }
    val convertedAvailability = if (availablePortions != null && catalog != null &&
        availablePortions.isFinite() && availablePortions >= 0.0
    ) {
        Units.convert(availablePortions, catalog.portionUnit, component.unit, catalog)
            ?.takeIf { it.isFinite() && it >= 0.0 }
    } else null
    val availabilityText = stockDescription(component, catalog, availablePortions, quantity, convertedAvailability)
    val estimatedText = if (estimatedCostOverflow) "Cost exceeds supported range" else "${BudgetMath.money(component.estimatedCost)} est."

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize().testTag("ingredient_card_${component.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(start = 2.dp)) {
                    Text(
                        text = component.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = estimatedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (estimatedCostOverflow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "Remove ${component.name} from this meal"
                    },
                ) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                IconButton(
                    onClick = {
                        val next = decreasedQuantity ?: return@IconButton
                        quantityText = next.stripTrailingZeros().toPlainString()
                        emit(component.copy(quantity = next.toDouble()))
                    },
                    enabled = enabled && decreasedQuantity != null,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "Decrease ${component.name} quantity"
                    },
                ) {
                    Icon(AppIcons.Remove, contentDescription = null)
                }

                val quantityShape = RoundedCornerShape(12.dp)
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .clip(quantityShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = when {
                                !validQuantity -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = quantityShape,
                        )
                        .padding(horizontal = 9.dp),
                ) {
                    val unitMaxWidth = minOf(56.dp, maxWidth * 0.45f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = quantityText,
                            onValueChange = { raw ->
                                quantityText = raw
                                val parsed = raw.asDoubleOrNull()
                                if (parsed != null && parsed > 0.0) emit(component.copy(quantity = parsed))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                                .semantics {
                                    contentDescription = "${component.name} quantity"
                                    if (!validQuantity) error("Enter a quantity greater than zero")
                                }
                                .testTag("ingredient_quantity_${component.id}"),
                            enabled = enabled,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) { innerTextField() }
                            },
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = component.unit,
                            modifier = Modifier.widthIn(max = unitMaxWidth),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val next = increasedQuantity ?: return@IconButton
                        quantityText = next.stripTrailingZeros().toPlainString()
                        emit(component.copy(quantity = next.toDouble()))
                    },
                    enabled = enabled && increasedQuantity != null,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "Increase ${component.name} quantity"
                    },
                ) {
                    Icon(AppIcons.Add, contentDescription = null)
                }

                IconButton(
                    onClick = { optionsExpanded = !optionsExpanded },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp)
                        .testTag("ingredient_options_${component.id}")
                        .semantics { contentDescription = "Edit ${component.name} options" },
                ) {
                    Icon(
                        imageVector = if (optionsExpanded) AppIcons.ExpandLess else AppIcons.Tune,
                        contentDescription = null,
                    )
                }
            }

            if (!validQuantity) {
                Text(
                    "Enter a quantity greater than zero",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (!optionsExpanded && !validPrice) {
                Text(
                    "Enter a valid cost of zero or more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (!optionsExpanded && !validUnit) {
                Text(
                    "Unit cannot be blank",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (estimatedCostOverflow) {
                Text(
                    "Ingredient cost is too large. Reduce the quantity or unit cost.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            AnimatedVisibility(visible = optionsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (tieredSizes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                tieredSizes.forEach { size ->
                                    val selected = selectedTierName.equals(size.name, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        enabled = enabled,
                                        onClick = {
                                            unitText = size.name
                                            priceText = size.price.cleanNumber()
                                            val oldSizeSuffix = nameTierName?.let { " ($it)" }
                                            val updatedName = if (oldSizeSuffix != null && component.name.endsWith(oldSizeSuffix, ignoreCase = true)) {
                                                component.name.dropLast(oldSizeSuffix.length) + " (${size.name})"
                                            } else component.name
                                            emit(component.copy(name = updatedName, unit = size.name, costPerUnit = size.price))
                                        },
                                        label = { Text("${size.name} - ${BudgetMath.money(size.price)}") },
                                        leadingIcon = if (selected) {
                                            { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                        modifier = Modifier.semantics {
                                            contentDescription = "${component.name}, ${size.name} size, ${BudgetMath.money(size.price)} per ${size.name}${if (selected) ", selected" else ""}"
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Use pantry stock for ${component.name}. $availabilityText"
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .toggleable(
                                    value = component.useStock,
                                    enabled = enabled,
                                    role = Role.Switch,
                                    onValueChange = { emit(component.copy(useStock = it)) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("Use pantry stock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(availabilityText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = component.useStock, onCheckedChange = null, enabled = enabled)
                        }
                    }

                    Text("Cost and unit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { raw ->
                            priceText = raw
                            val parsed = raw.asDoubleOrNull()
                            if (parsed != null && parsed >= 0.0) emit(component.copy(costPerUnit = parsed))
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "${component.name} cost per ${component.unit} in Egyptian pounds"
                        },
                        label = { Text("Cost per ${component.unit} (EGP)") },
                        singleLine = true,
                        isError = !validPrice,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = if (!validPrice) ({ Text("Enter a valid price of zero or more") }) else null,
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { raw ->
                            unitText = raw
                            val parsedPrice = priceText.asDoubleOrNull()
                            if (raw.isNotBlank() && parsedPrice != null && parsedPrice >= 0.0) {
                                emit(component.copy(unit = raw.trim(), costPerUnit = parsedPrice))
                            }
                        },
                        enabled = enabled,
                        readOnly = catalog != null,
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "${component.name} quantity unit"
                        },
                        label = { Text("Unit") },
                        singleLine = true,
                        isError = !validUnit,
                        supportingText = when {
                            !validUnit -> ({ Text("Unit cannot be blank") })
                            catalog != null -> ({ Text("Unit comes from the food catalog.") })
                            else -> null
                        },
                    )
                }
            }
        }
    }
}

private fun stockDescription(
    component: MealComponent,
    catalog: FoodCatalogItem?,
    availablePortions: Double?,
    quantity: Double?,
    availableInComponentUnit: Double?,
): String {
    if (catalog == null) return "Link this ingredient to a catalog item to check stock."
    if (!catalog.hasUsableConversion) {
        return "Stock availability is unknown because the catalog conversion needs to be completed."
    }
    if (availablePortions == null || !availablePortions.isFinite() || availablePortions < 0.0) {
        return "Stock availability is unknown."
    }
    if (availableInComponentUnit == null) {
        return "Stock availability cannot be compared with ${component.unit}; its conversion is unknown."
    }
    if (availableInComponentUnit <= 0.0) return "No usable ${component.name} stock is available."
    val availableText = "${availableInComponentUnit.cleanNumber()} ${component.unit} available"
    return if (quantity != null && quantity.isFinite() && quantity > availableInComponentUnit) {
        "Only $availableText for this ${quantity.cleanNumber()} ${component.unit} recipe amount."
    } else {
        availableText
    }
}
