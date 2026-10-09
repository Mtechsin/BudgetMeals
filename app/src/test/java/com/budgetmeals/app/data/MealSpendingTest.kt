package com.budgetmeals.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MealSpendingTest {
    private fun meal(
        cost: Double = 100.0,
        status: MealStatus = MealStatus.EATEN,
        consumedCost: Double = cost,
        components: List<MealComponent> = emptyList(),
    ) = MealLog(
        id = "meal-1",
        date = LocalDate.of(2026, 10, 8),
        mealType = MealType.LUNCH,
        name = "Lunch",
        cost = cost,
        consumedCost = consumedCost,
        status = status,
        components = components,
    )

    @Test
    fun mealsWithoutComponentsOrTrackedStockChargeTheirFullCost() {
        assertEquals(40.0, MealSpending.cashCost(meal(cost = 40.0), emptyMap()), 0.000001)
        val component = MealComponent(id = "rice", name = "Rice", quantity = 2.0, costPerUnit = 20.0)
        assertEquals(40.0, MealSpending.cashCost(meal(cost = 40.0, components = listOf(component)), emptyMap()), 0.000001)
    }

    @Test
    fun completeStockCoverageDoesNotCreateCashSpending() {
        val rice = MealComponent(id = "rice", name = "Rice", quantity = 2.0, costPerUnit = 20.0)
        assertEquals(
            0.0,
            MealSpending.cashCost(meal(components = listOf(rice)), mapOf("rice" to MealStockCoverage(2.0, 40.0))),
            0.000001,
        )
    }

    @Test
    fun mixedIngredientCoverageUsesUnequalPricesAndPartialQuantities() {
        val rice = MealComponent(id = "rice", name = "Rice", quantity = 2.0, costPerUnit = 10.0)
        val chicken = MealComponent(id = "chicken", name = "Chicken", quantity = 1.0, costPerUnit = 80.0)

        // Stock covers 1.5/2 rice (15 EGP of 20) and 0.25/1 chicken (20 EGP of 80).
        assertEquals(
            65.0,
            MealSpending.cashCost(
                meal(components = listOf(rice, chicken)),
                mapOf("rice" to MealStockCoverage(1.5, 15.0), "chicken" to MealStockCoverage(0.25, 20.0)),
            ),
            0.000001,
        )
    }

    @Test
    fun mealCostOverrideScalesTheIngredientCoverageProportion() {
        val rice = MealComponent(id = "rice", name = "Rice", quantity = 2.0, costPerUnit = 10.0)
        val chicken = MealComponent(id = "chicken", name = "Chicken", quantity = 1.0, costPerUnit = 80.0)

        assertEquals(
            130.0,
            MealSpending.cashCost(
                meal(cost = 200.0, components = listOf(rice, chicken)),
                mapOf("rice" to MealStockCoverage(1.5, 15.0), "chicken" to MealStockCoverage(0.25, 20.0)),
            ),
            0.000001,
        )
    }

    @Test
    fun optedOutIngredientsRemainCashCostEvenWhenStockCoverageIsProvided() {
        val purchased = MealComponent(
            id = "rice",
            name = "Rice",
            quantity = 2.0,
            costPerUnit = 20.0,
            useStock = false,
        )
        assertEquals(
            40.0,
            MealSpending.cashCost(meal(cost = 40.0, components = listOf(purchased)), mapOf("rice" to MealStockCoverage(2.0, 40.0))),
            0.000001,
        )
    }

    @Test
    fun plannedSkippedAndUnrecordedMealsHaveNoCashSpending() {
        val component = MealComponent(id = "rice", name = "Rice", quantity = 1.0, costPerUnit = 20.0)
        listOf(MealStatus.PLANNED, MealStatus.SKIPPED, MealStatus.UNRECORDED).forEach { status ->
            assertEquals(
                "$status should not be charged",
                0.0,
                MealSpending.cashCost(meal(cost = 20.0, status = status, components = listOf(component)), emptyMap()),
                0.000001,
            )
        }
    }

    @Test
    fun partialMealStillChargesFullRecipeCostInsteadOfConsumedCost() {
        val meal = meal(cost = 50.0, consumedCost = 12.0, status = MealStatus.PARTIAL)
        assertEquals(38.0, meal.leftoverCost, 0.000001)
        assertEquals(50.0, MealSpending.cashCost(meal, emptyMap()), 0.000001)
    }

    @Test
    fun manuallyPricedMealCreditsRecordedStockValue() {
        val rice = MealComponent(id = "rice", name = "Rice", quantity = 2.0, costPerUnit = 0.0)
        val chicken = MealComponent(id = "chicken", name = "Chicken", quantity = 1.0, costPerUnit = 0.0)

        assertEquals(
            42.5,
            MealSpending.cashCost(
                meal(cost = 50.0, components = listOf(rice, chicken)),
                mapOf("rice" to MealStockCoverage(1.0, 7.5)),
            ),
            0.000001,
        )
        assertEquals(
            0.0,
            MealSpending.cashCost(
                meal(cost = 50.0, components = listOf(rice)),
                mapOf("rice" to MealStockCoverage(2.0, 20.0)),
            ),
            0.000001,
        )
    }

    @Test
    fun missingPriceOnOneIngredientUsesRecordedCoverageForManualFallback() {
        val rice = MealComponent(id = "rice", name = "Rice", quantity = 1.0, costPerUnit = 40.0)
        val chicken = MealComponent(id = "chicken", name = "Chicken", quantity = 1.0, costPerUnit = 0.0)

        assertEquals(
            60.0,
            MealSpending.cashCost(
                meal(cost = 100.0, components = listOf(rice, chicken)),
                mapOf("rice" to MealStockCoverage(1.0, 40.0)),
            ),
            0.000001,
        )
    }

    @Test
    fun nonfiniteIngredientPriceUsesManualFallbackWithoutProducingNan() {
        val rice = MealComponent(id = "rice", name = "Rice", quantity = 1.0, costPerUnit = 40.0)
        val chicken = MealComponent(id = "chicken", name = "Chicken", quantity = 1.0, costPerUnit = Double.NaN)

        assertEquals(
            60.0,
            MealSpending.cashCost(
                meal(cost = 100.0, components = listOf(rice, chicken)),
                mapOf("rice" to MealStockCoverage(1.0, 40.0)),
            ),
            0.000001,
        )
    }

    @Test
    fun tinyFractionalStockQuantitiesAreComparedByTheirFraction() {
        val spice = MealComponent(id = "spice", name = "Spice", quantity = 0.0003, costPerUnit = 10.0)
        assertEquals(
            0.006666666666666667,
            MealSpending.cashCost(meal(cost = 0.01, components = listOf(spice)), mapOf("spice" to MealStockCoverage(0.0001, 0.0001))),
            0.000000001,
        )
    }

    @Test
    fun expenseIdIsStableAndLinkedToTheMealLog() {
        assertEquals("meal-log:meal-1", MealSpending.expenseId("meal-1"))
    }
}
