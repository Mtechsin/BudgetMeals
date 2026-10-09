package com.budgetmeals.app.data

import java.time.LocalDate
import java.util.Locale

internal object BudgetCsvCodec {
    fun encode(snapshot: AppSnapshot, exportedOn: LocalDate): String {
        return buildString {
            appendLine("BudgetMeals export")
            appendLine("Exported,$exportedOn")
            appendLine()
            appendLine("Expenses")
            appendLine("Date,Category,Amount EGP,Description,Recurring,Correction")
            snapshot.expenses.sortedBy { it.date }.forEach { expense ->
                val category = snapshot.categories.firstOrNull { it.id == expense.categoryId }?.name ?: "Other"
                appendLine("${expense.date},${csvEscape(category)},${money(expense.amount)},${csvEscape(expense.description)},${expense.isRecurring},${expense.isCorrection}")
            }
            appendLine()
            appendLine("Stock")
            appendLine("Name,Category,Quantity,Unit,Price,Remaining,Days left,Cost per day")
            snapshot.stock.forEach { item ->
                appendLine("${csvEscape(item.name)},${item.category.label},${number(item.totalQuantity)},${csvEscape(item.unit)},${money(item.totalPrice)},${number(item.remainingQuantity)},${number(item.daysRemaining)},${money(item.costPerDay)}")
            }
            appendLine()
            appendLine("Shopping list")
            appendLine("Name,Quantity,Unit,Estimated price,Checked")
            snapshot.shopping.forEach { item ->
                appendLine("${csvEscape(item.name)},${number(item.quantity)},${csvEscape(item.unit)},${money(item.estimatedPrice)},${item.isChecked}")
            }
            appendLine()
            appendLine("Spares")
            appendLine("Date,Amount EGP,Reason,Category")
            snapshot.spares.forEach { transaction ->
                appendLine("${transaction.date},${money(transaction.amount)},${csvEscape(transaction.reason)},${csvEscape(transaction.category.orEmpty())}")
            }
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun number(value: Double): String = String.format(Locale.US, "%.3f", value)
}
