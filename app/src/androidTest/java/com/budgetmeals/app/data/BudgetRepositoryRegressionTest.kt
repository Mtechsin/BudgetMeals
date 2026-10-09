package com.budgetmeals.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetRepositoryRegressionTest {
    @get:Rule
    internal val storage = BudgetStorageRule()

    @Test
    fun dayPlanAndMealConsumptionKeepComponentGramsAndReverseStockUse() {
        val repository = storage.openRepository()
        val database = storage.openDatabase()
        val catalog = FoodCatalogItem(
            id = "regression-spoon-catalog",
            name = "Regression paste",
            stockUnit = "jar (100g)",
            portionUnit = "spoon",
            portionsPerStockUnit = 10.0,
            defaultCostPerPortion = 4.0,
            notes = FoodMeasurementCodec.encodeNotes(
                userNotes = "",
                measurement = FoodMeasurementType.SpoonsToGrams(10.0),
            ),
        )
        val component = MealComponent(
            id = "regression-spoon-component",
            catalogId = catalog.id,
            name = catalog.name,
            quantity = 5.0,
            unit = "g",
            costPerUnit = 0.4,
        )
        val template = MealTemplate(
            id = "regression-grams-meal",
            name = "Grams test meal",
            mealType = MealType.LUNCH,
            cost = 10.0,
            components = listOf(component),
        )
        val date = storage.today.plusDays(2)

        repository.saveFoodCatalogItem(catalog)
        repository.saveStock(
            StockItem(
                id = "regression-spoon-stock",
                name = catalog.name,
                category = ItemCategory.FOOD_STAPLE,
                unit = "g",
                totalQuantity = 100.0,
                totalPrice = 40.0,
                catalogId = catalog.id,
            ),
            catalogItem = catalog,
        )
        assertEquals(1, repository.ensureDayPlan(date, listOf(template)))
        val planned = database.loadSnapshot(BudgetSettings()).mealPlan(date).single()
        assertEquals(component, planned.components.single())

        val log = MealLog(
            id = "regression-grams-log",
            date = date,
            mealType = MealType.LUNCH,
            templateId = template.id,
            name = template.name,
            cost = template.cost,
            components = listOf(component),
        )
        repository.recordMeal(log)

        assertEquals(5.0, requireNotNull(database.loadStockById("regression-spoon-stock")).consumedQuantity, 0.0001)
        assertEquals(5.0, database.loadSnapshot(BudgetSettings()).stock.single().consumedQuantity, 0.0001)
        val allocation = database.writableDatabase.rawQuery(
            "SELECT requested_quantity, consumed_quantity, portion_unit FROM meal_consumptions WHERE log_id = ?",
            arrayOf(log.id),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            Triple(cursor.getDouble(0), cursor.getDouble(1), cursor.getString(2))
        }
        assertEquals(5.0, allocation.first, 0.0001)
        assertEquals(5.0, allocation.second, 0.0001)
        assertEquals("g", allocation.third)

        repository.deleteMealLog(log.id)
        assertEquals(0.0, requireNotNull(database.loadStockById("regression-spoon-stock")).consumedQuantity, 0.0001)
        assertEquals(0.0, database.loadSnapshot(BudgetSettings()).stock.single().consumedQuantity, 0.0001)
        assertEquals(0, rowCount(database.writableDatabase, "meal_consumptions"))
    }

    @Test
    fun staleShoppingBuyAndUndoOnlyApplyOnce() {
        val repository = storage.openRepository()
        val staleItem = ShoppingItem(
            id = "regression-laundry-list-item",
            name = "Laundry powder",
            quantity = 2.0,
            unit = "pack",
            estimatedPrice = 45.0,
            category = ItemCategory.HOUSEHOLD,
        )
        repository.saveShopping(staleItem)

        assertTrue(repository.buyShoppingItem(staleItem, price = 45.0))
        assertFalse(repository.buyShoppingItem(staleItem, price = 45.0))
        val purchased = repository.loadSnapshot()
        assertEquals(1, purchased.stock.count { it.name == staleItem.name })
        assertEquals(1, purchased.expenses.count { it.description == "Bought ${staleItem.name}" })

        assertTrue(repository.undoShoppingPurchase(staleItem))
        assertFalse(repository.undoShoppingPurchase(staleItem))
        val undone = repository.loadSnapshot()
        assertTrue(undone.stock.none { it.name == staleItem.name })
        assertTrue(undone.expenses.none { it.description == "Bought ${staleItem.name}" })
        assertFalse(undone.shopping.single().isChecked)
    }

    @Test
    fun buyingDeletedShoppingItemDoesNotResurrectIt() {
        val repository = storage.openRepository()
        val staleItem = ShoppingItem(
            id = "regression-deleted-list-item",
            name = "Deleted detergent",
            quantity = 1.0,
            unit = "bottle",
            estimatedPrice = 20.0,
            category = ItemCategory.HOUSEHOLD,
        )
        repository.saveShopping(staleItem)
        repository.deleteShopping(staleItem.id)

        assertFalse(repository.buyShoppingItem(staleItem, price = 20.0))
        val snapshot = repository.loadSnapshot()
        assertTrue(snapshot.shopping.isEmpty())
        assertTrue(snapshot.stock.none { it.name == staleItem.name })
        assertTrue(snapshot.expenses.none { it.description == "Bought ${staleItem.name}" })
    }

    @Test
    fun repeatedDailySavingsSaveCreatesOneTransaction() {
        val repository = storage.openRepository()
        val staleSettings = BudgetSettings(
            monthlyFoodBudget = 500.0,
            dailyFoodBudget = 75.0,
            weeklyFoodLimit = 300.0,
            themeMode = AppThemeMode.LIGHT,
        )
        repository.saveSettings(staleSettings)
        repository.readSettings()

        val latestSettings = staleSettings.copy(
            monthlyFoodBudget = 600.0,
            dailyFoodBudget = 90.0,
            weeklyFoodLimit = 450.0,
            remindersEnabled = false,
            themeMode = AppThemeMode.DARK,
        )
        storage.openRepository().saveSettings(latestSettings)

        assertEquals(90.0, repository.saveTodayToSpares(), 0.0001)
        assertEquals(0.0, repository.saveTodayToSpares(), 0.0001)
        val saved = repository.loadSnapshot().spares.filter { it.category == "daily_savings" }
        assertEquals(1, saved.size)
        assertEquals(90.0, saved.single().amount, 0.0001)
        val persistedSettings = storage.openRepository().readSettings()
        assertEquals(600.0, persistedSettings.monthlyFoodBudget, 0.0001)
        assertEquals(90.0, persistedSettings.dailyFoodBudget, 0.0001)
        assertFalse(persistedSettings.remindersEnabled)
        assertEquals(AppThemeMode.DARK, persistedSettings.themeMode)
        assertEquals(storage.today, persistedSettings.lastSparesAutoSaveDate)
    }

    @Test
    fun newPurchaseSetsCatalogCostPerPortionFromUnitPrice() {
        val repository = storage.openRepository()
        val item = StockItem(
            id = "regression-cost-stock",
            name = "Regression oranges",
            category = ItemCategory.FOOD_FRESH,
            unit = "piece",
            totalQuantity = 4.0,
            totalPrice = 20.0,
            purchaseDate = storage.today,
        )

        repository.recordPurchase(item)

        assertEquals(5.0, requireNotNull(repository.findFoodCatalogItem(item.name)).defaultCostPerPortion, 0.0001)
    }

    @Test
    fun changingPurchasePriceUpdatesItsLinkedExpenseWhenBatchesHaveIdenticalPrices() {
        val repository = storage.openRepository()
        val first = StockItem(
            id = "first-soap",
            name = "Soap",
            category = ItemCategory.HOUSEHOLD,
            totalQuantity = 1.0,
            totalPrice = 20.0,
            purchaseDate = storage.today,
        )
        repository.recordPurchase(first, expenseId = "first-expense")
        repository.recordPurchase(first.copy(id = "second-soap"), expenseId = "second-expense")
        val savedFirst = repository.loadSnapshot().stock.single { it.id == first.id }

        repository.saveStock(savedFirst.copy(totalPrice = 25.0, linkedExpenseId = null))

        val expenses = repository.loadSnapshot().expenses.associateBy { it.id }
        assertEquals("first-expense", savedFirst.linkedExpenseId)
        assertEquals(
            "first-expense",
            repository.loadSnapshot().stock.single { it.id == first.id }.linkedExpenseId,
        )
        assertEquals(25.0, expenses.getValue("first-expense").amount, 0.0001)
        assertEquals(20.0, expenses.getValue("second-expense").amount, 0.0001)
    }

    @Test
    fun snapshotAndDayReviewUseTheRepositoryClock() {
        val repository = storage.openRepository()
        val template = MealTemplate(
            id = "clock-lunch",
            name = "Clock lunch",
            mealType = MealType.LUNCH,
            cost = 10.0,
        )
        repository.ensureDayPlan(storage.today, listOf(template))

        repository.reconcileMeals(
            storage.today,
            listOf(MealReconciliation(template, consumedCost = 0.0, status = MealStatus.SKIPPED)),
        )

        val snapshot = repository.loadSnapshot()
        assertEquals(storage.today, snapshot.today)
        assertEquals(storage.today.atTime(12, 0), snapshot.dayClosure(storage.today)?.closedAt)
        assertEquals(storage.today.atTime(12, 0), snapshot.todayLogs.getValue(MealType.LUNCH).actualTime)
    }

    private fun rowCount(db: android.database.sqlite.SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
