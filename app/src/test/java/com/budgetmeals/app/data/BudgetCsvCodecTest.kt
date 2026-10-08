package com.budgetmeals.app.data

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCsvCodecTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun escapesCommasQuotesAndCarriageReturnsInExpenseText() {
        val category = ExpenseCategory(
            id = "food",
            name = "Food, \"fresh\"",
            icon = "restaurant",
            monthlyBudget = 1_000.0,
            isFood = true,
        )
        val expense = Expense(
            id = "rice",
            categoryId = category.id,
            amount = 12.5,
            date = LocalDate.of(2026, 10, 3),
            description = "Rice,\r\ncalled \"basmati\"",
        )
        val snapshot = AppSnapshot(
            today = today,
            categories = listOf(category),
            expenses = listOf(expense),
        )

        val csv = BudgetCsvCodec.encode(snapshot, exportedOn = today)

        assertTrue(
            csv.contains("2026-10-03,\"Food, \"\"fresh\"\"\",12.50,\"Rice,\r\ncalled \"\"basmati\"\"\",false,false"),
        )
    }

    @Test
    fun includesTheExplicitExportDate() {
        val snapshot = AppSnapshot(today = today)
        val exportDate = LocalDate.of(2026, 10, 12)

        val csv = BudgetCsvCodec.encode(snapshot, exportedOn = exportDate)

        assertTrue(csv.contains("Exported,2026-10-12"))
    }

    @Test
    fun formatsDecimalsIndependentlyOfTheDefaultLocale() {
        val category = ExpenseCategory(
            id = "food",
            name = "Food",
            icon = "restaurant",
            monthlyBudget = 1_000.0,
            isFood = true,
        )
        val snapshot = AppSnapshot(
            today = today,
            categories = listOf(category),
            expenses = listOf(
                Expense(
                    id = "bread",
                    categoryId = category.id,
                    amount = 12.5,
                    date = today,
                    description = "Bread",
                ),
            ),
        )
        val previousLocale = Locale.getDefault()

        try {
            Locale.setDefault(Locale.GERMANY)

            val csv = BudgetCsvCodec.encode(snapshot, exportedOn = today)

            assertTrue(csv.contains(",12.50,Bread,"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
