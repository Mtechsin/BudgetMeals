package com.budgetmeals.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.budgetmeals.app.state.BudgetMath
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

abstract class BudgetDao(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    fun loadSnapshot(settings: BudgetSettings, today: LocalDate = LocalDate.now()): AppSnapshot {
        val db = readableDatabase
        return AppSnapshot(
            categories = db.query("categories", null, null, null, null, null, "sort_order ASC, name ASC")
                .use { it.readRows(::readCategory) },
            foodCatalog = db.query("food_catalog", null, null, null, null, null, "name COLLATE NOCASE ASC")
                .use { it.readRows(::readFoodCatalog) },
            stock = db.query("stock", null, null, null, null, null, "purchase_date DESC, name ASC")
                .use { it.readRows(::readStock) },
            expenses = loadExpenses(),
            templates = db.query("meal_templates", null, null, null, null, null, "meal_type ASC, cost ASC")
                .use { it.readRows(::readTemplate) },
            mealLogs = db.query("meal_logs", null, null, null, null, null, "date DESC, actual_time DESC")
                .use { it.readRows(::readMealLog) },
            shopping = db.query("shopping", null, null, null, null, null, "is_checked ASC, created_date DESC, name ASC")
                .use { it.readRows(::readShopping) },
            spares = db.query("spares", null, null, null, null, null, "date ASC, rowid ASC")
                .use { it.readRows(::readSpares) },
            dayPlans = db.query("day_plans", null, null, null, null, null, "date ASC, meal_type ASC")
                .use { it.readRows(::readDayPlan) },
            dayClosures = db.query("day_closures", null, null, null, null, null, "date DESC")
                .use { it.readRows(::readDayClosure) },
            settings = settings,
            today = today,
        )
    }

    fun findOpenShoppingByName(name: String): ShoppingItem? {
        return readableDatabase.query(
            "shopping",
            null,
            "is_checked = 0 AND name = ? COLLATE NOCASE",
            arrayOf(name),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) readShopping(cursor) else null
        }
    }

    fun loadShoppingById(id: String): ShoppingItem? {
        return writableDatabase.query(
            "shopping",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) readShopping(cursor) else null
        }
    }

    fun findCategoryById(id: String): ExpenseCategory? {
        return readableDatabase.query("categories", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) readCategory(cursor) else null }
    }

    fun findTemplateByNameLike(query: String): MealTemplate? {
        return readableDatabase.query("meal_templates", null, "name LIKE ?", arrayOf("%$query%"), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) readTemplate(cursor) else null }
    }

    fun upsertCategory(category: ExpenseCategory) {
        val values = ContentValues().apply {
            put("id", category.id)
            put("name", category.name)
            put("icon", category.icon)
            put("monthly_budget", category.monthlyBudget)
            put("is_food", if (category.isFood) 1 else 0)
            put("is_custom", if (category.isCustom) 1 else 0)
            put("sort_order", category.sortOrder)
        }
        check(writableDatabase.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Could not save the category."
        }
    }

    fun upsertFoodCatalogItems(items: List<FoodCatalogItem>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.forEach { item -> upsertFoodCatalogItem(item) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertFoodCatalogItem(item: FoodCatalogItem) {
        val values = ContentValues().apply {
            put("id", item.id)
            put("name", item.name)
            put("category", item.category.name)
            put("stock_unit", item.stockUnit)
            put("portion_unit", item.portionUnit)
            put("portions_per_stock_unit", item.portionsPerStockUnit.coerceAtLeast(0.0001))
            put("default_cost_per_portion", item.defaultCostPerPortion.coerceAtLeast(0.0))
            put("notes", item.notes)
            put("purchase_price", item.purchasePrice)
            put("purchase_quantity", item.purchaseQuantity)
            put("purchase_unit", item.purchaseUnit)
            put("meal_usage", item.mealUsage)
            put("price_options_json", FoodCatalogJsonCodec.encodePriceOptions(item.priceOptions))
            put("price_known", if (item.priceKnown) 1 else 0)
            put("conversion_known", if (item.conversionKnown) 1 else 0)
        }
        check(writableDatabase.insertWithOnConflict("food_catalog", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Could not save the food."
        }
    }

    fun deleteFoodCatalogItem(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("food_catalog", "id = ?", arrayOf(id))
            db.execSQL("UPDATE stock SET catalog_id = NULL WHERE catalog_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun findFoodCatalogItem(name: String): FoodCatalogItem? {
        val typedName = name.trim()
        val canonicalName = when (typedName.lowercase(Locale.US)) {
            "tomato", "tomatoes" -> "Tomatoes"
            "cucumber", "cucumbers" -> "Cucumbers"
            "bread", "baladi bread" -> "Baladi bread"
            else -> typedName
        }
        return readableDatabase.rawQuery(
            "SELECT * FROM food_catalog " +
                "WHERE TRIM(LOWER(name)) = TRIM(LOWER(?)) OR TRIM(LOWER(name)) = TRIM(LOWER(?)) " +
                "ORDER BY CASE WHEN TRIM(LOWER(name)) = TRIM(LOWER(?)) THEN 0 ELSE 1 END, name LIMIT 1",
            arrayOf(canonicalName, typedName, typedName),
        ).use { cursor -> if (cursor.moveToFirst()) readFoodCatalog(cursor) else null }
    }

    fun findFoodCatalogItemById(id: String): FoodCatalogItem? {
        return readableDatabase.query(
            "food_catalog",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) readFoodCatalog(cursor) else null }
    }

    fun deleteCategory(id: String) {
        writableDatabase.delete("categories", "id = ?", arrayOf(id))
    }

    fun categoryHasExpenses(id: String): Boolean {
        return readableDatabase.query(
            "expenses",
            arrayOf("id"),
            "category_id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { it.moveToFirst() }
    }

    fun upsertStock(item: StockItem) {
        val values = ContentValues().apply {
            put("id", item.id)
            put("name", item.name)
            put("category", item.category.name)
            put("unit", item.unit)
            put("total_quantity", item.totalQuantity.coerceAtLeast(0.0))
            put("total_price", item.totalPrice)
            put("purchase_date", item.purchaseDate.toEpochDay())
            put("expiry_date", item.expiryDate?.toEpochDay())
            put("batch_type", item.batchType.name)
            put("usage_per_day", item.estimatedUsagePerDay)
            put("consumed_quantity", item.consumedQuantity.coerceIn(0.0, item.totalQuantity.coerceAtLeast(0.0)))
            put("usage_history", StockUsageCodec.encode(item))
            put("notes", item.notes)
            put("catalog_id", item.catalogId)
            put("linked_expense_id", item.linkedExpenseId)
            put("package_label", item.packageLabel)
            put("package_size", item.packageSize)
            put("conversion_known", if (item.conversionKnown) 1 else 0)
        }
        val updated = writableDatabase.update("stock", values, "id = ?", arrayOf(item.id))
        if (updated == 0) {
            writableDatabase.insertOrThrow("stock", null, values)
        }
    }

    fun deleteStock(id: String) {
        writableDatabase.delete("stock", "id = ?", arrayOf(id))
    }

    fun clearAllStock() {
        writableDatabase.delete("stock", null, null)
    }

    fun addUsage(id: String, amount: Double, date: LocalDate = LocalDate.now()): Boolean {
        require(amount.isFinite() && amount > 0.0) { "Enter a positive usage amount." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = loadStockById(id) ?: return false
            if (current.remainingQuantity <= 0.0) return false
            if (amount > current.remainingQuantity) return false
            upsertStock(current.copy(
                consumedQuantity = current.consumedQuantity + amount,
                usageHistory = current.usageHistory + amount,
                datedUsageHistory = current.datedUsageHistory + DatedUsage(date, amount),
            ))
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun loadStockById(id: String): StockItem? {
        return readableDatabase.query("stock", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) readStock(cursor) else null }
    }

    fun stockHasMealConsumption(id: String): Boolean = readableDatabase.query(
        "meal_consumptions", arrayOf("id"), "stock_item_id = ? AND stock_quantity > 0", arrayOf(id),
        null, null, null, "1",
    ).use { it.moveToFirst() }

    fun loadExpenses(): List<Expense> {
        return readableDatabase.query("expenses", null, null, null, null, null, "date DESC, rowid DESC")
            .use { it.readRows(::readExpense) }
    }

    fun upsertExpense(expense: Expense) {
        val values = ContentValues().apply {
            put("id", expense.id)
            put("category_id", expense.categoryId)
            put("amount", expense.amount)
            put("date", expense.date.toEpochDay())
            put("description", expense.description)
            put("is_recurring", if (expense.isRecurring) 1 else 0)
            put("recurring_frequency", expense.recurringFrequency)
            put("is_correction", if (expense.isCorrection) 1 else 0)
            put("recurring_schedule_id", expense.recurringScheduleId)
            put("is_budget_transfer", if (expense.isBudgetTransfer) 1 else 0)
            put("food_classification", expense.foodClassification)
            put("notes", expense.notes)
        }
        check(writableDatabase.insertWithOnConflict("expenses", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Could not save the expense."
        }
    }

    fun deleteExpense(id: String) {
        writableDatabase.delete("expenses", "id = ?", arrayOf(id))
    }

    fun findExpenseById(id: String): Expense? {
        return readableDatabase.query("expenses", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) readExpense(cursor) else null }
    }

    fun expenseHasStockPurchase(id: String): Boolean = readableDatabase.query(
        "stock", arrayOf("id"), "linked_expense_id = ? OR 'stock-' || id = ?", arrayOf(id, id),
        null, null, null, "1",
    ).use { it.moveToFirst() }

    fun stopRecurringSchedule(expense: Expense) {
        val values = ContentValues().apply { put("is_recurring", 0) }
        val scheduleId = expense.recurringScheduleId
        if (scheduleId != null) {
            writableDatabase.update("expenses", values, "recurring_schedule_id = ?", arrayOf(scheduleId))
        } else {
            val description = expense.description.trim().lowercase(Locale.US)
            loadExpenses().filter {
                it.recurringScheduleId == null && it.categoryId == expense.categoryId &&
                    it.description.trim().lowercase(Locale.US) == description
            }.forEach {
                writableDatabase.update("expenses", values, "id = ?", arrayOf(it.id))
            }
        }
    }

    fun findExpenseForStockPurchase(date: LocalDate, name: String, amount: Double): Expense? {
        return readableDatabase.query(
            "expenses",
            null,
            "date = ? AND description = ? AND ABS(amount - ?) < 0.001",
            arrayOf(date.toEpochDay().toString(), "Bought $name", amount.toString()),
            null,
            null,
            "rowid DESC",
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) readExpense(cursor) else null }
    }

    fun clearSampleStock() {
        DatabaseMigrations.clearSampleStock(writableDatabase)
    }

    fun upsertTemplate(template: MealTemplate) {
        val values = ContentValues().apply {
            put("id", template.id)
            put("name", template.name)
            put("meal_type", template.mealType.name)
            put("cost", template.cost)
            put("is_recurring", if (template.isRecurring) 1 else 0)
            put("day_of_week", template.dayOfWeek?.name)
            put("notes", template.notes)
            put("is_custom", if (template.isCustom) 1 else 0)
            put("components_json", MealDataCodec.encodeComponents(template.components))
            put("meal_marker", template.marker.name)
        }
        val inserted = writableDatabase.insertWithOnConflict("meal_templates", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        check(inserted != -1L) { "Could not save the meal. Please try again." }
    }

    fun deleteTemplate(id: String) {
        writableDatabase.delete("meal_templates", "id = ?", arrayOf(id))
    }

    fun upsertMealLog(log: MealLog) {
        saveMealLogsWithComponents(listOf(log))
    }

    fun loadMealLog(date: LocalDate, mealType: MealType): MealLog? {
        return readableDatabase.query(
            "meal_logs",
            null,
            "date = ? AND meal_type = ?",
            arrayOf(date.toEpochDay().toString(), mealType.name),
            null,
            null,
            "actual_time DESC",
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) readMealLog(cursor) else null }
    }

    fun upsertMealLogs(logs: List<MealLog>, closure: DayClosure? = null) {
        saveMealLogsWithComponents(logs, closure)
    }

    fun loadMealLogs(date: LocalDate): List<MealLog> = readableDatabase.query(
        "meal_logs", null, "date = ?", arrayOf(date.toEpochDay().toString()), null, null, null,
    ).use { it.readRows(::readMealLog) }

    fun saveMealLogsWithComponents(logs: List<MealLog>, closure: DayClosure? = null) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val replacedIds = logs.flatMap { log ->
                db.rawQuery(
                    "SELECT id FROM meal_logs WHERE date = ? AND meal_type = ?",
                    arrayOf(log.date.toEpochDay().toString(), log.mealType.name),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }.filter { it != log.id }
            }.distinct()
            // Restore every meal in the review first, so one meal can use stock released by another.
            (replacedIds + logs.map { it.id }).distinct().forEach { logId ->
                reverseMealConsumption(db, logId)
                MealExpenseLedger.delete(db, logId)
            }
            replacedIds.forEach { db.delete("meal_logs", "id = ?", arrayOf(it)) }
            logs.sortedWith(compareBy<MealLog>({ it.date }, { it.mealType.ordinal })).forEach { log ->
                val updated = db.update("meal_logs", mealLogValues(log), "id = ?", arrayOf(log.id))
                if (updated == 0) db.insertOrThrow("meal_logs", null, mealLogValues(log))
                applyMealConsumption(db, log)
                MealExpenseLedger.sync(db, log)
            }
            if (closure != null) {
                val values = dayClosureValues(closure)
                val updated = db.update("day_closures", values, "date = ?", arrayOf(closure.date.toEpochDay().toString()))
                if (updated == 0) db.insertOrThrow("day_closures", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun mealLogValues(log: MealLog): ContentValues = ContentValues().apply {
        put("id", log.id)
        put("date", log.date.toEpochDay())
        put("meal_type", log.mealType.name)
        put("template_id", log.templateId)
        put("name", log.name)
        put("cost", log.cost)
        put("actual_time", log.actualTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond())
        put("consumed_cost", log.consumedCost.coerceIn(0.0, log.cost.coerceAtLeast(0.0)))
        put("foods", log.foods)
        put("status", log.status.name)
        put("components_json", MealDataCodec.encodeComponents(log.components))
        put("cash_paid", log.cashPaid)
        put("meal_marker", log.marker.name)
    }

    private fun dayClosureValues(closure: DayClosure): ContentValues = ContentValues().apply {
        put("date", closure.date.toEpochDay())
        put("closed_at", closure.closedAt.atZone(java.time.ZoneId.systemDefault()).toEpochSecond())
        put("planned_cost", closure.plannedCost)
        put("consumed_cost", closure.consumedCost)
        put("leftover_cost", closure.leftoverCost)
        put("skipped_cost", closure.skippedCost)
    }

    private fun reverseMealConsumption(db: SQLiteDatabase, logId: String) {
        val allocations = db.query(
            "meal_consumptions",
            arrayOf("stock_item_id", "stock_quantity"),
            "log_id = ?",
            arrayOf(logId),
            null,
            null,
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        cursor.getString(0) to cursor.getDouble(1),
                    )
                }
            }
        }
        allocations.forEach { (stockId, quantity) ->
            if (stockId.isNullOrBlank() || quantity <= 0.0) return@forEach
            val history = db.query("stock", arrayOf("usage_history"), "id = ?", arrayOf(stockId), null, null, null).use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            }
            val retainedHistory = history.split(',').filterNot { entry ->
                entry.substringAfterLast(':', "").trim() == logId
            }.joinToString(",")
            db.execSQL(
                "UPDATE stock SET consumed_quantity = MAX(0, consumed_quantity - ?), usage_history = ? WHERE id = ?",
                arrayOf(quantity, retainedHistory, stockId),
            )
        }
        db.delete("meal_consumptions", "log_id = ?", arrayOf(logId))
    }

    private fun applyMealConsumption(db: SQLiteDatabase, log: MealLog) {
        if (!log.isConsumed || log.components.isEmpty()) return
        // When a meal is cooked with leftovers, ensure all raw ingredients are deducted from stock
        // (since they were cooked), while leftover portions are tracked as prepared food.
        log.components.forEach { component ->
            if (!component.useStock) return@forEach
            val requestedPortions = component.quantity
            if (requestedPortions <= 0.0) return@forEach
            val catalogId = component.catalogId
            val catalog = catalogId?.let { loadFoodCatalog(db, it) } ?: db.query(
                "food_catalog", null, "TRIM(name) = ? COLLATE NOCASE", arrayOf(component.name.trim()),
                null, null, null, "1",
            ).use { cursor -> if (cursor.moveToFirst()) readFoodCatalog(cursor) else null }
            if (catalog == null || !catalog.hasUsableConversion) {
                insertConsumption(
                    db = db,
                    logId = log.id,
                    component = component,
                    requested = requestedPortions,
                    consumed = requestedPortions,
                    stockQuantity = 0.0,
                    stockUnit = null,
                    stockId = null,
                )
                return@forEach
            }
            val basePerComponentUnit = Units.basePerUnit(component.unit, catalog)
            if (basePerComponentUnit == null || basePerComponentUnit <= 0.0) {
                // We cannot say how much of the stock one of these is, so nothing is deducted
                // and the reason is stored with the meal instead of guessing.
                insertConsumption(
                    db = db,
                    logId = log.id,
                    component = component,
                    requested = requestedPortions,
                    consumed = requestedPortions,
                    stockQuantity = 0.0,
                    stockUnit = catalog.baseUnit,
                    stockId = null,
                )
                return@forEach
            }
            var neededStock = requestedPortions * basePerComponentUnit
            val lots = db.query(
                "stock",
                arrayOf("id", "unit", "total_quantity", "consumed_quantity"),
                "catalog_id = ? AND consumed_quantity < total_quantity AND purchase_date <= ? " +
                    "AND conversion_known = 1 AND (expiry_date IS NULL OR expiry_date <= 0 OR expiry_date >= ?)",
                arrayOf(catalog.id, log.date.toEpochDay().toString(), log.date.toEpochDay().toString()),
                null,
                null,
                "COALESCE(expiry_date, purchase_date) ASC, rowid ASC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val unit = cursor.getString(1)
                        if (Units.sameUnit(unit, catalog.baseUnit)) {
                            add(
                                StockLot(
                                    id = cursor.getString(0),
                                    unit = unit,
                                    total = cursor.getDouble(2),
                                    consumed = cursor.getDouble(3),
                                ),
                            )
                        }
                    }
                }
            }
            lots.forEach { lot ->
                if (neededStock <= 0.000001) return@forEach
                val remaining = (lot.total - lot.consumed).coerceAtLeast(0.0)
                val take = minOf(remaining, neededStock)
                val currentHistory = db.query("stock", arrayOf("usage_history"), "id = ?", arrayOf(lot.id), null, null, null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else ""
                }
                val newEntry = "${log.date}:$take:${log.id}"
                val updatedHistory = if (currentHistory.isNullOrBlank()) newEntry else "$currentHistory,$newEntry"
                db.execSQL(
                    "UPDATE stock SET consumed_quantity = MIN(total_quantity, consumed_quantity + ?), usage_history = ? WHERE id = ?",
                    arrayOf(take, updatedHistory, lot.id),
                )
                insertConsumption(
                    db = db,
                    logId = log.id,
                    component = component,
                    requested = take / basePerComponentUnit,
                    consumed = take / basePerComponentUnit,
                    stockQuantity = take,
                    stockUnit = lot.unit,
                    stockId = lot.id,
                )
                neededStock -= take
            }
            if (neededStock > 0.000001) {
                insertConsumption(
                    db = db,
                    logId = log.id,
                    component = component,
                    requested = neededStock / basePerComponentUnit,
                    consumed = neededStock / basePerComponentUnit,
                    stockQuantity = 0.0,
                    stockUnit = catalog.baseUnit,
                    stockId = null,
                )
            }
        }
    }

    private fun loadFoodCatalog(db: SQLiteDatabase, id: String): FoodCatalogItem? {
        return db.query("food_catalog", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) readFoodCatalog(cursor) else null }
    }

    private fun insertConsumption(
        db: SQLiteDatabase,
        logId: String,
        component: MealComponent,
        requested: Double,
        consumed: Double,
        stockQuantity: Double,
        stockUnit: String?,
        stockId: String?,
    ) {
        db.insertOrThrow("meal_consumptions", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("log_id", logId)
            put("component_id", component.id)
            put("catalog_id", component.catalogId)
            put("name", component.name)
            put("portion_unit", component.unit)
            put("requested_quantity", requested.coerceAtLeast(0.0))
            put("consumed_quantity", consumed.coerceAtLeast(0.0))
            put("stock_quantity", stockQuantity.coerceAtLeast(0.0))
            put("stock_unit", stockUnit)
            put("stock_item_id", stockId)
            put("created_at", System.currentTimeMillis() / 1000)
        })
    }

    private data class StockLot(
        val id: String,
        val unit: String,
        val total: Double,
        val consumed: Double,
    )

    fun deleteMealLog(id: String) {
        val db = writableDatabase
        val date = db.query(
            "meal_logs",
            arrayOf("date"),
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) LocalDate.ofEpochDay(cursor.getLong(0)) else null
        }
        db.beginTransaction()
        try {
            reverseMealConsumption(db, id)
            // If the meal had an explicit cash expense recorded, preserve actual spending by decoupling the expense ID
            val expenseCursor = db.rawQuery(
                "SELECT amount, date, description, food_classification FROM expenses WHERE id = ?",
                arrayOf(MealSpending.expenseId(id)),
            )
            var preservedExpense: ContentValues? = null
            expenseCursor.use { cursor ->
                if (cursor.moveToFirst()) {
                    val amount = cursor.getDouble(0)
                    if (amount > 0.0) {
                        preservedExpense = ContentValues().apply {
                            put("id", UUID.randomUUID().toString())
                            put("category_id", "food")
                            put("amount", amount)
                            put("date", cursor.getLong(1))
                            put("description", cursor.getString(2) ?: "Meal purchase")
                            put("is_recurring", 0)
                            put("is_correction", 0)
                            put("is_budget_transfer", 0)
                            put("food_classification", cursor.getString(3) ?: "meal")
                        }
                    }
                }
            }
            MealExpenseLedger.delete(db, id)
            if (preservedExpense != null) {
                db.insert("expenses", null, preservedExpense)
            }
            db.delete("meal_logs", "id = ?", arrayOf(id))
            if (date != null) db.delete("day_closures", "date = ?", arrayOf(date.toEpochDay().toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun upsertShopping(item: ShoppingItem) {
        val values = ContentValues().apply {
            put("id", item.id)
            put("name", item.name)
            put("quantity", item.quantity)
            put("unit", item.unit)
            put("estimated_price", item.estimatedPrice)
            put("category", item.category.name)
            put("is_checked", if (item.isChecked) 1 else 0)
            put("created_date", item.createdDate.toEpochDay())
            put("source_item_id", item.sourceItemId)
            put("note", item.note)
            put("purchase_expense_id", item.purchaseExpenseId)
            put("purchase_stock_id", item.purchaseStockId)
            put("price_known", if (item.priceKnown) 1 else 0)
        }
        check(writableDatabase.insertWithOnConflict("shopping", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Could not save the shopping item."
        }
    }

    fun deleteShopping(id: String) {
        writableDatabase.delete("shopping", "id = ?", arrayOf(id))
    }

    fun upsertSpares(transaction: SparesTransaction) {
        val values = ContentValues().apply {
            put("id", transaction.id)
            put("date", transaction.date.toEpochDay())
            put("amount", transaction.amount)
            put("reason", transaction.reason)
            put("category", transaction.category.orEmpty())
        }
        check(writableDatabase.insertWithOnConflict("spares", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Could not save the savings transaction."
        }
    }

    fun deleteSpares(id: String) {
        writableDatabase.delete("spares", "id = ?", arrayOf(id))
    }

    fun hasSparesTransaction(id: String): Boolean = readableDatabase.query(
        "spares", arrayOf("id"), "id = ?", arrayOf(id), null, null, null, "1",
    ).use { it.moveToFirst() }

    fun ensureDayPlan(date: LocalDate, templates: List<MealTemplate>): Int {
        val db = writableDatabase
        var insertedCount = 0
        val existingTypes = db.query(
            "day_plans",
            arrayOf("meal_type"),
            "date = ?",
            arrayOf(date.toEpochDay().toString()),
            null,
            null,
            null,
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        templates.forEach { template ->
            if (template.mealType.name in existingTypes) return@forEach
            val values = ContentValues().apply {
                put("id", "$date-${template.mealType.name}")
                put("date", date.toEpochDay())
                put("meal_type", template.mealType.name)
                put("template_id", template.id)
                put("name", template.name)
                put("cost", template.cost)
                put("foods", template.notes)
                put("components_json", MealDataCodec.encodeComponents(template.components))
                put("is_cleared", 0)
                put("meal_marker", template.marker.name)
            }
            val rowId = db.insertWithOnConflict("day_plans", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (rowId != -1L) {
                insertedCount++
            }
        }
        return insertedCount
    }

    fun replaceOpenDayPlan(date: LocalDate, templates: List<MealTemplate>): Boolean {
        val db = writableDatabase
        var replaced = false
        db.beginTransaction()
        try {
            val isClosed = db.query(
                "day_closures",
                arrayOf("date"),
                "date = ?",
                arrayOf(date.toEpochDay().toString()),
                null,
                null,
                null,
                "1",
            ).use { it.moveToFirst() }
            val hasLogs = db.query(
                "meal_logs",
                arrayOf("id"),
                "date = ?",
                arrayOf(date.toEpochDay().toString()),
                null,
                null,
                null,
                "1",
            ).use { it.moveToFirst() }
            if (!isClosed && !hasLogs) {
                replaced = true
                db.delete("day_plans", "date = ?", arrayOf(date.toEpochDay().toString()))
                templates.forEach { template ->
                    val values = ContentValues().apply {
                        put("id", "$date-${template.mealType.name}")
                        put("date", date.toEpochDay())
                        put("meal_type", template.mealType.name)
                        put("template_id", template.id)
                        put("name", template.name)
                        put("cost", template.cost)
                        put("foods", template.notes)
                        put("components_json", MealDataCodec.encodeComponents(template.components))
                        put("is_cleared", 0)
                        put("meal_marker", template.marker.name)
                    }
                    db.insertWithOnConflict("day_plans", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return replaced
    }

    fun saveDayPlanMeals(date: LocalDate, meals: Map<MealType, MealTemplate>): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val isClosed = db.query(
                "day_closures",
                arrayOf("date"),
                "date = ?",
                arrayOf(date.toEpochDay().toString()),
                null,
                null,
                null,
                "1",
            ).use { it.moveToFirst() }
            if (isClosed) return false

            db.delete("day_plans", "date = ?", arrayOf(date.toEpochDay().toString()))
            MealType.entries.forEach { type ->
                val template = meals[type]
                val values = ContentValues().apply {
                    put("id", "$date-${type.name}")
                    put("date", date.toEpochDay())
                    put("meal_type", type.name)
                    put("template_id", template?.id)
                    put("name", template?.name ?: "Empty")
                    put("cost", template?.cost ?: 0.0)
                    put("foods", template?.notes ?: "")
                    put("components_json", MealDataCodec.encodeComponents(template?.components ?: emptyList()))
                    put("is_cleared", if (template == null) 1 else 0)
                    put("meal_marker", template?.marker?.name ?: MealMarker.UNMARKED.name)
                }
                db.insertWithOnConflict("day_plans", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun loadDayPlans(date: LocalDate): List<DailyMealPlan> {
        return readableDatabase.query(
            "day_plans", null, "date = ?", arrayOf(date.toEpochDay().toString()), null, null, "meal_type ASC",
        ).use { it.readRows(::readDayPlan) }
    }

    fun deleteDayClosure(date: LocalDate) {
        writableDatabase.delete("day_closures", "date = ?", arrayOf(date.toEpochDay().toString()))
    }

    fun autoClosePastDays(
        today: LocalDate,
        closedAt: LocalDateTime = LocalDateTime.now(),
        settings: BudgetSettings? = null,
    ) {
        val cutoffDate = if (settings != null && settings.dinnerEnd <= settings.dinnerStart && closedAt.toLocalTime() < settings.dinnerEnd) {
            today.minusDays(1)
        } else {
            today
        }
        val openDates = readableDatabase.rawQuery(
            "SELECT days.date FROM (SELECT date FROM day_plans UNION SELECT date FROM meal_logs) AS days " +
                "LEFT JOIN day_closures AS closure ON closure.date = days.date " +
                "WHERE days.date < ? AND closure.date IS NULL ORDER BY days.date ASC",
            arrayOf(cutoffDate.toEpochDay().toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(LocalDate.ofEpochDay(cursor.getLong(0)))
            }
        }
        closeDates(openDates, closedAt)
    }

    fun closeDay(date: LocalDate, closedAt: LocalDateTime = LocalDateTime.now()) {
        val alreadyClosed = readableDatabase.query(
            "day_closures",
            arrayOf("date"),
            "date = ?",
            arrayOf(date.toEpochDay().toString()),
            null,
            null,
            null,
            "1",
        ).use { it.moveToFirst() }
        if (!alreadyClosed) closeDates(listOf(date), closedAt)
    }

    private fun closeDates(dates: List<LocalDate>, closedAt: LocalDateTime) {
        dates.forEach { date ->
            val db = writableDatabase
            db.beginTransaction()
            try {
                val isClosed = db.query(
                    "day_closures", arrayOf("date"), "date = ?", arrayOf(date.toEpochDay().toString()),
                    null, null, null, "1",
                ).use { it.moveToFirst() }
                if (isClosed) return@forEach
                val plans = loadDayPlans(date)
                val existingLogs = loadMealLogs(date)
                if (plans.isEmpty() && existingLogs.isEmpty()) return@forEach
                val byType = existingLogs.associateBy { it.mealType }.toMutableMap()
                val changedLogs = existingLogs.filter { it.status == MealStatus.PLANNED }.map {
                    it.copy(status = MealStatus.UNRECORDED, consumedCost = 0.0)
                }.toMutableList()
                plans.filter { !it.isCleared && it.mealType !in byType }.forEach { plan ->
                    changedLogs += MealLog(
                        id = "closed-$date-${plan.mealType.name}",
                        date = date,
                        mealType = plan.mealType,
                        templateId = plan.templateId,
                        name = plan.name,
                        cost = plan.cost,
                        actualTime = date.atTime(23, 59),
                        consumedCost = 0.0,
                        foods = plan.foods,
                        status = MealStatus.UNRECORDED,
                        components = plan.components.ifEmpty { MealDataCodec.legacyComponents(plan.foods) },
                    )
                }
                changedLogs.forEach { byType[it.mealType] = it }
                val logs = byType.values
                // Closing a day must not reverse stock or re-price already recorded meals.
                upsertMealLogs(changedLogs, DayClosure(
                    date = date,
                    closedAt = closedAt,
                    plannedCost = logs.sumOf { it.cost },
                    consumedCost = logs.filter { it.isConsumed }.sumOf { it.consumedCost },
                    leftoverCost = logs.filter { it.isConsumed }.sumOf { it.leftoverCost },
                    skippedCost = logs.filter { it.isSkipped }.sumOf { it.cost },
                ))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun clearAllUserData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("day_closures", "day_plans", "meal_consumptions", "meal_logs", "shopping", "spares", "expenses", "stock", "food_catalog", "meal_templates", "categories").forEach {
                db.delete(it, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DATABASE_NAME = "budget_meals.db"
        const val DATABASE_VERSION = 16
    }
}
