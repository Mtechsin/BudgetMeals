package com.budgetmeals.app.data

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Supports the legacy amount list as well as dated usage with an optional source ID. */
internal object StockUsageCodec {
    fun decodeAmounts(raw: String): List<Double> = raw.split(',').mapNotNull { entry ->
        entry.trim().substringAfter(':').substringBefore(':').toDoubleOrNull()
    }

    fun decode(raw: String, defaultDate: LocalDate): List<DatedUsage> =
        raw.split(',').mapNotNull { entry ->
            val parts = entry.trim().split(':', limit = 3)
            if (parts.size == 1) {
                parts[0].toDoubleOrNull()?.let { DatedUsage(defaultDate, it) }
            } else {
                val amount = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val date = try {
                    LocalDate.parse(parts[0])
                } catch (_: DateTimeParseException) {
                    defaultDate
                }
                DatedUsage(date, amount, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
        }

    fun encode(item: StockItem): String = if (item.datedUsageHistory.isNotEmpty()) {
        item.datedUsageHistory.joinToString(",") { "${it.date}:${it.amount}:${it.sourceId.orEmpty()}" }
    } else {
        item.usageHistory.joinToString(",")
    }
}
