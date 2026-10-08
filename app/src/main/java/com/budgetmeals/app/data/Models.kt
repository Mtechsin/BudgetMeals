package com.budgetmeals.app.data

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class ItemCategory(val label: String, val icon: String) {
    FOOD_STAPLE("Staples", "rice"),
    FOOD_FRESH("Fresh", "egg"),
    FOOD_STREET("Street food", "restaurant"),
    SNACK("Snack", "cookie"),
    HOUSEHOLD("Household", "home"),
    OTHER("Other", "more_horiz"),
}

enum class BatchType(val label: String) {
    SINGLE_MEAL("One sitting"),
    WEEKLY("Weekly batch"),
    MONTHLY("Monthly batch"),
    CUSTOM("Custom batch"),
}

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
}

enum class MealStatus {
    PLANNED,
    EATEN,
    PARTIAL,
    SKIPPED,
    UNRECORDED,
}

@Immutable
data class FoodPriceOption(
    val name: String,
    val price: Double,
    val quantity: Double? = null,
    val unit: String? = null,
)

@Immutable
data class FoodCatalogItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: ItemCategory = ItemCategory.FOOD_FRESH,
    val stockUnit: String = "piece",
    val portionUnit: String = "piece",
    val portionsPerStockUnit: Double = 1.0,
    val defaultCostPerPortion: Double = 0.0,
    val notes: String = "",
    val purchasePrice: Double? = null,
    val purchaseQuantity: Double? = null,
    val purchaseUnit: String? = null,
    val mealUsage: String = "",
    val priceOptions: List<FoodPriceOption> = emptyList(),
    val priceKnown: Boolean = true,
    val conversionKnown: Boolean = true,
) {
    val measurement: FoodMeasurementType by lazy(LazyThreadSafetyMode.NONE) {
        FoodMeasurementCodec.decodeMeasurement(notes, name, portionUnit, stockUnit)
    }

    val cleanUserNotes: String by lazy(LazyThreadSafetyMode.NONE) {
        FoodMeasurementCodec.extractUserNotes(notes)
    }

    val isSpoons: Boolean
        get() = measurement is FoodMeasurementType.SpoonsToGrams

    val isTieredSizes: Boolean
        get() = measurement is FoodMeasurementType.TieredSizes

    /**
     * Package/portion/base facts derived once from [stockUnit], [portionUnit],
     * [portionsPerStockUnit] and the spoon measurement. Stock quantities are stored in
     * [baseUnit], so this is what makes "1000 g of tomatoes" and "6 pieces per kg" agree.
     */
    val conversion: CatalogConversion by lazy(LazyThreadSafetyMode.NONE) {
        val spoonGrams = (measurement as? FoodMeasurementType.SpoonsToGrams)?.gramsPerSpoon
        Units.catalogConversion(stockUnit, portionUnit, portionsPerStockUnit, spoonGrams)
    }

    /** The unit every quantity of this food is stored in: g, ml, egg, cup, portion... */
    val baseUnit: String
        get() = conversion.baseUnit

    /** How many base units one portion costs, e.g. tomatoes 166.67 g per piece. */
    val baseUnitsPerPortion: Double
        get() = conversion.baseUnitsPerPortion

    /** How many base units one package holds, e.g. 30 eggs per carton. */
    val baseUnitsPerStockUnit: Double
        get() = conversion.baseUnitsPerStockUnit

    /** Label to show for a package, e.g. "carton (30)". */
    val packageLabel: String?
        get() = conversion.packageLabel

    /** Parsed size of one package, e.g. 380 g for "jar (380g)". Null when no size is stated. */
    val packageSize: PackageSize?
        get() = conversion.packageSize

    val hasUsableConversion: Boolean
        get() = conversionKnown && portionsPerStockUnit > 0.0 && conversion.usable

    val hasKnownPrice: Boolean
        get() = priceKnown && (defaultCostPerPortion > 0.0 || purchasePrice != null || priceOptions.any { it.price > 0.0 })

    val purchaseLabel: String?
        get() = when {
            purchaseQuantity != null && !purchaseUnit.isNullOrBlank() ->
                "${purchaseQuantity.cleanNumberForMeal()} ${purchaseUnit!!.trim()}"
            !purchaseUnit.isNullOrBlank() -> purchaseUnit!!.trim()
            else -> null
        }

    val priceRangeLabel: String? by lazy(LazyThreadSafetyMode.NONE) {
        when {
            priceOptions.size >= 2 -> {
                val prices = priceOptions.map { it.price }.filter { it >= 0.0 }
                if (prices.size >= 2) "${prices.min().cleanNumberForMeal()}-${prices.max().cleanNumberForMeal()} EGP"
                else null
            }
            purchasePrice != null && purchasePrice > 0.0 -> "${purchasePrice.cleanNumberForMeal()} EGP${purchaseLabel?.let { " / $it" } ?: ""}"
            else -> null
        }
    }

    val conversionLabel: String by lazy(LazyThreadSafetyMode.NONE) {
        when {
            !hasUsableConversion -> "Conversion needs to be completed"
            else -> when (val m = measurement) {
                is FoodMeasurementType.SpoonsToGrams -> {
                    "1 ${m.spoonUnitName} = ${m.gramsPerSpoon.catalogNumber()}g · 1 $stockUnit = ${portionsPerStockUnit.catalogNumber()} ${m.spoonUnitName}s"
                }
                is FoodMeasurementType.TieredSizes -> {
                    "Sizes: " + m.sizes.joinToString(" · ") { "${it.name} ${it.price.cleanNumberForMeal()} EGP" }
                }
                FoodMeasurementType.Standard -> {
                    "1 $stockUnit = ${portionsPerStockUnit.catalogNumber()} $portionUnit"
                }
            }
        }
    }

    fun formatPortionQuantity(quantity: Double): String {
        return when (val m = measurement) {
            is FoodMeasurementType.SpoonsToGrams -> {
                val totalGrams = quantity * m.gramsPerSpoon
                val qtyStr = quantity.cleanNumberForMeal()
                val unitStr = if (quantity == 1.0) m.spoonUnitName else "${m.spoonUnitName}s"
                "$qtyStr $unitStr (~${totalGrams.cleanNumberForMeal()}g)"
            }
            is FoodMeasurementType.TieredSizes -> {
                val size = m.sizes.firstOrNull { it.price == defaultCostPerPortion }
                if (size != null) {
                    "${quantity.cleanNumberForMeal()} ${size.name}"
                } else {
                    "${quantity.cleanNumberForMeal()} $portionUnit"
                }
            }
            FoodMeasurementType.Standard -> {
                "${quantity.cleanNumberForMeal()} $portionUnit"
            }
        }
    }
}

@Immutable
data class MealComponent(
    val id: String = UUID.randomUUID().toString(),
    val catalogId: String? = null,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "piece",
    val costPerUnit: Double = 0.0,
    val useStock: Boolean = true,
) {
    val estimatedCost: Double
        get() = quantity * costPerUnit
}

@Immutable
data class DatedUsage(
    val date: LocalDate,
    val amount: Double,
    val sourceId: String? = null,
)

@Immutable
data class StockItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: ItemCategory = ItemCategory.FOOD_STAPLE,
    /** The base unit every quantity below is expressed in: g, ml, egg, cup, portion... */
    val unit: String = "piece",
    val totalQuantity: Double,
    val totalPrice: Double,
    val purchaseDate: LocalDate = LocalDate.now(),
    val expiryDate: LocalDate? = null,
    val batchType: BatchType = BatchType.MONTHLY,
    val estimatedUsagePerDay: Double = 0.0,
    val consumedQuantity: Double = 0.0,
    val usageHistory: List<Double> = emptyList(),
    val datedUsageHistory: List<DatedUsage> = emptyList(),
    val notes: String = "",
    val catalogId: String? = null,
    val linkedExpenseId: String? = null,
    /** How this batch was bought, e.g. "carton (30)". Belongs to the batch, not the catalog. */
    val packageLabel: String = "",
    /** What one package of this batch holds in [unit]. 0 means "not stated". */
    val packageSize: Double = 0.0,
    /** False when we could not convert the entered amount, so the numbers stay untouched. */
    val conversionKnown: Boolean = true,
) {
    val costPerUnit: Double
        get() = if (totalQuantity <= 0.0) 0.0 else totalPrice / totalQuantity

    val remainingQuantity: Double
        get() = (totalQuantity - consumedQuantity).coerceAtLeast(0.0)

    val remainingPercent: Double
        get() = if (totalQuantity <= 0.0) 0.0 else (remainingQuantity / totalQuantity).coerceIn(0.0, 1.0)

    /** Packages bought, when the batch says how big one package is. */
    val packageCount: Double
        get() = if (packageSize > 0.0) totalQuantity / packageSize else 0.0

    val hasPackageInfo: Boolean
        get() = packageSize > 0.0 && packageLabel.isNotBlank()

    /** "1000 g" or "2 × carton (30)" for lists and receipts. */
    val quantityLabel: String
        get() = if (hasPackageInfo) {
            "${packageCount.cleanNumberForMeal()} × $packageLabel (${totalQuantity.cleanNumberForMeal()} $unit)"
        } else {
            "${totalQuantity.cleanNumberForMeal()} $unit"
        }

    val dailyUsageByDay: Map<LocalDate, Double>
        get() = datedUsageHistory.groupBy { it.date }.mapValues { (_, events) -> events.sumOf { it.amount } }

    val hasLearnedDailyRate: Boolean
        get() = dailyUsageByDay.size >= 2

    val learnedDailyRate: Double?
        get() {
            val days = dailyUsageByDay.keys.sorted()
            if (days.size < 2) return null
            val spanDays = (java.time.temporal.ChronoUnit.DAYS.between(days.first(), days.last()) + 1).coerceAtLeast(1L)
            val totalUsed = dailyUsageByDay.values.sum()
            return if (spanDays > 0) totalUsed / spanDays.toDouble() else null
        }

    val effectiveUsagePerDay: Double
        get() = learnedDailyRate ?: estimatedUsagePerDay

    val isUsageEstimated: Boolean
        get() = !hasLearnedDailyRate && estimatedUsagePerDay > 0.0

    val daysRemaining: Double
        get() = if (effectiveUsagePerDay > 0.0) remainingQuantity / effectiveUsagePerDay else 999.0

    val costPerDay: Double
        get() = costPerUnit * effectiveUsagePerDay

    val isLow: Boolean
        get() = remainingPercent <= 0.25 || (daysRemaining < 3.0 && daysRemaining < 999.0)

    val isFinished: Boolean
        get() = remainingQuantity <= 0.001

    val daysLabel: String
        get() = when {
            isFinished -> "Finished"
            daysRemaining >= 999.0 -> "No daily estimate"
            isUsageEstimated -> "~${daysRemaining.toInt()} days left (est.)"
            daysRemaining < 1.0 -> "Less than a day"
            else -> "${daysRemaining.toInt()} days left"
        }
}

@Immutable
data class MealTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mealType: MealType,
    val cost: Double,
    val isRecurring: Boolean = true,
    val dayOfWeek: DayOfWeek? = null,
    val notes: String = "",
    val isCustom: Boolean = false,
    val components: List<MealComponent> = emptyList(),
) {
    val foodSummary: String
        get() = components.joinToString(", ") { "${it.quantity.cleanNumberForMeal()} ${it.unit} ${it.name}" }
            .ifBlank { notes }
}

@Immutable
data class ExpenseCategory(
    val id: String,
    val name: String,
    val icon: String,
    val monthlyBudget: Double,
    val isFood: Boolean,
    val isCustom: Boolean = false,
    val sortOrder: Int = 0,
)

@Immutable
data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val amount: Double,
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val isRecurring: Boolean = false,
    val recurringFrequency: String? = null,
    val isCorrection: Boolean = false,
    val recurringScheduleId: String? = null,
    val isBudgetTransfer: Boolean = false,
)

@Immutable
data class MealLog(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate = LocalDate.now(),
    val mealType: MealType,
    val templateId: String? = null,
    val name: String,
    val cost: Double,
    val actualTime: LocalDateTime = LocalDateTime.now(),
    val consumedCost: Double = cost,
    val foods: String = "",
    val status: MealStatus = MealStatus.EATEN,
    val components: List<MealComponent> = emptyList(),
) {
    val leftoverCost: Double
        get() = (cost - consumedCost).coerceAtLeast(0.0)

    val consumedRatio: Double
        get() = when {
            status == MealStatus.SKIPPED || status == MealStatus.UNRECORDED -> 0.0
            cost <= 0.0 && isConsumed -> 1.0
            else -> (consumedCost / cost).coerceIn(0.0, 1.0)
        }

    val foodSummary: String
        get() = components.joinToString(", ") { "${it.quantity.cleanNumberForMeal()} ${it.unit} ${it.name}" }
            .ifBlank { foods }

    val isConsumed: Boolean
        get() = status == MealStatus.EATEN || status == MealStatus.PARTIAL

    val isSkipped: Boolean
        get() = status == MealStatus.SKIPPED

    val isUnrecorded: Boolean
        get() = status == MealStatus.UNRECORDED
}

@Immutable
data class MealReconciliation(
    val template: MealTemplate,
    val consumedCost: Double,
    val status: MealStatus,
)

@Immutable
data class DailyMealPlan(
    val id: String,
    val date: LocalDate,
    val mealType: MealType,
    val templateId: String?,
    val name: String,
    val cost: Double,
    val foods: String,
    val components: List<MealComponent> = emptyList(),
    val isCleared: Boolean = false,
)

@Immutable
data class DayClosure(
    val date: LocalDate,
    val closedAt: LocalDateTime,
    val plannedCost: Double,
    val consumedCost: Double,
    val leftoverCost: Double,
    val skippedCost: Double,
)

@Immutable
data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "piece",
    val estimatedPrice: Double = 0.0,
    val category: ItemCategory = ItemCategory.FOOD_STAPLE,
    val isChecked: Boolean = false,
    val createdDate: LocalDate = LocalDate.now(),
    val sourceItemId: String? = null,
    val note: String = "",
    val purchaseExpenseId: String? = null,
    val purchaseStockId: String? = null,
    val priceKnown: Boolean = true,
)

@Immutable
data class SparesTransaction(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate = LocalDate.now(),
    val amount: Double,
    val reason: String,
    val category: String? = null,
)

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Immutable
data class BudgetSettings(
    val monthlyFoodBudget: Double = 3_000.0,
    val dailyFoodBudget: Double = 100.0,
    val weeklyFoodLimit: Double = 750.0,
    val kosharyDay: DayOfWeek = DayOfWeek.WEDNESDAY,
    val kosharyPrice: Double = 60.0,
    val autoSaveSpares: Boolean = true,
    val remindersEnabled: Boolean = true,
    val shoppingDay: DayOfWeek = DayOfWeek.FRIDAY,
    val lastSparesAutoSaveDate: LocalDate? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
)

@Immutable
data class AppSnapshot(
    val categories: List<ExpenseCategory> = emptyList(),
    val stock: List<StockItem> = emptyList(),
    val foodCatalog: List<FoodCatalogItem> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val templates: List<MealTemplate> = emptyList(),
    val mealLogs: List<MealLog> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val spares: List<SparesTransaction> = emptyList(),
    val settings: BudgetSettings = BudgetSettings(),
    val dayPlans: List<DailyMealPlan> = emptyList(),
    val dayClosures: List<DayClosure> = emptyList(),
    val today: LocalDate = LocalDate.now(),
) {
    val dayPlansByDate: Map<LocalDate, List<DailyMealPlan>> by lazy(LazyThreadSafetyMode.NONE) {
        dayPlans.groupBy { it.date }.mapValues { (_, v) -> v.sortedBy { it.mealType.ordinal } }
    }

    val dayClosuresByDate: Map<LocalDate, DayClosure> by lazy(LazyThreadSafetyMode.NONE) {
        dayClosures.associateBy { it.date }
    }

    fun mealPlan(date: LocalDate): List<DailyMealPlan> =
        dayPlansByDate[date] ?: emptyList()

    fun dayClosure(date: LocalDate): DayClosure? = dayClosuresByDate[date]

    val currentMonthExpenses: List<Expense> by lazy(LazyThreadSafetyMode.NONE) {
        expenses.filter {
            it.date.year == today.year && it.date.month == today.month && it.date <= today && !it.isBudgetTransfer
        }
    }

    val recordedFoodSpentThisMonth: Double by lazy(LazyThreadSafetyMode.NONE) {
        val categoryIds = categories.filter { it.isFood }.map { it.id }.toSet()
        currentMonthExpenses
            .filter { it.categoryId in categoryIds }
            .sumOf { it.amount }
    }

    val foodSpentThisMonth: Double by lazy(LazyThreadSafetyMode.NONE) {
        recordedFoodSpentThisMonth.coerceAtLeast(0.0)
    }

    val foodRemainingThisMonth: Double by lazy(LazyThreadSafetyMode.NONE) {
        (settings.monthlyFoodBudget - foodSpentThisMonth - monthlyFoodTransfers).coerceAtLeast(0.0)
    }

    val monthlyFoodTransfers: Double by lazy(LazyThreadSafetyMode.NONE) {
        spares.filter { it.date.year == today.year && it.date.month == today.month &&
            (it.category == "daily_savings" || it.category == "daily_savings_spent")
        }.sumOf { it.amount }
    }

    val todayFoodSpent: Double by lazy(LazyThreadSafetyMode.NONE) {
        val foodCategoryIds = categories.filter { it.isFood }.map { it.id }.toSet()
        currentMonthExpenses
            .filter { it.date == today && it.categoryId in foodCategoryIds }
            .sumOf { it.amount }
            .coerceAtLeast(0.0)
    }

    val todayFoodRemaining: Double by lazy(LazyThreadSafetyMode.NONE) {
        val todayTransfers = spares.filter { it.date == today &&
            (it.category == "daily_savings" || it.category == "daily_savings_spent")
        }.sumOf { it.amount }
        minOf((settings.dailyFoodBudget - todayFoodSpent - todayTransfers).coerceAtLeast(0.0), foodRemainingThisMonth)
    }

    val sparesBalance: Double by lazy(LazyThreadSafetyMode.NONE) {
        spares.sumOf { it.amount }
    }

    val openShoppingCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        shopping.count { !it.isChecked }
    }

    val lowStock: List<StockItem> by lazy(LazyThreadSafetyMode.NONE) {
        stock.filter { !it.isFinished && it.isLow }.sortedBy { it.daysRemaining }
    }

    val foodCatalogById: Map<String, FoodCatalogItem> by lazy(LazyThreadSafetyMode.NONE) {
        foodCatalog.associateBy { it.id }
    }

    val foodCatalogByName: Map<String, FoodCatalogItem> by lazy(LazyThreadSafetyMode.NONE) {
        val map = HashMap<String, FoodCatalogItem>(foodCatalog.size * 2)
        for (item in foodCatalog) {
            map[item.name.lowercase(java.util.Locale.US)] = item
        }
        map
    }

    val stockByCatalogId: Map<String, List<StockItem>> by lazy(LazyThreadSafetyMode.NONE) {
        stock.filter { it.catalogId != null }.groupBy { it.catalogId!! }
    }

    /** Lots of a food we may compute with: right base unit, not finished, not expired. */
    fun usableLots(catalog: FoodCatalogItem): List<StockItem> =
        stockByCatalogId[catalog.id].orEmpty().filter { it.isUsableFor(catalog, today) }

    /** Lots of a food we must not compute with, so the UI can say why they are left out. */
    fun unusableLots(catalog: FoodCatalogItem): List<StockItem> =
        stockByCatalogId[catalog.id].orEmpty().filter { !it.isFinished && !it.isUsableFor(catalog, today) }

    /** Portions of a food a meal can actually use, plus the reasons some lots are excluded. */
    fun availabilityFor(catalog: FoodCatalogItem): CatalogAvailability {
        val usable = usableLots(catalog)
        val excluded = unusableLots(catalog)
        val lots = stockByCatalogId[catalog.id].orEmpty()
        if (!catalog.hasUsableConversion) {
            return CatalogAvailability(
                portions = 0.0,
                baseQuantity = 0.0,
                usableLots = 0,
                unusableLots = excluded.size,
                hasAnyStock = lots.isNotEmpty(),
                baseUnit = catalog.baseUnit,
                note = "Conversion needs to be completed",
            )
        }
        val baseQuantity = usable.sumOf { it.remainingQuantity }
        return CatalogAvailability(
            portions = if (catalog.baseUnitsPerPortion > 0.0) baseQuantity / catalog.baseUnitsPerPortion else 0.0,
            baseQuantity = baseQuantity,
            usableLots = usable.size,
            unusableLots = excluded.size,
            hasAnyStock = lots.isNotEmpty(),
            baseUnit = catalog.baseUnit,
            note = when {
                usable.isNotEmpty() -> null
                excluded.any { !it.conversionKnown } -> "Stock unit could not be converted"
                excluded.isNotEmpty() -> "Stock is finished or expired"
                else -> "Nothing tracked yet"
            },
        )
    }

    val catalogAvailablePortions: Map<String, Double> by lazy(LazyThreadSafetyMode.NONE) {
        val result = HashMap<String, Double>(foodCatalog.size * 2)
        for (catalog in foodCatalog) {
            if (!catalog.hasUsableConversion) continue
            result[catalog.id] = availabilityFor(catalog).portions
        }
        result
    }

    val currentMonthExpensesByCategoryId: Map<String, Double> by lazy(LazyThreadSafetyMode.NONE) {
        currentMonthExpenses.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val todayLogs: Map<MealType, MealLog> by lazy(LazyThreadSafetyMode.NONE) {
        mealLogs
            .filter { it.date == today }
            .groupBy { it.mealType }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.actualTime }!! }
    }
}

/** What a kitchen actually holds for one catalog food, and why lots were left out. */
@Immutable
data class CatalogAvailability(
    val portions: Double = 0.0,
    val baseQuantity: Double = 0.0,
    val usableLots: Int = 0,
    val unusableLots: Int = 0,
    val hasAnyStock: Boolean = false,
    val baseUnit: String = "",
    val note: String? = null,
) {
    val hasUsableStock: Boolean
        get() = usableLots > 0 && baseQuantity > 0.0
}

/**
 * A lot may only be used for a food when it is stored in that food's base unit and its
 * amount was converted when it was entered. Anything else is surfaced instead of guessed.
 */
fun StockItem.isUsableFor(catalog: FoodCatalogItem, today: LocalDate): Boolean {
    if (isFinished || !conversionKnown) return false
    if (expiryDate != null && expiryDate.isBefore(today)) return false
    return Units.sameUnit(unit, catalog.baseUnit)
}

private fun Double.cleanNumberForMeal(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.2f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')

private fun Double.catalogNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.3f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')
