package com.budgetmeals.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodMeasurementCodec
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.FoodPreset
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.TieredSize
import java.util.UUID


enum class MeasurementMode(val label: String) {
    STANDARD("Standard"),
    SPOONS("Spoons → Grams"),
    TIERED("Tiered sizes"),
}

enum class StandardItemStyle(val label: String) {
    SINGLE("Single (1:1)"),
    PACK("Package"),
    WEIGHT("By Weight (Kg)"),
}


class FoodItemFormState(
    val existing: FoodCatalogItem?,
) {
    val initialMeasurement = existing?.measurement ?: FoodMeasurementType.Standard
    val initialMode = when (initialMeasurement) {
        is FoodMeasurementType.SpoonsToGrams -> MeasurementMode.SPOONS
        is FoodMeasurementType.TieredSizes -> MeasurementMode.TIERED
        FoodMeasurementType.Standard -> MeasurementMode.STANDARD
    }

    var measurementMode by mutableStateOf(initialMode)
    var name by mutableStateOf(existing?.name.orEmpty())
    var category by mutableStateOf(existing?.category ?: ItemCategory.FOOD_FRESH)
    var portionUnit by mutableStateOf(existing?.portionUnit ?: "piece")
    var defaultCost by mutableStateOf(existing?.defaultCostPerPortion?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "")
    var stockUnit by mutableStateOf(existing?.stockUnit ?: "piece")
    var portionsPerStockUnit by mutableStateOf(existing?.portionsPerStockUnit?.cleanNumber() ?: "1")
    var notes by mutableStateOf(existing?.cleanUserNotes.orEmpty())
    var mealUsage by mutableStateOf(existing?.mealUsage.orEmpty())
    var purchasePrice by mutableStateOf(existing?.purchasePrice?.cleanNumber().orEmpty())
    var purchaseQuantity by mutableStateOf(existing?.purchaseQuantity?.cleanNumber() ?: "1")
    var selectedMenuCategory by mutableStateOf("All")
    var appliedPresetName by mutableStateOf(if (existing != null && FoodMeasurementCodec.presets.any { it.name.equals(existing.name, ignoreCase = true) }) existing.name else null)
    var isSuggestionsDismissed by mutableStateOf(false)

    val initialStandardStyle = if (existing == null) {
        StandardItemStyle.SINGLE
    } else if (existing.stockUnit.equals("kg", ignoreCase = true) && !existing.portionUnit.equals("kg", ignoreCase = true)) {
        StandardItemStyle.WEIGHT
    } else if (existing.portionsPerStockUnit > 1.0 || existing.stockUnit != existing.portionUnit) {
        StandardItemStyle.PACK
    } else {
        StandardItemStyle.SINGLE
    }
    var standardStyle by mutableStateOf(initialStandardStyle)
    var packagePrice by mutableStateOf(
        if (existing != null && existing.defaultCostPerPortion > 0 && existing.portionsPerStockUnit > 1.0) {
            (existing.defaultCostPerPortion * existing.portionsPerStockUnit).cleanNumber()
        } else {
            ""
        }
    )

    // Spoons to grams state
    val initialSpoons = initialMeasurement as? FoodMeasurementType.SpoonsToGrams
    var gramsPerSpoon by mutableStateOf(initialSpoons?.gramsPerSpoon?.cleanNumber() ?: "10")
    var spoonUnitName by mutableStateOf(initialSpoons?.spoonUnitName ?: "spoon")

    // Tiered sizes state
    val initialSizes = (initialMeasurement as? FoodMeasurementType.TieredSizes)?.sizes ?: listOf(
        TieredSize("Small", 10.0),
        TieredSize("Medium", 15.0),
        TieredSize("Large", 20.0),
    )
    var smallPrice by mutableStateOf(initialSizes.firstOrNull { it.name.equals("Small", ignoreCase = true) }?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "10")
    var mediumPrice by mutableStateOf(initialSizes.firstOrNull { it.name.equals("Medium", ignoreCase = true) }?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "15")
    var largePrice by mutableStateOf(initialSizes.firstOrNull { it.name.equals("Large", ignoreCase = true) }?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "20")

    val initialContainerGrams = if (initialSpoons != null && (existing?.portionsPerStockUnit ?: 0.0) > 0) {
        (existing!!.portionsPerStockUnit * initialSpoons.gramsPerSpoon).cleanNumber()
    } else if (existing?.stockUnit?.equals("kg", ignoreCase = true) == true) {
        "1000"
    } else {
        "380"
    }
    val initialContainerPrice = if (existing != null && existing.defaultCostPerPortion > 0 && existing.portionsPerStockUnit > 0) {
        (existing.defaultCostPerPortion * existing.portionsPerStockUnit).cleanNumber()
    } else {
        ""
    }
    var containerGrams by mutableStateOf(initialContainerGrams)
    var containerPrice by mutableStateOf(initialContainerPrice)

    // Simplified UX: progressive disclosure state (UI only, no logic change)
    var showAdvanced by mutableStateOf(false)
    var showMoreSingleUnits by mutableStateOf(false)
    var showMorePackUnits by mutableStateOf(false)
    var showMoreWeightUnits by mutableStateOf(false)
    var showSpoonTuning by mutableStateOf(false)

    fun applyPreset(preset: FoodPreset) {
        name = preset.name
        category = preset.category
        stockUnit = preset.stockUnit
        portionUnit = preset.portionUnit
        portionsPerStockUnit = preset.portionsPerStockUnit.cleanNumber()
        defaultCost = if (preset.defaultCostPerPortion == 0.0) "" else preset.defaultCostPerPortion.cleanNumber()
        notes = preset.description
        if (preset.mealUsage.isNotBlank()) {
            mealUsage = preset.mealUsage
        }
        if (preset.purchasePrice != null && preset.purchasePrice > 0.0) {
            purchasePrice = preset.purchasePrice.cleanNumber()
        }
        if (preset.purchaseQuantity > 0.0) {
            purchaseQuantity = preset.purchaseQuantity.cleanNumber()
        }
        when (val m = preset.measurement) {
            is FoodMeasurementType.SpoonsToGrams -> {
                measurementMode = MeasurementMode.SPOONS
                gramsPerSpoon = m.gramsPerSpoon.cleanNumber()
                spoonUnitName = m.spoonUnitName
                containerGrams = (preset.portionsPerStockUnit * m.gramsPerSpoon).cleanNumber()
                containerPrice = (preset.defaultCostPerPortion * preset.portionsPerStockUnit).cleanNumber()
            }
            is FoodMeasurementType.TieredSizes -> {
                measurementMode = MeasurementMode.TIERED
                smallPrice = m.sizes.getOrNull(0)?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "10"
                mediumPrice = m.sizes.getOrNull(1)?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "15"
                largePrice = m.sizes.getOrNull(2)?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "20"
            }
            FoodMeasurementType.Standard -> {
                measurementMode = MeasurementMode.STANDARD
                standardStyle = if (preset.stockUnit.contains("kg", ignoreCase = true) && !preset.portionUnit.contains("kg", ignoreCase = true)) {
                    StandardItemStyle.WEIGHT
                } else if (preset.portionsPerStockUnit > 1.0) {
                    StandardItemStyle.PACK
                } else {
                    StandardItemStyle.SINGLE
                }
                packagePrice = if (preset.portionsPerStockUnit > 1.0) {
                    (preset.defaultCostPerPortion * preset.portionsPerStockUnit).cleanNumber()
                } else {
                    ""
                }
            }
        }
    }

    fun applyCatalogItem(item: FoodCatalogItem) {
        name = item.name
        category = item.category
        stockUnit = item.stockUnit
        portionUnit = item.portionUnit
        portionsPerStockUnit = item.portionsPerStockUnit.cleanNumber()
        defaultCost = if (item.defaultCostPerPortion == 0.0) "" else item.defaultCostPerPortion.cleanNumber()
        notes = item.cleanUserNotes
        if (item.mealUsage.isNotBlank()) {
            mealUsage = item.mealUsage
        }
        if (item.purchasePrice != null && item.purchasePrice > 0.0) {
            purchasePrice = item.purchasePrice.cleanNumber()
        }
        if (item.purchaseQuantity != null && item.purchaseQuantity > 0.0) {
            purchaseQuantity = item.purchaseQuantity.cleanNumber()
        }
        when (val m = item.measurement) {
            is FoodMeasurementType.SpoonsToGrams -> {
                measurementMode = MeasurementMode.SPOONS
                gramsPerSpoon = m.gramsPerSpoon.cleanNumber()
                spoonUnitName = m.spoonUnitName
                containerGrams = (item.portionsPerStockUnit * m.gramsPerSpoon).cleanNumber()
                containerPrice = (item.defaultCostPerPortion * item.portionsPerStockUnit).cleanNumber()
            }
            is FoodMeasurementType.TieredSizes -> {
                measurementMode = MeasurementMode.TIERED
                smallPrice = m.sizes.getOrNull(0)?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "10"
                mediumPrice = m.sizes.getOrNull(1)?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "15"
                largePrice = m.sizes.getOrNull(2)?.price?.let { if (it == 0.0) "" else it.cleanNumber() } ?: "20"
            }
            FoodMeasurementType.Standard -> {
                measurementMode = MeasurementMode.STANDARD
                standardStyle = if (item.stockUnit.contains("kg", ignoreCase = true) && !item.portionUnit.contains("kg", ignoreCase = true)) {
                    StandardItemStyle.WEIGHT
                } else if (item.portionsPerStockUnit > 1.0) {
                    StandardItemStyle.PACK
                } else {
                    StandardItemStyle.SINGLE
                }
                packagePrice = if (item.portionsPerStockUnit > 1.0) {
                    (item.defaultCostPerPortion * item.portionsPerStockUnit).cleanNumber()
                } else {
                    ""
                }
            }
        }
    }

    val selectedKind: String
        get() = when (measurementMode) {
            MeasurementMode.SPOONS -> "spoons"
            MeasurementMode.TIERED -> "tiered"
            MeasurementMode.STANDARD -> when (standardStyle) {
                StandardItemStyle.SINGLE -> "single"
                StandardItemStyle.PACK -> "pack"
                StandardItemStyle.WEIGHT -> "weight"
            }
        }

    fun selectKind(kind: String) {
        when (kind) {
            "single" -> {
                measurementMode = MeasurementMode.STANDARD
                standardStyle = StandardItemStyle.SINGLE
                stockUnit = portionUnit.ifBlank { "piece" }
                portionsPerStockUnit = "1"
                packagePrice = ""
            }
            "pack" -> {
                measurementMode = MeasurementMode.STANDARD
                standardStyle = StandardItemStyle.PACK
                if (stockUnit == "piece" || stockUnit == "kg") stockUnit = "carton"
                if (portionUnit == "carton") portionUnit = "egg"
                if (portionsPerStockUnit == "1") portionsPerStockUnit = "30"
                val count = portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
                val singleCost = defaultCost.asDouble()
                if (singleCost > 0) packagePrice = (singleCost * count).cleanNumber()
            }
            "weight" -> {
                measurementMode = MeasurementMode.STANDARD
                standardStyle = StandardItemStyle.WEIGHT
                stockUnit = "kg"
                val lower = name.lowercase()
                if (lower.contains("sugar") || lower.contains("flour") || lower.contains("salt")) {
                    if (portionUnit == "piece") portionUnit = "g"
                    if (portionsPerStockUnit == "1" || portionsPerStockUnit == "6") portionsPerStockUnit = "1000"
                } else {
                    if (portionUnit == "g" && portionsPerStockUnit == "1000") {
                        // keep bulk as-is
                    } else if (portionsPerStockUnit == "1" || portionsPerStockUnit == "1000") {
                        portionsPerStockUnit = "6"
                        if (portionUnit == "piece" || portionUnit == "carton" || portionUnit == "g") {
                            portionUnit = if (lower.contains("tomato")) "tomato" else "piece"
                        }
                    }
                }
                val count = portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
                val singleCost = defaultCost.asDouble()
                if (singleCost > 0) packagePrice = (singleCost * count).cleanNumber()
            }
            "spoons" -> {
                measurementMode = MeasurementMode.SPOONS
                if (portionUnit == "piece") {
                    portionUnit = "spoon"
                    spoonUnitName = "spoon"
                    stockUnit = "jar"
                    portionsPerStockUnit = "100"
                    if (defaultCost.isBlank() || defaultCost == "0") defaultCost = "1.6"
                } else {
                    spoonUnitName = portionUnit
                }
            }
            "tiered" -> {
                measurementMode = MeasurementMode.TIERED
                portionUnit = "pack"
                stockUnit = "pack"
                portionsPerStockUnit = "1"
            }
        }
    }

    val isValid: Boolean
        get() = name.isNotBlank() && when (measurementMode) {
            MeasurementMode.STANDARD -> defaultCost.asDouble() >= 0.0 && portionsPerStockUnit.asDouble() > 0.0 && portionUnit.isNotBlank() && stockUnit.isNotBlank()
            MeasurementMode.SPOONS -> defaultCost.asDouble() >= 0.0 && gramsPerSpoon.asDouble() > 0.0 && spoonUnitName.isNotBlank()
            MeasurementMode.TIERED -> smallPrice.asDouble() >= 0.0 && mediumPrice.asDouble() >= 0.0 && largePrice.asDouble() >= 0.0
        }

    fun buildCatalogItem(existingId: String?): FoodCatalogItem {
        val builtMeasurement = when (measurementMode) {
            MeasurementMode.SPOONS -> {
                FoodMeasurementType.SpoonsToGrams(
                    gramsPerSpoon = gramsPerSpoon.asDouble().coerceAtLeast(1.0),
                    spoonUnitName = spoonUnitName.trim().ifBlank { "spoon" },
                )
            }
            MeasurementMode.TIERED -> {
                FoodMeasurementType.TieredSizes(
                    listOf(
                        TieredSize("Small", smallPrice.asDouble().coerceAtLeast(0.0)),
                        TieredSize("Medium", mediumPrice.asDouble().coerceAtLeast(0.0)),
                        TieredSize("Large", largePrice.asDouble().coerceAtLeast(0.0)),
                    ),
                )
            }
            MeasurementMode.STANDARD -> FoodMeasurementType.Standard
        }

        val finalPortionUnit = when (measurementMode) {
            MeasurementMode.SPOONS -> spoonUnitName.trim().ifBlank { "spoon" }
            MeasurementMode.TIERED -> "pack"
            MeasurementMode.STANDARD -> portionUnit.trim().ifBlank { "piece" }
        }

        val finalStockUnit = when (measurementMode) {
            MeasurementMode.SPOONS -> stockUnit.trim().ifBlank { "kg" }
            MeasurementMode.TIERED -> "pack"
            MeasurementMode.STANDARD -> stockUnit.trim().ifBlank { "piece" }
        }

        val finalPortionsPerStock = when (measurementMode) {
            MeasurementMode.SPOONS -> portionsPerStockUnit.asDouble().coerceAtLeast(1.0)
            MeasurementMode.TIERED -> 1.0
            MeasurementMode.STANDARD -> portionsPerStockUnit.asDouble().coerceAtLeast(0.0001)
        }

        val finalDefaultCost = when (measurementMode) {
            MeasurementMode.SPOONS -> defaultCost.asDouble().coerceAtLeast(0.0)
            MeasurementMode.TIERED -> mediumPrice.asDouble().coerceAtLeast(0.0)
            MeasurementMode.STANDARD -> defaultCost.asDouble().coerceAtLeast(0.0)
        }

        val encodedNotes = FoodMeasurementCodec.encodeNotes(notes, builtMeasurement)
        val finalPurchasePrice = purchasePrice.asDouble().takeIf { it > 0.0 }
        val finalPurchaseQuantity = purchaseQuantity.asDouble().takeIf { it > 0.0 } ?: 1.0

        return FoodCatalogItem(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name.trim(),
            category = category,
            stockUnit = finalStockUnit,
            portionUnit = finalPortionUnit,
            portionsPerStockUnit = finalPortionsPerStock,
            defaultCostPerPortion = finalDefaultCost,
            notes = encodedNotes,
            purchasePrice = finalPurchasePrice,
            purchaseQuantity = finalPurchaseQuantity,
            purchaseUnit = finalStockUnit,
            mealUsage = mealUsage.trim(),
            priceKnown = finalPurchasePrice != null || finalDefaultCost > 0.0,
            conversionKnown = finalPortionsPerStock > 0.0,
        )
    }
}
