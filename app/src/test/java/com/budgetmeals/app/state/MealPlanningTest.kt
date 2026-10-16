package com.budgetmeals.app.state

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.DailyMealPlan
import com.budgetmeals.app.data.DayClosure
import com.budgetmeals.app.data.DynamicMealDefaults
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealLog
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanningTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun starterRecipesUseDeclaredCatalogUnitsAndKeepIngredientPriceTotals() {
        val expectedTotals = mapOf(
            "fuul_combo" to 40.0,
            "koshary_day" to 68.5,
            "egg_dinner" to 28.0,
            "cheese_plate" to 14.3,
            "jam_breakfast" to 18.0,
            "falafel_night" to 30.1,
            "chips_treat" to 21.93,
            "ful_sandwich" to 30.0,
            "yogurt_breakfast" to 23.0,
        )
        val snapshot = AppSnapshot(today = today, foodCatalog = DynamicMealDefaults.catalog)
        for ((id, expectedTotal) in expectedTotals) {
            val components = DynamicMealDefaults.componentsForTemplate(id, emptyMap())
            val template = MealTemplate(id = id, name = id, mealType = MealType.LUNCH, cost = expectedTotal, components = components)

            assertEquals(expectedTotal, components.sumOf { it.estimatedCost }, 0.0001)
            assertEquals(expectedTotal, BudgetMath.extraPurchasesNeeded(snapshot, template), 0.0001)
        }
    }


    @Test
    fun daySummaryIncludesLogsForAbsentAndClearedSlotsOnce() {
        val snapshot = AppSnapshot(
            today = today,
            dayPlans = listOf(
                DailyMealPlan("breakfast", today, MealType.BREAKFAST, null, "Breakfast", 10.0, ""),
                DailyMealPlan("cleared-lunch", today, MealType.LUNCH, null, "", 0.0, "", isCleared = true),
            ),
            mealLogs = listOf(
                MealLog(id = "breakfast-log", date = today, mealType = MealType.BREAKFAST, name = "Breakfast", cost = 12.0, actualTime = today.atTime(8, 0)),
                MealLog(id = "old-lunch", date = today, mealType = MealType.LUNCH, name = "Lunch", cost = 20.0, actualTime = today.atTime(12, 0)),
                MealLog(id = "latest-lunch", date = today, mealType = MealType.LUNCH, name = "Lunch", cost = 30.0, consumedCost = 20.0, status = MealStatus.PARTIAL, actualTime = today.atTime(13, 0)),
                MealLog(id = "extra-snack", date = today, mealType = MealType.SNACK, name = "Snack", cost = 5.0, actualTime = today.atTime(15, 0)),
                MealLog(id = "yesterday", date = today.minusDays(1), mealType = MealType.DINNER, name = "Dinner", cost = 100.0, actualTime = today.minusDays(1).atTime(20, 0)),
            ),
        )

        val summary = BudgetMath.dayMealSummary(snapshot, today)

        assertEquals(47.0, summary.plannedCost, 0.001)
        assertEquals(37.0, summary.consumedCost, 0.001)
        assertEquals(10.0, summary.leftoverCost, 0.001)
        assertEquals(3, summary.consumedMeals)
        assertEquals(1, summary.leftoverMeals)
        assertEquals(0, summary.pendingMeals)
    }


    @Test
    fun weeklyBudgetDoesNotReuseTheSameStockForEveryDay() {
        val catalog = FoodCatalogItem(id = "egg", name = "Egg", portionUnit = "egg", stockUnit = "egg", defaultCostPerPortion = 10.0)
        val eggMeal = MealTemplate(
            id = "egg-meal", name = "Egg breakfast", mealType = MealType.BREAKFAST, cost = 10.0,
            components = listOf(MealComponent(catalogId = catalog.id, name = catalog.name, unit = "egg", costPerUnit = 10.0)),
        )
        val cashMeal = MealTemplate(id = "cash-meal", name = "Small breakfast", mealType = MealType.BREAKFAST, cost = 5.0)
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(catalog),
            stock = listOf(StockItem(name = "Egg", catalogId = catalog.id, unit = "egg", totalQuantity = 1.0, totalPrice = 10.0, purchaseDate = today.minusDays(7))),
            templates = listOf(eggMeal, cashMeal),
            settings = com.budgetmeals.app.data.BudgetSettings(weeklyFoodLimit = 30.0),
        )

        val repeated = BudgetMath.mealPlanDates(today).map { WeeklyPlanDay(it, mapOf(MealType.BREAKFAST to eggMeal)) }
        assertEquals(60.0, BudgetMath.extraPurchasesForPlan(snapshot, repeated), 0.001)
        val week = buildBalancedWeek(snapshot, today)
        assertEquals(30.0, BudgetMath.extraPurchasesForPlan(snapshot, week), 0.001)
        assertEquals(1, week.count { it.meals[MealType.BREAKFAST]?.id == eggMeal.id })
    }


    @Test
    fun persistedDayPlanDoesNotChangeWhenTemplatesChange() {
        val savedPlan = DailyMealPlan(
            id = "saved-lunch",
            date = today,
            mealType = MealType.LUNCH,
            templateId = "old-template",
            name = "Saved lunch",
            cost = 55.0,
            foods = "Rice, beans, salad",
        )
        val snapshot = AppSnapshot(
            today = today,
            dayPlans = listOf(savedPlan),
        )

        val planned = BudgetMath.plannedMealsForDate(snapshot, today)
        val summary = BudgetMath.dayMealSummary(snapshot, today)

        assertEquals("Saved lunch", planned.getValue(MealType.LUNCH).name)
        assertEquals(55.0, summary.plannedCost, 0.001)
        assertEquals(1, summary.pendingMeals)
    }

    @Test
    fun explicitStatusHandlesAZeroCostEatenMeal() {
        val plan = DailyMealPlan(
            id = "zero-cost-breakfast",
            date = today,
            mealType = MealType.BREAKFAST,
            templateId = "free-breakfast",
            name = "Free breakfast",
            cost = 0.0,
            foods = "Tea",
        )
        val log = MealLog(
            actualTime = today.atTime(12, 0),
            date = today,
            mealType = MealType.BREAKFAST,
            name = plan.name,
            cost = 0.0,
            consumedCost = 0.0,
            status = MealStatus.EATEN,
        )
        val snapshot = AppSnapshot(
            today = today,
            dayPlans = listOf(plan),
            mealLogs = listOf(log),
            dayClosures = listOf(
                DayClosure(
                    date = today,
                    closedAt = today.atTime(23, 0),
                    plannedCost = 0.0,
                    consumedCost = 0.0,
                    leftoverCost = 0.0,
                    skippedCost = 0.0,
                ),
            ),
        )

        val summary = BudgetMath.dayMealSummary(snapshot, today)

        assertEquals(1, summary.consumedMeals)
        assertEquals(0, summary.skippedMeals)
        assertEquals(0, summary.pendingMeals)
        assertTrue(summary.isClosed)
    }

    @Test
    fun mealPlanWindowAlwaysIncludesFullWeek() {
        val saturday = LocalDate.of(2026, 9, 26)
        val saturdayWindow = BudgetMath.mealPlanDates(saturday)
        val mondayWindow = BudgetMath.mealPlanDates(LocalDate.of(2026, 9, 28))
        val fridayWindow = BudgetMath.mealPlanDates(LocalDate.of(2026, 10, 2))

        assertEquals(7, fridayWindow.size)
        assertEquals(LocalDate.of(2026, 10, 2), fridayWindow.first())
        assertEquals(LocalDate.of(2026, 10, 8), fridayWindow.last())

        assertEquals(7, saturdayWindow.size)
        assertEquals(saturday, saturdayWindow.first())
        assertEquals(LocalDate.of(2026, 10, 2), saturdayWindow.last())

        assertEquals(7, mondayWindow.size)
        assertEquals(LocalDate.of(2026, 9, 28), mondayWindow.first())
        assertEquals(LocalDate.of(2026, 10, 4), mondayWindow.last())
    }

    @Test
    fun suggestedMealsPrioritizesInStockAndChecksExtraPurchases() {
        val eggsCatalog = FoodCatalogItem(
            id = "eggs",
            name = "Eggs",
            purchaseUnit = "carton",
            purchasePrice = 120.0,
            portionUnit = "piece",
            portionsPerStockUnit = 30.0,
            defaultCostPerPortion = 4.0,
            priceKnown = true,
            conversionKnown = true,
        )
        val chickenCatalog = FoodCatalogItem(
            id = "chicken",
            name = "Chicken",
            purchaseUnit = "kg",
            purchasePrice = 160.0,
            portionUnit = "portion",
            portionsPerStockUnit = 4.0,
            defaultCostPerPortion = 40.0,
            priceKnown = true,
            conversionKnown = true,
        )
        // Eggs are in stock
        val eggStock = StockItem(
            id = "egg-stock",
            catalogId = "eggs",
            name = "Eggs",
            totalQuantity = 10.0,
            unit = "piece",
            totalPrice = 40.0,
            purchaseDate = today,
        )
        val omelet = MealTemplate(
            id = "omelet",
            name = "Omelet",
            mealType = MealType.BREAKFAST,
            cost = 12.0,
            components = listOf(
                MealComponent(
                    name = "Eggs",
                    catalogId = "eggs",
                    quantity = 3.0,
                    unit = "piece",
                    costPerUnit = 4.0,
                    useStock = true,
                ),
            ),
        )
        val chickenMeal = MealTemplate(
            id = "chicken-meal",
            name = "Chicken Meal",
            mealType = MealType.LUNCH,
            cost = 40.0,
            components = listOf(
                MealComponent(
                    name = "Chicken",
                    catalogId = "chicken",
                    quantity = 1.0,
                    unit = "portion",
                    costPerUnit = 40.0,
                    useStock = true,
                ),
            ),
        )

        // Remaining budget is 20 EGP. The omelet is in stock; the chicken meal needs 40 EGP.
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(eggsCatalog, chickenCatalog),
            stock = listOf(eggStock),
            templates = listOf(omelet, chickenMeal),
            settings = com.budgetmeals.app.data.BudgetSettings(dailyFoodBudget = 20.0),
        )

        assertEquals(0.0, BudgetMath.extraPurchasesNeeded(snapshot, omelet), 0.001)
        assertEquals(40.0, BudgetMath.extraPurchasesNeeded(snapshot, chickenMeal), 0.001)

        val suggestions = BudgetMath.suggestedMeals(snapshot)
        assertTrue(suggestions.any { it.id == "omelet" })
        assertFalse(suggestions.any { it.id == "chicken-meal" })
    }

    @Test
    fun mealCostEstimateIncompleteWhenPriceIsUnknown() {
        val knownCatalog = FoodCatalogItem(
            id = "known",
            name = "Known Item",
            defaultCostPerPortion = 10.0,
            priceKnown = true,
        )
        val unknownCatalog = FoodCatalogItem(
            id = "unknown",
            name = "Unknown Item",
            defaultCostPerPortion = 0.0,
            priceKnown = false,
        )
        val completeTemplate = MealTemplate(
            id = "complete-meal",
            name = "Complete Meal",
            mealType = MealType.LUNCH,
            cost = 10.0,
            components = listOf(
                MealComponent(name = "Known", catalogId = "known", quantity = 1.0, costPerUnit = 10.0)
            ),
        )
        val incompleteTemplate = MealTemplate(
            id = "incomplete-meal",
            name = "Incomplete Meal",
            mealType = MealType.LUNCH,
            cost = 10.0,
            components = listOf(
                MealComponent(name = "Known", catalogId = "known", quantity = 1.0, costPerUnit = 10.0),
                MealComponent(name = "Unknown", catalogId = "unknown", quantity = 1.0, costPerUnit = 0.0)
            ),
        )
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(knownCatalog, unknownCatalog),
            templates = listOf(completeTemplate, incompleteTemplate),
        )

        assertFalse(BudgetMath.isMealCostIncomplete(snapshot, completeTemplate))
        assertTrue(BudgetMath.isMealCostIncomplete(snapshot, incompleteTemplate))
    }

    @Test
    fun plannedMealsForDateFiltersClearedMeals() {
        val activeMeal = DailyMealPlan(
            id = "plan-lunch",
            date = today,
            mealType = MealType.LUNCH,
            templateId = "template-pasta",
            name = "Pasta",
            cost = 30.0,
            foods = "Pasta",
            isCleared = false,
        )
        val clearedMeal = DailyMealPlan(
            id = "plan-snack",
            date = today,
            mealType = MealType.SNACK,
            templateId = null,
            name = "Empty",
            cost = 0.0,
            foods = "",
            isCleared = true,
        )
        val snapshot = AppSnapshot(
            today = today,
            dayPlans = listOf(activeMeal, clearedMeal),
        )

        val planned = BudgetMath.plannedMealsForDate(snapshot, today)
        assertTrue(planned.containsKey(MealType.LUNCH))
        assertFalse(
            "Cleared meals must not be present in planned meals map",
            planned.containsKey(MealType.SNACK),
        )
    }

    @Test
    fun buildBalancedWeekProducesVarietyAcrossDays() {
        val lunch1 = MealTemplate(
            id = "l1",
            name = "Lentils",
            mealType = MealType.LUNCH,
            cost = 15.0,
            isRecurring = true,
        )
        val lunch2 = MealTemplate(
            id = "l2",
            name = "Ful Mudammas",
            mealType = MealType.LUNCH,
            cost = 16.0,
            isRecurring = true,
        )
        val lunch3 = MealTemplate(
            id = "l3",
            name = "Vegetable Tagine",
            mealType = MealType.LUNCH,
            cost = 18.0,
            isRecurring = true,
        )
        val snapshot = AppSnapshot(
            today = today,
            templates = listOf(lunch1, lunch2, lunch3),
        )

        val week = buildBalancedWeek(snapshot, BudgetMath.currentWeekStart(today))
        assertEquals(7, week.size)

        val lunchNames = week.mapNotNull { it.meals[MealType.LUNCH]?.name }
        val distinctLunches = lunchNames.distinct()
        assertTrue(
            "Week plan should include varied lunch options, found: $distinctLunches",
            distinctLunches.size > 1,
        )
    }
}
