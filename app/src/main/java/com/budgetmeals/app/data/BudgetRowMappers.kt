package com.budgetmeals.app.data

import android.database.Cursor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

internal fun Cursor.optionalDouble(column: String): Double? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getDouble(index)
}

internal fun Cursor.optionalString(column: String): String? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getString(index)
}

internal fun Cursor.optionalBoolean(column: String, default: Boolean): Boolean {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) default else getInt(index) == 1
}

internal inline fun <T> Cursor.readRows(readRow: (Cursor) -> T): List<T> = buildList {
    while (moveToNext()) add(readRow(this@readRows))
}

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
private fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))
private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
private fun Cursor.boolean(column: String): Boolean = getInt(getColumnIndexOrThrow(column)) == 1
private fun Cursor.date(column: String): LocalDate = LocalDate.ofEpochDay(long(column))
private fun Cursor.dateTime(column: String) =
    Instant.ofEpochSecond(long(column)).atZone(ZoneId.systemDefault()).toLocalDateTime()

/** Row readers share the same mapping for snapshots and individual lookups. */
internal fun readCategory(cursor: Cursor): ExpenseCategory = with(cursor) {
    ExpenseCategory(
        id = string("id"),
        name = string("name"),
        icon = string("icon"),
        monthlyBudget = double("monthly_budget"),
        isFood = boolean("is_food"),
        isCustom = boolean("is_custom"),
        sortOrder = getInt(getColumnIndexOrThrow("sort_order")),
    )
}

internal fun readFoodCatalog(cursor: Cursor): FoodCatalogItem = with(cursor) {
    FoodCatalogItem(
        id = string("id"),
        name = string("name"),
        category = enumValueOrDefault(string("category"), ItemCategory.FOOD_FRESH),
        stockUnit = string("stock_unit"),
        portionUnit = string("portion_unit"),
        portionsPerStockUnit = double("portions_per_stock_unit").coerceAtLeast(0.0001),
        defaultCostPerPortion = double("default_cost_per_portion").coerceAtLeast(0.0),
        notes = string("notes"),
        purchasePrice = optionalDouble("purchase_price"),
        purchaseQuantity = optionalDouble("purchase_quantity"),
        purchaseUnit = optionalString("purchase_unit"),
        mealUsage = optionalString("meal_usage").orEmpty(),
        priceOptions = FoodCatalogJsonCodec.decodePriceOptions(optionalString("price_options_json").orEmpty()),
        priceKnown = optionalBoolean("price_known", true),
        conversionKnown = optionalBoolean("conversion_known", true),
    )
}

internal fun readStock(cursor: Cursor): StockItem = with(cursor) {
    val purchaseDate = date("purchase_date")
    val history = string("usage_history")
    val expiryIndex = getColumnIndexOrThrow("expiry_date")
    StockItem(
        id = string("id"),
        name = string("name"),
        category = enumValueOrDefault(string("category"), ItemCategory.OTHER),
        unit = string("unit"),
        totalQuantity = double("total_quantity"),
        totalPrice = double("total_price"),
        purchaseDate = purchaseDate,
        expiryDate = if (isNull(expiryIndex)) null else LocalDate.ofEpochDay(getLong(expiryIndex)),
        batchType = enumValueOrDefault(string("batch_type"), BatchType.CUSTOM),
        estimatedUsagePerDay = double("usage_per_day"),
        consumedQuantity = double("consumed_quantity"),
        usageHistory = StockUsageCodec.decodeAmounts(history),
        datedUsageHistory = StockUsageCodec.decode(history, purchaseDate),
        notes = string("notes"),
        catalogId = optionalString("catalog_id"),
        linkedExpenseId = optionalString("linked_expense_id"),
        packageLabel = optionalString("package_label").orEmpty(),
        packageSize = double("package_size"),
        conversionKnown = boolean("conversion_known"),
    )
}

internal fun readExpense(cursor: Cursor): Expense = with(cursor) {
    Expense(
        id = string("id"),
        categoryId = string("category_id"),
        amount = double("amount"),
        date = date("date"),
        description = string("description"),
        isRecurring = boolean("is_recurring"),
        recurringFrequency = optionalString("recurring_frequency"),
        isCorrection = boolean("is_correction"),
        recurringScheduleId = optionalString("recurring_schedule_id"),
        isBudgetTransfer = optionalBoolean("is_budget_transfer", false),
    )
}

internal fun readTemplate(cursor: Cursor): MealTemplate = with(cursor) {
    MealTemplate(
        id = string("id"),
        name = string("name"),
        mealType = enumValueOrDefault(string("meal_type"), MealType.SNACK),
        cost = double("cost"),
        isRecurring = boolean("is_recurring"),
        dayOfWeek = optionalString("day_of_week")?.let { value ->
            DayOfWeek.entries.firstOrNull { it.name == value }
        },
        notes = string("notes"),
        isCustom = boolean("is_custom"),
        components = MealDataCodec.decodeComponents(string("components_json")),
    )
}

internal fun readMealLog(cursor: Cursor): MealLog = with(cursor) {
    val cost = double("cost")
    MealLog(
        id = string("id"),
        date = date("date"),
        mealType = enumValueOrDefault(string("meal_type"), MealType.SNACK),
        templateId = optionalString("template_id"),
        name = string("name"),
        cost = cost,
        actualTime = dateTime("actual_time"),
        consumedCost = double("consumed_cost").coerceIn(0.0, cost.coerceAtLeast(0.0)),
        foods = string("foods"),
        status = enumValueOrDefault(string("status"), MealStatus.EATEN),
        components = MealDataCodec.decodeComponents(string("components_json")),
    )
}

internal fun readShopping(cursor: Cursor): ShoppingItem = with(cursor) {
    ShoppingItem(
        id = string("id"),
        name = string("name"),
        quantity = double("quantity"),
        unit = string("unit"),
        estimatedPrice = double("estimated_price"),
        category = enumValueOrDefault(string("category"), ItemCategory.OTHER),
        isChecked = boolean("is_checked"),
        createdDate = date("created_date"),
        sourceItemId = optionalString("source_item_id"),
        note = string("note"),
        purchaseExpenseId = optionalString("purchase_expense_id"),
        purchaseStockId = optionalString("purchase_stock_id"),
        priceKnown = optionalBoolean("price_known", true),
    )
}

internal fun readSpares(cursor: Cursor): SparesTransaction = with(cursor) {
    SparesTransaction(
        id = string("id"),
        date = date("date"),
        amount = double("amount"),
        reason = string("reason"),
        category = optionalString("category"),
    )
}

internal fun readDayPlan(cursor: Cursor): DailyMealPlan = with(cursor) {
    DailyMealPlan(
        id = string("id"),
        date = date("date"),
        mealType = enumValueOrDefault(string("meal_type"), MealType.SNACK),
        templateId = optionalString("template_id"),
        name = string("name"),
        cost = double("cost"),
        foods = string("foods"),
        components = MealDataCodec.decodeComponents(string("components_json")),
        isCleared = optionalBoolean("is_cleared", false),
    )
}

internal fun readDayClosure(cursor: Cursor): DayClosure = with(cursor) {
    DayClosure(
        date = date("date"),
        closedAt = dateTime("closed_at"),
        plannedCost = double("planned_cost"),
        consumedCost = double("consumed_cost"),
        leftoverCost = double("leftover_cost"),
        skippedCost = double("skipped_cost"),
    )
}
