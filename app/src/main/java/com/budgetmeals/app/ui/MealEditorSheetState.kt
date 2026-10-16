package com.budgetmeals.app.ui

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodCatalogJsonCodec
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.MealDataCodec
import com.budgetmeals.app.data.MealMarker
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.ShoppingItem
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.data.StockUsageCodec
import java.time.DayOfWeek
import java.time.LocalDate

/** Keeps the original meal and editor route available when the Activity is recreated. */
internal val mealEditorSheetSaver = Saver<AppSheet?, Bundle>(
    save = { sheet ->
        if (sheet == null) return@Saver null
        Bundle().apply {
            when (sheet) {
                AppSheet.LogFood -> putString("editor", "log_food")
                is AppSheet.HelpMeChoose -> {
                    putString("editor", "help_me_choose")
                    sheet.mealType?.let { putString("meal_type", it.name) }
                }
                is AppSheet.MealAction -> {
                    putString("editor", "meal_action")
                    putString("meal_type", sheet.mealType.name)
                    putString("date", sheet.date.toString())
                }
                AppSheet.QuickAdd -> putString("editor", "quick_add")
                AppSheet.AddPurchase -> putString("editor", "add_purchase")
                AppSheet.AddExpense -> putString("editor", "add_expense")
                AppSheet.AddCategory -> putString("editor", "add_category")
                AppSheet.AddFoodItem -> putString("editor", "add_food_item")
                AppSheet.AddTemplate -> putString("editor", "add")
                AppSheet.AddShopping -> putString("editor", "add_shopping")
                AppSheet.AddSpares -> putString("editor", "add_spares")
                AppSheet.DayReview -> putString("editor", "day_review")
                AppSheet.BudgetCorrection -> putString("editor", "budget_correction")
                AppSheet.Settings -> putString("editor", "settings")
                is AppSheet.EditTemplate -> {
                    putString("editor", "edit")
                    writeTemplate(sheet.template, prefix = "")
                }
                is AppSheet.AssignDayMeal -> {
                    putString("editor", "assign_day_meal")
                    putString("date", sheet.date.toString())
                    putString("type", sheet.mealType.name)
                    putBoolean("has_template", sheet.currentTemplate != null)
                    sheet.currentTemplate?.let { writeTemplate(it, prefix = "template_") }
                }
                is AppSheet.EditExpense -> {
                    putString("editor", "edit_expense")
                    putString("expense_id", sheet.expense.id)
                    putString("category_id", sheet.expense.categoryId)
                    putDouble("amount", sheet.expense.amount)
                    putString("date", sheet.expense.date.toString())
                    putString("description", sheet.expense.description)
                    putBoolean("is_transfer", sheet.expense.isBudgetTransfer)
                    putBoolean("is_recurring", sheet.expense.isRecurring)
                    putString("recurring_frequency", sheet.expense.recurringFrequency)
                    putBoolean("is_correction", sheet.expense.isCorrection)
                    putString("recurring_schedule_id", sheet.expense.recurringScheduleId)
                }
                is AppSheet.EditStock -> {
                    putString("editor", "edit_stock")
                    writeStockItem(sheet.item)
                }
                is AppSheet.LogUsage -> {
                    putString("editor", "log_usage")
                    writeStockItem(sheet.item)
                }
                is AppSheet.EditShopping -> {
                    putString("editor", "edit_shopping")
                    writeShoppingItem(sheet.item)
                }
                is AppSheet.BuyShopping -> {
                    putString("editor", "buy_shopping")
                    writeShoppingItem(sheet.item)
                }
                is AppSheet.EditFoodItem -> {
                    putString("editor", "edit_food_item")
                    writeFoodItem(sheet.item)
                }
            }
        }
    },
    restore = { saved ->
        runCatching {
            when (saved.getString("editor")) {
                "log_food" -> AppSheet.LogFood
                "help_me_choose" -> {
                    val type = saved.getString("meal_type")?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                    AppSheet.HelpMeChoose(type)
                }
                "meal_action" -> {
                    val date = LocalDate.parse(requireNotNull(saved.getString("date")))
                    val type = MealType.valueOf(requireNotNull(saved.getString("meal_type")))
                    AppSheet.MealAction(type, date)
                }
                "quick_add" -> AppSheet.QuickAdd
                "add_purchase" -> AppSheet.AddPurchase
                "add_expense" -> AppSheet.AddExpense
                "add_category" -> AppSheet.AddCategory
                "add_food_item" -> AppSheet.AddFoodItem
                "add" -> AppSheet.AddTemplate
                "add_shopping" -> AppSheet.AddShopping
                "add_spares" -> AppSheet.AddSpares
                "day_review" -> AppSheet.DayReview
                "budget_correction" -> AppSheet.BudgetCorrection
                "settings" -> AppSheet.Settings
                "edit" -> AppSheet.EditTemplate(readTemplate(saved, prefix = ""))
                "assign_day_meal" -> {
                    val date = LocalDate.parse(requireNotNull(saved.getString("date")))
                    val type = MealType.valueOf(requireNotNull(saved.getString("type")))
                    val currentTemplate = if (saved.getBoolean("has_template")) {
                        readTemplate(saved, prefix = "template_")
                    } else {
                        null
                    }
                    AppSheet.AssignDayMeal(date = date, mealType = type, currentTemplate = currentTemplate)
                }
                "edit_expense" -> AppSheet.EditExpense(
                    Expense(
                        id = requireNotNull(saved.getString("expense_id")),
                        categoryId = requireNotNull(saved.getString("category_id")),
                        amount = saved.getDouble("amount"),
                        date = saved.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                        description = saved.getString("description").orEmpty(),
                        isRecurring = saved.getBoolean("is_recurring"),
                        recurringFrequency = saved.getString("recurring_frequency"),
                        isCorrection = saved.getBoolean("is_correction"),
                        recurringScheduleId = saved.getString("recurring_schedule_id"),
                        isBudgetTransfer = saved.getBoolean("is_transfer"),
                    ),
                )
                "edit_stock" -> AppSheet.EditStock(readStockItem(saved))
                "log_usage" -> AppSheet.LogUsage(readStockItem(saved))
                "edit_shopping" -> AppSheet.EditShopping(readShoppingItem(saved))
                "buy_shopping" -> AppSheet.BuyShopping(readShoppingItem(saved))
                "edit_food_item" -> AppSheet.EditFoodItem(readFoodItem(saved))
                else -> null
            }
        }.getOrNull()
    },
)

private fun Bundle.writeTemplate(template: MealTemplate, prefix: String = "") {
    putString("${prefix}id", template.id)
    putString("${prefix}name", template.name)
    putString("${prefix}type", template.mealType.name)
    putDouble("${prefix}cost", template.cost)
    putBoolean("${prefix}recurring", template.isRecurring)
    putString("${prefix}day", template.dayOfWeek?.name)
    putString("${prefix}notes", template.notes)
    putBoolean("${prefix}custom", template.isCustom)
    putString("${prefix}components", MealDataCodec.encodeComponents(template.components))
    putString("${prefix}marker", template.marker.name)
}

private fun readTemplate(bundle: Bundle, prefix: String = ""): MealTemplate {
    return MealTemplate(
        id = requireNotNull(bundle.getString("${prefix}id")),
        name = bundle.getString("${prefix}name").orEmpty(),
        mealType = MealType.valueOf(requireNotNull(bundle.getString("${prefix}type"))),
        cost = bundle.getDouble("${prefix}cost"),
        isRecurring = bundle.getBoolean("${prefix}recurring"),
        dayOfWeek = bundle.getString("${prefix}day")?.let(DayOfWeek::valueOf),
        notes = bundle.getString("${prefix}notes").orEmpty(),
        isCustom = bundle.getBoolean("${prefix}custom"),
        components = MealDataCodec.decodeComponents(bundle.getString("${prefix}components").orEmpty()),
        marker = bundle.getString("${prefix}marker")?.let { runCatching { MealMarker.valueOf(it) }.getOrNull() } ?: MealMarker.UNMARKED,
    )
}

private fun Bundle.writeStockItem(item: StockItem) {
    putString("id", item.id)
    putString("name", item.name)
    putString("category", item.category.name)
    putString("unit", item.unit)
    putDouble("totalQuantity", item.totalQuantity)
    putDouble("totalPrice", item.totalPrice)
    putString("purchaseDate", item.purchaseDate.toString())
    putString("expiryDate", item.expiryDate?.toString())
    putString("batchType", item.batchType.name)
    putDouble("usagePerDay", item.estimatedUsagePerDay)
    putDouble("consumedQuantity", item.consumedQuantity)
    putString("usageHistory", StockUsageCodec.encode(item))
    putString("notes", item.notes)
    putString("catalogId", item.catalogId)
    putString("linkedExpenseId", item.linkedExpenseId)
    putString("packageLabel", item.packageLabel)
    putDouble("packageSize", item.packageSize)
    putBoolean("conversionKnown", item.conversionKnown)
}

private fun readStockItem(bundle: Bundle): StockItem {
    val purchaseDate = bundle.getString("purchaseDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    val history = bundle.getString("usageHistory").orEmpty()
    return StockItem(
        id = requireNotNull(bundle.getString("id")),
        name = bundle.getString("name").orEmpty(),
        category = bundle.getString("category")?.let { runCatching { ItemCategory.valueOf(it) }.getOrNull() } ?: ItemCategory.FOOD_STAPLE,
        unit = bundle.getString("unit") ?: "piece",
        totalQuantity = bundle.getDouble("totalQuantity"),
        totalPrice = bundle.getDouble("totalPrice"),
        purchaseDate = purchaseDate,
        expiryDate = bundle.getString("expiryDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        batchType = bundle.getString("batchType")?.let { runCatching { BatchType.valueOf(it) }.getOrNull() } ?: BatchType.MONTHLY,
        estimatedUsagePerDay = bundle.getDouble("usagePerDay"),
        consumedQuantity = bundle.getDouble("consumedQuantity"),
        usageHistory = StockUsageCodec.decodeAmounts(history),
        datedUsageHistory = StockUsageCodec.decode(history, purchaseDate),
        notes = bundle.getString("notes").orEmpty(),
        catalogId = bundle.getString("catalogId"),
        linkedExpenseId = bundle.getString("linkedExpenseId"),
        packageLabel = bundle.getString("packageLabel").orEmpty(),
        packageSize = bundle.getDouble("packageSize"),
        conversionKnown = if (bundle.containsKey("conversionKnown")) bundle.getBoolean("conversionKnown") else true,
    )
}

private fun Bundle.writeShoppingItem(item: ShoppingItem) {
    putString("id", item.id)
    putString("name", item.name)
    putString("unit", item.unit)
    putDouble("estimatedPrice", item.estimatedPrice)
    putDouble("estimatedQuantity", item.quantity)
    putString("category", item.category.name)
    putString("notes", item.note)
    putBoolean("isChecked", item.isChecked)
    putString("createdDate", item.createdDate.toString())
    putString("sourceItemId", item.sourceItemId)
    putString("purchaseExpenseId", item.purchaseExpenseId)
    putString("purchaseStockId", item.purchaseStockId)
    putBoolean("priceKnown", item.priceKnown)
}

private fun readShoppingItem(bundle: Bundle): ShoppingItem {
    return ShoppingItem(
        id = requireNotNull(bundle.getString("id")),
        name = bundle.getString("name").orEmpty(),
        unit = bundle.getString("unit") ?: "piece",
        estimatedPrice = bundle.getDouble("estimatedPrice"),
        quantity = bundle.getDouble("estimatedQuantity", 1.0),
        category = bundle.getString("category")?.let { runCatching { ItemCategory.valueOf(it) }.getOrNull() } ?: ItemCategory.FOOD_STAPLE,
        note = bundle.getString("notes").orEmpty(),
        isChecked = bundle.getBoolean("isChecked"),
        createdDate = bundle.getString("createdDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
        sourceItemId = bundle.getString("sourceItemId"),
        purchaseExpenseId = bundle.getString("purchaseExpenseId"),
        purchaseStockId = bundle.getString("purchaseStockId"),
        priceKnown = if (bundle.containsKey("priceKnown")) bundle.getBoolean("priceKnown") else true,
    )
}

private fun Bundle.writeFoodItem(item: FoodCatalogItem) {
    putString("id", item.id)
    putString("name", item.name)
    putString("category", item.category.name)
    putString("stockUnit", item.stockUnit)
    putString("portionUnit", item.portionUnit)
    putDouble("portionsPerStockUnit", item.portionsPerStockUnit)
    putDouble("defaultCostPerPortion", item.defaultCostPerPortion)
    putString("notes", item.notes)
    if (item.purchasePrice != null) putDouble("purchasePrice", item.purchasePrice)
    if (item.purchaseQuantity != null) putDouble("purchaseQuantity", item.purchaseQuantity)
    putString("purchaseUnit", item.purchaseUnit)
    putString("mealUsage", item.mealUsage)
    putString("priceOptions", FoodCatalogJsonCodec.encodePriceOptions(item.priceOptions))
    putBoolean("priceKnown", item.priceKnown)
    putBoolean("conversionKnown", item.conversionKnown)
}

private fun readFoodItem(bundle: Bundle): FoodCatalogItem {
    return FoodCatalogItem(
        id = requireNotNull(bundle.getString("id")),
        name = bundle.getString("name").orEmpty(),
        category = bundle.getString("category")?.let { runCatching { ItemCategory.valueOf(it) }.getOrNull() } ?: ItemCategory.FOOD_FRESH,
        stockUnit = bundle.getString("stockUnit") ?: "piece",
        portionUnit = bundle.getString("portionUnit") ?: "piece",
        portionsPerStockUnit = bundle.getDouble("portionsPerStockUnit", 1.0),
        defaultCostPerPortion = bundle.getDouble("defaultCostPerPortion", 0.0),
        notes = bundle.getString("notes").orEmpty(),
        purchasePrice = if (bundle.containsKey("purchasePrice")) bundle.getDouble("purchasePrice") else null,
        purchaseQuantity = if (bundle.containsKey("purchaseQuantity")) bundle.getDouble("purchaseQuantity") else null,
        purchaseUnit = bundle.getString("purchaseUnit"),
        mealUsage = bundle.getString("mealUsage").orEmpty(),
        priceOptions = FoodCatalogJsonCodec.decodePriceOptions(bundle.getString("priceOptions").orEmpty()),
        priceKnown = bundle.getBoolean("priceKnown", true),
        conversionKnown = bundle.getBoolean("conversionKnown", true),
    )
}
