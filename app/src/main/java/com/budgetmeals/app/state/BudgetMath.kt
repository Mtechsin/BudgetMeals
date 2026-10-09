package com.budgetmeals.app.state

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.data.Units
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object BudgetMath {
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)

    fun money(value: Double, decimals: Int? = null): String {
        if (decimals != null) {
            return String.format(Locale.US, "%.${decimals}f EGP", value)
        }
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f EGP", value)
        } else if (value < 0.1 && value > 0.0) {
            String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.') + " EGP"
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.') + " EGP"
        }
    }

    fun quantity(value: Double): String {
        return if (value % 1.0 == 0.0) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    fun compactMoney(value: Double): String {
        val rounded = value.roundToInt()
        return if (rounded % 100 == 0 && rounded >= 100) {
            "${rounded / 100}k"
        } else {
            rounded.toString()
        }
    }

    fun clamp(value: Double, min: Double = 0.0, max: Double = 1.0): Double = value.coerceIn(min, max)

    fun percent(spent: Double, budget: Double): Double = if (budget <= 0.0) 0.0 else clamp(spent / budget)

    fun daysInMonth(date: LocalDate = LocalDate.now()): Int = date.lengthOfMonth()

    fun daysLeftInMonth(date: LocalDate = LocalDate.now()): Int =
        (date.lengthOfMonth() - date.dayOfMonth + 1).coerceAtLeast(1)

    fun monthToDate(date: LocalDate = LocalDate.now()): Double = date.dayOfMonth.toDouble()

    fun standingDailyCost(snapshot: AppSnapshot): Double {
        return snapshot.stock
            .filter { it.batchType != BatchType.SINGLE_MEAL && !it.isFinished }
            .sumOf { it.costPerDay }
    }

    fun currentWeekStart(date: LocalDate = LocalDate.now()): LocalDate {
        return date.with(DayOfWeek.MONDAY)
    }

    fun currentWeekDates(date: LocalDate = LocalDate.now()): List<LocalDate> {
        val start = currentWeekStart(date)
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    fun mealPlanDates(start: LocalDate): List<LocalDate> {
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    fun foodSpentOn(date: LocalDate, snapshot: AppSnapshot): Double {
        val categoryIds = snapshot.categories.filter { it.isFood }.map { it.id }.toSet()
        return snapshot.expenses
            .filter { it.date == date && it.categoryId in categoryIds }
            .sumOf { it.amount }
            .coerceAtLeast(0.0)
    }

    fun foodSpentForWeek(date: LocalDate, snapshot: AppSnapshot): Double {
        return currentWeekDates(date).sumOf { foodSpentOn(it, snapshot) }
    }

    fun projectedMonthEnd(snapshot: AppSnapshot): Double {
        val today = snapshot.today
        val spent = snapshot.foodSpentThisMonth
        val daysPassed = today.dayOfMonth
        val dailyRate = if (daysPassed == 0) 0.0 else spent / daysPassed
        return snapshot.settings.monthlyFoodBudget - (dailyRate * daysInMonth(today))
    }

    fun bestMealForBudget(snapshot: AppSnapshot, mealType: MealType? = null): MealTemplate? {
        val candidates = snapshot.templates
            .filter { mealType == null || it.mealType == mealType }
            .sortedBy { it.cost }
        return candidates.firstOrNull { it.cost <= snapshot.todayFoodRemaining + 0.01 }
            ?: candidates.minByOrNull { it.cost }
    }

    fun extraPurchasesNeeded(
        snapshot: AppSnapshot,
        template: MealTemplate,
        availablePortions: Map<String, Double>? = null,
    ): Double {
        val components = template.components.ifEmpty {
            com.budgetmeals.app.data.MealDataCodec.legacyComponents(template.notes)
        }
        if (components.isEmpty()) {
            return template.cost
        }
        var extraCash = 0.0
        val remainingPortions = snapshot.catalogAvailablePortions.toMutableMap().apply {
            availablePortions?.let { putAll(it) }
        }
        for (component in components) {
            if (!component.useStock) {
                if (component.costPerUnit <= 0.0) return Double.POSITIVE_INFINITY
                extraCash += component.estimatedCost
                continue
            }
            val catalog = if (component.catalogId != null) {
                snapshot.foodCatalogById[component.catalogId]
            } else {
                snapshot.foodCatalogByName[component.name.lowercase(Locale.US)]
            }
            if (catalog == null) {
                if (component.costPerUnit <= 0.0) return Double.POSITIVE_INFINITY
                extraCash += component.estimatedCost
                continue
            }
            if (!catalog.hasUsableConversion) return Double.POSITIVE_INFINITY

            val basePerUnit = Units.basePerUnit(component.unit, catalog)
                ?.takeIf { it > 0.0 && it.isFinite() }
                ?: return Double.POSITIVE_INFINITY
            val basePerPortion = catalog.baseUnitsPerPortion
                .takeIf { it > 0.0 && it.isFinite() }
                ?: return Double.POSITIVE_INFINITY
            val requiredPortions = component.quantity.coerceAtLeast(0.0) * basePerUnit / basePerPortion
            if (!requiredPortions.isFinite()) return Double.POSITIVE_INFINITY
            val available = remainingPortions.getOrDefault(catalog.id, 0.0).coerceAtLeast(0.0)
            val missingPortions = (requiredPortions - available).coerceAtLeast(0.0)
            remainingPortions[catalog.id] = (available - requiredPortions).coerceAtLeast(0.0)
            if (missingPortions > 0.0) {
                if (!catalog.priceKnown || !catalog.hasKnownPrice) return Double.POSITIVE_INFINITY
                val unitCost = componentCostPerPortion(component, catalog, basePerUnit)
                    .takeIf { it > 0.0 }
                    ?: catalogCostPerPortion(snapshot, catalog)
                if (unitCost <= 0.0 || !unitCost.isFinite()) return Double.POSITIVE_INFINITY
                extraCash += missingPortions * unitCost
            }
        }
        return extraCash
    }

    fun inStockCompleteness(snapshot: AppSnapshot, template: MealTemplate): Double {
        val components = template.components.ifEmpty {
            com.budgetmeals.app.data.MealDataCodec.legacyComponents(template.notes)
        }
        if (components.isEmpty()) return 0.0
        val stockComponents = components.filter { it.useStock }
        if (stockComponents.isEmpty()) return 1.0
        val remainingPortions = snapshot.catalogAvailablePortions.toMutableMap()
        val covered = stockComponents.count { component ->
            val catalog = if (component.catalogId != null) {
                snapshot.foodCatalogById[component.catalogId]
            } else {
                snapshot.foodCatalogByName[component.name.lowercase(Locale.US)]
            }
            if (catalog == null) return@count false
            if (!catalog.hasUsableConversion) return@count false
            val basePerUnit = Units.basePerUnit(component.unit, catalog)
                ?.takeIf { it > 0.0 && it.isFinite() }
                ?: return@count false
            val basePerPortion = catalog.baseUnitsPerPortion
                .takeIf { it > 0.0 && it.isFinite() }
                ?: return@count false
            val requiredPortions = component.quantity.coerceAtLeast(0.0) * basePerUnit / basePerPortion
            if (!requiredPortions.isFinite()) return@count false
            val available = remainingPortions.getOrDefault(catalog.id, 0.0).coerceAtLeast(0.0)
            remainingPortions[catalog.id] = (available - requiredPortions).coerceAtLeast(0.0)
            available + 0.000001 >= requiredPortions
        }
        return covered.toDouble() / stockComponents.size.toDouble()
    }

    private fun componentCostPerPortion(
        component: MealComponent,
        catalog: FoodCatalogItem,
        basePerComponentUnit: Double,
    ): Double {
        val basePerPortion = catalog.baseUnitsPerPortion
        if (component.costPerUnit <= 0.0 || basePerComponentUnit <= 0.0 || basePerPortion <= 0.0) return 0.0
        return component.costPerUnit * basePerPortion / basePerComponentUnit
    }

    fun suggestedMeals(snapshot: AppSnapshot): List<MealTemplate> {
        val available = snapshot.todayFoodRemaining
        return snapshot.templates
            .distinctBy { it.name.lowercase(Locale.US) }
            .map { template ->
                val extraNeeded = extraPurchasesNeeded(snapshot, template)
                val inStock = inStockCompleteness(snapshot, template)
                Triple(template, extraNeeded, inStock)
            }
            .filter { (_, extraNeeded, _) -> extraNeeded <= available + 0.01 }
            .filter { (template, extraNeeded, _) ->
                template.mealType != MealType.SNACK || snapshot.sparesBalance >= extraNeeded
            }
            .sortedWith(
                compareByDescending<Triple<MealTemplate, Double, Double>> { it.third }
                    .thenBy { it.second }
                    .thenBy { it.first.cost }
                    .thenBy { it.first.mealType.ordinal }
            )
            .map { it.first }
            .take(3)
    }

    fun hasUnknownPrice(snapshot: AppSnapshot, component: com.budgetmeals.app.data.MealComponent): Boolean {
        if (component.costPerUnit <= 0.0) return true
        val catalog = if (component.catalogId != null) {
            snapshot.foodCatalogById[component.catalogId]
        } else {
            snapshot.foodCatalogByName[component.name.lowercase(Locale.US)]
        }
        return catalog != null && (!catalog.priceKnown || !catalog.hasKnownPrice)
    }

    fun isMealCostIncomplete(snapshot: AppSnapshot, template: MealTemplate): Boolean {
        val components = template.components.ifEmpty {
            com.budgetmeals.app.data.MealDataCodec.legacyComponents(template.notes)
        }
        if (components.isEmpty()) {
            return template.cost <= 0.0
        }
        return components.any { hasUnknownPrice(snapshot, it) }
    }

    /**
     * Unit labels that name the same unit (case/plurals only). This is an identity check
     * for display and grouping — every quantity must still be converted through [com.budgetmeals.app.data.Units].
     */
    fun areStockUnitsMatching(unit1: String, unit2: String): Boolean {
        return com.budgetmeals.app.data.Units.sameUnit(unit1, unit2)
    }

    /**
     * Cost of one portion from the batches this food can actually be eaten from, using FIFO
     * order (earliest purchase / expiry date first, matching BudgetDao consumption order).
     */
    fun catalogCostPerPortion(snapshot: AppSnapshot, catalog: com.budgetmeals.app.data.FoodCatalogItem): Double {
        if (!catalog.hasUsableConversion) return catalog.defaultCostPerPortion
        val perPortion = catalog.baseUnitsPerPortion
        if (perPortion <= 0.0) return catalog.defaultCostPerPortion
        var remaining = perPortion
        var totalCost = 0.0
        val lots = snapshot.usableLots(catalog)
            .sortedWith(compareBy<StockItem>({ it.expiryDate ?: it.purchaseDate }, { it.purchaseDate }))
        for (lot in lots) {
            val take = minOf(lot.remainingQuantity, remaining)
            if (take <= 0.0) continue
            totalCost += take * lot.costPerUnit
            remaining -= take
            if (remaining <= 0.000001) break
        }
        if (remaining > 0.000001) {
            val unitCost = catalog.defaultCostPerPortion / perPortion
            if (unitCost > 0.0) totalCost += remaining * unitCost
        }
        // totalCost is already the cost of a single portion (base units x cost per base unit).
        return totalCost
    }

    fun catalogAvailablePortions(snapshot: AppSnapshot, catalog: com.budgetmeals.app.data.FoodCatalogItem): Double {
        return snapshot.availabilityFor(catalog).portions
    }

    fun plannedMealsForDate(snapshot: AppSnapshot, date: LocalDate): Map<MealType, MealTemplate> {
        val saved = snapshot.mealPlan(date)
        if (saved.isNotEmpty()) {
            return saved.filter { !it.isCleared }.associate { plan ->
                plan.mealType to MealTemplate(
                    id = plan.templateId ?: "plan-${plan.date}-${plan.mealType.name}",
                    name = plan.name,
                    mealType = plan.mealType,
                    cost = plan.cost,
                    isRecurring = false,
                    notes = plan.foods,
                    isCustom = true,
                    components = plan.components.ifEmpty { com.budgetmeals.app.data.MealDataCodec.legacyComponents(plan.foods) },
                )
            }
        }
        return generatedMealsForDate(snapshot, date)
    }

    fun generatedMealsForDate(snapshot: AppSnapshot, date: LocalDate): Map<MealType, MealTemplate> {
        val week = buildBalancedWeek(snapshot, currentWeekStart(date))
        return week.firstOrNull { it.date == date }?.meals.orEmpty()
    }

    fun dayMealSummary(snapshot: AppSnapshot, date: LocalDate = LocalDate.now()): DayMealSummary {
        val persistedPlan = snapshot.mealPlan(date)
        val plans = if (persistedPlan.isNotEmpty()) {
            persistedPlan.filter { !it.isCleared }
        } else {
            plannedMealsForDate(snapshot, date).map { (mealType, template) ->
                com.budgetmeals.app.data.DailyMealPlan(
                    id = "generated-$date-$mealType",
                    date = date,
                    mealType = mealType,
                    templateId = template.id,
                    name = template.name,
                    cost = template.cost,
                    foods = template.notes,
                    components = template.components.ifEmpty { com.budgetmeals.app.data.MealDataCodec.legacyComponents(template.notes) },
                )
            }
        }
        val logs = snapshot.mealLogs
            .filter { it.date == date }
            .groupBy { it.mealType }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.actualTime } }
        var plannedCost = 0.0
        var consumedCost = 0.0
        var leftoverCost = 0.0
        var skippedCost = 0.0
        var pendingCost = 0.0
        var consumedMeals = 0
        var leftoverMeals = 0
        var skippedMeals = 0
        var pendingMeals = 0

        plans.forEach { plan ->
            val log = logs[plan.mealType]
            val mealCost = log?.cost ?: plan.cost
            plannedCost += mealCost
            when {
                log == null -> {
                    pendingCost += mealCost
                    pendingMeals++
                }
                log.isConsumed -> {
                    consumedCost += log.consumedCost
                    leftoverCost += log.leftoverCost
                    consumedMeals++
                    if (log.leftoverCost > 0.001) leftoverMeals++
                }
                log.isSkipped -> {
                    skippedCost += mealCost
                    skippedMeals++
                }
                else -> {
                    pendingCost += mealCost
                    pendingMeals++
                }
            }
        }

        return DayMealSummary(
            plannedCost = plannedCost,
            consumedCost = consumedCost,
            leftoverCost = leftoverCost,
            skippedCost = skippedCost,
            pendingCost = pendingCost,
            consumedMeals = consumedMeals,
            leftoverMeals = leftoverMeals,
            skippedMeals = skippedMeals,
            pendingMeals = pendingMeals,
            isClosed = snapshot.dayClosure(date) != null,
        )
    }

    fun sparesStreak(snapshot: AppSnapshot, today: LocalDate = LocalDate.now()): Int {
        val dates = snapshot.spares
            .filter { it.amount > 0 && it.reason.contains("food budget", ignoreCase = true) }
            .map { it.date }
            .toSet()
        var streak = 0
        var cursor = today
        while (dates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun categorySpent(snapshot: AppSnapshot, categoryId: String, month: LocalDate = LocalDate.now()): Double {
        if (month.year == snapshot.today.year && month.month == snapshot.today.month) {
            return snapshot.currentMonthExpensesByCategoryId[categoryId] ?: 0.0
        }
        return snapshot.expenses
            .filter { it.categoryId == categoryId && it.date.year == month.year && it.date.month == month.month }
            .sumOf { it.amount }
            .coerceAtLeast(0.0)
    }

    fun foodWeeklyProgress(snapshot: AppSnapshot): Double {
        val target = snapshot.settings.weeklyFoodLimit
        return percent(foodSpentForWeek(snapshot.today, snapshot), target)
    }

    fun lowStockMessage(items: List<StockItem>): String? {
        val first = items.firstOrNull() ?: return null
        return when {
            first.daysRemaining < 1.0 -> "${first.name} is nearly finished. Add it before your next shop."
            else -> "${first.name} has about ${first.daysRemaining.toInt()} days left. Add it to the list?"
        }
    }

    fun formatDate(date: LocalDate): String = shortDateFormatter.format(date)

    fun formatWeekday(date: LocalDate): String = weekdayFormatter.format(date)

    fun titleCase(value: String): String = value.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.US) }
    }

    fun computeRecurringExpensesToGenerate(
        existingExpenses: List<Expense>,
        today: LocalDate = LocalDate.now(),
    ): List<Expense> {
        val currentYearMonth = java.time.YearMonth.from(today)
        val schedules = existingExpenses.filter { it.isRecurring || it.recurringScheduleId != null }
            .groupBy { expense ->
                expense.recurringScheduleId ?: "legacy:${expense.categoryId}:${expense.description.trim().lowercase(Locale.US)}"
            }
        return schedules.mapNotNull { (scheduleId, rows) ->
            val latest = rows.maxWithOrNull(compareBy<Expense>({ it.date }, { it.id })) ?: return@mapNotNull null
            if (!latest.isRecurring) return@mapNotNull null
            if (rows.any { java.time.YearMonth.from(it.date) == currentYearMonth }) return@mapNotNull null
            val targetDay = minOf(latest.date.dayOfMonth, currentYearMonth.lengthOfMonth())
            val dueDate = currentYearMonth.atDay(targetDay)
            if (today.isBefore(dueDate)) return@mapNotNull null
            latest.copy(
                id = "$scheduleId-${currentYearMonth.year}-${currentYearMonth.monthValue.toString().padStart(2, '0')}",
                date = dueDate,
                recurringScheduleId = scheduleId,
                recurringFrequency = latest.recurringFrequency ?: "MONTHLY",
                isCorrection = false,
                isBudgetTransfer = false,
            )
        }
    }
}

data class DayMealSummary(
    val plannedCost: Double = 0.0,
    val consumedCost: Double = 0.0,
    val leftoverCost: Double = 0.0,
    val skippedCost: Double = 0.0,
    val pendingCost: Double = 0.0,
    val consumedMeals: Int = 0,
    val leftoverMeals: Int = 0,
    val skippedMeals: Int = 0,
    val pendingMeals: Int = 0,
    val isClosed: Boolean = false,
)

data class WeeklyPlanDay(
    val date: LocalDate,
    val meals: Map<MealType, MealTemplate>,
)

fun buildMealPlanWindow(snapshot: AppSnapshot, start: LocalDate): List<WeeklyPlanDay> {
    val dates = BudgetMath.mealPlanDates(start)
    val weekStarts = dates.map { BudgetMath.currentWeekStart(it) }.distinct()
    val balancedWeeks = weekStarts.associateWith { weekStart ->
        buildBalancedWeek(snapshot, weekStart).associateBy { it.date }
    }
    return dates.map { date ->
        val saved = snapshot.mealPlan(date)
        val meals = if (saved.isNotEmpty()) {
            saved.filter { !it.isCleared }.associate { plan ->
                plan.mealType to MealTemplate(
                    id = plan.templateId ?: "plan-${plan.date}-${plan.mealType.name}",
                    name = plan.name,
                    mealType = plan.mealType,
                    cost = plan.cost,
                    isRecurring = false,
                    notes = plan.foods,
                    isCustom = true,
                    components = plan.components.ifEmpty { com.budgetmeals.app.data.MealDataCodec.legacyComponents(plan.foods) },
                )
            }
        } else {
            val weekStart = BudgetMath.currentWeekStart(date)
            balancedWeeks[weekStart]?.get(date)?.meals.orEmpty()
        }
        WeeklyPlanDay(
            date = date,
            meals = meals,
        )
    }
}

fun buildBalancedWeek(snapshot: AppSnapshot, start: LocalDate = BudgetMath.currentWeekStart()): List<WeeklyPlanDay> {
    val byType = MealType.entries.associateWith { type ->
        val list = snapshot.templates.filter { it.mealType == type }
        if (list.any { it.isRecurring }) list.filter { it.isRecurring } else list
    }
    val usageCount = mutableMapOf<String, Int>()
    val resultDays = mutableListOf<WeeklyPlanDay>()
    val weeklyLimit = snapshot.settings.weeklyFoodLimit

    for (offset in 0..6) {
        val date = start.plusDays(offset.toLong())
        val choices = mutableMapOf<MealType, MealTemplate>()

        for (type in MealType.entries) {
            val candidates = byType[type].orEmpty().filter { template ->
                template.dayOfWeek == null || template.dayOfWeek == date.dayOfWeek
            }
            if (candidates.isEmpty()) continue

            // Fixed day of week match takes highest priority
            val pinned = candidates.firstOrNull { it.dayOfWeek == date.dayOfWeek }
            if (pinned != null) {
                choices[type] = pinned
                usageCount[pinned.id] = (usageCount[pinned.id] ?: 0) + 1
                continue
            }

            // Special case: Koshary day for Lunch
            if (type == MealType.LUNCH && date.dayOfWeek == snapshot.settings.kosharyDay) {
                val koshary = candidates.firstOrNull { it.name.contains("kosh", ignoreCase = true) }
                if (koshary != null) {
                    choices[type] = koshary
                    usageCount[koshary.id] = (usageCount[koshary.id] ?: 0) + 1
                    continue
                }
            }

            // Snack check: spares balance
            val validCandidates = if (type == MealType.SNACK) {
                candidates.filter {
                    it.cost <= snapshot.sparesBalance || BudgetMath.extraPurchasesNeeded(snapshot, it) <= snapshot.sparesBalance
                }
            } else {
                candidates
            }
            if (validCandidates.isEmpty()) continue

            // Select with variety across days (penalizing already-used templates)
            // and prioritizing ingredients in stock / lowest extra purchases needed
            val selected = validCandidates.minWithOrNull(
                compareBy<MealTemplate> { usageCount[it.id] ?: 0 }
                    .thenByDescending { BudgetMath.inStockCompleteness(snapshot, it) }
                    .thenBy { BudgetMath.extraPurchasesNeeded(snapshot, it) }
                    .thenBy { it.cost }
            ) ?: validCandidates.first()

            choices[type] = selected
            usageCount[selected.id] = (usageCount[selected.id] ?: 0) + 1
        }
        resultDays.add(WeeklyPlanDay(date, choices))
    }

    // Check whether complete plan fits weekly budget (settings.weeklyFoodLimit)
    if (weeklyLimit > 0.0) {
        var currentTotalExtra = resultDays.sumOf { day ->
            day.meals.values.sumOf { BudgetMath.extraPurchasesNeeded(snapshot, it) }
        }
        if (currentTotalExtra > weeklyLimit) {
            for (dayIdx in resultDays.indices) {
                if (currentTotalExtra <= weeklyLimit) break
                val day = resultDays[dayIdx]
                val updatedMeals = day.meals.toMutableMap()
                for ((type, currentMeal) in day.meals) {
                    if (currentTotalExtra <= weeklyLimit) break
                    if (currentMeal.dayOfWeek == day.date.dayOfWeek) continue
                    val alternatives = byType[type].orEmpty().filter {
                        (it.dayOfWeek == null || it.dayOfWeek == day.date.dayOfWeek) && it.id != currentMeal.id
                    }
                    val currentExtra = BudgetMath.extraPurchasesNeeded(snapshot, currentMeal)
                    val cheaper = alternatives
                        .map { it to BudgetMath.extraPurchasesNeeded(snapshot, it) }
                        .filter { it.second < currentExtra }
                        .minByOrNull { it.second }
                    if (cheaper != null) {
                        currentTotalExtra -= (currentExtra - cheaper.second)
                        updatedMeals[type] = cheaper.first
                    }
                }
                resultDays[dayIdx] = WeeklyPlanDay(day.date, updatedMeals)
            }
        }
    }

    return resultDays
}
