package com.budgetmeals.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase

class BudgetDatabase(context: Context) : BudgetDao(context) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE categories (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                icon TEXT NOT NULL,
                monthly_budget REAL NOT NULL,
                is_food INTEGER NOT NULL,
                is_custom INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE food_catalog (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                stock_unit TEXT NOT NULL,
                portion_unit TEXT NOT NULL,
                portions_per_stock_unit REAL NOT NULL DEFAULT 1,
                default_cost_per_portion REAL NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                purchase_price REAL,
                purchase_quantity REAL,
                purchase_unit TEXT,
                meal_usage TEXT NOT NULL DEFAULT '',
                price_options_json TEXT NOT NULL DEFAULT '',
                price_known INTEGER NOT NULL DEFAULT 1,
                conversion_known INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE stock (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                unit TEXT NOT NULL,
                total_quantity REAL NOT NULL,
                total_price REAL NOT NULL,
                purchase_date INTEGER NOT NULL,
                expiry_date INTEGER,
                batch_type TEXT NOT NULL,
                usage_per_day REAL NOT NULL,
                consumed_quantity REAL NOT NULL DEFAULT 0,
                usage_history TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                catalog_id TEXT,
                package_label TEXT NOT NULL DEFAULT '',
                package_size REAL NOT NULL DEFAULT 0,
                conversion_known INTEGER NOT NULL DEFAULT 1,
                linked_expense_id TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE expenses (
                id TEXT PRIMARY KEY NOT NULL,
                category_id TEXT NOT NULL,
                amount REAL NOT NULL,
                date INTEGER NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                is_recurring INTEGER NOT NULL DEFAULT 0,
                recurring_frequency TEXT,
                is_correction INTEGER NOT NULL DEFAULT 0,
                recurring_schedule_id TEXT,
                is_budget_transfer INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE meal_templates (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                meal_type TEXT NOT NULL,
                cost REAL NOT NULL,
                is_recurring INTEGER NOT NULL DEFAULT 1,
                day_of_week TEXT,
                notes TEXT NOT NULL DEFAULT '',
                is_custom INTEGER NOT NULL DEFAULT 0,
                components_json TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE meal_logs (
                id TEXT PRIMARY KEY NOT NULL,
                date INTEGER NOT NULL,
                meal_type TEXT NOT NULL,
                template_id TEXT,
                name TEXT NOT NULL,
                actual_time INTEGER NOT NULL,
                cost REAL NOT NULL,
                consumed_cost REAL NOT NULL DEFAULT 0,
                foods TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'EATEN',
                components_json TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE meal_consumptions (
                id TEXT PRIMARY KEY NOT NULL,
                log_id TEXT NOT NULL,
                component_id TEXT,
                catalog_id TEXT,
                name TEXT NOT NULL,
                portion_unit TEXT NOT NULL,
                requested_quantity REAL NOT NULL,
                consumed_quantity REAL NOT NULL,
                stock_quantity REAL NOT NULL,
                stock_unit TEXT,
                stock_item_id TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE shopping (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                quantity REAL NOT NULL,
                unit TEXT NOT NULL,
                estimated_price REAL NOT NULL,
                category TEXT NOT NULL,
                is_checked INTEGER NOT NULL DEFAULT 0,
                created_date INTEGER NOT NULL,
                source_item_id TEXT,
                note TEXT NOT NULL DEFAULT '',
                purchase_expense_id TEXT,
                purchase_stock_id TEXT,
                price_known INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE spares (
                id TEXT PRIMARY KEY NOT NULL,
                date INTEGER NOT NULL,
                amount REAL NOT NULL,
                reason TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE day_plans (
                id TEXT PRIMARY KEY NOT NULL,
                date INTEGER NOT NULL,
                meal_type TEXT NOT NULL,
                template_id TEXT,
                name TEXT NOT NULL,
                cost REAL NOT NULL,
                foods TEXT NOT NULL DEFAULT '',
                components_json TEXT NOT NULL DEFAULT '',
                is_cleared INTEGER NOT NULL DEFAULT 0,
                UNIQUE(date, meal_type)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE day_closures (
                date INTEGER PRIMARY KEY NOT NULL,
                closed_at INTEGER NOT NULL,
                planned_cost REAL NOT NULL,
                consumed_cost REAL NOT NULL,
                leftover_cost REAL NOT NULL,
                skipped_cost REAL NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_consumptions_log ON meal_consumptions(log_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_consumptions_catalog ON meal_consumptions(catalog_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_category ON expenses(category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_logs_date ON meal_logs(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_logs_meal_type ON meal_logs(meal_type)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_meal_logs_date_type ON meal_logs(date, meal_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_catalog_id ON stock(catalog_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_day_plans_date ON day_plans(date)")
        DatabaseMigrations.seed(db, includeStarterPantry = false)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DatabaseMigrations.onUpgrade(db, oldVersion, newVersion)
    }
}
