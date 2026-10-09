package com.budgetmeals.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

internal object MealExpenseLedger {
    fun sync(db: SQLiteDatabase, log: MealLog) {
        val coverage = db.rawQuery(
            """
            SELECT usage.component_id, SUM(usage.consumed_quantity),
                   SUM(usage.stock_quantity * COALESCE(stock.total_price / NULLIF(stock.total_quantity, 0), 0))
            FROM meal_consumptions usage
            LEFT JOIN stock ON stock.id = usage.stock_item_id
            WHERE usage.log_id = ? AND usage.stock_item_id IS NOT NULL AND usage.stock_quantity > 0
            GROUP BY usage.component_id
            """.trimIndent(),
            arrayOf(log.id),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(0)) {
                        put(cursor.getString(0), MealStockCoverage(cursor.getDouble(1), cursor.getDouble(2)))
                    }
                }
            }
        }
        val amount = MealSpending.cashCost(log, coverage)
        if (amount <= 0.0) {
            delete(db, log.id)
            return
        }
        db.execSQL(
            "INSERT OR IGNORE INTO categories " +
                "(id, name, icon, monthly_budget, is_food, is_custom, sort_order) " +
                "VALUES ('food', 'Food', 'restaurant', 0, 1, 0, 0)",
        )
        val inserted = db.insertWithOnConflict("expenses", null, ContentValues().apply {
            put("id", MealSpending.expenseId(log.id))
            put("category_id", "food")
            put("amount", amount)
            put("date", log.date.toEpochDay())
            put("description", "${log.mealType.label}: ${log.name}")
            put("is_recurring", 0)
            put("is_correction", 0)
            put("is_budget_transfer", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        check(inserted != -1L) { "Could not record meal spending." }
    }

    fun delete(db: SQLiteDatabase, logId: String) {
        db.delete("expenses", "id = ?", arrayOf(MealSpending.expenseId(logId)))
    }

    fun backfill(db: SQLiteDatabase) {
        val logs = db.query("meal_logs", null, null, null, null, null, null)
            .use { it.readRows(::readMealLog) }
        logs.forEach { sync(db, it) }
    }
}
