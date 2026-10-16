package com.budgetmeals.app.state

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.ExpenseCategory
import com.budgetmeals.app.data.MealLog
import com.budgetmeals.app.data.MealSpending
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.data.MealType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BudgetAccountingTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun historicalCategorySpendingExcludesBudgetTransfers() {
        val lastMonth = today.minusMonths(1)
        val snapshot = AppSnapshot(today = today, expenses = listOf(
            Expense(categoryId = "food", amount = 25.0, date = lastMonth),
            Expense(categoryId = "food", amount = 100.0, date = lastMonth, isBudgetTransfer = true),
        ))

        assertEquals(25.0, BudgetMath.categorySpent(snapshot, "food", lastMonth), 0.001)
    }

    private val foodCategory = ExpenseCategory(
        id = "food",
        name = "Food",
        icon = "restaurant",
        monthlyBudget = 3_000.0,
        isFood = true,
    )

    @Test
    fun linkedMealExpenseIsCountedOnceFromTheExpenseLedger() {
        val snapshot = AppSnapshot(
            today = today,
            categories = listOf(foodCategory),
            expenses = listOf(
                Expense(categoryId = "food", amount = 100.0, date = today),
                Expense(
                    id = MealSpending.expenseId("lunch-1"),
                    categoryId = "food",
                    amount = 40.0,
                    date = today,
                    description = "Lunch",
                ),
            ),
            mealLogs = listOf(
                MealLog(
                    id = "lunch-1",
                    actualTime = today.atTime(12, 0),
                    date = today,
                    mealType = MealType.LUNCH,
                    name = "Lunch",
                    cost = 40.0,
                    status = MealStatus.EATEN,
                ),
            ),
        )

        assertEquals(140.0, snapshot.foodSpentThisMonth, 0.001)
        assertEquals(140.0, BudgetMath.foodSpentOn(today, snapshot), 0.001)
    }

    @Test
    fun foodCorrectionsRemainPartOfTheExpenseLedger() {
        val snapshot = AppSnapshot(
            today = today,
            categories = listOf(foodCategory),
            expenses = listOf(
                Expense(categoryId = "food", amount = 100.0, date = today),
                Expense(categoryId = "food", amount = 30.0, date = today, isCorrection = true),
                Expense(categoryId = "food", amount = -10.0, date = today, isCorrection = true),
            ),
        )

        assertEquals(120.0, snapshot.foodSpentThisMonth, 0.001)
    }

    @Test
    fun moneyFormattingSupportsFractionsUnderTenPiasters() {
        assertEquals("0.035 EGP", BudgetMath.money(0.035))
        assertEquals("0.005 EGP", BudgetMath.money(0.005))
        assertEquals("0.5 EGP", BudgetMath.money(0.5))
        assertEquals("15 EGP", BudgetMath.money(15.0))
        assertEquals("15.5 EGP", BudgetMath.money(15.5))
        assertEquals("15.75 EGP", BudgetMath.money(15.75))
    }

    @Test
    fun todayFoodRemainingCannotExceedFoodRemainingThisMonth() {
        val settings = com.budgetmeals.app.data.BudgetSettings(
            monthlyFoodBudget = 1000.0,
        )
        val snapshot = AppSnapshot(
            today = today,
            settings = settings,
            categories = listOf(foodCategory),
            expenses = listOf(
                Expense(categoryId = "food", amount = 980.0, date = today.minusDays(1)),
            ),
        )
        assertEquals(20.0, snapshot.foodRemainingThisMonth, 0.001)
        assertEquals(20.0, snapshot.todayFoodRemaining, 0.001)
    }

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
    fun compactMoneyFormatsThousandSuffixProperly() {
        assertEquals("1k", BudgetMath.compactMoney(1000.0))
        assertEquals("2k", BudgetMath.compactMoney(2000.0))
        assertEquals("100", BudgetMath.compactMoney(100.0))
        assertEquals("500", BudgetMath.compactMoney(500.0))
        assertEquals("1500", BudgetMath.compactMoney(1500.0))
    }

    @Test
    fun inStockCompletenessReturnsZeroWhenNoStockComponents() {
        val templateNoComponents = com.budgetmeals.app.data.MealTemplate(
            id = "t1",
            name = "Takeout",
            mealType = MealType.DINNER,
            cost = 50.0,
            components = emptyList(),
        )
        val templateCashOnly = com.budgetmeals.app.data.MealTemplate(
            id = "t2",
            name = "Cash Meal",
            mealType = MealType.DINNER,
            cost = 50.0,
            components = listOf(
                com.budgetmeals.app.data.MealComponent(
                    name = "Sandwich",
                    quantity = 1.0,
                    unit = "piece",
                    useStock = false,
                )
            ),
        )
        val snapshot = AppSnapshot(today = today)
        assertEquals(0.0, BudgetMath.inStockCompleteness(snapshot, templateNoComponents), 0.001)
        assertEquals(0.0, BudgetMath.inStockCompleteness(snapshot, templateCashOnly), 0.001)
    }

    @Test
    fun sparesStreakCountsFromYesterdayIfTodayNotRecorded() {
        val snapshot = AppSnapshot(
            today = today,
            spares = listOf(
                com.budgetmeals.app.data.SparesTransaction(
                    amount = 10.0,
                    reason = "Food budget spare",
                    date = today.minusDays(1),
                ),
                com.budgetmeals.app.data.SparesTransaction(
                    amount = 15.0,
                    reason = "Food budget spare",
                    date = today.minusDays(2),
                ),
            ),
        )
        assertEquals(2, BudgetMath.sparesStreak(snapshot, today))
    }

    @Test
    fun foodSpentOnIgnoresBudgetTransfers() {
        val snapshot = AppSnapshot(
            today = today,
            categories = listOf(foodCategory),
            expenses = listOf(
                Expense(categoryId = "food", amount = 100.0, date = today),
                Expense(categoryId = "food", amount = 500.0, date = today, isBudgetTransfer = true),
            ),
        )
        assertEquals(100.0, BudgetMath.foodSpentOn(today, snapshot), 0.001)
    }

    @Test
    fun mealLogConsumedRatioHandlesZeroCostWithoutNaN() {
        val logZeroCost = MealLog(
            name = "Zero cost meal",
            mealType = MealType.LUNCH,
            cost = 0.0,
            consumedCost = 0.0,
            status = MealStatus.PLANNED,
        )
        assertEquals(0.0, logZeroCost.consumedRatio, 0.0)

        val eatenZeroCost = MealLog(
            name = "Eaten zero cost meal",
            mealType = MealType.LUNCH,
            cost = 0.0,
            consumedCost = 0.0,
            status = MealStatus.EATEN,
        )
        assertEquals(1.0, eatenZeroCost.consumedRatio, 0.0)
    }

    @Test
    fun budgetMathEpsilonConstantsAreExposed() {
        assertEquals(0.01, BudgetMath.CURRENCY_EPSILON, 0.0)
        assertEquals(0.0001, BudgetMath.QUANTITY_EPSILON, 0.0)
    }
}
