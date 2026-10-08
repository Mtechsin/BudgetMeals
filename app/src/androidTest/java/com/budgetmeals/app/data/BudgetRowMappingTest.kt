package com.budgetmeals.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetmeals.app.state.BudgetMath
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetRowMappingTest {
    @get:Rule
    internal val storage = BudgetStorageRule()

    @Test
    fun snapshotPreservesShoppingPurchaseLinksAndUnknownPrice() {
        val database = storage.openDatabase()
        val item = ShoppingItem(
            id = "linked-shopping",
            name = "Detergent",
            createdDate = storage.today,
            isChecked = true,
            purchaseExpenseId = "purchase-expense",
            purchaseStockId = "purchase-stock",
            priceKnown = false,
        )
        database.upsertShopping(item)

        val snapshotItem = database.loadSnapshot(BudgetSettings(), storage.today).shopping.single()

        assertEquals(item, database.loadShoppingById(item.id))
        assertEquals(item, snapshotItem)
    }

    @Test
    fun snapshotKeepsClearedMealsOutOfThePlan() {
        val database = storage.openDatabase()
        assertTrue(database.saveDayPlanMeals(storage.today, emptyMap()))

        val snapshot = database.loadSnapshot(BudgetSettings(), storage.today)

        assertEquals(database.loadDayPlans(storage.today), snapshot.mealPlan(storage.today))
        assertEquals(MealType.entries.size, snapshot.mealPlan(storage.today).size)
        assertTrue(snapshot.mealPlan(storage.today).all { it.isCleared })
        assertTrue(BudgetMath.plannedMealsForDate(snapshot, storage.today).isEmpty())
    }

    @Test
    fun consumptionDoesNotUseCatalogStockWithUnknownConversion() {
        val database = storage.openDatabase()
        val catalog = FoodCatalogItem(
            id = "unknown-conversion",
            name = "Unmeasured flour",
            stockUnit = "piece",
            portionUnit = "piece",
            conversionKnown = false,
        )
        database.upsertFoodCatalogItem(catalog)
        database.upsertStock(
            StockItem(
                id = "unmeasured-stock",
                name = catalog.name,
                catalogId = catalog.id,
                totalQuantity = 10.0,
                totalPrice = 20.0,
                purchaseDate = storage.today,
            ),
        )
        database.upsertMealLog(
            MealLog(
                id = "unmeasured-log",
                date = storage.today,
                mealType = MealType.LUNCH,
                name = "Flour meal",
                cost = 2.0,
                components = listOf(
                    MealComponent(id = "flour-component", catalogId = catalog.id, name = catalog.name),
                ),
            ),
        )

        assertEquals(0.0, requireNotNull(database.loadStockById("unmeasured-stock")).consumedQuantity, 0.0001)
        assertFalse(requireNotNull(database.findFoodCatalogItemById(catalog.id)).conversionKnown)
    }

    @Test
    fun purchaseExpenseLookupMatchesTheOriginalAmount() {
        val database = storage.openDatabase()
        val original = Expense(
            id = "original-purchase",
            categoryId = "food",
            amount = 20.0,
            date = storage.today,
            description = "Bought Rice",
            recurringScheduleId = "schedule-id",
            isBudgetTransfer = true,
        )
        database.upsertExpense(original)
        database.upsertExpense(original.copy(id = "later-purchase", amount = 50.0))

        assertEquals(original, database.findExpenseForStockPurchase(storage.today, "Rice", 20.0))
        assertNull(database.findExpenseForStockPurchase(storage.today, "Rice", 35.0))
    }

    @Test
    fun stockRoundTripPreservesEpochExpiryDateAndUsageLinks() {
        val database = storage.openDatabase()
        val item = StockItem(
            id = "dated-stock",
            name = "Old stock",
            totalQuantity = 10.0,
            totalPrice = 20.0,
            purchaseDate = storage.today,
            expiryDate = LocalDate.ofEpochDay(0),
            consumedQuantity = 2.0,
            usageHistory = listOf(2.0),
            datedUsageHistory = listOf(DatedUsage(storage.today, 2.0, "meal-log")),
            linkedExpenseId = "stock-expense",
        )
        database.upsertStock(item)

        assertEquals(item, database.loadStockById(item.id))
        assertEquals(item, database.loadSnapshot(BudgetSettings(), storage.today).stock.single())
    }
}
