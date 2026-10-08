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
        writableDatabase.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
        writableDatabase.insertWithOnConflict("food_catalog", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteFoodCatalogItem(id: String) {
        writableDatabase.delete("food_catalog", "id = ?", arrayOf(id))
        writableDatabase.execSQL("UPDATE stock SET catalog_id = NULL WHERE catalog_id = ?", arrayOf(id))
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
            put("total_quantity", item.totalQuantity.coerceAtLeast(item.consumedQuantity))
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
            writableDatabase.insert("stock", null, values)
        }
    }

    fun deleteStock(id: String) {
        writableDatabase.delete("stock", "id = ?", arrayOf(id))
    }

    fun clearAllStock() {
        writableDatabase.delete("stock", null, null)
    }

    fun loadSamplePantry(today: LocalDate = LocalDate.now()) {
        val sampleStock = listOf(
            StockItem("jam", "Jam", ItemCategory.FOOD_STAPLE, "g", 650.0, 55.0, today, null, BatchType.MONTHLY, 30.0, 0.0, notes = "Monthly staple", catalogId = "catalog_jam", packageLabel = "jar (380g)", packageSize = 380.0),
            StockItem("oil", "Oil", ItemCategory.FOOD_STAPLE, "ml", 1_000.0, 120.0, today, null, BatchType.MONTHLY, 15.0, catalogId = "catalog_oil", packageLabel = "bottle (700ml)", packageSize = 700.0),
            StockItem("tea", "Tea", ItemCategory.FOOD_STAPLE, "g", 200.0, 50.0, today, null, BatchType.MONTHLY, 5.0, catalogId = "catalog_tea", packageLabel = "pack (100g)", packageSize = 100.0),
            StockItem("sugar", "Sugar", ItemCategory.FOOD_STAPLE, "g", 1_000.0, 40.0, today, null, BatchType.MONTHLY, 20.0, catalogId = "catalog_sugar"),
            StockItem("eggs", "Eggs", ItemCategory.FOOD_FRESH, "egg", 12.0, 60.0, today, null, BatchType.WEEKLY, 1.5, catalogId = "catalog_eggs", packageLabel = "carton (30)", packageSize = 30.0),
            StockItem("cheese", "Cheese", ItemCategory.FOOD_FRESH, "g", 250.0, 25.0, today, null, BatchType.WEEKLY, 25.0, catalogId = "catalog_cheese", packageLabel = "tub (500g)", packageSize = 500.0),
            StockItem("yogurt", "Yogurt", ItemCategory.FOOD_FRESH, "cup", 5.0, 25.0, today, null, BatchType.WEEKLY, 0.8, catalogId = "catalog_yogurt", packageLabel = "pack (6)", packageSize = 6.0),
            StockItem("tomatoes", "Tomatoes", ItemCategory.FOOD_FRESH, "g", 1_000.0, 30.0, today, null, BatchType.WEEKLY, 150.0, catalogId = "catalog_tomatoes"),
            StockItem("cucumbers", "Cucumbers", ItemCategory.FOOD_FRESH, "g", 1_000.0, 25.0, today, null, BatchType.WEEKLY, 100.0, catalogId = "catalog_cucumbers"),
            StockItem("fruit", "Fruit", ItemCategory.FOOD_FRESH, "g", 1_000.0, 35.0, today, null, BatchType.WEEKLY, 200.0, catalogId = "catalog_fruit"),
        )
        sampleStock.forEach { sample ->
            val alreadyPresent = readableDatabase.query(
                "stock", arrayOf("id"), "id = ? OR catalog_id = ?",
                arrayOf(sample.id, sample.catalogId), null, null, null, "1",
            ).use { it.moveToFirst() }
            if (!alreadyPresent) upsertStock(sample)
        }
    }

    fun addUsage(id: String, amount: Double, date: LocalDate = LocalDate.now()): Boolean {
        val current = loadStockById(id) ?: return false
        val safeAmount = amount.coerceIn(0.0, current.remainingQuantity)
        val updated = current.copy(
            consumedQuantity = (current.consumedQuantity + safeAmount).coerceAtMost(current.totalQuantity),
            usageHistory = current.usageHistory + safeAmount,
            datedUsageHistory = current.datedUsageHistory + DatedUsage(date, safeAmount),
        )
        upsertStock(updated)
        return true
    }

    fun loadStockById(id: String): StockItem? {
        return readableDatabase.query("stock", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) readStock(cursor) else null }
    }

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
        }
        writableDatabase.insertWithOnConflict("expenses", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteExpense(id: String) {
        writableDatabase.delete("expenses", "id = ?", arrayOf(id))
    }

    fun findExpenseById(id: String): Expense? {
        return readableDatabase.query("expenses", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) readExpense(cursor) else null }
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
        }
        writableDatabase.insertWithOnConflict("meal_templates", null, values, SQLiteDatabase.CONFLICT_REPLACE)
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

    fun saveMealLogsWithComponents(logs: List<MealLog>, closure: DayClosure? = null) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            logs.forEach { log ->
                val previousIds = db.rawQuery(
                    "SELECT id FROM meal_logs WHERE date = ? AND meal_type = ?",
                    arrayOf(log.date.toEpochDay().toString(), log.mealType.name),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
                previousIds.filter { it != log.id }.forEach { previousId ->
                    reverseMealConsumption(db, previousId)
                    db.delete("meal_logs", "id = ?", arrayOf(previousId))
                }
                val updated = db.update("meal_logs", mealLogValues(log), "id = ?", arrayOf(log.id))
                if (updated == 0) db.insert("meal_logs", null, mealLogValues(log))
                reverseMealConsumption(db, log.id)
                applyMealConsumption(db, log)
            }
            if (closure != null) {
                val values = dayClosureValues(closure)
                val updated = db.update("day_closures", values, "date = ?", arrayOf(closure.date.toEpochDay().toString()))
                if (updated == 0) db.insert("day_closures", null, values)
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
            val catalog = catalogId?.let { loadFoodCatalog(db, it) }
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
                    "AND (expiry_date IS NULL OR expiry_date <= 0 OR expiry_date >= ?)",
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
        db.insert("meal_consumptions", null, ContentValues().apply {
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
        writableDatabase.insertWithOnConflict("shopping", null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
            put("category", transaction.category)
        }
        writableDatabase.insertWithOnConflict("spares", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteSpares(id: String) {
        writableDatabase.delete("spares", "id = ?", arrayOf(id))
    }

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

    fun autoClosePastDays(today: LocalDate, closedAt: LocalDateTime = LocalDateTime.now()) {
        val openDates = readableDatabase.rawQuery(
            "SELECT DISTINCT plan.date FROM day_plans AS plan " +
                "LEFT JOIN day_closures AS closure ON closure.date = plan.date " +
                "WHERE plan.date < ? AND closure.date IS NULL ORDER BY plan.date ASC",
            arrayOf(today.toEpochDay().toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(LocalDate.ofEpochDay(cursor.getLong(0)))
            }
        }
        closeDates(openDates, closedAt)
        writableDatabase.delete("day_plans", "date < ?", arrayOf(today.minusDays(90).toEpochDay().toString()))
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
            val plans = loadDayPlans(date)
            if (plans.isEmpty()) return@forEach
            val logs = plans.filter { !it.isCleared }.map { plan ->
                val existing = loadMealLog(date, plan.mealType)
                existing?.let {
                    if (it.status == MealStatus.PLANNED) it.copy(status = MealStatus.UNRECORDED, consumedCost = 0.0) else it
                } ?: MealLog(
                    id = "closed-$date-${plan.mealType.name}",
                    date = date,
                    mealType = plan.mealType,
                    templateId = plan.templateId,
                    name = plan.name,
                    cost = plan.cost,
                    actualTime = date.plusDays(1).atTime(23, 59),
                    consumedCost = 0.0,
                    foods = plan.foods,
                    status = MealStatus.UNRECORDED,
                    components = plan.components.ifEmpty { MealDataCodec.legacyComponents(plan.foods) },
                )
            }
            val plannedCost = plans.filter { !it.isCleared }.sumOf { it.cost }
            val consumedCost = logs.filter { it.isConsumed }.sumOf { it.consumedCost }
            val leftoverCost = logs.filter { it.isConsumed }.sumOf { it.leftoverCost }
            val skippedCost = logs.filter { it.isSkipped }.sumOf { it.cost }
            upsertMealLogs(
                logs,
                DayClosure(
                    date = date,
                    closedAt = closedAt,
                    plannedCost = plannedCost,
                    consumedCost = consumedCost,
                    leftoverCost = leftoverCost,
                    skippedCost = skippedCost,
                ),
            )
        }
    }

    fun clearAllUserData() {
        val db = writableDatabase
        listOf("day_closures", "day_plans", "meal_consumptions", "meal_logs", "shopping", "spares", "expenses", "stock", "food_catalog", "meal_templates", "categories").forEach {
            db.delete(it, null, null)
        }
    }

    companion object {
        const val DATABASE_NAME = "budget_meals.db"
        const val DATABASE_VERSION = 12
    }
}
