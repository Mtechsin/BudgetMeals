package com.budgetmeals.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetRepositoryRegressionTest {
    private val context = IsolatedBudgetTestContext(
        base = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
        prefix = "regression-${UUID.randomUUID()}-",
    )

    @After
    fun cleanUp() {
        openedDatabase?.close()
        context.clearPreferences("budget_meals")
        context.cleanDatabase(BudgetDao.DATABASE_NAME)
    }

    @Test
    fun dayPlanAndMealConsumptionKeepComponentGramsAndReverseStockUse() {
        val repository = BudgetRepository(context)
        val database = openDatabase()
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
        val date = LocalDate.now().plusDays(2)

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

        assertEquals(5.0, database.loadStockById("regression-spoon-stock")!!.consumedQuantity, 0.0001)
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
        assertEquals(0.0, database.loadStockById("regression-spoon-stock")!!.consumedQuantity, 0.0001)
        assertEquals(0.0, database.loadSnapshot(BudgetSettings()).stock.single().consumedQuantity, 0.0001)
        assertEquals(0, rowCount(database.writableDatabase, "meal_consumptions"))
    }

    @Test
    fun staleShoppingBuyAndUndoOnlyApplyOnce() {
        val repository = BudgetRepository(context)
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
        val repository = BudgetRepository(context)
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
        val repository = BudgetRepository(context)
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
        BudgetRepository(context).saveSettings(latestSettings)

        assertEquals(90.0, repository.saveTodayToSpares(), 0.0001)
        assertEquals(0.0, repository.saveTodayToSpares(), 0.0001)
        val saved = repository.loadSnapshot().spares.filter { it.category == "daily_savings" }
        assertEquals(1, saved.size)
        assertEquals(90.0, saved.single().amount, 0.0001)
        val persistedSettings = BudgetRepository(context).readSettings()
        assertEquals(600.0, persistedSettings.monthlyFoodBudget, 0.0001)
        assertEquals(90.0, persistedSettings.dailyFoodBudget, 0.0001)
        assertFalse(persistedSettings.remindersEnabled)
        assertEquals(AppThemeMode.DARK, persistedSettings.themeMode)
        assertEquals(LocalDate.now(), persistedSettings.lastSparesAutoSaveDate)
    }

    @Test
    fun newPurchaseSetsCatalogCostPerPortionFromUnitPrice() {
        val repository = BudgetRepository(context)
        val item = StockItem(
            id = "regression-cost-stock",
            name = "Regression oranges",
            category = ItemCategory.FOOD_FRESH,
            unit = "piece",
            totalQuantity = 4.0,
            totalPrice = 20.0,
            purchaseDate = LocalDate.now(),
        )

        repository.recordPurchase(item)

        assertEquals(5.0, repository.findFoodCatalogItem(item.name)!!.defaultCostPerPortion, 0.0001)
    }

    private var openedDatabase: BudgetDatabase? = null

    private fun openDatabase(): BudgetDatabase = BudgetDatabase(context).also { openedDatabase = it }

    private fun rowCount(db: android.database.sqlite.SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
