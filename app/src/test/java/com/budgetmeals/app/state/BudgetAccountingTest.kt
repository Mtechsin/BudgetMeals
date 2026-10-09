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
}
