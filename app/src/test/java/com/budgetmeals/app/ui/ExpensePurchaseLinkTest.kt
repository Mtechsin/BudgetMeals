package com.budgetmeals.app.ui

import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.StockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpensePurchaseLinkTest {
    @Test
    fun currentAndLegacyPurchaseExpensesRouteThroughTheirStock() {
        val stock = StockItem(id = "rice", name = "Rice", totalQuantity = 1.0, totalPrice = 50.0, linkedExpenseId = "purchase-rice")

        assertEquals(stock, findPurchaseForExpense(listOf(stock), expense("purchase-rice")))
        assertEquals(stock, findPurchaseForExpense(listOf(stock), expense("stock-rice")))
    }

    @Test
    fun sameLookingManualExpenseRemainsEditable() {
        val stock = StockItem(id = "rice", name = "Rice", totalQuantity = 1.0, totalPrice = 50.0, linkedExpenseId = "purchase-rice")

        assertNull(findPurchaseForExpense(listOf(stock), expense("manual-rice")))
    }

    @Test
    fun historicalPurchaseWithNoRemainingStockRowIsNotBlocked() {
        assertNull(findPurchaseForExpense(emptyList(), expense("stock-rice")))
    }

    private fun expense(id: String) = Expense(id = id, categoryId = "food", amount = 50.0, description = "Bought Rice")
}
