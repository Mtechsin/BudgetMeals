package com.budgetmeals.app.ui

import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.StockItem

/** Purchase cash and stock valuation must be changed together through the stock editor. */
internal fun findPurchaseForExpense(stock: List<StockItem>, expense: Expense): StockItem? =
    stock.firstOrNull { it.linkedExpenseId == expense.id || expense.id == "stock-${it.id}" }
