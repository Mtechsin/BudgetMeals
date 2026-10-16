package com.budgetmeals.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a separate database and preferences, so running against an installed app is safe. */
@RunWith(AndroidJUnit4::class)
class BudgetPersistenceTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var context: IsolatedContext
    private lateinit var database: BudgetDatabase
    private lateinit var repository: BudgetRepository

    @Before
    fun setUp() {
        context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        database = BudgetDatabase(context)
        repository = BudgetRepository(context, clock)
        database.writableDatabase
    }

    @After
    fun tearDown() {
        repository.close()
        database.close()
        context.deleteDatabase(BudgetDao.DATABASE_NAME)
        context.getSharedPreferences("budget_meals", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun savingsWithNoOptionalCategoryPersistBothSidesOfWithdrawal() {
        repository.addSpares(100.0, "Deposit")
        repository.spendFromSpares(30.0, "Treat")

        val snapshot = repository.loadSnapshot()
        assertEquals(70.0, snapshot.sparesBalance, 0.000001)
        assertEquals(2, snapshot.spares.size)
        assertEquals(30.0, snapshot.expenses.single().amount, 0.000001)
    }

    @Test
    fun overdrawingSavingsLeavesBothLedgersUnchanged() {
        repository.addSpares(20.0, "Deposit")
        expectFailure { repository.spendFromSpares(21.0, "Treat") }

        val snapshot = repository.loadSnapshot()
        assertEquals(20.0, snapshot.sparesBalance, 0.000001)
        assertEquals(1, snapshot.spares.size)
        assertTrue(snapshot.expenses.isEmpty())
    }

    @Test
    fun savingAnOlderSettingsFormPreservesTheCurrentSavingsMarker() {
        val openingSettings = repository.readSettings()
        assertEquals(100.0, repository.saveTodayToSpares(), 0.000001)

        repository.saveSettings(openingSettings.copy(monthlyFoodBudget = 5_000.0))

        assertEquals(today, repository.readSettings().lastSparesAutoSaveDate)
        assertEquals(5_000.0, repository.readSettings().monthlyFoodBudget, 0.000001)
        assertEquals(0.0, repository.saveTodayToSpares(), 0.000001)
        assertEquals(100.0, repository.loadSnapshot().sparesBalance, 0.000001)
    }

    @Test
    fun settingsReadsObserveSavesFromAnotherRepositoryInstance() {
        repository.readSettings()
        BudgetRepository(context, clock).use { background ->
            background.saveSettings(background.readSettings().copy(dailyFoodBudget = 150.0))
            background.saveTodayToSpares()
        }

        assertEquals(150.0, repository.readSettings().dailyFoodBudget, 0.000001)
        assertEquals(today, repository.readSettings().lastSparesAutoSaveDate)
    }

    @Test
    fun anExistingSavingsRowCannotBeReplacedWhenThePreferenceMarkerIsMissing() {
        assertEquals(100.0, repository.saveTodayToSpares(), 0.000001)
        context.getSharedPreferences("budget_meals", Context.MODE_PRIVATE).edit()
            .remove("last_spares_date").commit()
        repository.saveSettings(repository.readSettings().copy(dailyFoodBudget = 150.0))

        assertEquals(0.0, repository.saveTodayToSpares(), 0.000001)

        val snapshot = repository.loadSnapshot()
        assertEquals(100.0, snapshot.sparesBalance, 0.000001)
        assertEquals(1, snapshot.spares.size)
        assertEquals(today, snapshot.settings.lastSparesAutoSaveDate)
    }

    @Test
    fun buyAgainUsesTheCurrentShoppingRowAndDoesNotResurrectDeletedRows() {
        val stale = ShoppingItem(id = "shopping", name = "Soap", category = ItemCategory.HOUSEHOLD)
        repository.saveShopping(stale)
        assertTrue(repository.buyShoppingItem(stale, 15.0))
        val purchased = database.loadShoppingById(stale.id)!!
        repository.saveShopping(purchased.copy(name = "Updated soap", quantity = 3.0))

        assertTrue(repository.buyAgainShoppingItem(stale))
        val reopened = database.loadShoppingById(stale.id)!!
        assertEquals("Updated soap", reopened.name)
        assertEquals(3.0, reopened.quantity, 0.000001)
        assertFalse(reopened.isChecked)
        assertNull(reopened.purchaseExpenseId)
        assertNull(reopened.purchaseStockId)
        assertEquals(1, database.loadExpenses().size)

        repository.deleteShopping(stale.id)
        assertFalse(repository.buyAgainShoppingItem(purchased))
        assertNull(database.loadShoppingById(stale.id))
    }

    @Test
    fun purchaseStoresAnExplicitNewCatalogWithTheBatch() {
        val catalog = FoodCatalogItem(id = "custom-catalog", name = "Custom food", stockUnit = "g", portionUnit = "g")
        repository.recordPurchase(stock(name = catalog.name, unit = "g", catalogId = catalog.id), catalogItem = catalog)

        assertNotNull(database.findFoodCatalogItemById(catalog.id))
        assertEquals(catalog.id, database.loadStockById("batch")!!.catalogId)
        assertEquals(1, database.loadExpenses().size)
    }

    @Test
    fun undoPurchaseRequiresUndoingMealsThatUsedTheBatchFirst() {
        val shopping = ShoppingItem(id = "shopping", name = "Eggs", category = ItemCategory.FOOD_FRESH, quantity = 3.0)
        repository.saveShopping(shopping)
        assertTrue(repository.buyShoppingItem(shopping, 30.0))
        val purchased = database.loadShoppingById(shopping.id)!!
        val component = MealComponent(id = "eggs", catalogId = "catalog_eggs", name = "Eggs", quantity = 1.0, unit = "piece", costPerUnit = 10.0)
        repository.recordMeal(MealLog(id = "meal", date = today, mealType = MealType.LUNCH, name = "Egg", cost = 10.0, components = listOf(component)))

        expectFailure { repository.undoShoppingPurchase(purchased) }

        assertTrue(database.loadShoppingById(shopping.id)!!.isChecked)
        assertNotNull(database.loadStockById(purchased.purchaseStockId!!))
        assertEquals(1, database.loadExpenses().size)
        repository.deleteMealLog("meal")
        assertTrue(repository.undoShoppingPurchase(purchased))
        assertNull(database.loadStockById(purchased.purchaseStockId))
        assertTrue(database.loadExpenses().isEmpty())
    }

    @Test
    fun singleMealPurchaseCanStillBeUndoneWithoutARecordedMeal() {
        val shopping = ShoppingItem(id = "snack", name = "Custom snack", category = ItemCategory.SNACK)
        repository.saveShopping(shopping)
        assertTrue(repository.buyShoppingItem(shopping, 10.0))

        assertTrue(repository.undoShoppingPurchase(shopping))

        assertTrue(database.loadExpenses().isEmpty())
        assertTrue(repository.loadSnapshot().stock.isEmpty())
    }

    @Test
    fun confirmingAFreePurchaseKeepsItsPriceKnown() {
        val shopping = ShoppingItem(id = "free-item", name = "Free soap", category = ItemCategory.HOUSEHOLD, priceKnown = false)
        repository.saveShopping(shopping)

        assertTrue(repository.buyShoppingItem(shopping, 0.0))

        val checked = database.loadShoppingById(shopping.id)!!
        assertTrue(checked.isChecked)
        assertTrue(checked.priceKnown)
        assertEquals(0.0, checked.estimatedPrice, 0.000001)
        assertEquals(0.0, database.loadExpenses().single().amount, 0.000001)
    }

    @Test
    fun editingStockWithoutChangingPriceUpdatesItsPurchaseMetadata() {
        repository.recordPurchase(stock())
        val saved = database.loadStockById("batch")!!
        repository.saveStock(saved.copy(name = "Household item", category = ItemCategory.HOUSEHOLD, purchaseDate = today.minusDays(1)))

        val expense = database.loadExpenses().single()
        assertEquals("household", expense.categoryId)
        assertEquals("Bought Household item", expense.description)
        assertEquals(today.minusDays(1), expense.date)
        assertEquals(30.0, expense.amount, 0.000001)
    }

    @Test
    fun purchaseExpenseCannotBeEditedOrDeletedIndependentlyOfItsStock() {
        repository.recordPurchase(stock(), expenseId = "linked-purchase")
        val expense = database.loadExpenses().single()

        expectFailure { repository.saveExpense(expense.copy(amount = 1.0)) }
        expectFailure { repository.saveExpense(expense.copy(date = today.minusDays(1))) }
        expectFailure { repository.deleteExpense(expense.id) }
        expectFailure { repository.saveBudgetCorrection(expense.copy(amount = 1.0)) }
        expectFailure { repository.deleteBudgetCorrection(expense) }

        assertEquals(expense, database.findExpenseById(expense.id))
        assertEquals(30.0, database.loadStockById("batch")!!.totalPrice, 0.000001)
    }

    @Test
    fun legacyStockExpenseIdIsProtectedWithoutAnExplicitLink() {
        database.upsertStock(stock())
        val expense = Expense(id = "stock-batch", categoryId = "food", amount = 30.0, date = today)
        database.upsertExpense(expense)

        expectFailure { repository.saveExpense(expense.copy(amount = 1.0)) }
        expectFailure { repository.deleteExpense(expense.id) }

        assertEquals(expense, database.findExpenseById(expense.id))
    }

    @Test
    fun unrelatedManualExpensesRemainEditableAndDeletable() {
        val expense = Expense(id = "manual", categoryId = "other", amount = 30.0, date = today)
        repository.saveExpense(expense)
        val edited = expense.copy(amount = 12.0, date = today.minusDays(1))

        repository.saveExpense(edited)
        assertEquals(edited, database.findExpenseById(expense.id))
        repository.deleteExpense(expense.id)

        assertNull(database.findExpenseById(expense.id))
    }

    @Test
    fun editingAndStoppingGeneratedRecurrenceKeepsItsOriginalSchedule() {
        repository.saveExpense(Expense(id = "subscription", categoryId = "other", amount = 30.0,
            date = today.minusMonths(1).withDayOfMonth(1), isRecurring = true, recurringFrequency = "MONTHLY"))
        val generated = repository.processRecurringExpenses(today).single()
        repository.saveExpense(generated.copy(amount = 45.0, recurringScheduleId = null))

        val next = repository.processRecurringExpenses(today.plusMonths(1).withDayOfMonth(1)).single()
        assertEquals("subscription", next.recurringScheduleId)
        assertEquals(45.0, next.amount, 0.000001)
        repository.saveExpense(next.copy(isRecurring = false, recurringScheduleId = null))

        assertEquals("subscription", database.findExpenseById(next.id)!!.recurringScheduleId)
        assertTrue(repository.processRecurringExpenses(today.plusMonths(2).withDayOfMonth(1)).isEmpty())
        assertEquals(3, database.loadExpenses().size)
    }

    @Test
    fun deletingGeneratedOccurrenceStopsRepeatsWithoutChangingHistoricalAmounts() {
        repository.saveExpense(Expense(id = "subscription", categoryId = "other", amount = 30.0,
            date = today.minusMonths(1).withDayOfMonth(1), isRecurring = true, recurringFrequency = "MONTHLY"))
        val generated = repository.processRecurringExpenses(today).single()
        val original = database.findExpenseById("subscription")!!

        repository.deleteExpense(generated.id)

        repeat(2) {
            val snapshot = repository.loadSnapshot()
            assertNull(database.findExpenseById(generated.id))
            assertEquals(listOf(original.copy(isRecurring = false)), snapshot.expenses)
            assertEquals(30.0, snapshot.expenses.sumOf { expense -> expense.amount }, 0.000001)
        }
        assertTrue(repository.processRecurringExpenses(today.plusMonths(1)).isEmpty())
    }

    @Test
    fun savingsWithdrawalExpenseStaysAttachedToItsTransaction() {
        repository.addSpares(100.0, "Deposit")
        repository.spendFromSpares(30.0, "Treat")
        val expense = database.loadExpenses().single()
        val withdrawal = repository.loadSnapshot().spares.single { it.amount < 0.0 }
        assertEquals("${BudgetRepository.SPARES_EXPENSE_PREFIX}${withdrawal.id}", expense.id)

        expectFailure { repository.saveExpense(expense.copy(amount = 1.0)) }
        expectFailure { repository.deleteExpense(expense.id) }

        assertEquals(expense, database.findExpenseById(expense.id))
        assertEquals(70.0, repository.loadSnapshot().sparesBalance, 0.000001)
        repository.deleteSpares(withdrawal.id)
        assertTrue(database.loadExpenses().isEmpty())
        assertEquals(100.0, repository.loadSnapshot().sparesBalance, 0.000001)
    }

    @Test
    fun invalidPurchaseRollsBackTheNewCatalogAndExpense() {
        val catalog = FoodCatalogItem(id = "invalid-catalog", name = "Invalid food")
        expectFailure { repository.recordPurchase(stock(name = catalog.name, totalPrice = Double.NaN), catalogItem = catalog) }

        assertNull(database.loadStockById("batch"))
        assertNull(database.findFoodCatalogItemById(catalog.id))
        assertTrue(database.loadExpenses().isEmpty())
    }

    @Test
    fun reducingStockBelowItsConsumedQuantityDoesNotCreateRemainingStock() {
        database.upsertStock(stock(totalQuantity = 3.0, consumedQuantity = 5.0))

        val stored = database.loadStockById("batch")!!
        assertEquals(3.0, stored.totalQuantity, 0.000001)
        assertEquals(3.0, stored.consumedQuantity, 0.000001)
        assertEquals(0.0, stored.remainingQuantity, 0.000001)
    }

    @Test
    fun closingDayPreservesRecordedStockCoverageEvenWhenBatchWasDeleted() {
        repository.recordPurchase(stock(catalogId = "catalog_eggs"))
        val component = MealComponent(id = "eggs", catalogId = "catalog_eggs", name = "Eggs", quantity = 2.0, unit = "piece", costPerUnit = 25.0)
        val meal = MealLog(id = "eaten", date = today, mealType = MealType.LUNCH, name = "Egg lunch", cost = 50.0, components = listOf(component))
        repository.ensureDayPlan(today, listOf(MealTemplate(id = "planned", name = meal.name, mealType = meal.mealType, cost = meal.cost, components = meal.components)))
        repository.recordMeal(meal)
        assertEquals(1, database.loadExpenses().size)
        repository.deleteStock("batch")

        repository.closeDay(today)

        assertEquals(1, database.loadExpenses().size)
        assertEquals(50.0, repository.loadSnapshot().dayClosure(today)!!.consumedCost, 0.000001)
    }

    @Test
    fun autoCloseIncludesMealsRecordedWithoutADayPlan() {
        val yesterday = today.minusDays(1)
        repository.recordMeal(MealLog(id = "extra", date = yesterday, mealType = MealType.SNACK, name = "Extra snack", cost = 12.0))

        val closure = repository.loadSnapshot().dayClosure(yesterday)!!

        assertEquals(12.0, closure.plannedCost, 0.000001)
        assertEquals(12.0, closure.consumedCost, 0.000001)
        assertEquals(1, database.loadExpenses().size)
    }

    @Test
    fun mealReviewClosureIncludesOtherMealsAlreadyRecordedThatDay() {
        repository.recordMeal(MealLog(id = "snack", date = today, mealType = MealType.SNACK, name = "Snack", cost = 12.0))
        val lunch = MealTemplate(id = "lunch", name = "Lunch", mealType = MealType.LUNCH, cost = 20.0)

        repository.reconcileMeals(today, listOf(MealReconciliation(lunch, 20.0, MealStatus.EATEN)))

        val snapshot = repository.loadSnapshot()
        assertEquals(32.0, snapshot.dayClosure(today)!!.plannedCost, 0.000001)
        assertEquals(32.0, snapshot.dayClosure(today)!!.consumedCost, 0.000001)
        assertEquals(2, snapshot.mealLogs.size)
    }

    @Test
    fun failedMealEditRestoresItsPreviousStockAllocation() {
        repository.recordPurchase(stock(catalogId = "catalog_eggs"))
        val component = MealComponent(id = "eggs", catalogId = "catalog_eggs", name = "Eggs", quantity = 2.0, unit = "piece", costPerUnit = 10.0)
        val meal = MealLog(id = "meal", date = today, mealType = MealType.LUNCH, name = "Egg lunch", cost = 20.0, components = listOf(component))
        repository.recordMeal(meal)
        val before = database.loadStockById("batch")!!

        expectFailure { database.upsertMealLog(meal.copy(cost = Double.NaN)) }

        assertEquals(before, database.loadStockById("batch"))
        assertEquals(20.0, database.loadMealLog(today, MealType.LUNCH)!!.cost, 0.000001)
        assertEquals(1, database.loadExpenses().size)
    }

    @Test
    fun versionEightMigrationPreservesDatedUsageAndConvertsMealAllocations() {
        val legacyContext = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        createVersionEightDatabase(legacyContext)
        val migrated = BudgetDatabase(legacyContext)
        try {
            val stock = migrated.loadStockById("legacy-stock")!!
            assertEquals("g", stock.unit)
            assertEquals(2_000.0, stock.totalQuantity, 0.000001)
            assertEquals(1_000.0, stock.consumedQuantity, 0.000001)
            assertEquals(listOf(DatedUsage(today.minusDays(1), 1_000.0, "legacy-meal")), stock.datedUsageHistory)
            migrated.readableDatabase.rawQuery("SELECT stock_quantity, stock_unit FROM meal_consumptions", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1_000.0, it.getDouble(0), 0.000001)
                assertEquals("g", it.getString(1))
            }

            migrated.deleteMealLog("legacy-meal")

            val restored = migrated.loadStockById("legacy-stock")!!
            assertEquals(0.0, restored.consumedQuantity, 0.000001)
            assertTrue(restored.datedUsageHistory.isEmpty())
        } finally {
            migrated.close()
            legacyContext.deleteDatabase(BudgetDao.DATABASE_NAME)
        }
    }

    @Test
    fun versionThirteenUpgradeRepairsUntouchedRecipesAndOpenPlansWithoutChangingHistoryOrPrices() {
        val upgradeContext = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val migrationToday = LocalDate.now()
        val future = migrationToday.plusDays(2)
        val historical = migrationToday.minusDays(2)
        val recorded = migrationToday.plusDays(3)
        val closed = migrationToday.plusDays(4)
        val edited = migrationToday.plusDays(5)
        val oldTemplate = MealTemplate(id = "fuul_combo", name = "My lunch name", mealType = MealType.LUNCH,
            cost = 91.0, notes = "My notes", components = shippedVersionThirteenFuul())
        lateinit var before: AppSnapshot
        BudgetDatabase(upgradeContext).use { legacy ->
            legacy.upsertTemplate(oldTemplate)
            listOf(migrationToday, future, historical, recorded, closed).forEach {
                legacy.ensureDayPlan(it, listOf(oldTemplate))
            }
            legacy.ensureDayPlan(edited, listOf(oldTemplate.copy(
                components = oldTemplate.components.mapIndexed { index, component ->
                    if (index == 0) component.copy(quantity = 149.0) else component
                },
            )))
            legacy.upsertMealLog(MealLog(id = "recorded-lunch", date = recorded, mealType = MealType.LUNCH,
                name = oldTemplate.name, cost = oldTemplate.cost, components = oldTemplate.components))
            legacy.closeDay(closed)
            before = legacy.loadSnapshot(BudgetSettings(), migrationToday)
            legacy.writableDatabase.version = 13
        }
        BudgetDatabase(upgradeContext).use { migrated ->
            val after = migrated.loadSnapshot(BudgetSettings(), migrationToday)
            val repaired = DynamicMealDefaults.componentsForTemplate("fuul_combo", emptyMap())
            assertEquals(oldTemplate.copy(components = repaired), after.templates.single { it.id == "fuul_combo" })
            listOf(migrationToday, future).forEach { date ->
                assertEquals(before.mealPlan(date).single().copy(components = repaired), after.mealPlan(date).single())
            }
            listOf(historical, recorded, closed, edited).forEach { date ->
                assertEquals(before.mealPlan(date), after.mealPlan(date))
            }
            assertEquals(before.mealLogs, after.mealLogs)
            assertEquals(before.dayClosures, after.dayClosures)
            assertEquals(before.expenses, after.expenses)
            assertEquals(before.foodCatalog, after.foodCatalog)
            assertEquals(14, migrated.readableDatabase.version)
        }
        upgradeContext.deleteDatabase(BudgetDao.DATABASE_NAME)
    }

    @Test
    fun versionThirteenUpgradePreservesEditedStructuredRecipes() {
        val upgradeContext = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val edited = MealTemplate(id = "fuul_combo", name = "Edited lunch", mealType = MealType.LUNCH,
            cost = 71.0, components = shippedVersionThirteenFuul().mapIndexed { index, component ->
                if (index == 0) component.copy(quantity = 149.0, costPerUnit = 0.11) else component
            })
        val custom = edited.copy(id = "custom-lunch", isCustom = true, components = shippedVersionThirteenFuul())
        lateinit var before: List<MealTemplate>
        BudgetDatabase(upgradeContext).use { legacy ->
            legacy.upsertTemplate(edited)
            legacy.upsertTemplate(custom)
            before = legacy.loadSnapshot(BudgetSettings(), today).templates
            legacy.writableDatabase.version = 13
        }
        BudgetDatabase(upgradeContext).use { migrated ->
            assertEquals(before, migrated.loadSnapshot(BudgetSettings(), today).templates)
        }
        upgradeContext.deleteDatabase(BudgetDao.DATABASE_NAME)
    }

    private fun shippedVersionThirteenFuul(): List<MealComponent> = listOf(
        MealComponent("component_fuul", "catalog_ful", "Ful medames", 150.0, "g", 0.1),
        MealComponent("component_falafel", "catalog_falafel", "Falafel", 2.0, "piece", 5.0),
        MealComponent("component_bread", "catalog_bread", "Baladi bread", 1.0, "piece", 12.0),
        MealComponent("component_pickles", "catalog_pickles", "Pickles", 1.0, "piece", 3.0),
    )

    private fun createVersionEightDatabase(legacyContext: Context) {
        SQLiteDatabase.openOrCreateDatabase(legacyContext.getDatabasePath(BudgetDao.DATABASE_NAME), null).use { legacy ->
            val schemas = listOf(
                "CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, icon TEXT, monthly_budget REAL, is_food INTEGER, is_custom INTEGER, sort_order INTEGER)",
                "CREATE TABLE food_catalog (id TEXT PRIMARY KEY, name TEXT, category TEXT, stock_unit TEXT, portion_unit TEXT, portions_per_stock_unit REAL, default_cost_per_portion REAL, notes TEXT, purchase_price REAL, purchase_quantity REAL, purchase_unit TEXT, meal_usage TEXT, price_options_json TEXT, price_known INTEGER, conversion_known INTEGER)",
                "CREATE TABLE stock (id TEXT PRIMARY KEY, name TEXT, category TEXT, unit TEXT, total_quantity REAL, total_price REAL, purchase_date INTEGER, expiry_date INTEGER, batch_type TEXT, usage_per_day REAL, consumed_quantity REAL, usage_history TEXT, notes TEXT, catalog_id TEXT)",
                "CREATE TABLE shopping (id TEXT PRIMARY KEY, name TEXT, quantity REAL, unit TEXT, estimated_price REAL, category TEXT, is_checked INTEGER, created_date INTEGER, source_item_id TEXT, note TEXT)",
                "CREATE TABLE expenses (id TEXT PRIMARY KEY, category_id TEXT, amount REAL, date INTEGER, description TEXT, is_recurring INTEGER, recurring_frequency TEXT, is_correction INTEGER)",
                "CREATE TABLE day_plans (id TEXT PRIMARY KEY, date INTEGER, meal_type TEXT, template_id TEXT, name TEXT, cost REAL, foods TEXT, components_json TEXT)",
                "CREATE TABLE day_closures (date INTEGER PRIMARY KEY, closed_at INTEGER, planned_cost REAL, consumed_cost REAL, leftover_cost REAL, skipped_cost REAL)",
                "CREATE TABLE meal_templates (id TEXT PRIMARY KEY, name TEXT, meal_type TEXT, cost REAL, is_recurring INTEGER, day_of_week TEXT, notes TEXT, is_custom INTEGER, components_json TEXT)",
                "CREATE TABLE meal_logs (id TEXT PRIMARY KEY, date INTEGER, meal_type TEXT, template_id TEXT, name TEXT, actual_time INTEGER, cost REAL, consumed_cost REAL, foods TEXT, status TEXT, components_json TEXT)",
                "CREATE TABLE meal_consumptions (id TEXT PRIMARY KEY, log_id TEXT, component_id TEXT, catalog_id TEXT, name TEXT, portion_unit TEXT, requested_quantity REAL, consumed_quantity REAL, stock_quantity REAL, stock_unit TEXT, stock_item_id TEXT, created_at INTEGER)",
            )
            schemas.forEach(legacy::execSQL)
            legacy.execSQL("INSERT INTO food_catalog VALUES ('legacy-catalog', 'Flour', 'FOOD_STAPLE', 'kg', 'g', 1000, 0.01, '', NULL, NULL, NULL, '', '', 1, 1)")
            legacy.execSQL(
                "INSERT INTO stock VALUES ('legacy-stock', 'Flour', 'FOOD_STAPLE', 'kg', 2, 20, ?, NULL, 'MONTHLY', 0, 1, ?, '', 'legacy-catalog')",
                arrayOf(today.minusDays(2).toEpochDay(), "${today.minusDays(1)}:1.0:legacy-meal"),
            )
            val component = MealComponent(id = "legacy-component", catalogId = "legacy-catalog", name = "Flour", quantity = 1000.0, unit = "g", costPerUnit = 0.01)
            legacy.execSQL(
                "INSERT INTO meal_logs VALUES ('legacy-meal', ?, 'LUNCH', NULL, 'Lunch', 0, 10, 10, '', 'EATEN', ?)",
                arrayOf(today.minusDays(1).toEpochDay(), MealDataCodec.encodeComponents(listOf(component))),
            )
            legacy.execSQL("INSERT INTO meal_consumptions VALUES ('legacy-allocation', 'legacy-meal', 'legacy-component', 'legacy-catalog', 'Flour', 'g', 1000, 1000, 1, 'kg', 'legacy-stock', 0)")
            legacy.version = 8
        }
    }

    private fun stock(
        name: String = "Eggs",
        unit: String = "piece",
        totalQuantity: Double = 3.0,
        totalPrice: Double = 30.0,
        consumedQuantity: Double = 0.0,
        catalogId: String? = null,
    ) = StockItem(id = "batch", name = name, unit = unit, totalQuantity = totalQuantity,
        totalPrice = totalPrice, purchaseDate = today, consumedQuantity = consumedQuantity, catalogId = catalogId)

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected the invalid operation to fail")
        } catch (_: Exception) {
            // AssertionError is deliberately not caught.
        }
    }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "logic-test-${UUID.randomUUID()}-"
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File = super.getDatabasePath(prefix + name)
        override fun deleteDatabase(name: String): Boolean = super.deleteDatabase(prefix + name)
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences(prefix + name, mode)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            super.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            super.openOrCreateDatabase(prefix + name, mode, factory, errorHandler)
    }
}
