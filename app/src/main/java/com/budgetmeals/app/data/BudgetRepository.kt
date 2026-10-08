package com.budgetmeals.app.data

import android.content.Context
import com.budgetmeals.app.state.BudgetMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class BudgetRepository(context: Context) {
    private val database = BudgetDatabase(context.applicationContext)
    private val preferences = context.applicationContext.getSharedPreferences("budget_meals", Context.MODE_PRIVATE)

    private var lastSnapshot: AppSnapshot? = null

    private fun <T> stable(old: List<T>?, new: List<T>): List<T> =
        if (old != null && old == new) old else new

    fun loadSnapshot(): AppSnapshot {
        database.autoClosePastDays(LocalDate.now())
        processRecurringExpenses(LocalDate.now())
        val raw = database.loadSnapshot(readSettings())
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
        return snapshot
    }

    fun ensureDayPlan(date: LocalDate, templates: List<MealTemplate>): Int {
        return database.ensureDayPlan(date, templates)
    }

    fun replaceOpenDayPlan(date: LocalDate, templates: List<MealTemplate>): Boolean {
        return database.replaceOpenDayPlan(date, templates)
    }

    fun assignMealToDay(
        date: LocalDate,
        mealType: MealType,
        template: MealTemplate?,
        snapshot: AppSnapshot,
    ): Boolean {
        val currentMeals = BudgetMath.plannedMealsForDate(snapshot, date).toMutableMap()
        if (template == null) {
            currentMeals.remove(mealType)
        } else {
            currentMeals[mealType] = template
        }
        return database.saveDayPlanMeals(date, currentMeals)
    }

    fun rebuildPlanDates(dates: List<LocalDate>, snapshot: AppSnapshot): Int {
        var count = 0
        dates.forEach { date ->
            val generated = BudgetMath.generatedMealsForDate(snapshot, date).values.toList()
            if (database.replaceOpenDayPlan(date, generated)) {
                count++
            }
        }
        return count
    }

    fun closeDay(date: LocalDate) {
        database.closeDay(date)
    }

    @Volatile
    private var cachedSettings: BudgetSettings? = null

    fun readSettings(forceRefresh: Boolean = false): BudgetSettings {
        val cached = cachedSettings
        if (!forceRefresh && cached != null) return cached
        val storedKoshary = preferences.getString(KEY_KOSHARY_DAY, null)?.let { value ->
            runCatching { DayOfWeek.valueOf(value) }.getOrNull()
        }
        val storedShopping = preferences.getString(KEY_SHOPPING_DAY, null)?.let { value ->
            runCatching { DayOfWeek.valueOf(value) }.getOrNull()
        }
        val storedTheme = preferences.getString(KEY_THEME_MODE, null)?.let { value ->
            runCatching { AppThemeMode.valueOf(value) }.getOrNull()
        }
        val settings = BudgetSettings(
            monthlyFoodBudget = preferences.getFloat(KEY_MONTHLY_FOOD, 3_000f).toDouble(),
            dailyFoodBudget = preferences.getFloat(KEY_DAILY_FOOD, 100f).toDouble(),
            weeklyFoodLimit = preferences.getFloat(KEY_WEEKLY_FOOD, 750f).toDouble(),
            kosharyDay = storedKoshary ?: DayOfWeek.WEDNESDAY,
            kosharyPrice = preferences.getFloat(KEY_KOSHARY_PRICE, 60f).toDouble(),
            autoSaveSpares = preferences.getBoolean(KEY_AUTO_SPARES, true),
            remindersEnabled = preferences.getBoolean(KEY_REMINDERS, true),
            shoppingDay = storedShopping ?: DayOfWeek.FRIDAY,
            lastSparesAutoSaveDate = preferences.getString(KEY_LAST_SPARES_DATE, null)?.let { value ->
                runCatching { LocalDate.parse(value) }.getOrNull()
            },
            themeMode = storedTheme ?: AppThemeMode.SYSTEM,
        )
        cachedSettings = settings
        return settings
    }

    fun saveSettings(settings: BudgetSettings) {
        cachedSettings = settings
        preferences.edit()
            .putFloat(KEY_MONTHLY_FOOD, settings.monthlyFoodBudget.toFloat())
            .putFloat(KEY_DAILY_FOOD, settings.dailyFoodBudget.toFloat())
            .putFloat(KEY_WEEKLY_FOOD, settings.weeklyFoodLimit.toFloat())
            .putString(KEY_KOSHARY_DAY, settings.kosharyDay.name)
            .putFloat(KEY_KOSHARY_PRICE, settings.kosharyPrice.toFloat())
            .putBoolean(KEY_AUTO_SPARES, settings.autoSaveSpares)
            .putBoolean(KEY_REMINDERS, settings.remindersEnabled)
            .putString(KEY_SHOPPING_DAY, settings.shoppingDay.name)
            .putString(KEY_LAST_SPARES_DATE, settings.lastSparesAutoSaveDate?.toString())
            .putString(KEY_THEME_MODE, settings.themeMode.name)
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

    fun saveFoodCatalogItem(item: FoodCatalogItem) {
        database.upsertFoodCatalogItem(item)
    }

    fun importFoodCatalogJson(raw: String): Int {
        val items = FoodCatalogJsonCodec.decode(raw).getOrThrow()
        database.upsertFoodCatalogItems(items)
        return items.size
    }

    fun exportFoodCatalogJson(snapshot: AppSnapshot): String {
        return FoodCatalogJsonCodec.encode(snapshot.foodCatalog)
    }

    fun deleteFoodCatalogItem(id: String) {
        database.deleteFoodCatalogItem(id)
    }

    fun findFoodCatalogItem(name: String): FoodCatalogItem? {
        return database.findFoodCatalogItem(name)
    }

    fun saveCategory(category: ExpenseCategory) {
        database.upsertCategory(category)
    }

    fun deleteCategory(id: String) {
        if (id == "food" || id == "spares") return
        if (database.categoryHasExpenses(id)) return
        database.deleteCategory(id)
    }

    fun saveStock(item: StockItem, rememberForNextShop: Boolean = false, catalogItem: FoodCatalogItem? = null) {
        val resolvedCatalog = if (item.category == ItemCategory.HOUSEHOLD || item.category == ItemCategory.OTHER) {
            null
        } else {
            catalogItem ?: item.catalogId?.let { database.findFoodCatalogItemById(it) }
                ?: database.findFoodCatalogItem(item.name)
        }
        val linkedItem = withPackageFacts(linkToCatalogBase(item, resolvedCatalog), resolvedCatalog, item.unit)
        val singleMealAdjusted = if (linkedItem.batchType == BatchType.SINGLE_MEAL) {
            linkedItem.copy(
                consumedQuantity = linkedItem.totalQuantity,
                usageHistory = if (linkedItem.usageHistory.isEmpty()) listOf(linkedItem.totalQuantity) else linkedItem.usageHistory,
                datedUsageHistory = if (linkedItem.datedUsageHistory.isEmpty()) listOf(DatedUsage(linkedItem.purchaseDate, linkedItem.totalQuantity)) else linkedItem.datedUsageHistory,
            )
        } else {
            linkedItem
        }
        val shoppingItem = if (rememberForNextShop) rememberedShoppingItem(singleMealAdjusted) else null

        val existing = database.loadStockById(item.id)
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
            database.upsertStock(singleMealAdjusted)

            if (existing != null && kotlin.math.abs(existing.totalPrice - singleMealAdjusted.totalPrice) > 0.001) {
                val diff = singleMealAdjusted.totalPrice - existing.totalPrice
                val linkedExpense = database.findExpenseById("stock-${existing.id}")
                    ?: database.findExpenseForStockPurchase(existing.purchaseDate, existing.name, existing.totalPrice)
                if (linkedExpense != null) {
                    database.upsertExpense(
                        linkedExpense.copy(
                            amount = singleMealAdjusted.totalPrice,
                            description = "Bought ${singleMealAdjusted.name}",
                            categoryId = expenseCategoryId,
                        ),
                    )
                } else {
                    database.upsertExpense(
                        Expense(
                            id = UUID.randomUUID().toString(),
                            categoryId = expenseCategoryId,
                            amount = diff,
                            date = singleMealAdjusted.purchaseDate,
                            description = "Price correction for ${singleMealAdjusted.name}",
                            isCorrection = true,
                        ),
                    )
                }
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

    fun recordPurchase(
        item: StockItem,
        rememberForNextShop: Boolean = false,
        catalogItem: FoodCatalogItem? = null,
        expenseId: String = "stock-${item.id}",
    ) {
        val expenseCategoryId = when (item.category) {
            ItemCategory.HOUSEHOLD -> "household"
            ItemCategory.OTHER -> "other"
            else -> "food"
        }
        val isHousehold = item.category == ItemCategory.HOUSEHOLD || item.category == ItemCategory.OTHER
        var resolvedCatalog: FoodCatalogItem? = null
        var isNewCatalog = false
        val baseItem = if (isHousehold) {
            item
        } else {
            val existingCatalog = catalogItem ?: item.catalogId?.let { database.findFoodCatalogItemById(it) }
                ?: database.findFoodCatalogItem(item.name)
            val catalog = if (existingCatalog != null) {
                existingCatalog
            } else {
                isNewCatalog = true
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
        val singleMealAdjusted = if (linkedItem.batchType == BatchType.SINGLE_MEAL) {
            linkedItem.copy(
                consumedQuantity = linkedItem.totalQuantity,
                usageHistory = if (linkedItem.usageHistory.isEmpty()) listOf(linkedItem.totalQuantity) else linkedItem.usageHistory,
                datedUsageHistory = if (linkedItem.datedUsageHistory.isEmpty()) listOf(DatedUsage(linkedItem.purchaseDate, linkedItem.totalQuantity)) else linkedItem.datedUsageHistory,
            )
        } else {
            linkedItem
        }
        val expense = Expense(
            id = expenseId,
            categoryId = expenseCategoryId,
            amount = singleMealAdjusted.totalPrice,
            date = singleMealAdjusted.purchaseDate,
            description = "Bought ${singleMealAdjusted.name}",
        )
        val shoppingItem = if (rememberForNextShop) rememberedShoppingItem(singleMealAdjusted) else null
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            if (resolvedCatalog != null && isNewCatalog) database.upsertFoodCatalogItem(resolvedCatalog)
            database.upsertStock(singleMealAdjusted)
            database.upsertExpense(expense)
            if (shoppingItem != null) database.upsertShopping(shoppingItem)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteStock(id: String) {
        database.deleteStock(id)
    }

    fun rememberStockForNextShop(item: StockItem) {
        database.upsertShopping(rememberedShoppingItem(item))
    }

    fun logUsage(itemId: String, amount: Double, date: LocalDate = LocalDate.now()) {
        database.addUsage(itemId, amount, date)
    }

    fun clearSampleStock() {
        database.clearSampleStock()
    }

    fun clearAllStock() {
        database.clearAllStock()
    }

    fun saveExpense(expense: Expense) {
        database.upsertExpense(expense.copy(
            recurringScheduleId = expense.recurringScheduleId ?: if (expense.isRecurring) expense.id else null,
        ))
    }

    fun deleteExpense(id: String) {
        database.deleteExpense(id)
    }

    fun processRecurringExpenses(today: LocalDate = LocalDate.now()): List<Expense> {
        val allExpenses = database.loadExpenses()
        val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(allExpenses, today)
        toGenerate.forEach { database.upsertExpense(it) }
        return toGenerate
    }

    fun resetPantryToEmpty() {
        database.clearAllStock()
    }

    fun loadSamplePantry() {
        database.loadSamplePantry()
    }

    fun saveTemplate(template: MealTemplate) {
        database.upsertTemplate(template)
    }

    fun deleteTemplate(id: String) {
        database.deleteTemplate(id)
    }

    fun recordMeal(log: MealLog) {
        val existing = database.loadMealLog(log.date, log.mealType)
        database.upsertMealLog(
            log.copy(
                id = existing?.id ?: log.id,
                consumedCost = log.consumedCost.coerceIn(0.0, log.cost.coerceAtLeast(0.0)),
                foods = log.foods.ifBlank { existing?.foods.orEmpty() },
                status = log.status,
            ),
        )
        database.deleteDayClosure(log.date)
    }

    fun reconcileMeals(date: LocalDate, entries: List<MealReconciliation>) {
        val logs = entries.map { entry ->
            val template = entry.template
            val existing = database.loadMealLog(date, template.mealType)
            MealLog(
                id = existing?.id ?: UUID.randomUUID().toString(),
                date = date,
                mealType = template.mealType,
                templateId = template.id,
                name = template.name,
                cost = template.cost,
                actualTime = existing?.actualTime ?: java.time.LocalDateTime.now(),
                consumedCost = entry.consumedCost.coerceIn(0.0, template.cost.coerceAtLeast(0.0)),
                foods = existing?.foods?.ifBlank { template.foodSummary } ?: template.foodSummary,
                status = entry.status,
                components = template.components.ifEmpty { MealDataCodec.legacyComponents(template.notes) },
            )
        }
        val plannedCost = entries.sumOf { it.template.cost }
        val consumedCost = logs.filter { it.isConsumed }.sumOf { it.consumedCost }
        val leftoverCost = logs.filter { it.isConsumed }.sumOf { it.leftoverCost }
        val skippedCost = logs.filter { it.isSkipped }.sumOf { it.cost }
        database.upsertMealLogs(
            logs,
            DayClosure(
                date = date,
                closedAt = java.time.LocalDateTime.now(),
                plannedCost = plannedCost,
                consumedCost = consumedCost,
                leftoverCost = leftoverCost,
                skippedCost = skippedCost,
            ),
        )
    }

    fun deleteMealLog(id: String) {
        database.deleteMealLog(id)
    }

    fun saveShopping(item: ShoppingItem) {
        database.upsertShopping(item)
    }

    fun deleteShopping(id: String) {
        database.deleteShopping(id)
    }

    fun toggleShopping(item: ShoppingItem) {
        if (item.isChecked) {
            undoShoppingPurchase(item)
        } else {
            buyShoppingItem(item, item.estimatedPrice, false)
        }
    }

    fun buyShoppingItem(item: ShoppingItem, price: Double, rememberForNextShop: Boolean = false): Boolean {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val current = database.loadShoppingById(item.id) ?: return false
            if (current.isChecked) return false

            val stockId = UUID.randomUUID().toString()
            val expenseId = UUID.randomUUID().toString()
            val stockItem = StockItem(
                id = stockId,
                name = current.name,
                category = current.category,
                unit = current.unit,
                totalQuantity = current.quantity,
                totalPrice = price,
                purchaseDate = LocalDate.now(),
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
                    priceKnown = price > 0.0,
                ),
            )
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun undoShoppingPurchase(item: ShoppingItem): Boolean {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val current = database.loadShoppingById(item.id) ?: return false
            if (!current.isChecked) return false
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
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun buyAgainShoppingItem(item: ShoppingItem) {
        database.upsertShopping(
            item.copy(
                isChecked = false,
                purchaseExpenseId = null,
                purchaseStockId = null,
            ),
        )
    }

    fun spendFromSpares(
        amount: Double,
        reason: String,
        category: String? = null,
        expenseCategoryId: String = "spares",
        date: LocalDate = LocalDate.now(),
    ) {
        val positiveAmount = kotlin.math.abs(amount)
        if (positiveAmount <= 0.0) return
        val sparesTransaction = SparesTransaction(
            amount = -positiveAmount,
            date = date,
            reason = reason,
            category = if (expenseCategoryId == "food") "daily_savings_spent" else category,
        )
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            categoryId = expenseCategoryId,
            amount = positiveAmount,
            date = date,
            description = reason,
        )
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            database.upsertSpares(sparesTransaction)
            database.upsertExpense(expense)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addSpares(amount: Double, reason: String, category: String? = null, date: LocalDate = LocalDate.now()) {
        if (amount == 0.0) return
        if (amount < 0.0) {
            spendFromSpares(-amount, reason, category, "spares", date)
            return
        }
        database.upsertSpares(SparesTransaction(amount = amount, date = date, reason = reason, category = category))
    }

    fun deleteSpares(id: String) {
        database.deleteSpares(id)
    }

    fun saveBudgetCorrection(expense: Expense) {
        database.upsertExpense(expense.copy(categoryId = "food", isCorrection = true))
    }

    fun deleteBudgetCorrection(expense: Expense) {
        database.deleteExpense(expense.id)
    }

    fun saveTodayToSpares(): Double {
        val today = LocalDate.now()
        val db = database.writableDatabase
        var amount = 0.0
        var settingsToMark: BudgetSettings? = null
        db.beginTransaction()
        try {
            val currentSettings = readSettings(forceRefresh = true)
            if (currentSettings.lastSparesAutoSaveDate != today) {
                val currentSnapshot = database.loadSnapshot(currentSettings)
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
                settingsToMark = currentSettings
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        settingsToMark?.let { currentSettings ->
            saveSettings(currentSettings.copy(lastSparesAutoSaveDate = today))
        }
        return amount
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

    fun exportCsv(snapshot: AppSnapshot): String {
        return buildString {
            appendLine("BudgetMeals export")
            appendLine("Exported,${LocalDate.now()}")
            appendLine()
            appendLine("Expenses")
            appendLine("Date,Category,Amount EGP,Description,Recurring,Correction")
            snapshot.expenses.sortedBy { it.date }.forEach { expense ->
                val category = snapshot.categories.firstOrNull { it.id == expense.categoryId }?.name ?: "Other"
                appendLine("${expense.date},${csvEscape(category)},${money(expense.amount)},${csvEscape(expense.description)},${expense.isRecurring},${expense.isCorrection}")
            }
            appendLine()
            appendLine("Stock")
            appendLine("Name,Category,Quantity,Unit,Price,Remaining,Days left,Cost per day")
            snapshot.stock.forEach { item ->
                appendLine("${csvEscape(item.name)},${item.category.label},${number(item.totalQuantity)},${csvEscape(item.unit)},${money(item.totalPrice)},${number(item.remainingQuantity)},${number(item.daysRemaining)},${money(item.costPerDay)}")
            }
            appendLine()
            appendLine("Shopping list")
            appendLine("Name,Quantity,Unit,Estimated price,Checked")
            snapshot.shopping.forEach { item ->
                appendLine("${csvEscape(item.name)},${number(item.quantity)},${csvEscape(item.unit)},${money(item.estimatedPrice)},${item.isChecked}")
            }
            appendLine()
            appendLine("Spares")
            appendLine("Date,Amount EGP,Reason,Category")
            snapshot.spares.forEach { transaction ->
                appendLine("${transaction.date},${money(transaction.amount)},${csvEscape(transaction.reason)},${csvEscape(transaction.category.orEmpty())}")
            }
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun money(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
    private fun number(value: Double): String = String.format(java.util.Locale.US, "%.3f", value)

    companion object {
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
