package com.budgetmeals.app.state

import com.budgetmeals.app.data.StockItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityFormattingTest {
    @Test
    fun largeIntegerQuantityDoesNotOverflowInt() {
        assertEquals("3000000000", BudgetMath.quantity(3_000_000_000.0))
    }

    @Test
    fun stockQuantityLabelKeepsLargeIntegerAmounts() {
        val item = StockItem(
            name = "Counted pieces",
            unit = "piece",
            totalQuantity = 3_000_000_000.0,
            totalPrice = 30_000.0,
            purchaseDate = LocalDate.of(2026, 10, 8),
        )

        assertEquals("3000000000 piece", item.quantityLabel)
    }
}
