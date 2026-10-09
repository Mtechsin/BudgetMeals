package com.budgetmeals.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealSpendingRegressionTest {
    @get:Rule
    internal val storage = BudgetStorageRule()

    @Test
    fun mealWithoutStockCreatesFoodExpenseIncludedInDailyAndMonthlyTotals() {
        val repository = storage.openRepository()
        val meal = mealLog("no-stock", cost = 42.0)
        repository.recordMeal(meal)

        val snapshot = repository.loadSnapshot()
        val expense = snapshot.expenses.single { it.id == MealSpending.expenseId(meal.id) }
        assertEquals("food", expense.categoryId)
        assertEquals(storage.today, expense.date)
        assertEquals(42.0, expense.amount, 0.0001)
        assertEquals(42.0, snapshot.todayFoodSpent, 0.0001)
        assertEquals(42.0, snapshot.foodSpentThisMonth, 0.0001)
    }

    @Test
    fun fullyStockedMealAddsNoMealExpenseAndKeepsPurchaseExpense() {
        val repository = storage.openRepository()
        val (catalog, component) = ingredient("rice", quantity = 4.0, unitCost = 5.0)
        repository.recordPurchase(
            StockItem(
                id = "meal-spend-rice-lot",
                name = catalog.name,
                category = catalog.category,
                unit = catalog.baseUnit,
                totalQuantity = 4.0,
                totalPrice = 20.0,
                purchaseDate = storage.today,
            ),
            catalogItem = catalog,
        )
        val meal = mealLog("fully-stocked", cost = 20.0, components = listOf(component))

        repository.recordMeal(meal)

        val snapshot = repository.loadSnapshot()
        assertTrue(snapshot.expenses.any { it.id == "stock-meal-spend-rice-lot" && it.amount == 20.0 })
        assertFalse(snapshot.expenses.any { it.id == MealSpending.expenseId(meal.id) })
        assertEquals(4.0, snapshot.stock.single { it.id == "meal-spend-rice-lot" }.consumedQuantity, 0.0001)
    }

    @Test
    fun partialStockUsesComponentPricesToChargeOnlyUncoveredRecipeCost() {
        val repository = storage.openRepository()
        val (rice, riceComponent) = ingredient("partial-rice", quantity = 10.0, unitCost = 2.0)
        val (beans, beansComponent) = ingredient("partial-beans", quantity = 5.0, unitCost = 3.0)
        repository.saveStock(stock("partial-rice-stock", rice, quantity = 4.0, price = 8.0))
        repository.saveStock(stock("partial-beans-stock", beans, quantity = 5.0, price = 15.0))
        val meal = mealLog("partial-meal", cost = 90.0, components = listOf(riceComponent, beansComponent))

        repository.recordMeal(meal)

        val snapshot = repository.loadSnapshot()
        // Recipe estimates are 20 + 15; the stock covers 8 of the first 20, leaving 27/35 of cash cost.
        assertEquals(90.0 * 12.0 / 35.0, snapshot.expenses.single { it.id == MealSpending.expenseId(meal.id) }.amount, 0.0001)
        assertEquals(4.0, snapshot.stock.single { it.id == "partial-rice-stock" }.consumedQuantity, 0.0001)
        assertEquals(5.0, snapshot.stock.single { it.id == "partial-beans-stock" }.consumedQuantity, 0.0001)
    }

    @Test
    fun editingMealAtSameDateAndTypeReusesOneGeneratedExpenseAndReallocatesStock() {
        val repository = storage.openRepository()
        val (catalog, component) = ingredient("edit-oats", quantity = 5.0, unitCost = 2.0)
        repository.saveStock(stock("edit-oats-stock", catalog, quantity = 5.0, price = 10.0))
        val first = mealLog("original-meal-id", cost = 10.0, components = listOf(component))
        repository.recordMeal(first)

        repository.recordMeal(
            mealLog("new-incoming-id", cost = 20.0, components = emptyList()).copy(
                components = listOf(component.copy(id = "edited-oats-component", quantity = 10.0)),
            ),
        )
        repository.recordMeal(
            mealLog("new-incoming-id", cost = 20.0, components = emptyList()).copy(
                components = listOf(component.copy(id = "edited-oats-component", quantity = 10.0)),
            ),
        )

        val snapshot = repository.loadSnapshot()
        assertEquals(1, snapshot.mealLogs.count { it.date == storage.today && it.mealType == MealType.LUNCH })
        assertEquals("original-meal-id", snapshot.mealLogs.single().id)
        assertEquals(1, snapshot.expenses.count { it.isMealExpense })
        assertEquals(10.0, snapshot.expenses.single { it.isMealExpense }.amount, 0.0001)
        assertEquals(5.0, snapshot.stock.single { it.id == "edit-oats-stock" }.consumedQuantity, 0.0001)
    }

    @Test
    fun deletingOrChangingMealToSkippedOrUnrecordedRemovesCashAndRestoresStock() {
        val repository = storage.openRepository()
        val (catalog, component) = ingredient("undo-pasta", quantity = 3.0, unitCost = 4.0)
        repository.saveStock(stock("undo-pasta-stock", catalog, quantity = 1.0, price = 4.0))
        val template = MealTemplate(id = "undo-template", name = "Pasta", mealType = MealType.LUNCH, cost = 12.0, components = listOf(component))
        repository.recordMeal(mealLog("undo-log", cost = 12.0, components = listOf(component)))
        assertEquals(1.0, requireNotNull(repository.loadSnapshot().stock.singleOrNull { it.id == "undo-pasta-stock" }).consumedQuantity, 0.0001)
        assertEquals(8.0, repository.loadSnapshot().expenses.single { it.isMealExpense }.amount, 0.0001)

        repository.reconcileMeals(storage.today, listOf(MealReconciliation(template, 0.0, MealStatus.SKIPPED)))
        assertEquals(0.0, repository.loadSnapshot().stock.single { it.id == "undo-pasta-stock" }.consumedQuantity, 0.0001)
        assertFalse(repository.loadSnapshot().expenses.any { it.isMealExpense })

        repository.reconcileMeals(storage.today, listOf(MealReconciliation(template, 0.0, MealStatus.UNRECORDED)))
        assertFalse(repository.loadSnapshot().expenses.any { it.isMealExpense })
        repository.recordMeal(mealLog("replacement-id", cost = 12.0, components = listOf(component)))
        val savedId = repository.loadSnapshot().mealLogs.single().id
        repository.deleteMealLog(savedId)
        val snapshot = repository.loadSnapshot()
        assertEquals(0.0, snapshot.stock.single { it.id == "undo-pasta-stock" }.consumedQuantity, 0.0001)
        assertFalse(snapshot.expenses.any { it.isMealExpense })
    }

    @Test
    fun reviewRestoresAllMealsBeforeChronologicalReallocation() {
        val repository = storage.openRepository()
        val (catalog, breakfastComponent) = ingredient("review-eggs", quantity = 1.0, unitCost = 9.0)
        repository.saveStock(stock("review-eggs-stock", catalog, quantity = 1.0, price = 9.0))
        val breakfast = MealTemplate(id = "review-breakfast", name = "Eggs", mealType = MealType.BREAKFAST, cost = 9.0, components = listOf(breakfastComponent))
        val lunch = MealTemplate(id = "review-lunch", name = "Egg lunch", mealType = MealType.LUNCH, cost = 9.0, components = listOf(breakfastComponent.copy(id = "lunch-egg")))
        repository.recordMeal(mealLog("review-b", MealType.BREAKFAST, 9.0, listOf(breakfastComponent)))
        repository.recordMeal(mealLog("review-l", MealType.LUNCH, 9.0, listOf(breakfastComponent.copy(id = "lunch-egg"))))
        assertEquals(1.0, repository.loadSnapshot().stock.single { it.id == "review-eggs-stock" }.consumedQuantity, 0.0001)

        // Incoming order is deliberately lunch then breakfast; chronological meal order must allocate eggs to lunch.
        repository.reconcileMeals(
            storage.today,
            listOf(
                MealReconciliation(lunch, 9.0, MealStatus.EATEN),
                MealReconciliation(breakfast, 0.0, MealStatus.SKIPPED),
            ),
        )

        val snapshot = repository.loadSnapshot()
        assertEquals(1.0, snapshot.stock.single { it.id == "review-eggs-stock" }.consumedQuantity, 0.0001)
        assertTrue(snapshot.expenses.none { it.id == MealSpending.expenseId("review-b") })
        assertFalse(snapshot.expenses.any { it.id == MealSpending.expenseId("review-l") })
        val allocationLogIds = storage.openDatabase().writableDatabase.rawQuery(
            "SELECT log_id FROM meal_consumptions WHERE stock_item_id = 'review-eggs-stock'",
            null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        assertEquals(listOf("review-l"), allocationLogIds)
    }

    @Test
    fun componentMarkedNotToUseStockIsFullyChargedAndLeavesStockUntouched() {
        val repository = storage.openRepository()
        val (catalog, component) = ingredient("cash-only-flour", quantity = 2.0, unitCost = 3.0)
        repository.saveStock(stock("cash-only-flour-stock", catalog, quantity = 2.0, price = 6.0))
        val meal = mealLog("cash-only-meal", cost = 15.0, components = listOf(component.copy(useStock = false)))

        repository.recordMeal(meal)

        assertEquals(15.0, repository.loadSnapshot().expenses.single { it.isMealExpense }.amount, 0.0001)
        assertEquals(0.0, repository.loadSnapshot().stock.single { it.id == "cash-only-flour-stock" }.consumedQuantity, 0.0001)
    }

    @Test
    fun futureAndExpiredLotsCannotCoverMeal() {
        val repository = storage.openRepository()
        val (catalog, component) = ingredient("dated-milk", quantity = 2.0, unitCost = 5.0)
        repository.saveStock(stock("expired-milk", catalog, 1.0, 5.0, expiry = storage.today.minusDays(1)))
        repository.saveStock(stock("future-milk", catalog, 1.0, 5.0, purchaseDate = storage.today.plusDays(1)))
        storage.openDatabase().upsertStock(stock("uncertain-milk", catalog, 1.0, 5.0).copy(conversionKnown = false))
        val meal = mealLog("dated-milk-meal", cost = 10.0, components = listOf(component))

        repository.recordMeal(meal)

        val snapshot = repository.loadSnapshot()
        assertEquals(10.0, snapshot.expenses.single { it.isMealExpense }.amount, 0.0001)
        assertEquals(0.0, snapshot.stock.sumOf { it.consumedQuantity }, 0.0001)
    }

    @Test
    fun componentWithoutCatalogIdFindsCatalogAndStockByName() {
        val repository = storage.openRepository()
        val (catalog, component) = ingredient("fallback-cumin", quantity = 3.0, unitCost = 4.0)
        repository.saveStock(stock("fallback-cumin-stock", catalog, 3.0, 12.0))
        val meal = mealLog("fallback-name-meal", cost = 12.0, components = listOf(component.copy(catalogId = null)))

        repository.recordMeal(meal)

        assertFalse(repository.loadSnapshot().expenses.any { it.isMealExpense })
        assertEquals(3.0, repository.loadSnapshot().stock.single { it.id == "fallback-cumin-stock" }.consumedQuantity, 0.0001)
    }

    @Test
    fun manualMealCostCreditsActualValueOfPartiallyUsedStock() {
        val repository = storage.openRepository()
        val (catalog, component) = ingredient("manual-cheese", quantity = 5.0, unitCost = 0.0)
        repository.saveStock(stock("manual-cheese-stock", catalog, 2.0, 8.0))
        val meal = mealLog("manual-cost-meal", cost = 30.0, components = listOf(component))

        repository.recordMeal(meal)

        assertEquals(22.0, repository.loadSnapshot().expenses.single { it.isMealExpense }.amount, 0.0001)
        assertEquals(2.0, repository.loadSnapshot().stock.single { it.id == "manual-cheese-stock" }.consumedQuantity, 0.0001)
    }

    @Test
    fun generatedExpenseFailureRollsBackMealAndStockChanges() {
        val repository = storage.openRepository()
        val database = storage.openDatabase()
        val (catalog, component) = ingredient("rollback-onion", quantity = 2.0, unitCost = 2.0)
        repository.saveStock(stock("rollback-onion-stock", catalog, 1.0, 2.0))
        database.writableDatabase.execSQL(
            "CREATE TRIGGER reject_meal_expense BEFORE INSERT ON expenses " +
                "WHEN NEW.id LIKE 'meal-log:%' BEGIN SELECT RAISE(ABORT, 'blocked'); END",
        )

        var failed = false
        try {
            repository.recordMeal(mealLog("rollback-log", cost = 4.0, components = listOf(component)))
        } catch (_: RuntimeException) {
            failed = true
        }

        assertTrue(failed)
        val snapshot = repository.loadSnapshot()
        assertTrue(snapshot.mealLogs.isEmpty())
        assertEquals(0.0, snapshot.stock.single { it.id == "rollback-onion-stock" }.consumedQuantity, 0.0001)
        assertFalse(snapshot.expenses.any { it.isMealExpense })
        assertEquals(0, rowCount(database.writableDatabase, "meal_consumptions"))
    }

    @Test
    fun consumptionInsertFailureRollsBackEarlierStockUpdateAndMealWrite() {
        val repository = storage.openRepository()
        val database = storage.openDatabase()
        val (catalog, component) = ingredient("consumption-abort-rice", quantity = 2.0, unitCost = 3.0)
        repository.saveStock(stock("consumption-abort-stock", catalog, quantity = 1.0, price = 3.0))
        database.writableDatabase.execSQL(
            "CREATE TRIGGER reject_meal_consumption BEFORE INSERT ON meal_consumptions " +
                "BEGIN SELECT RAISE(ABORT, 'blocked'); END",
        )

        var failed = false
        try {
            repository.recordMeal(mealLog("consumption-abort-log", cost = 12.0, components = listOf(component)))
        } catch (_: RuntimeException) {
            failed = true
        }

        assertTrue(failed)
        val snapshot = repository.loadSnapshot()
        assertTrue(snapshot.mealLogs.isEmpty())
        assertEquals(0.0, snapshot.stock.single { it.id == "consumption-abort-stock" }.consumedQuantity, 0.0001)
        assertFalse(snapshot.expenses.any { it.isMealExpense })
        assertEquals(0, rowCount(database.writableDatabase, "meal_consumptions"))
    }

    @Test
    fun generatedMealExpenseCanOnlyBeChangedThroughTheMealRecord() {
        val repository = storage.openRepository()
        val meal = mealLog("protected-meal", cost = 18.0)
        repository.recordMeal(meal)
        val expenseId = MealSpending.expenseId(meal.id)

        var editRejected = false
        try {
            repository.saveExpense(Expense(id = expenseId, categoryId = "food", amount = 1.0, date = storage.today))
        } catch (_: IllegalArgumentException) {
            editRejected = true
        }
        var deleteRejected = false
        try {
            repository.deleteExpense(expenseId)
        } catch (_: IllegalArgumentException) {
            deleteRejected = true
        }

        assertTrue(editRejected)
        assertTrue(deleteRejected)
        assertEquals(18.0, repository.loadSnapshot().expenses.single { it.id == expenseId }.amount, 0.0001)
        repository.deleteMealLog(meal.id)
        assertFalse(repository.loadSnapshot().expenses.any { it.id == expenseId })
    }

    @Test
    fun upgradeFromVersionTwelveBackfillsStoredCoverageWithoutChangingStockAndIsIdempotent() {
        val repository = storage.openRepository()
        val database = storage.openDatabase()
        val (catalog, component) = ingredient("migration-lentils", quantity = 4.0, unitCost = 2.0)
        repository.recordPurchase(
            StockItem(
                id = "migration-stock",
                name = catalog.name,
                category = catalog.category,
                unit = catalog.baseUnit,
                totalQuantity = 1.0,
                totalPrice = 5.0,
                purchaseDate = storage.today,
            ),
            catalogItem = catalog,
        )
        repository.saveExpense(Expense(id = "manual-food-expense", categoryId = "food", amount = 7.0, date = storage.today, description = "Manual"))
        repository.recordMeal(mealLog("migration-partial", cost = 20.0, components = listOf(component)))
        repository.recordMeal(mealLog("migration-no-stock", mealType = MealType.DINNER, cost = 12.0))
        val beforeConsumed = database.loadStockById("migration-stock")!!.consumedQuantity
        database.writableDatabase.delete("expenses", "id LIKE 'meal-log:%'", null)
        repository.close()
        database.close()

        val file = storage.context.getDatabasePath(BudgetDao.DATABASE_NAME)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { oldDb -> oldDb.version = 12 }
        val upgraded = storage.openDatabase()
        val snapshot = upgraded.loadSnapshot(BudgetSettings(), storage.today)
        val generated = snapshot.expenses.filter { it.isMealExpense }.associateBy { it.id }
        assertEquals(2, generated.size)
        assertEquals(15.0, generated.getValue(MealSpending.expenseId("migration-partial")).amount, 0.0001)
        assertEquals(12.0, generated.getValue(MealSpending.expenseId("migration-no-stock")).amount, 0.0001)
        assertEquals(7.0, snapshot.expenses.single { it.id == "manual-food-expense" }.amount, 0.0001)
        assertEquals(beforeConsumed, upgraded.loadStockById("migration-stock")!!.consumedQuantity, 0.0001)

        upgraded.close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { oldDb -> oldDb.version = 12 }
        val upgradedAgain = storage.openDatabase()
        val secondSnapshot = upgradedAgain.loadSnapshot(BudgetSettings(), storage.today)
        assertEquals(2, secondSnapshot.expenses.count { it.isMealExpense })
        assertEquals(7.0, secondSnapshot.expenses.single { it.id == "manual-food-expense" }.amount, 0.0001)
        assertEquals(beforeConsumed, upgradedAgain.loadStockById("migration-stock")!!.consumedQuantity, 0.0001)
    }

    private fun ingredient(key: String, quantity: Double, unitCost: Double): Pair<FoodCatalogItem, MealComponent> {
        val catalog = FoodCatalogItem(
            id = "catalog-$key",
            name = "Food $key",
            stockUnit = "g",
            portionUnit = "g",
            portionsPerStockUnit = 1.0,
            defaultCostPerPortion = unitCost,
        )
        storage.openRepository().saveFoodCatalogItem(catalog)
        return catalog to MealComponent(
            id = "component-$key",
            catalogId = catalog.id,
            name = catalog.name,
            quantity = quantity,
            unit = "g",
            costPerUnit = unitCost,
        )
    }

    private fun stock(
        id: String,
        catalog: FoodCatalogItem,
        quantity: Double,
        price: Double,
        purchaseDate: LocalDate = storage.today,
        expiry: LocalDate? = null,
    ) = StockItem(
        id = id,
        name = catalog.name,
        category = catalog.category,
        unit = catalog.baseUnit,
        totalQuantity = quantity,
        totalPrice = price,
        purchaseDate = purchaseDate,
        expiryDate = expiry,
        catalogId = catalog.id,
    )

    private fun mealLog(
        id: String,
        mealType: MealType = MealType.LUNCH,
        cost: Double,
        components: List<MealComponent> = emptyList(),
    ) = MealLog(
        id = id,
        date = storage.today,
        mealType = mealType,
        name = "Meal $id",
        cost = cost,
        components = components,
    )

    private fun rowCount(db: SQLiteDatabase, table: String): Int = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }
}
