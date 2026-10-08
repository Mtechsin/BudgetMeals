package com.budgetmeals.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StockUsageCodecTest {
    private val fallbackDate = LocalDate.of(2026, 10, 8)

    @Test
    fun decodesLegacyAmountLists() {
        val raw = "1.5,2.0"

        assertEquals(listOf(1.5, 2.0), StockUsageCodec.decodeAmounts(raw))
        assertEquals(
            listOf(
                DatedUsage(fallbackDate, 1.5),
                DatedUsage(fallbackDate, 2.0),
            ),
            StockUsageCodec.decode(raw, fallbackDate),
        )
    }

    @Test
    fun datedHistoryAndSourceIdsRoundTrip() {
        val history = listOf(
            DatedUsage(LocalDate.of(2026, 10, 1), 2.5, sourceId = "meal-123"),
            DatedUsage(LocalDate.of(2026, 10, 2), 1.0),
        )
        val item = StockItem(
            id = "rice-stock",
            name = "Rice",
            totalQuantity = 5.0,
            totalPrice = 20.0,
            purchaseDate = fallbackDate,
            datedUsageHistory = history,
        )

        val encoded = StockUsageCodec.encode(item)

        assertEquals(history, StockUsageCodec.decode(encoded, fallbackDate))
    }

    @Test
    fun skipsMalformedEntriesAndFallsBackForInvalidDates() {
        val raw = listOf(
            "1.25",
            "not-an-entry",
            "2026-10-01:2.5:meal-123",
            "invalid-date:3.0:source-456",
            "2026-10-03:not-a-number:meal-789",
        ).joinToString(",")

        assertEquals(
            listOf(
                DatedUsage(fallbackDate, 1.25),
                DatedUsage(LocalDate.of(2026, 10, 1), 2.5, sourceId = "meal-123"),
                DatedUsage(fallbackDate, 3.0, sourceId = "source-456"),
            ),
            StockUsageCodec.decode(raw, fallbackDate),
        )
        assertEquals(listOf(1.25, 2.5, 3.0), StockUsageCodec.decodeAmounts(raw))
    }

    @Test
    fun encodesLegacyAmountsWhenDatedHistoryIsMissing() {
        val item = StockItem(
            id = "oil-stock",
            name = "Oil",
            totalQuantity = 1.0,
            totalPrice = 50.0,
            purchaseDate = fallbackDate,
            usageHistory = listOf(0.25, 0.5),
        )

        assertEquals("0.25,0.5", StockUsageCodec.encode(item))
    }
}
