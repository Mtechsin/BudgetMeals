package com.budgetmeals.app.data

import java.util.Locale

/**
 * Canonical unit handling.
 *
 * The app used to decide that two units were "the same" when one name contained the
 * other, so `g` matched `kg` and `g` matched `jar (380g)`, and quantities were then used
 * without converting them. This file replaces that with explicit facts:
 *
 *  - a unit has a dimension (mass, volume, count) and a factor to a base unit,
 *  - a package label such as `jar (380g)` or `carton (30)` is *parsed* into a size,
 *  - anything unknown resolves to `null` and callers must say so instead of guessing.
 *
 * Count units are their own base ("cup" is never silently a "piece"). Only the generic
 * count word `piece` may stand in for another count unit, and only through a catalog.
 */
enum class UnitDimension { MASS, VOLUME, COUNT }

/** A unit understood without extra context. [basePerUnit] is how many base units it holds. */
data class UnitDef(
    val id: String,
    val dimension: UnitDimension,
    val basePerUnit: Double,
    val label: String,
)

/** A parsed package label, e.g. `jar (380g)` -> 380 g, `carton (30)` -> 30 portions. */
data class PackageSize(
    val container: String,
    val dimension: UnitDimension,
    val sizeInBase: Double,
    val baseUnit: String,
    val portionCount: Double?,
)

/** How a catalog item converts between packages, portions and its base unit. */
data class CatalogConversion(
    val baseUnit: String,
    val baseUnitsPerStockUnit: Double,
    val baseUnitsPerPortion: Double,
    val packageLabel: String?,
    val packageSize: PackageSize?,
) {
    val usable: Boolean
        get() = baseUnitsPerPortion > 0.0 && baseUnit.isNotBlank()
}


object Units {
    private val massUnits = mapOf("g" to 1.0, "kg" to 1_000.0)
    private val volumeUnits = mapOf("ml" to 1.0, "l" to 1_000.0)
    private val countUnits = setOf(
        "piece", "egg", "loaf", "cup", "spoon", "portion", "slice", "can", "jar", "bottle",
        "carton", "pack", "bag", "tub", "box", "sachet", "bar", "bunch", "head", "clove",
    )

    private val aliases = mapOf(
        "gram" to "g", "grams" to "g", "gr" to "g",
        "kilogram" to "kg", "kilograms" to "kg", "kgs" to "kg", "kilo" to "kg", "kilos" to "kg",
        "milliliter" to "ml", "milliliters" to "ml", "millilitre" to "ml", "millilitres" to "ml",
        "liter" to "l", "liters" to "l", "litre" to "l", "litres" to "l",
        "pieces" to "piece", "pcs" to "piece", "units" to "piece",
        "eggs" to "egg",
        "loaves" to "loaf",
        "cups" to "cup",
        "spoons" to "spoon", "tbsp" to "spoon", "tablespoon" to "spoon", "tablespoons" to "spoon",
        "tsp" to "spoon", "teaspoon" to "spoon", "teaspoons" to "spoon",
        "portions" to "portion",
        "slices" to "slice",
        "cans" to "can", "tins" to "can",
        "jars" to "jar",
        "bottles" to "bottle",
        "cartons" to "carton",
        "packs" to "pack", "packages" to "pack", "packets" to "pack",
        "bags" to "bag",
        "tubs" to "tub",
        "boxes" to "box",
        "sachets" to "sachet",
        "bars" to "bar",
        "bunches" to "bunch",
        "heads" to "head",
        "cloves" to "clove",
    )

    private val packagePattern = Regex("^(.+?)\\s*\\(([^)]+)\\)\\s*$")
    private val numberUnitPattern = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*([a-zA-Z]+)$")
    private val bareNumberPattern = Regex("^([0-9]+(?:[.,][0-9]+)?)$")

    /** Lowercases and folds plurals/aliases, but never merges two different units. */
    fun normalize(label: String): String {
        val raw = label.trim().lowercase(Locale.US)
        return aliases[raw] ?: raw
    }

    /** The bare container word of a package label: `jar (380g)` -> `jar`. */
    fun containerOf(label: String): String? {
        val match = packagePattern.matchEntire(label.trim()) ?: return null
        val container = normalize(match.groupValues[1])
        return container.ifBlank { null }
    }

    /** Resolves a label with no further context. Returns null for anything unknown. */
    fun canonical(label: String): UnitDef? {
        val id = normalize(label)
        massUnits[id]?.let { return UnitDef(id, UnitDimension.MASS, it, id) }
        volumeUnits[id]?.let { return UnitDef(id, UnitDimension.VOLUME, it, id) }
        if (id in countUnits) return UnitDef(id, UnitDimension.COUNT, 1.0, id)
        // "jar (380g)" is still the container unit "jar"; the size is read by [parsePackage].
        val container = containerOf(label)
        if (container != null && container in countUnits) {
            return UnitDef(container, UnitDimension.COUNT, 1.0, container)
        }
        return null
    }

    /** Reads a package label into a size. Never guesses: returns null when there is no size. */
    fun parsePackage(label: String): PackageSize? {
        val match = packagePattern.matchEntire(label.trim()) ?: return null
        val container = normalize(match.groupValues[1])
        val inside = match.groupValues[2].trim()

        val numberUnit = numberUnitPattern.matchEntire(inside)
        if (numberUnit != null) {
            val amount = numberUnit.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val unitId = normalize(numberUnit.groupValues[2])
            massUnits[unitId]?.let {
                return PackageSize(container, UnitDimension.MASS, amount * it, "g", null)
            }
            volumeUnits[unitId]?.let {
                return PackageSize(container, UnitDimension.VOLUME, amount * it, "ml", null)
            }
            if (unitId in countUnits) {
                return PackageSize(container, UnitDimension.COUNT, amount, unitId, amount)
            }
            return null
        }

        val bareNumber = bareNumberPattern.matchEntire(inside) ?: return null
        val amount = bareNumber.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        // A bare number is a count of portions; the portion unit comes from the catalog.
        return PackageSize(container, UnitDimension.COUNT, amount, "", amount)
    }

    fun dimensionOf(baseUnit: String): UnitDimension? = canonical(baseUnit)?.dimension

    /** True when both labels name the same unit (case/plural only, never a substring). */
    fun sameUnit(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val unitA = canonical(a) ?: return false
        val unitB = canonical(b) ?: return false
        return unitA.id == unitB.id && unitA.dimension == unitB.dimension
    }

    /**
     * Base unit and conversion factors for a catalog item.
     *
     * [portionsPerStockUnit] keeps its meaning: how many portions one package holds.
     * [spoonGrams] is only set for foods measured in spoons, where "1 spoon = N g" is
     * stated by the user and is therefore the most trustworthy fact we have.
     */
    fun catalogConversion(
        stockUnit: String,
        portionUnit: String,
        portionsPerStockUnit: Double,
        spoonGrams: Double?,
    ): CatalogConversion {
        val pkg = parsePackage(stockUnit)
        val portions = portionsPerStockUnit.takeIf { it > 0.0 && it.isFinite() }
        val portionCountUnit = canonical(portionUnit)?.takeIf { it.dimension == UnitDimension.COUNT }?.id
            ?: containerOf(portionUnit)
            ?: "unit"

        if (spoonGrams != null && spoonGrams > 0.0) {
            val packageGrams = pkg?.takeIf { it.dimension == UnitDimension.MASS }?.sizeInBase
            // The stated package size is the source of truth when the catalog also gives a
            // per-package portion count that does not contradict the spoon size.
            val perPortionFromPackage = packageGrams?.let { size -> portions?.let { size / it } }
            val perPortion = if (perPortionFromPackage != null && perPortionFromPackage > 0.0) {
                perPortionFromPackage
            } else {
                spoonGrams
            }
            return CatalogConversion(
                baseUnit = "g",
                baseUnitsPerStockUnit = packageGrams
                    ?: portions?.times(spoonGrams)
                    ?: spoonGrams,
                baseUnitsPerPortion = perPortion,
                packageLabel = stockUnit.takeIf { it.isNotBlank() },
                packageSize = pkg,
            )
        }

        val bareStock = canonical(stockUnit)?.takeIf { it.dimension != UnitDimension.COUNT }
        if (bareStock != null) {
            // The package is stated in a real unit, e.g. "kg": one package is one of it.
            val perStock = bareStock.basePerUnit
            val baseId = if (bareStock.dimension == UnitDimension.MASS) "g" else "ml"
            return CatalogConversion(
                baseUnit = baseId,
                baseUnitsPerStockUnit = perStock,
                baseUnitsPerPortion = portions?.let { perStock / it } ?: perStock,
                packageLabel = stockUnit.takeIf { it.isNotBlank() },
                packageSize = null,
            )
        }

        if (pkg != null && pkg.sizeInBase > 0.0) {
            val base = when (pkg.dimension) {
                UnitDimension.MASS -> "g"
                UnitDimension.VOLUME -> "ml"
                UnitDimension.COUNT -> pkg.baseUnit.ifBlank { portionCountUnit }
            }
            return CatalogConversion(
                baseUnit = base,
                baseUnitsPerStockUnit = pkg.sizeInBase,
                baseUnitsPerPortion = portions?.let { pkg.sizeInBase / it } ?: pkg.sizeInBase,
                packageLabel = stockUnit.takeIf { it.isNotBlank() },
                packageSize = pkg,
            )
        }

        // No stated package size: one portion is the base unit and the package holds
        // whatever the catalog says it holds.
        return CatalogConversion(
            baseUnit = portionCountUnit,
            baseUnitsPerStockUnit = portions ?: 1.0,
            baseUnitsPerPortion = 1.0,
            packageLabel = stockUnit.takeIf { it.isNotBlank() && canonical(it) != null },
            packageSize = null,
        )
    }

    /**
     * How many base units one [label] holds inside [catalog], or null when we cannot say.
     * This is the only door through which stock math may convert a label.
     */
    fun basePerUnit(label: String, catalog: FoodCatalogItem): Double? {
        if (label.isBlank()) return null
        val conversion = catalog.conversion
        if (!conversion.usable) return null
        val normalized = normalize(label)
        val normalizedPortion = normalize(catalog.portionUnit)
        val normalizedStock = normalize(catalog.stockUnit)
        val container = containerOf(catalog.stockUnit)

        if (normalized == normalizedPortion && conversion.baseUnitsPerPortion > 0.0) {
            return conversion.baseUnitsPerPortion
        }
        if (normalized == normalizedStock && conversion.baseUnitsPerStockUnit > 0.0) {
            return conversion.baseUnitsPerStockUnit
        }
        if (container != null && normalized == container && conversion.baseUnitsPerStockUnit > 0.0) {
            return conversion.baseUnitsPerStockUnit
        }
        val unit = canonical(label) ?: run {
            // Named sizes such as "Medium" stand for one portion of that food.
            val measurement = catalog.measurement
            if (measurement is FoodMeasurementType.TieredSizes &&
                measurement.sizes.any { it.name.equals(label.trim(), ignoreCase = true) }
            ) {
                return conversion.baseUnitsPerPortion
            }
            return null
        }
        if (unit.dimension == UnitDimension.COUNT) {
            // Counts only convert through the catalog, except the generic word "piece".
            return if (normalized == "piece") 1.0 else null
        }
        return if (unit.dimension == dimensionOf(conversion.baseUnit)) unit.basePerUnit else null
    }

    /** Converts [quantity] from one label to another, or null when the pair is not trustworthy. */
    fun convert(quantity: Double, from: String, to: String, catalog: FoodCatalogItem?): Double? {
        if (from.isBlank() || to.isBlank()) return null
        if (catalog != null) {
            val fromFactor = basePerUnit(from, catalog)
            val toFactor = basePerUnit(to, catalog)
            if (fromFactor != null && toFactor != null && toFactor > 0.0) {
                return quantity * fromFactor / toFactor
            }
        }
        if (sameUnit(from, to)) return quantity
        val fromUnit = canonical(from) ?: return null
        val toUnit = canonical(to) ?: return null
        if (fromUnit.dimension != toUnit.dimension) return null
        if (fromUnit.dimension == UnitDimension.COUNT) return null
        return quantity * fromUnit.basePerUnit / toUnit.basePerUnit
    }
}
