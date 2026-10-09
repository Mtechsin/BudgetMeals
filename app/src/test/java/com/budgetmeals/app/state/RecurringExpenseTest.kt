package com.budgetmeals.app.state

import com.budgetmeals.app.data.Expense
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringExpenseTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun recurringExpenseGeneratedForCurrentMonthWhenMarkedRecurringInPreviousMonth() {
        val testToday = LocalDate.of(2026, 10, 5)
        val lastMonthDate = testToday.minusMonths(1).withDayOfMonth(5)
        val previousExpense = Expense(
            id = "prev-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = lastMonthDate,
            description = "Internet home VDSL",
            isRecurring = true,
            recurringFrequency = "MONTHLY",
        )

        val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousExpense),
            today = testToday,
        )

        assertEquals(1, toGenerate.size)
        val generated = toGenerate.first()
        assertEquals("internet-cat", generated.categoryId)
        assertEquals(350.0, generated.amount, 0.001)
        assertEquals("Internet home VDSL", generated.description)
        assertTrue(generated.isRecurring)
        assertEquals(testToday.year, generated.date.year)
        assertEquals(testToday.month, generated.date.month)
        assertEquals(minOf(5, testToday.lengthOfMonth()), generated.date.dayOfMonth)
    }

    @Test
    fun recurringExpenseIsNotGeneratedBeforeDueDay() {
        val previousExpense = Expense(
            id = "prev-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = LocalDate.of(2026, 9, 5),
            description = "Internet home VDSL",
            isRecurring = true,
            recurringFrequency = "MONTHLY",
        )
        assertTrue(BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousExpense),
            today = LocalDate.of(2026, 10, 2),
        ).isEmpty())
    }

    @Test
    fun recurringExpenseNotDuplicatedIfAlreadyPresentInCurrentMonth() {
        val lastMonthDate = today.minusMonths(1).withDayOfMonth(5)
        val currentMonthDate = today.withDayOfMonth(5)
        val previousExpense = Expense(
            id = "prev-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = lastMonthDate,
            description = "Internet home VDSL",
            isRecurring = true,
        )
        val existingThisMonth = Expense(
            id = "current-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = currentMonthDate,
            description = "Internet home VDSL",
            isRecurring = true,
        )

        val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousExpense, existingThisMonth),
            today = currentMonthDate,
        )

        assertTrue("Should not duplicate existing recurring expense", toGenerate.isEmpty())
    }

    @Test
    fun nonRecurringExpensesFromPreviousMonthAreNotGenerated() {
        val lastMonthDate = today.minusMonths(1).withDayOfMonth(10)
        val previousNonRecurring = Expense(
            id = "prev-dinner",
            categoryId = "food",
            amount = 120.0,
            date = lastMonthDate,
            description = "Restaurant dinner",
            isRecurring = false,
        )

        val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousNonRecurring),
            today = today,
        )

        assertTrue(toGenerate.isEmpty())
    }
}
