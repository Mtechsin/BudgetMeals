package com.budgetmeals.app.state

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.ExpenseCategory
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodCatalogJsonCodec
import com.budgetmeals.app.data.FoodMeasurementCodec
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityRegressionTest {
    @Test
    fun snapshotDateParticipatesInEqualityAndExpenseTotals() {
        val food = ExpenseCategory(
            id = "food",
            name = "Food",
            icon = "restaurant",
            monthlyBudget = 1_000.0,
            isFood = true,
        )
        val expense = Expense(
            id = "october-food",
            categoryId = food.id,
            amount = 25.0,
            date = LocalDate.of(2026, 10, 1),
        )
        val september = AppSnapshot(
            today = LocalDate.of(2026, 9, 30),
            categories = listOf(food),
            expenses = listOf(expense),
        )
        val october = AppSnapshot(
            today = LocalDate.of(2026, 10, 1),
            categories = listOf(food),
            expenses = listOf(expense),
        )

        assertNotEquals(september, october)
        assertEquals(0.0, september.foodSpentThisMonth, 0.0001)
        assertEquals(25.0, october.foodSpentThisMonth, 0.0001)
    }

    @Test
    fun codecDerivesPortionPriceFromPackagePriceAndPortionCount() {
        val item = FoodCatalogJsonCodec.decode(
            """
            {
              "items": [{
                "name": "Tahini",
                "purchaseUnit": "jar",
                "purchaseQuantity": 2,
                "purchasePrice": 100,
                "portionUnit": "spoon",
                "portionsPerPurchaseUnit": 10
              }]
            }
            """.trimIndent(),
        ).getOrThrow().single()

        assertEquals(2.0, item.purchaseQuantity!!, 0.0001)
        assertEquals(5.0, item.defaultCostPerPortion, 0.0001)
    }

    @Test
    fun gramRequirementsUseGramStockWhenCatalogPortionIsASpoon() {
        val catalog = spoonCatalog()
        val snapshot = AppSnapshot(
            foodCatalog = listOf(catalog),
            stock = listOf(
                StockItem(
                    id = "paste-20g",
                    catalogId = catalog.id,
                    name = catalog.name,
                    unit = "g",
                    totalQuantity = 20.0,
                    totalPrice = 10.0,
                ),
            ),
        )
        val meal = template(
            MealComponent(
                id = "paste-50g",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 50.0,
                unit = "g",
                costPerUnit = 0.5,
            ),
        )

        assertEquals(15.0, BudgetMath.extraPurchasesNeeded(snapshot, meal), 0.0001)
        assertEquals(0.0, BudgetMath.inStockCompleteness(snapshot, meal), 0.0001)
    }

    @Test
    fun duplicateComponentsShareTheSameAvailableStock() {
        val catalog = spoonCatalog()
        val snapshot = AppSnapshot(
            foodCatalog = listOf(catalog),
            stock = listOf(
                StockItem(
                    id = "paste-one-spoon",
                    catalogId = catalog.id,
                    name = catalog.name,
                    unit = "g",
                    totalQuantity = 10.0,
                    totalPrice = 5.0,
                ),
            ),
        )
        val meal = template(
            MealComponent(
                id = "paste-spoon-a",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 1.0,
                unit = "spoon",
                costPerUnit = 5.0,
            ),
            MealComponent(
                id = "paste-spoon-b",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 1.0,
                unit = "spoon",
                costPerUnit = 5.0,
            ),
        )

        assertEquals(5.0, BudgetMath.extraPurchasesNeeded(snapshot, meal), 0.0001)
        assertEquals(0.5, BudgetMath.inStockCompleteness(snapshot, meal), 0.0001)
    }

    @Test
    fun stockWithUnsupportedConversionDoesNotCoverAComponent() {
        val catalog = FoodCatalogItem(
            id = "unconvertible-catalog",
            name = "Unknown unit flour",
            stockUnit = "crate",
            portionUnit = "g",
            portionsPerStockUnit = 1.0,
            defaultCostPerPortion = 3.0,
            conversionKnown = false,
        )
        val snapshot = AppSnapshot(
            foodCatalog = listOf(catalog),
            stock = listOf(
                StockItem(
                    id = "unconvertible-stock",
                    catalogId = catalog.id,
                    name = catalog.name,
                    unit = "crate",
                    totalQuantity = 100.0,
                    totalPrice = 50.0,
                ),
            ),
        )
        val meal = template(
            MealComponent(
                id = "unconvertible-component",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 1.0,
                unit = "g",
                costPerUnit = 3.0,
            ),
        )

        assertEquals(0.0, BudgetMath.inStockCompleteness(snapshot, meal), 0.0001)
        assertTrue(BudgetMath.extraPurchasesNeeded(snapshot, meal) > 0.0)
    }

    private fun spoonCatalog() = FoodCatalogItem(
        id = "spoon-catalog",
        name = "Test paste",
        stockUnit = "jar (100g)",
        portionUnit = "spoon",
        portionsPerStockUnit = 10.0,
        defaultCostPerPortion = 5.0,
        purchasePrice = 50.0,
        purchaseQuantity = 1.0,
        purchaseUnit = "jar (100g)",
        notes = FoodMeasurementCodec.encodeNotes(
            userNotes = "",
            measurement = FoodMeasurementType.SpoonsToGrams(10.0),
        ),
    )

    private fun template(vararg components: MealComponent) = MealTemplate(
        id = "quantity-regression-meal",
        name = "Quantity regression meal",
        mealType = MealType.LUNCH,
        cost = components.sumOf { it.estimatedCost },
        components = components.toList(),
    )
}
