package com.budgetmeals.app.data

import android.content.Context
import com.budgetmeals.app.state.BudgetMath
import com.budgetmeals.app.state.MissingIngredient
import com.budgetmeals.app.backup.BudgetBackupArchive
import java.io.Closeable
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock

class BudgetRepository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) : Closeable {
    val database = BudgetDatabase(context.applicationContext)
    val preferences = context.applicationContext.getSharedPreferences("budget_meals", Context.MODE_PRIVATE)
    val persistenceLock: ReentrantReadWriteLock get() = sharedPersistenceLock

    init {
        BudgetBackupArchive.checkAndPerformStartupRecovery(context.applicationContext, this)
    }

    private inline fun <T> inWriteLock(action: () -> T): T {
        persistenceLock.writeLock().lock()
        try {
            return action()
        } finally {
            persistenceLock.writeLock().unlock()
        }
    }

    private var lastSnapshot: AppSnapshot? = null

    private fun <T> stable(old: List<T>?, new: List<T>): List<T> =
        if (old != null && old == new) old else new

    fun loadSnapshot(): AppSnapshot = inWriteLock {
        val today = LocalDate.now(clock)
        val settings = readSettings()
        database.autoClosePastDays(today, LocalDateTime.now(clock), settings)
        processRecurringExpenses(today)
        val raw = database.loadSnapshot(settings, today)
        val prev = lastSnapshot
        val snapshot = if (prev != null) {
            raw.copy(
                categories = stable(prev.categories, raw.categories),
                foodCatalog = stable(prev.foodCatalog, raw.foodCatalog),
                stock = stable(prev.stock, raw.stock),
                expenses = stable(prev.expenses, raw.expenses),
                templates = stable(prev.templates, raw.templates),
                mealLogs = stable(prev.mealLogs, raw.mealLogs),
                shopping = stable(prev.shopping, raw.shopping),
                spares = stable(prev.spares, raw.spares),
                dayPlans = stable(prev.dayPlans, raw.dayPlans),
                dayClosures = stable(prev.dayClosures, raw.dayClosures),
            )
        } else {
            raw
        }
        lastSnapshot = snapshot
        snapshot.foodSpentThisMonth
        snapshot.todayFoodSpent
        snapshot.lowStock
        snapshot.sparesBalance
        snapshot.foodRemainingThisMonth
        snapshot.recordedFoodSpentThisMonth
        snapshot
    }

    fun ensureDayPlan(date: LocalDate, templates: List<MealTemplate>): Int = inWriteLock {
        database.ensureDayPlan(date, templates)
    }

    fun replaceOpenDayPlan(date: LocalDate, templates: List<MealTemplate>): Boolean = inWriteLock {
        database.replaceOpenDayPlan(date, templates)
    }

    fun assignMealToDay(
        date: LocalDate,
        mealType: MealType,
        template: MealTemplate?,
        snapshot: AppSnapshot,
    ): Boolean = inWriteLock {
        val currentMeals = BudgetMath.plannedMealsForDate(snapshot, date).toMutableMap()
        if (template == null) {
            currentMeals.remove(mealType)
        } else {
            currentMeals[mealType] = template
        }
        database.saveDayPlanMeals(date, currentMeals)
    }

    fun rebuildPlanDates(dates: List<LocalDate>, snapshot: AppSnapshot): Int = inWriteLock {
        var count = 0
        dates.forEach { date ->
            val generated = BudgetMath.generatedMealsForDate(snapshot, date).values.toList()
            if (database.replaceOpenDayPlan(date, generated)) {
                count++
            }
        }
        count
    }

    fun closeDay(date: LocalDate) = inWriteLock {
        database.closeDay(date, LocalDateTime.now(clock))
    }

    fun readSettings(): BudgetSettings {
        val storedKoshary = preferences.getString(KEY_KOSHARY_DAY, null)?.let { value ->
            runCatching { DayOfWeek.valueOf(value) }.getOrNull()
        }
        val storedShopping = preferences.getString(KEY_SHOPPING_DAY, null)?.let { value ->
            runCatching { DayOfWeek.valueOf(value) }.getOrNull()
        }
        val storedTheme = preferences.getString(KEY_THEME_MODE, null)?.let { value ->
            runCatching { AppThemeMode.valueOf(value) }.getOrNull()
        }
        val bStart = preferences.getString(KEY_BREAKFAST_START, "06:00")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(6, 0)
        val bEnd = preferences.getString(KEY_BREAKFAST_END, "11:00")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(11, 0)
        val lStart = preferences.getString(KEY_LUNCH_START, "12:00")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(12, 0)
        val lEnd = preferences.getString(KEY_LUNCH_END, "17:00")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(17, 0)
        val dStart = preferences.getString(KEY_DINNER_START, "18:00")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(18, 0)
        val dEnd = preferences.getString(KEY_DINNER_END, "23:00")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(23, 0)
        return BudgetSettings(
            monthlyFoodBudget = readMonetaryDouble(KEY_MONTHLY_FOOD, 3_000.0),
            dailyFoodBudget = readMonetaryDouble(KEY_DAILY_FOOD, 100.0),
            weeklyFoodLimit = readMonetaryDouble(KEY_WEEKLY_FOOD, 750.0),
            kosharyDay = storedKoshary ?: DayOfWeek.WEDNESDAY,
            kosharyPrice = readMonetaryDouble(KEY_KOSHARY_PRICE, 60.0),
            autoSaveSpares = preferences.getBoolean(KEY_AUTO_SPARES, true),
            remindersEnabled = preferences.getBoolean(KEY_REMINDERS, true),
            shoppingDay = storedShopping ?: DayOfWeek.FRIDAY,
            lastSparesAutoSaveDate = preferences.getString(KEY_LAST_SPARES_DATE, null)?.let { value ->
                runCatching { LocalDate.parse(value) }.getOrNull()
            },
            themeMode = storedTheme ?: AppThemeMode.SYSTEM,
            foodPreferences = preferences.getString(KEY_FOOD_PREFERENCES, "").orEmpty(),
            foodExclusions = preferences.getString(KEY_FOOD_EXCLUSIONS, "").orEmpty(),
            habitMessagesEnabled = preferences.getBoolean(KEY_HABIT_MESSAGES, true),
            breakfastStart = bStart,
            breakfastEnd = bEnd,
            lunchStart = lStart,
            lunchEnd = lEnd,
            dinnerStart = dStart,
            dinnerEnd = dEnd,
        )
    }

    private fun readMonetaryDouble(key: String, default: Double): Double {
        if (!preferences.contains(key)) return default
        return try {
            val stringVal = preferences.getString(key, null)
            stringVal?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: default
        } catch (_: ClassCastException) {
            try {
                val floatVal = preferences.getFloat(key, default.toFloat())
                if (floatVal.isFinite() && floatVal >= 0f) floatVal.toDouble() else default
            } catch (_: ClassCastException) {
                default
            }
        }
    }

    fun saveSettings(settings: BudgetSettings) = inWriteLock {
        require(settings.monthlyFoodBudget.isFinite() && settings.monthlyFoodBudget >= 0.0) {
            "Monthly food budget must be finite and non-negative."
        }
        require(settings.dailyFoodBudget.isFinite() && settings.dailyFoodBudget >= 0.0) {
            "Daily food budget must be finite and non-negative."
        }
        require(settings.weeklyFoodLimit.isFinite() && settings.weeklyFoodLimit >= 0.0) {
            "Weekly food limit must be finite and non-negative."
        }
        require(settings.kosharyPrice.isFinite() && settings.kosharyPrice >= 0.0) {
            "Koshary price must be finite and non-negative."
        }
        preferences.edit()
            .putString(KEY_MONTHLY_FOOD, settings.monthlyFoodBudget.toString())
            .putString(KEY_DAILY_FOOD, settings.dailyFoodBudget.toString())
            .putString(KEY_WEEKLY_FOOD, settings.weeklyFoodLimit.toString())
            .putString(KEY_KOSHARY_DAY, settings.kosharyDay.name)
            .putString(KEY_KOSHARY_PRICE, settings.kosharyPrice.toString())
            .putBoolean(KEY_AUTO_SPARES, settings.autoSaveSpares)
            .putBoolean(KEY_REMINDERS, settings.remindersEnabled)
            .putString(KEY_SHOPPING_DAY, settings.shoppingDay.name)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_FOOD_PREFERENCES, settings.foodPreferences)
            .putString(KEY_FOOD_EXCLUSIONS, settings.foodExclusions)
            .putBoolean(KEY_HABIT_MESSAGES, settings.habitMessagesEnabled)
            .putString(KEY_BREAKFAST_START, settings.breakfastStart.toString())
            .putString(KEY_BREAKFAST_END, settings.breakfastEnd.toString())
            .putString(KEY_LUNCH_START, settings.lunchStart.toString())
            .putString(KEY_LUNCH_END, settings.lunchEnd.toString())
            .putString(KEY_DINNER_START, settings.dinnerStart.toString())
            .putString(KEY_DINNER_END, settings.dinnerEnd.toString())
            .apply()
        val foodCategory = database.findCategoryById("food")
        if (foodCategory != null && foodCategory.monthlyBudget != settings.monthlyFoodBudget) {
            database.upsertCategory(foodCategory.copy(monthlyBudget = settings.monthlyFoodBudget))
        }
        val kosharyTemplate = database.findTemplateByNameLike("kosh")
        val kosharyTemplateCost = settings.kosharyPrice + 10.0
        if (kosharyTemplate != null && kosharyTemplate.cost != kosharyTemplateCost) {
            database.upsertTemplate(kosharyTemplate.copy(cost = kosharyTemplateCost))
        }
    }

    fun restoreSettingsDirectly(settings: BudgetSettings) = inWriteLock {
        preferences.edit()
            .putString(KEY_MONTHLY_FOOD, settings.monthlyFoodBudget.toString())
            .putString(KEY_DAILY_FOOD, settings.dailyFoodBudget.toString())
            .putString(KEY_WEEKLY_FOOD, settings.weeklyFoodLimit.toString())
            .putString(KEY_KOSHARY_DAY, settings.kosharyDay.name)
            .putString(KEY_KOSHARY_PRICE, settings.kosharyPrice.toString())
            .putBoolean(KEY_AUTO_SPARES, settings.autoSaveSpares)
            .putBoolean(KEY_REMINDERS, settings.remindersEnabled)
            .putString(KEY_SHOPPING_DAY, settings.shoppingDay.name)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_FOOD_PREFERENCES, settings.foodPreferences)
            .putString(KEY_FOOD_EXCLUSIONS, settings.foodExclusions)
            .putBoolean(KEY_HABIT_MESSAGES, settings.habitMessagesEnabled)
            .putString(KEY_BREAKFAST_START, settings.breakfastStart.toString())
            .putString(KEY_BREAKFAST_END, settings.breakfastEnd.toString())
            .putString(KEY_LUNCH_START, settings.lunchStart.toString())
            .putString(KEY_LUNCH_END, settings.lunchEnd.toString())
            .putString(KEY_DINNER_START, settings.dinnerStart.toString())
            .putString(KEY_DINNER_END, settings.dinnerEnd.toString())
            .apply {
                if (settings.lastSparesAutoSaveDate != null) {
                    putString(KEY_LAST_SPARES_DATE, settings.lastSparesAutoSaveDate.toString())
                } else {
                    remove(KEY_LAST_SPARES_DATE)
                }
            }
            .apply()
    }

    fun saveFoodCatalogItem(item: FoodCatalogItem) = inWriteLock {
        database.upsertFoodCatalogItem(item)
    }

    fun importFoodCatalogJson(raw: String): Int = inWriteLock {
        val items = FoodCatalogJsonCodec.decode(raw).getOrThrow()
        database.upsertFoodCatalogItems(items)
        items.size
    }

    fun exportFoodCatalogJson(snapshot: AppSnapshot): String {
        return FoodCatalogJsonCodec.encode(snapshot.foodCatalog)
    }

    fun deleteFoodCatalogItem(id: String) = inWriteLock {
        database.deleteFoodCatalogItem(id)
    }

    fun findFoodCatalogItem(name: String): FoodCatalogItem? {
        return database.findFoodCatalogItem(name)
    }

    fun saveCategory(category: ExpenseCategory) = inWriteLock {
        database.upsertCategory(category)
    }

    fun deleteCategory(id: String) = inWriteLock {
        if (id == "food" || id == "spares") return@inWriteLock
        if (database.categoryHasExpenses(id)) return@inWriteLock
        database.deleteCategory(id)
    }

    fun saveStock(item: StockItem, rememberForNextShop: Boolean = false, catalogItem: FoodCatalogItem? = null) = inWriteLock {
        val existing = database.loadStockById(item.id)
        val resolvedCatalog = if (item.category == ItemCategory.HOUSEHOLD || item.category == ItemCategory.OTHER) {
            null
        } else {
            catalogItem ?: item.catalogId?.let { database.findFoodCatalogItemById(it) }
                ?: database.findFoodCatalogItem(item.name)
        }
        val linkedItem = withPackageFacts(linkToCatalogBase(item, resolvedCatalog), resolvedCatalog, item.unit)
        val singleMealAdjusted = consumeSingleMeal(linkedItem).copy(
            linkedExpenseId = existing?.linkedExpenseId ?: linkedItem.linkedExpenseId,
        )
        val shoppingItem = if (rememberForNextShop) rememberedShoppingItem(singleMealAdjusted) else null

        val expenseCategoryId = when (item.category) {
            ItemCategory.HOUSEHOLD -> "household"
            ItemCategory.OTHER -> "other"
            else -> "food"
        }

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            if (resolvedCatalog != null && database.findFoodCatalogItemById(resolvedCatalog.id) == null) {
                database.upsertFoodCatalogItem(resolvedCatalog)
            }
            val linkedExpense = existing?.let {
                existing.linkedExpenseId?.let { database.findExpenseById(it) }
                    ?: database.findExpenseById("stock-${existing.id}")
                    ?: database.findExpenseForStockPurchase(existing.purchaseDate, existing.name, existing.totalPrice)
            }
            database.upsertStock(singleMealAdjusted.copy(
                linkedExpenseId = linkedExpense?.id ?: singleMealAdjusted.linkedExpenseId,
            ))

            if (linkedExpense != null) {
                database.upsertExpense(linkedExpense.copy(
                    amount = singleMealAdjusted.totalPrice,
                    date = singleMealAdjusted.purchaseDate,
                    description = "Bought ${singleMealAdjusted.name}",
                    categoryId = expenseCategoryId,
                ))
            } else if (existing != null && kotlin.math.abs(existing.totalPrice - singleMealAdjusted.totalPrice) > 0.001) {
                database.upsertExpense(Expense(
                    id = UUID.randomUUID().toString(),
                    categoryId = expenseCategoryId,
                    amount = singleMealAdjusted.totalPrice - existing.totalPrice,
                    date = singleMealAdjusted.purchaseDate,
                    description = "Price correction for ${singleMealAdjusted.name}",
                    isCorrection = true,
                ))
            }

            if (shoppingItem != null) database.upsertShopping(shoppingItem)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Stores a batch in the catalog's base unit and keeps the batch's own package facts.
     * A purchase must never rewrite the shared conversion for food already in the kitchen.
     * When the amount cannot be converted we keep the numbers and flag the batch instead
     * of quietly reinterpreting them.
     */
    private fun linkToCatalogBase(item: StockItem, catalog: FoodCatalogItem?): StockItem {
        if (catalog == null) return item.copy(catalogId = null)
        if (Units.sameUnit(item.unit, catalog.baseUnit)) {
            return item.copy(catalogId = catalog.id, unit = catalog.baseUnit)
        }
        val converted = Units.convert(item.totalQuantity, item.unit, catalog.baseUnit, catalog)
        val consumed = Units.convert(item.consumedQuantity, item.unit, catalog.baseUnit, catalog)
        return if (converted != null && consumed != null) {
            item.copy(
                catalogId = catalog.id,
                unit = catalog.baseUnit,
                totalQuantity = converted,
                consumedQuantity = consumed,
                estimatedUsagePerDay = Units.convert(item.estimatedUsagePerDay, item.unit, catalog.baseUnit, catalog) ?: 0.0,
                usageHistory = item.usageHistory.mapNotNull { Units.convert(it, item.unit, catalog.baseUnit, catalog) },
                datedUsageHistory = item.datedUsageHistory.mapNotNull { usage ->
                    Units.convert(usage.amount, item.unit, catalog.baseUnit, catalog)?.let { usage.copy(amount = it) }
                },
                conversionKnown = true,
            )
        } else {
            item.copy(catalogId = catalog.id, conversionKnown = false)
        }
    }

    /** Inherits the catalog's stated package size when the batch was bought as a package and does not carry its own. */
    private fun withPackageFacts(item: StockItem, catalog: FoodCatalogItem?, originalUnit: String = item.unit): StockItem {
        if (item.hasPackageInfo || catalog == null) return item
        if (catalog.packageSize == null) return item
        if (Units.sameUnit(originalUnit, catalog.baseUnit)) return item
        val label = catalog.packageLabel ?: return item
        val size = catalog.baseUnitsPerStockUnit
        if (size <= 0.0) return item
        return item.copy(packageLabel = label, packageSize = size)
    }

    private fun consumeSingleMeal(item: StockItem): StockItem {
        if (item.batchType != BatchType.SINGLE_MEAL) return item
        return item.copy(
            consumedQuantity = item.totalQuantity,
            usageHistory = item.usageHistory.ifEmpty { listOf(item.totalQuantity) },
            datedUsageHistory = item.datedUsageHistory.ifEmpty {
                listOf(DatedUsage(item.purchaseDate, item.totalQuantity))
            },
        )
    }

    fun recordPurchase(
        item: StockItem,
        rememberForNextShop: Boolean = false,
        catalogItem: FoodCatalogItem? = null,
        expenseId: String = "stock-${item.id}",
    ) = inWriteLock {
        val expenseCategoryId = when (item.category) {
            ItemCategory.HOUSEHOLD -> "household"
            ItemCategory.OTHER -> "other"
            else -> "food"
        }
        val isHousehold = item.category == ItemCategory.HOUSEHOLD || item.category == ItemCategory.OTHER
        var resolvedCatalog: FoodCatalogItem? = null
        val baseItem = if (isHousehold) {
            item
        } else {
            val existingCatalog = catalogItem ?: item.catalogId?.let { database.findFoodCatalogItemById(it) }
                ?: database.findFoodCatalogItem(item.name)
            val catalog = if (existingCatalog != null) {
                existingCatalog
            } else {
                FoodCatalogItem(
                    name = item.name,
                    category = item.category,
                    // For a brand new food the entered unit becomes its base unit.
                    stockUnit = item.unit.ifBlank { "piece" },
                    portionUnit = item.unit.ifBlank { "piece" },
                    portionsPerStockUnit = 1.0,
                    defaultCostPerPortion = if (item.totalQuantity > 0.0) {
                        item.totalPrice / item.totalQuantity
                    } else {
                        0.0
                    },
                )
            }
            resolvedCatalog = catalog
            linkToCatalogBase(item, catalog)
        }
        val linkedItem = withPackageFacts(baseItem, resolvedCatalog, item.unit)
        val singleMealAdjusted = consumeSingleMeal(linkedItem).copy(linkedExpenseId = expenseId)
        val expense = Expense(
            id = expenseId,
            categoryId = expenseCategoryId,
            amount = singleMealAdjusted.totalPrice,
            date = singleMealAdjusted.purchaseDate,
            description = "Bought ${singleMealAdjusted.name}",
            foodClassification = if (expenseCategoryId == "food") "groceries" else null,
        )
        val shoppingItem = if (rememberForNextShop) rememberedShoppingItem(singleMealAdjusted) else null
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            if (resolvedCatalog != null && database.findFoodCatalogItemById(resolvedCatalog.id) == null) {
                database.upsertFoodCatalogItem(resolvedCatalog)
            }
            database.upsertStock(singleMealAdjusted)
            database.upsertExpense(expense)
            if (shoppingItem != null) database.upsertShopping(shoppingItem)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteStock(id: String) = inWriteLock {
        database.deleteStock(id)
    }

    fun rememberStockForNextShop(item: StockItem) = inWriteLock {
        database.upsertShopping(rememberedShoppingItem(item))
    }

    fun logUsage(itemId: String, amount: Double, date: LocalDate = LocalDate.now(clock)) = inWriteLock {
        require(amount.isFinite() && amount > 0.0) { "Enter a positive usage amount." }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val current = database.loadStockById(itemId)
                ?: throw IllegalArgumentException("Stock item was not found.")
            require(current.remainingQuantity > 0.0) {
                "Cannot log usage for exhausted stock item '${current.name}'."
            }
            require(amount <= current.remainingQuantity) {
                "Usage amount ($amount ${current.unit}) exceeds available stock (${current.remainingQuantity} ${current.unit})."
            }
            val recorded = database.addUsage(itemId, amount, date)
            check(recorded) { "Could not persist usage for '${current.name}'." }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearSampleStock() = inWriteLock {
        database.clearSampleStock()
    }

    fun clearAllStock() = inWriteLock {
        database.clearAllStock()
    }

    fun saveExpense(expense: Expense) = inWriteLock {
        require(!expense.isMealExpense) { "Change this meal's spending through meal review." }
        require(!expense.id.startsWith(SPARES_EXPENSE_PREFIX)) { "Manage this withdrawal from spares." }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            require(!database.expenseHasStockPurchase(expense.id)) {
                "Manage this purchase from stock, or use Match real spending."
            }
            val current = database.findExpenseById(expense.id)
            database.upsertExpense(expense.copy(
                recurringScheduleId = expense.recurringScheduleId ?: current?.recurringScheduleId
                    ?: if (expense.isRecurring) expense.id else null,
            ))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteExpense(id: String) = inWriteLock {
        require(!id.startsWith(MealSpending.EXPENSE_PREFIX)) { "Undo this meal through meal review to remove its spending." }
        require(!id.startsWith(SPARES_EXPENSE_PREFIX)) { "Manage this withdrawal from spares." }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            require(!database.expenseHasStockPurchase(id)) {
                "Manage this purchase from stock, or use Match real spending."
            }
            database.findExpenseById(id)?.let { expense ->
                if (expense.isRecurring || expense.recurringScheduleId != null) {
                    database.stopRecurringSchedule(expense)
                }
            }
            database.deleteExpense(id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun processRecurringExpenses(today: LocalDate = LocalDate.now(clock)): List<Expense> = inWriteLock {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(database.loadExpenses(), today)
            toGenerate.forEach { database.upsertExpense(it) }
            db.setTransactionSuccessful()
            toGenerate
        } finally {
            db.endTransaction()
        }
    }

    fun resetPantryToEmpty() = inWriteLock {
        database.clearAllStock()
    }

    fun saveTemplate(template: MealTemplate) = inWriteLock {
        database.upsertTemplate(template)
    }

    fun deleteTemplate(id: String) = inWriteLock {
        database.deleteTemplate(id)
    }

    fun recordFoodExpense(
        amount: Double,
        date: LocalDate = LocalDate.now(clock),
        name: String = "",
        classification: String? = null,
        notes: String = "",
    ): Expense = inWriteLock {
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            categoryId = "food",
            amount = amount,
            date = date,
            description = name.ifBlank { "Food" }.trim(),
            foodClassification = classification,
            notes = notes.trim(),
        )
        database.upsertExpense(expense)
        expense
    }

    fun skipMeal(date: LocalDate, mealType: MealType): MealLog = inWriteLock {
        val existing = database.loadMealLog(date, mealType)
        val plans = database.loadDayPlans(date).associateBy { it.mealType }
        val plan = plans[mealType]
        val log = MealLog(
            id = existing?.id ?: UUID.randomUUID().toString(),
            date = date,
            mealType = mealType,
            templateId = existing?.templateId ?: plan?.templateId,
            name = existing?.name ?: plan?.name ?: mealType.label,
            cost = existing?.cost ?: plan?.cost ?: 0.0,
            actualTime = LocalDateTime.now(clock),
            consumedCost = 0.0,
            foods = existing?.foods ?: plan?.foods ?: "",
            status = MealStatus.SKIPPED,
            components = existing?.components ?: plan?.components ?: emptyList(),
            cashPaid = existing?.cashPaid,
            marker = existing?.marker ?: plan?.marker ?: MealMarker.UNMARKED,
        )
        database.upsertMealLog(log)
        log
    }

    fun undoSkipMeal(date: LocalDate, mealType: MealType) = inWriteLock {
        val existing = database.loadMealLog(date, mealType)
        if (existing != null && existing.status == MealStatus.SKIPPED) {
            database.deleteMealLog(existing.id)
        }
    }

    fun recordMeal(log: MealLog) = inWriteLock {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val existing = database.loadMealLog(log.date, log.mealType)
            val resolvedCashPaid = log.cashPaid ?: (if (existing != null) existing.cashPaid else 0.0)
            database.upsertMealLog(log.copy(
                id = existing?.id ?: log.id,
                consumedCost = log.consumedCost.coerceIn(0.0, log.cost.coerceAtLeast(0.0)),
                foods = log.foods.ifBlank { existing?.foods.orEmpty() },
                cashPaid = resolvedCashPaid,
            ))
            database.deleteDayClosure(log.date)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun reconcileMeals(date: LocalDate, entries: List<MealReconciliation>) = inWriteLock {
        require(entries.map { it.template.mealType }.distinct().size == entries.size) {
            "Review each meal type once."
        }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val existingLogs = database.loadMealLogs(date).associateBy { it.mealType }
            val logs = entries.map { entry ->
                val template = entry.template
                val existing = existingLogs[template.mealType]
                val resolvedCashPaid = entry.cashPaid ?: (if (existing != null) existing.cashPaid else 0.0)
                MealLog(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    date = date,
                    mealType = template.mealType,
                    templateId = template.id,
                    name = template.name,
                    cost = template.cost,
                    actualTime = existing?.actualTime ?: LocalDateTime.now(clock),
                    consumedCost = entry.consumedCost.coerceIn(0.0, template.cost.coerceAtLeast(0.0)),
                    foods = existing?.foods?.ifBlank { template.foodSummary } ?: template.foodSummary,
                    status = entry.status,
                    components = template.components.ifEmpty { MealDataCodec.legacyComponents(template.notes) },
                    cashPaid = resolvedCashPaid,
                    marker = existing?.marker ?: template.marker,
                )
            }
            val allLogs = (existingLogs + logs.associateBy { it.mealType }).values
            database.upsertMealLogs(logs, DayClosure(
                date = date,
                closedAt = LocalDateTime.now(clock),
                plannedCost = allLogs.sumOf { it.cost },
                consumedCost = allLogs.filter { it.isConsumed }.sumOf { it.consumedCost },
                leftoverCost = allLogs.filter { it.isConsumed }.sumOf { it.leftoverCost },
                skippedCost = allLogs.filter { it.isSkipped }.sumOf { it.cost },
            ))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteMealLog(id: String) = inWriteLock {
        database.deleteMealLog(id)
    }

    fun saveShopping(item: ShoppingItem) = inWriteLock {
        database.upsertShopping(item)
    }

    fun addMissingIngredientsToShopping(items: List<MissingIngredient>) = inWriteLock {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            items.forEach { missing ->
                val existing = database.findOpenShoppingByName(missing.name)
                if (existing != null && Units.sameUnit(existing.unit, missing.unit)) {
                    database.upsertShopping(
                        existing.copy(
                            quantity = existing.quantity + missing.quantity,
                            estimatedPrice = if (existing.priceKnown && missing.priceKnown) {
                                existing.estimatedPrice + missing.estimatedCost
                            } else {
                                existing.estimatedPrice
                            },
                            priceKnown = existing.priceKnown && missing.priceKnown,
                        ),
                    )
                } else {
                    database.upsertShopping(
                        ShoppingItem(
                            id = UUID.randomUUID().toString(),
                            name = missing.name,
                            quantity = missing.quantity,
                            unit = missing.unit,
                            estimatedPrice = if (missing.priceKnown) missing.estimatedCost else 0.0,
                            category = ItemCategory.FOOD_STAPLE,
                            isChecked = false,
                            priceKnown = missing.priceKnown,
                            note = if (missing.priceKnown) "" else "Price unknown",
                        ),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteShopping(id: String) = inWriteLock {
        database.deleteShopping(id)
    }

    fun toggleShopping(item: ShoppingItem) {
        if (item.isChecked) {
            undoShoppingPurchase(item)
        } else {
            buyShoppingItem(item, item.estimatedPrice, false)
        }
    }

    fun buyShoppingItem(item: ShoppingItem, price: Double, rememberForNextShop: Boolean = false): Boolean = inWriteLock {
        require(price.isFinite() && price >= 0.0) { "Enter a valid purchase price." }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val current = database.loadShoppingById(item.id) ?: return@inWriteLock false
            if (current.isChecked) return@inWriteLock false

            val stockId = UUID.randomUUID().toString()
            val expenseId = UUID.randomUUID().toString()
            val stockItem = StockItem(
                id = stockId,
                name = current.name,
                category = current.category,
                unit = current.unit,
                totalQuantity = current.quantity,
                totalPrice = price,
                purchaseDate = LocalDate.now(clock),
                batchType = if (current.category == ItemCategory.FOOD_STREET || current.category == ItemCategory.SNACK) BatchType.SINGLE_MEAL else BatchType.WEEKLY,
                estimatedUsagePerDay = if (current.category == ItemCategory.FOOD_STREET || current.category == ItemCategory.SNACK) current.quantity else 0.0,
                notes = "Bought from shopping list",
            )
            recordPurchase(stockItem, false, expenseId = expenseId)
            if (rememberForNextShop) {
                database.upsertShopping(
                    rememberedShoppingItem(stockItem).copy(
                        id = UUID.randomUUID().toString(),
                        isChecked = false,
                    ),
                )
            }
            database.upsertShopping(
                current.copy(
                    isChecked = true,
                    estimatedPrice = price,
                    purchaseExpenseId = expenseId,
                    purchaseStockId = stockId,
                    priceKnown = true,
                ),
            )
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    fun undoShoppingPurchase(item: ShoppingItem): Boolean = inWriteLock {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val current = database.loadShoppingById(item.id) ?: return@inWriteLock false
            if (!current.isChecked) return@inWriteLock false
            require(current.purchaseStockId?.let { database.stockHasMealConsumption(it) } != true) {
                "Undo the meals using this purchase before undoing the purchase."
            }
            current.purchaseStockId?.let { database.deleteStock(it) }
            current.purchaseExpenseId?.let { database.deleteExpense(it) }
            database.upsertShopping(
                current.copy(
                    isChecked = false,
                    purchaseExpenseId = null,
                    purchaseStockId = null,
                ),
            )
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    fun buyAgainShoppingItem(item: ShoppingItem): Boolean = inWriteLock {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val current = database.loadShoppingById(item.id) ?: return@inWriteLock false
            if (!current.isChecked) return@inWriteLock false
            database.upsertShopping(current.copy(
                isChecked = false,
                purchaseExpenseId = null,
                purchaseStockId = null,
            ))
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    fun spendFromSpares(
        amount: Double,
        reason: String,
        category: String? = null,
        expenseCategoryId: String = "spares",
        date: LocalDate = LocalDate.now(clock),
    ) = inWriteLock {
        require(amount.isFinite()) { "Enter a valid savings amount." }
        val positiveAmount = kotlin.math.abs(amount)
        if (positiveAmount <= 0.0) return@inWriteLock
        val sparesTransaction = SparesTransaction(
            amount = -positiveAmount,
            date = date,
            reason = reason,
            category = if (expenseCategoryId == "food") "daily_savings_spent" else category,
        )
        val expense = Expense(
            id = "$SPARES_EXPENSE_PREFIX${sparesTransaction.id}",
            categoryId = expenseCategoryId,
            amount = positiveAmount,
            date = date,
            description = reason,
        )
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val balance = database.loadSnapshot(readSettings(), date).sparesBalance
            require(positiveAmount <= balance + 0.000001) { "There is not enough money in spares." }
            database.upsertSpares(sparesTransaction)
            database.upsertExpense(expense)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addSpares(amount: Double, reason: String, category: String? = null, date: LocalDate = LocalDate.now(clock)) = inWriteLock {
        require(amount.isFinite()) { "Enter a valid savings amount." }
        if (amount == 0.0) return@inWriteLock
        if (amount < 0.0) {
            spendFromSpares(-amount, reason, category, "spares", date)
            return@inWriteLock
        }
        database.upsertSpares(SparesTransaction(amount = amount, date = date, reason = reason, category = category))
    }

    fun deleteSpares(id: String) = inWriteLock {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            database.deleteExpense("$SPARES_EXPENSE_PREFIX$id")
            database.deleteSpares(id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saveBudgetCorrection(expense: Expense) = inWriteLock {
        saveExpense(expense.copy(categoryId = "food", isCorrection = true))
    }

    fun deleteBudgetCorrection(expense: Expense) = inWriteLock {
        deleteExpense(expense.id)
    }

    fun saveTodayToSpares(): Double = inWriteLock {
        val today = LocalDate.now(clock)
        val db = database.writableDatabase
        var amount = 0.0
        var markSavedDate = false
        db.beginTransaction()
        try {
            val currentSettings = readSettings()
            val alreadySaved = database.hasSparesTransaction("daily-savings-$today")
            markSavedDate = alreadySaved
            if (currentSettings.lastSparesAutoSaveDate != today && !alreadySaved) {
                val currentSnapshot = database.loadSnapshot(currentSettings, today)
                amount = currentSnapshot.todayFoodRemaining
            }
            if (amount > 0.0) {
                database.upsertSpares(
                    SparesTransaction(
                        id = "daily-savings-$today",
                        amount = amount,
                        date = today,
                        reason = "Saved from today's food budget",
                        category = "daily_savings",
                    ),
                )
                markSavedDate = true
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (markSavedDate) {
            // This bookkeeping key belongs to the savings operation. Settings forms may
            // contain an older copy, and saving savings must not overwrite user settings.
            preferences.edit().putString(KEY_LAST_SPARES_DATE, today.toString()).apply()
        }
        amount
    }

    fun convertStockQuantity(
        quantity: Double,
        fromUnit: String,
        toUnit: String,
        catalog: FoodCatalogItem? = null,
    ): Double? = Companion.convertStockQuantity(quantity, fromUnit, toUnit, catalog)

    private fun rememberedShoppingItem(item: StockItem): ShoppingItem {
        val existing = database.findOpenShoppingByName(item.name)
        val note = "Remembered from last purchase · ${item.quantityLabel}"
        return existing?.copy(
            quantity = item.totalQuantity,
            unit = item.unit,
            estimatedPrice = item.totalPrice,
            category = item.category,
            sourceItemId = item.id,
            note = note,
        ) ?: ShoppingItem(
            name = item.name,
            quantity = item.totalQuantity,
            unit = item.unit,
            estimatedPrice = item.totalPrice,
            category = item.category,
            sourceItemId = item.id,
            note = note,
        )
    }

    fun exportCsv(snapshot: AppSnapshot): String =
        BudgetCsvCodec.encode(snapshot, LocalDate.now(clock))

    override fun close() {
        database.close()
    }

    companion object {
        val sharedPersistenceLock = ReentrantReadWriteLock()
        const val SPARES_EXPENSE_PREFIX = "spares-transaction:"
        private const val KEY_MONTHLY_FOOD = "monthly_food_budget"
        private const val KEY_DAILY_FOOD = "daily_food_budget"
        private const val KEY_WEEKLY_FOOD = "weekly_food_limit"
        private const val KEY_KOSHARY_DAY = "koshary_day"
        private const val KEY_KOSHARY_PRICE = "koshary_price"
        private const val KEY_AUTO_SPARES = "auto_save_spares"
        private const val KEY_REMINDERS = "reminders_enabled"
        private const val KEY_SHOPPING_DAY = "shopping_day"
        private const val KEY_LAST_SPARES_DATE = "last_spares_date"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FOOD_PREFERENCES = "food_preferences"
        private const val KEY_FOOD_EXCLUSIONS = "food_exclusions"
        private const val KEY_HABIT_MESSAGES = "habit_messages_enabled"
        private const val KEY_BREAKFAST_START = "breakfast_start"
        private const val KEY_BREAKFAST_END = "breakfast_end"
        private const val KEY_LUNCH_START = "lunch_start"
        private const val KEY_LUNCH_END = "lunch_end"
        private const val KEY_DINNER_START = "dinner_start"
        private const val KEY_DINNER_END = "dinner_end"

        /**
         * Converts between labels using explicit factors only: stated package sizes, the
         * metric prefixes, and per-food facts such as "1 spoon = 20 g". It returns null
         * whenever we cannot be sure, and never matches one unit by being part of another.
         */
        fun convertStockQuantity(
            quantity: Double,
            fromUnit: String,
            toUnit: String,
            catalog: FoodCatalogItem? = null,
        ): Double? = Units.convert(quantity, fromUnit, toUnit, catalog)

        fun normalizeUnit(unit: String): String = Units.normalize(unit)

        /** Identity check for unit labels: case and plurals only, never a substring. */
        fun isUnitEquivalent(unitA: String, unitB: String): Boolean = Units.sameUnit(unitA, unitB)
    }
}
