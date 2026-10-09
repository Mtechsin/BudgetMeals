package com.budgetmeals.app.data

/** Stock coverage is recorded when the meal is saved, rather than inferred from today's inventory. */
internal data class MealStockCoverage(val quantity: Double, val cost: Double)

internal object MealSpending {
    const val EXPENSE_PREFIX = "meal-log:"

    fun expenseId(logId: String): String = "$EXPENSE_PREFIX$logId"

    fun cashCost(log: MealLog, stockCoverage: Map<String, MealStockCoverage>): Double {
        if (!log.isConsumed || !log.cost.isFinite() || log.cost <= 0.0) return 0.0
        val components = log.components.filter { it.quantity.isFinite() && it.quantity > 0.0 }
        if (components.isEmpty()) return log.cost

        fun coveredFraction(component: MealComponent): Double =
            if (component.useStock) {
                ((stockCoverage[component.id]?.quantity ?: 0.0) / component.quantity).coerceIn(0.0, 1.0)
            } else {
                0.0
            }

        if (components.all { coveredFraction(it) >= 1.0 - 0.000001 }) return 0.0
        val hasCompletePrices = components.all { it.estimatedCost.isFinite() && it.estimatedCost > 0.0 }
        val cash = if (hasCompletePrices) {
            val estimatedTotal = components.sumOf { it.estimatedCost }
            val stockEstimate = components.sumOf { it.estimatedCost * coveredFraction(it) }
            log.cost * (1.0 - stockEstimate / estimatedTotal)
        } else {
            // With missing ingredient prices, credit known stock value against the saved meal total.
            log.cost - components.filter { it.useStock }.sumOf { stockCoverage[it.id]?.cost ?: 0.0 }
        }
        // Leftovers still used the full recipe / purchased meal, so they do not refund its cash cost.
        return cash.coerceIn(0.0, log.cost).takeIf { it > 0.000001 } ?: 0.0
    }
}
