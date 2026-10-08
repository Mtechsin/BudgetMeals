package com.budgetmeals.app.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.util.Locale

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
            map[item.name.lowercase(Locale.US)] = item
        }
        map
    }

    val stockByCatalogId: Map<String, List<StockItem>> by lazy(LazyThreadSafetyMode.NONE) {
        stock.mapNotNull { item -> item.catalogId?.let { it to item } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
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
            .mapValues { (_, entries) -> entries.maxBy { it.actualTime } }
    }
}
