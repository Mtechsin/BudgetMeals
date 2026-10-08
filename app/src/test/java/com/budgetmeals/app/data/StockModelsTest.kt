package com.budgetmeals.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockModelsTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun singleMealBatchIsImmediatelyConsumedOnPurchaseDay() {
        val item = StockItem(
            purchaseDate = today,
            name = "Takeout Sandwich",
            totalQuantity = 1.0,
            unit = "portion",
            totalPrice = 45.0,
            batchType = BatchType.SINGLE_MEAL,
            consumedQuantity = 1.0,
            usageHistory = listOf(1.0),
            datedUsageHistory = listOf(DatedUsage(today, 1.0)),
        )
        assertEquals(0.0, item.remainingQuantity, 0.001)
        assertEquals(0.0, item.remainingPercent, 0.001)
        assertTrue(item.isFinished)
        assertEquals("Finished", item.daysLabel)
    }

    @Test
    fun batchStockPackageSizeIsolation() {
        val catalogItem = FoodCatalogItem(
            id = "jam_catalog",
            name = "Strawberry Jam",
            stockUnit = "jar (400g)",
            portionUnit = "portion",
            portionsPerStockUnit = 20.0,
        )
        // Batch 1: Standard 400g jar
        val batch1 = StockItem(
            purchaseDate = today,
            name = "Strawberry Jam",
            unit = "g",
            totalQuantity = 400.0,
            totalPrice = 40.0,
            catalogId = catalogItem.id,
            packageLabel = "jar",
            packageSize = 400.0,
        )
        assertEquals(1.0, batch1.packageCount, 0.001)
        assertEquals("1 × jar (400 g)", batch1.quantityLabel)

        // Batch 2: Large 800g family jar (batch owns its own package size)
        val batch2 = StockItem(
            purchaseDate = today,
            name = "Strawberry Jam",
            unit = "g",
            totalQuantity = 800.0,
            totalPrice = 75.0,
            catalogId = catalogItem.id,
            packageLabel = "family jar",
            packageSize = 800.0,
        )
        assertEquals(1.0, batch2.packageCount, 0.001)
        assertEquals("1 × family jar (800 g)", batch2.quantityLabel)

        // Batch 3: Loose grams purchase (no package forced onto loose stock)
        val batch3 = StockItem(
            purchaseDate = today,
            name = "Strawberry Jam",
            unit = "g",
            totalQuantity = 250.0,
            totalPrice = 25.0,
            catalogId = catalogItem.id,
            packageLabel = "",
            packageSize = 0.0,
        )
        assertFalse(batch3.hasPackageInfo)
        assertEquals("250 g", batch3.quantityLabel)
    }

    @Test
    fun daysLeftEstimationWithLearnedRateAndFallback() {
        // Item with estimated usage only
        val estimatedItem = StockItem(
            purchaseDate = today,
            name = "Rice",
            unit = "g",
            totalQuantity = 1000.0,
            consumedQuantity = 200.0,
            totalPrice = 30.0,
            estimatedUsagePerDay = 100.0,
        )
        assertTrue(estimatedItem.isUsageEstimated)
        assertFalse(estimatedItem.hasLearnedDailyRate)
        assertEquals(8.0, estimatedItem.daysRemaining, 0.001)
        assertEquals("~8 days left (est.)", estimatedItem.daysLabel)

        // Item with dated history across multiple days learns true daily rate
        val learnedItem = estimatedItem.copy(
            datedUsageHistory = listOf(
                DatedUsage(today.minusDays(3), 100.0),
                DatedUsage(today, 100.0),
            ),
        )
        assertTrue(learnedItem.hasLearnedDailyRate)
        assertFalse(learnedItem.isUsageEstimated)
        // 200g across 4 days (minusDays(3) to today inclusive = 4 days) -> 50g/day
        assertEquals(50.0, learnedItem.effectiveUsagePerDay, 0.001)
        assertEquals(16.0, learnedItem.daysRemaining, 0.001)
        assertEquals("16 days left", learnedItem.daysLabel)
    }

    @Test
    fun starterPantryStockIdsContainsExpectedDefaults() {
        val expected = setOf(
            "jam", "oil", "tea", "sugar", "eggs",
            "cheese", "yogurt", "tomatoes", "cucumbers", "fruit",
        )
        assertEquals(expected, DatabaseMigrations.STARTER_STOCK_IDS)
    }
}
