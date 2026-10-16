package com.budgetmeals.app.state

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.BudgetSettings
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealMarker
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.data.Units
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object BudgetMath {
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
    const val CURRENCY_EPSILON = 0.01
    const val QUANTITY_EPSILON = 0.0001

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
        return if (rounded % 1000 == 0 && rounded >= 1000) {
            "${rounded / 1000}k"
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
            .filter { it.date == date && it.categoryId in categoryIds && !it.isBudgetTransfer }
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
        val remainingPortions = snapshot.catalogAvailablePortions.toMutableMap().apply {
            availablePortions?.let { putAll(it) }
        }
        return extraPurchasesFromStock(snapshot, template, remainingPortions)
    }

    internal fun extraPurchasesForPlan(snapshot: AppSnapshot, days: List<WeeklyPlanDay>): Double {
        val remainingPortions = snapshot.catalogAvailablePortions.toMutableMap()
        return days.sumOf { day ->
            day.meals.values.sumOf { extraPurchasesFromStock(snapshot, it, remainingPortions) }
        }
    }

    private fun extraPurchasesFromStock(
        snapshot: AppSnapshot,
        template: MealTemplate,
        remainingPortions: MutableMap<String, Double>,
    ): Double {
        val components = template.components.ifEmpty {
            com.budgetmeals.app.data.MealDataCodec.legacyComponents(template.notes)
        }
        if (components.isEmpty()) {
            return template.cost
        }
        var extraCash = 0.0
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
        if (stockComponents.isEmpty()) return 0.0
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

    internal fun componentCostPerPortion(
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
            .mapValues { (_, entries) -> entries.maxBy { it.actualTime } }
        val plansByType = plans.associateBy { it.mealType }
        var plannedCost = 0.0
        var consumedCost = 0.0
        var leftoverCost = 0.0
        var skippedCost = 0.0
        var pendingCost = 0.0
        var consumedMeals = 0
        var leftoverMeals = 0
        var skippedMeals = 0
        var pendingMeals = 0

        (plansByType.keys + logs.keys).forEach { mealType ->
            val log = logs[mealType]
            val mealCost = log?.cost ?: plansByType.getValue(mealType).cost
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
        var cursor = if (dates.contains(today)) today else today.minusDays(1)
        while (dates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun categorySpent(snapshot: AppSnapshot, categoryId: String, month: LocalDate = LocalDate.now()): Double {
        if (month.year == snapshot.today.year && month.month == snapshot.today.month) {
            return (snapshot.currentMonthExpensesByCategoryId[categoryId] ?: 0.0).coerceAtLeast(0.0)
        }
        return snapshot.expenses
            .filter { it.categoryId == categoryId && it.date.year == month.year && it.date.month == month.month && !it.isBudgetTransfer }
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
            val anchor = rows.minWithOrNull(compareBy<Expense>({ it.date }, { it.id })) ?: latest
            val targetDay = minOf(anchor.date.dayOfMonth, currentYearMonth.lengthOfMonth())
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
        var currentTotalExtra = BudgetMath.extraPurchasesForPlan(snapshot, resultDays)
        if (currentTotalExtra > weeklyLimit) {
            for (dayIdx in resultDays.indices) {
                if (currentTotalExtra <= weeklyLimit) break
                val day = resultDays[dayIdx]
                for ((type, currentMeal) in day.meals) {
                    if (currentTotalExtra <= weeklyLimit) break
                    if (currentMeal.dayOfWeek == day.date.dayOfWeek) continue
                    val alternatives = byType[type].orEmpty().filter {
                        (it.dayOfWeek == null || it.dayOfWeek == day.date.dayOfWeek) && it.id != currentMeal.id
                    }
                    val cheaper = alternatives
                        .map { alternative ->
                            val replacement = resultDays.toMutableList()
                            replacement[dayIdx] = resultDays[dayIdx].copy(
                                meals = resultDays[dayIdx].meals + (type to alternative),
                            )
                            alternative to BudgetMath.extraPurchasesForPlan(snapshot, replacement)
                        }
                        .filter { it.second < currentTotalExtra }
                        .minByOrNull { it.second }
                    if (cheaper != null) {
                        currentTotalExtra = cheaper.second
                        resultDays[dayIdx] = resultDays[dayIdx].copy(
                            meals = resultDays[dayIdx].meals + (type to cheaper.first),
                        )
                    }
                }
            }
        }
    }

    return resultDays
}

data class MissingIngredient(
    val name: String,
    val quantity: Double,
    val unit: String,
    val estimatedCost: Double,
    val priceKnown: Boolean,
    val catalogId: String? = null,
)

enum class RecommendationKind(val label: String) {
    PRACTICAL("Practical choice"),
    DIFFERENT("Something different"),
    NICER("A nicer meal"),
}

data class MealIngredientAnalysis(
    val availableIngredients: List<String>,
    val missingIngredients: List<MissingIngredient>,
    val additionalCost: Double,
    val hasUnknownPrice: Boolean,
    val inStockRatio: Double,
)

data class MealRecommendation(
    val template: MealTemplate,
    val kind: RecommendationKind,
    val availableIngredients: List<String>,
    val missingIngredients: List<MissingIngredient>,
    val estimatedAdditionalCost: Double,
    val hasUnknownPrice: Boolean,
    val inStockPortion: Double,
)

fun analyzeMealIngredients(snapshot: AppSnapshot, template: MealTemplate): MealIngredientAnalysis {
    val components = template.components.ifEmpty {
        com.budgetmeals.app.data.MealDataCodec.legacyComponents(template.notes)
    }
    val availableList = mutableListOf<String>()
    val missingList = mutableListOf<MissingIngredient>()
    var additionalCost = 0.0
    var unknownPrice = false
    val remainingPortions = snapshot.catalogAvailablePortions.toMutableMap()

    if (components.isEmpty()) {
        if (template.cost > 0.0) {
            missingList.add(MissingIngredient(template.name, 1.0, "meal", template.cost, true))
            additionalCost = template.cost
        } else {
            missingList.add(MissingIngredient(template.name, 1.0, "meal", 0.0, false))
            unknownPrice = true
        }
        return MealIngredientAnalysis(availableList, missingList, additionalCost, unknownPrice, 0.0)
    }

    var coveredComponents = 0

    for (component in components) {
        val catalog = if (component.catalogId != null) {
            snapshot.foodCatalogById[component.catalogId]
        } else {
            snapshot.foodCatalogByName[component.name.lowercase(Locale.US)]
        }
        if (!component.useStock || catalog == null || !catalog.hasUsableConversion) {
            val cost = if (component.costPerUnit > 0.0) component.estimatedCost else 0.0
            val priceKnown = component.costPerUnit > 0.0
            if (!priceKnown) unknownPrice = true
            missingList.add(MissingIngredient(component.name, component.quantity, component.unit, cost, priceKnown, catalog?.id))
            additionalCost += cost
            continue
        }

        val basePerUnit = Units.basePerUnit(component.unit, catalog) ?: 0.0
        val basePerPortion = catalog.baseUnitsPerPortion
        if (basePerUnit <= 0.0 || basePerPortion <= 0.0) {
            val cost = if (component.costPerUnit > 0.0) component.estimatedCost else 0.0
            val priceKnown = component.costPerUnit > 0.0
            if (!priceKnown) unknownPrice = true
            missingList.add(MissingIngredient(component.name, component.quantity, component.unit, cost, priceKnown, catalog.id))
            additionalCost += cost
            continue
        }

        val requiredPortions = component.quantity * basePerUnit / basePerPortion
        val available = remainingPortions.getOrDefault(catalog.id, 0.0)
        if (available >= requiredPortions - 0.0001) {
            remainingPortions[catalog.id] = (available - requiredPortions).coerceAtLeast(0.0)
            availableList.add(component.name)
            coveredComponents++
        } else {
            val missingPortions = (requiredPortions - available).coerceAtLeast(0.0)
            remainingPortions[catalog.id] = 0.0
            if (available > 0.0001) {
                availableList.add("${component.name} (partial)")
            }
            val missingQty = (missingPortions * basePerPortion / basePerUnit).coerceAtLeast(0.0)
            val unitCost = BudgetMath.componentCostPerPortion(component, catalog, basePerUnit).takeIf { it > 0.0 }
                ?: BudgetMath.catalogCostPerPortion(snapshot, catalog)
            val priceKnown = (catalog.priceKnown && catalog.hasKnownPrice && unitCost > 0.0) || component.costPerUnit > 0.0
            val missingCost = if (priceKnown) missingPortions * unitCost else 0.0
            if (!priceKnown) unknownPrice = true
            missingList.add(MissingIngredient(component.name, missingQty, component.unit, missingCost, priceKnown, catalog.id))
            additionalCost += missingCost
        }
    }
    val inStockRatio = if (components.isNotEmpty()) coveredComponents.toDouble() / components.size.toDouble() else 0.0
    return MealIngredientAnalysis(availableList, missingList, additionalCost, unknownPrice, inStockRatio)
}

data class ActiveMealStatus(
    val activeMealType: MealType?,
    val mealDate: LocalDate,
    val nextMealType: MealType?,
    val nextMealStartTime: LocalTime?,
)

fun activeMealStatus(
    settings: BudgetSettings,
    now: LocalDateTime = LocalDateTime.now(),
): ActiveMealStatus {
    val time = now.toLocalTime()
    val today = now.toLocalDate()
    val windows = settings.mealWindows()

    for (window in windows) {
        if (window.contains(time)) {
            val date = if (window.mealType == MealType.DINNER && window.isOvernight && time < window.end) {
                today.minusDays(1)
            } else {
                today
            }
            return ActiveMealStatus(
                activeMealType = window.mealType,
                mealDate = date,
                nextMealType = null,
                nextMealStartTime = null,
            )
        }
    }

    val timeMinute = time.hour * 60 + time.minute
    val nextWindow = windows.minByOrNull { window ->
        val startMinute = window.start.hour * 60 + window.start.minute
        (startMinute - timeMinute + 1440) % 1440
    }

    return ActiveMealStatus(
        activeMealType = null,
        mealDate = today,
        nextMealType = nextWindow?.mealType,
        nextMealStartTime = nextWindow?.start,
    )
}

fun helpMeChooseRecommendations(
    snapshot: AppSnapshot,
    mealType: MealType? = null,
    showNiceMeals: Boolean = false,
): List<MealRecommendation> {
    var templates = snapshot.templates
    if (templates.isEmpty()) return emptyList()

    if (mealType != null) {
        templates = templates.filter { it.mealType == mealType }
        if (templates.isEmpty()) return emptyList()
    }

    templates = if (showNiceMeals) {
        templates.filter { it.marker == MealMarker.NICE }
    } else {
        templates.filter { it.marker != MealMarker.NICE }
    }
    if (templates.isEmpty()) return emptyList()

    val today = snapshot.today
    val sevenDaysAgo = today.minusDays(7)

    val eatenRecentMealNames = snapshot.mealLogs
        .filter { it.date >= sevenDaysAgo && it.date <= today && (it.status == MealStatus.EATEN || it.status == MealStatus.PARTIAL) }
        .map { it.name.trim().lowercase(Locale.US) }
        .toSet()

    val eatenRecentExpenseNames = snapshot.expenses
        .filter {
            it.date >= sevenDaysAgo && it.date <= today && !it.isBudgetTransfer &&
                (it.foodClassification in listOf("ready_to_eat", "restaurant", "meal", "street_food", "snack") ||
                    it.description.contains("snack", ignoreCase = true) ||
                    it.description.contains("takeaway", ignoreCase = true))
        }
        .map { it.description.trim().lowercase(Locale.US) }
        .toSet()

    val repetitionNames = eatenRecentMealNames + eatenRecentExpenseNames

    val exclusions = snapshot.settings.foodExclusions
        .split(',', ';', '\n')
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() }

    val preferences = snapshot.settings.foodPreferences
        .split(',', ';', '\n')
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() }

    fun matchesAny(text: String, keywords: List<String>): Boolean {
        val lower = text.lowercase(Locale.US)
        return keywords.any { lower.contains(it) }
    }

    val nonExcluded = templates.filter { t ->
        if (exclusions.isEmpty()) true
        else {
            val combined = t.name + " " + t.notes + " " + t.components.joinToString(" ") { it.name }
            !matchesAny(combined, exclusions)
        }
    }
    if (nonExcluded.isEmpty()) return emptyList()

    data class Candidate(
        val template: MealTemplate,
        val analysis: MealIngredientAnalysis,
        val isRecentlyEaten: Boolean,
        val matchesPreference: Boolean,
    )

    val candidates = nonExcluded.map { template ->
        val analysis = analyzeMealIngredients(snapshot, template)
        val templateLower = template.name.trim().lowercase(Locale.US)
        val isRecent = repetitionNames.any { it.contains(templateLower) || templateLower.contains(it) }
        val combined = template.name + " " + template.notes + " " + template.components.joinToString(" ") { it.name }
        val matchesPref = preferences.isNotEmpty() && matchesAny(combined, preferences)
        Candidate(template, analysis, isRecent, matchesPref)
    }

    val practicalCandidate = candidates.sortedWith(
        compareBy<Candidate> { it.isRecentlyEaten }
            .thenBy { it.analysis.hasUnknownPrice }
            .thenByDescending { it.analysis.inStockRatio }
            .thenBy { it.analysis.additionalCost }
            .thenBy { it.template.cost }
    ).firstOrNull() ?: return emptyList()

    val practicalRec = MealRecommendation(
        template = practicalCandidate.template,
        kind = RecommendationKind.PRACTICAL,
        availableIngredients = practicalCandidate.analysis.availableIngredients,
        missingIngredients = practicalCandidate.analysis.missingIngredients,
        estimatedAdditionalCost = practicalCandidate.analysis.additionalCost,
        hasUnknownPrice = practicalCandidate.analysis.hasUnknownPrice,
        inStockPortion = practicalCandidate.analysis.inStockRatio,
    )

    val remainingAfterPractical = candidates.filter { it.template.id != practicalCandidate.template.id }
    if (remainingAfterPractical.isEmpty()) return listOf(practicalRec)

    val practicalComponentNames = practicalCandidate.template.components.map { it.name.trim().lowercase(Locale.US) }.toSet()
    val differentCandidate = remainingAfterPractical.sortedWith(
        compareBy<Candidate> { it.isRecentlyEaten }
            .thenBy { it.analysis.hasUnknownPrice }
            .thenBy { candidate ->
                candidate.template.components.count { practicalComponentNames.contains(it.name.trim().lowercase(Locale.US)) }
            }
            .thenBy { it.analysis.additionalCost }
    ).firstOrNull()

    val differentRec = differentCandidate?.let {
        MealRecommendation(
            template = it.template,
            kind = RecommendationKind.DIFFERENT,
            availableIngredients = it.analysis.availableIngredients,
            missingIngredients = it.analysis.missingIngredients,
            estimatedAdditionalCost = it.analysis.additionalCost,
            hasUnknownPrice = it.analysis.hasUnknownPrice,
            inStockPortion = it.analysis.inStockRatio,
        )
    }

    val remainingAfterDifferent = remainingAfterPractical.filter { differentCandidate == null || it.template.id != differentCandidate.template.id }

    val thirdCandidate = remainingAfterDifferent
        .sortedWith(
            compareBy<Candidate> { it.isRecentlyEaten }
                .thenByDescending { it.matchesPreference }
                .thenByDescending { it.analysis.inStockRatio }
                .thenBy { it.analysis.additionalCost }
                .thenBy { it.analysis.hasUnknownPrice }
        ).firstOrNull()

    val thirdRec = thirdCandidate?.let {
        val kind = if (showNiceMeals || it.template.marker == MealMarker.NICE) {
            RecommendationKind.NICER
        } else {
            RecommendationKind.DIFFERENT
        }
        MealRecommendation(
            template = it.template,
            kind = kind,
            availableIngredients = it.analysis.availableIngredients,
            missingIngredients = it.analysis.missingIngredients,
            estimatedAdditionalCost = it.analysis.additionalCost,
            hasUnknownPrice = it.analysis.hasUnknownPrice,
            inStockPortion = it.analysis.inStockRatio,
        )
    }

    return listOfNotNull(practicalRec, differentRec, thirdRec).distinctBy { it.template.id }.take(3)
}

data class SpendingComparison(
    val currentSpent: Double,
    val previousSpent: Double,
    val difference: Double,
    val comparisonLabel: String,
) {
    val label: String get() = comparisonLabel
}

fun weekFoodSpendingComparison(snapshot: AppSnapshot, weekStart: LocalDate): SpendingComparison {
    val today = snapshot.today
    val weekEnd = weekStart.plusDays(6)
    val elapsedDays = when {
        today in weekStart..weekEnd -> java.time.temporal.ChronoUnit.DAYS.between(weekStart, today).toInt() + 1
        weekEnd < today -> 7
        else -> 0
    }
    val currentSpent = (0 until elapsedDays).sumOf { BudgetMath.foodSpentOn(weekStart.plusDays(it.toLong()), snapshot) }
    val prevWeekStart = weekStart.minusWeeks(1)
    val prevSpent = (0 until elapsedDays).sumOf { BudgetMath.foodSpentOn(prevWeekStart.plusDays(it.toLong()), snapshot) }
    val diff = prevSpent - currentSpent
    val label = when {
        elapsedDays == 0 -> "No days elapsed"
        prevSpent <= 0.0 && currentSpent > 0.0 -> "First recorded week"
        prevSpent <= 0.0 && currentSpent <= 0.0 -> "No food spending recorded"
        diff > 0.001 -> "${BudgetMath.money(diff)} less than the same days last week"
        diff < -0.001 -> "${BudgetMath.money(-diff)} more than the same days last week"
        else -> "Same as the same days last week"
    }
    return SpendingComparison(currentSpent, prevSpent, diff, label)
}

fun monthFoodSpendingComparison(snapshot: AppSnapshot, monthStart: LocalDate): SpendingComparison {
    val today = snapshot.today
    val monthLength = monthStart.lengthOfMonth()
    val isCurrentMonth = today.year == monthStart.year && today.month == monthStart.month
    val elapsedDays = when {
        isCurrentMonth -> today.dayOfMonth
        monthStart < today.withDayOfMonth(1) -> monthLength
        else -> 0
    }
    val currentSpent = (1..elapsedDays).sumOf { BudgetMath.foodSpentOn(monthStart.withDayOfMonth(it), snapshot) }
    val prevMonthStart = monthStart.minusMonths(1)
    val prevMonthLength = prevMonthStart.lengthOfMonth()
    val prevElapsedDays = minOf(elapsedDays, prevMonthLength)
    val prevSpent = (1..prevElapsedDays).sumOf { BudgetMath.foodSpentOn(prevMonthStart.withDayOfMonth(it), snapshot) }
    val diff = prevSpent - currentSpent
    val label = when {
        elapsedDays == 0 -> "No days elapsed"
        prevSpent <= 0.0 && currentSpent > 0.0 -> "First recorded month"
        prevSpent <= 0.0 && currentSpent <= 0.0 -> "No food spending recorded"
        diff > 0.001 -> "${BudgetMath.money(diff)} less than the same days last month"
        diff < -0.001 -> "${BudgetMath.money(-diff)} more than the same days last month"
        else -> "Same as the same days last month"
    }
    return SpendingComparison(currentSpent, prevSpent, diff, label)
}

data class HabitObservation(
    val title: String,
    val message: String,
    val destination: String,
)

fun habitObservationForHome(snapshot: AppSnapshot): HabitObservation? {
    if (!snapshot.settings.habitMessagesEnabled) return null
    val today = snapshot.today
    val weekStart = BudgetMath.currentWeekStart(today)
    val weekComparison = weekFoodSpendingComparison(snapshot, weekStart)
    val weeklyFoodLimit = snapshot.settings.weeklyFoodLimit

    // Priority 1: Configured budget overruns
    if (weeklyFoodLimit > 0.0 && weekComparison.currentSpent > weeklyFoodLimit) {
        return HabitObservation(
            title = "Weekly target",
            message = "Food spending this week (${BudgetMath.money(weekComparison.currentSpent)}) is ${BudgetMath.money(weekComparison.currentSpent - weeklyFoodLimit)} above your weekly target.",
            destination = "spending",
        )
    }
    if (snapshot.settings.monthlyFoodBudget > 0.0 && snapshot.foodSpentThisMonth > snapshot.settings.monthlyFoodBudget) {
        return HabitObservation(
            title = "Monthly ceiling",
            message = "Food spending this month (${BudgetMath.money(snapshot.foodSpentThisMonth)}) has reached your ${BudgetMath.money(snapshot.settings.monthlyFoodBudget)} monthly ceiling.",
            destination = "spending",
        )
    }
    if (snapshot.settings.dailyFoodBudget > 0.0 && snapshot.todayFoodSpent > snapshot.settings.dailyFoodBudget) {
        return HabitObservation(
            title = "Today's spending",
            message = "Food spending today (${BudgetMath.money(snapshot.todayFoodSpent)}) is above your daily average target of ${BudgetMath.money(snapshot.settings.dailyFoodBudget)}.",
            destination = "spending",
        )
    }

    // Priority 2: Repeated ready-made/snack spending
    val sevenDaysAgo = today.minusDays(7)
    val recentSnacks = snapshot.expenses.filter {
        it.date >= sevenDaysAgo && it.date <= today && !it.isBudgetTransfer &&
            (it.foodClassification in listOf("ready_to_eat", "ready_made", "snack", "street_food", "restaurant") ||
                it.description.contains("snack", ignoreCase = true) ||
                it.description.contains("takeaway", ignoreCase = true))
    }
    if (recentSnacks.size >= 3) {
        val totalSnacks = recentSnacks.sumOf { it.amount }
        return HabitObservation(
            title = "Ready-made food",
            message = "${recentSnacks.size} ready-made or snack purchases recorded in the last 7 days (${BudgetMath.money(totalSnacks)}).",
            destination = "spending",
        )
    }

    // Priority 3: Expired pantry items
    val expiredItems = snapshot.stock.filter { !it.isFinished && it.expiryDate != null && it.expiryDate <= today }
    if (expiredItems.isNotEmpty()) {
        val names = expiredItems.take(2).joinToString(", ") { it.name }
        val msg = if (expiredItems.size == 1) "$names has passed its expiry date." else "${expiredItems.size} pantry items have passed their expiry date ($names)."
        return HabitObservation(
            title = "Pantry notice",
            message = msg,
            destination = "stock",
        )
    }

    // Quiet encouragement if progress
    if (weekComparison.difference > 0.001 && weekComparison.previousSpent > 0.0) {
        return HabitObservation(
            title = "Weekly progress",
            message = "${BudgetMath.money(weekComparison.difference)} less spent than the same days last week.",
            destination = "spending",
        )
    }

    return null
}

