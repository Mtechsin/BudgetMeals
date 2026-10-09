package com.budgetmeals.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate

internal object DatabaseMigrations {
    fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE stock ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN is_correction INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE meal_logs ADD COLUMN consumed_cost REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE meal_logs ADD COLUMN foods TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE meal_logs ADD COLUMN status TEXT NOT NULL DEFAULT 'EATEN'")
            db.execSQL("UPDATE meal_logs SET consumed_cost = cost WHERE consumed_cost <= 0")
            db.execSQL(
                "DELETE FROM meal_logs WHERE EXISTS (" +
                    "SELECT 1 FROM meal_logs newer " +
                    "WHERE newer.date = meal_logs.date AND newer.meal_type = meal_logs.meal_type " +
                    "AND (newer.actual_time > meal_logs.actual_time " +
                    "OR (newer.actual_time = meal_logs.actual_time AND newer.rowid > meal_logs.rowid)))",
            )
            db.execSQL("CREATE UNIQUE INDEX idx_meal_logs_date_type ON meal_logs(date, meal_type)")
            db.execSQL(
                "INSERT OR IGNORE INTO categories " +
                    "(id, name, icon, monthly_budget, is_food, is_custom, sort_order) " +
                    "VALUES ('other', 'Other', 'more_horiz', 0.0, 0, 0, 6)",
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
            db.execSQL("UPDATE meal_templates SET notes = 'Ful medames, falafel, baladi bread, pickles' WHERE id = 'fuul_combo' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Rice, koshari, split fava, tomato sauce, fried onions' WHERE id = 'koshary_day' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Eggs, yogurt, baladi bread, jam' WHERE id = 'egg_dinner' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Cheese, bread, tomato, cucumber' WHERE id = 'cheese_plate' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Jam, bread, milk' WHERE id = 'jam_breakfast' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Falafel, bread, tahina, salad' WHERE id = 'falafel_night' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Chips, soda' WHERE id = 'chips_treat' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Ful, baladi bread, salad' WHERE id = 'ful_sandwich' AND TRIM(COALESCE(notes, '')) = ''")
            db.execSQL("UPDATE meal_templates SET notes = 'Yogurt, bread, honey' WHERE id = 'yogurt_breakfast' AND TRIM(COALESCE(notes, '')) = ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE stock ADD COLUMN catalog_id TEXT")
            db.execSQL("ALTER TABLE meal_templates ADD COLUMN components_json TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE meal_logs ADD COLUMN components_json TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE day_plans ADD COLUMN components_json TEXT NOT NULL DEFAULT ''")
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
                    notes TEXT NOT NULL DEFAULT ''
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
            db.execSQL("CREATE INDEX idx_meal_consumptions_log ON meal_consumptions(log_id)")
            db.execSQL(
                "INSERT OR IGNORE INTO food_catalog " +
                    "(id, name, category, stock_unit, portion_unit, portions_per_stock_unit, default_cost_per_portion, notes) " +
                    "SELECT 'catalog_' || id, name, category, unit, unit, 1.0, " +
                    "CASE WHEN total_quantity > 0 THEN total_price / total_quantity ELSE 0 END, notes FROM stock",
            )
            db.execSQL("UPDATE stock SET catalog_id = 'catalog_' || id WHERE catalog_id IS NULL OR TRIM(catalog_id) = ''")
            db.execSQL("UPDATE food_catalog SET portion_unit = 'piece', portions_per_stock_unit = 0.01 WHERE LOWER(name) IN ('tomatoes', 'cucumbers') AND stock_unit = 'g'")
            db.execSQL("UPDATE food_catalog SET portion_unit = 'piece', portions_per_stock_unit = 1.0 WHERE LOWER(name) IN ('eggs', 'fruit') AND stock_unit = 'piece'")
        }
        // Add the catalog metadata columns before any backfill writes them.
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE food_catalog ADD COLUMN purchase_price REAL")
            db.execSQL("ALTER TABLE food_catalog ADD COLUMN purchase_quantity REAL")
            db.execSQL("ALTER TABLE food_catalog ADD COLUMN purchase_unit TEXT")
            db.execSQL("ALTER TABLE food_catalog ADD COLUMN meal_usage TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE food_catalog ADD COLUMN price_options_json TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE food_catalog ADD COLUMN price_known INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE food_catalog ADD COLUMN conversion_known INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 6) {
            backfillDynamicMealData(db, fillKnownDefaults = oldVersion < 4)
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_category ON expenses(category_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_logs_date ON meal_logs(date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_logs_meal_type ON meal_logs(meal_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_catalog_id ON stock(catalog_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_day_plans_date ON day_plans(date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_consumptions_catalog ON meal_consumptions(catalog_id)")
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE stock ADD COLUMN package_label TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE stock ADD COLUMN package_size REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE stock ADD COLUMN conversion_known INTEGER NOT NULL DEFAULT 1")
            migrateStockToBaseUnits(db)
        }
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE shopping ADD COLUMN purchase_expense_id TEXT")
            db.execSQL("ALTER TABLE shopping ADD COLUMN purchase_stock_id TEXT")
            db.execSQL("ALTER TABLE shopping ADD COLUMN price_known INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE day_plans ADD COLUMN is_cleared INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 11) {
            db.execSQL("ALTER TABLE stock ADD COLUMN linked_expense_id TEXT")
            db.execSQL("ALTER TABLE expenses ADD COLUMN recurring_schedule_id TEXT")
            db.execSQL("ALTER TABLE expenses ADD COLUMN is_budget_transfer INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE stock SET linked_expense_id = 'stock-' || id WHERE linked_expense_id IS NULL")
            db.execSQL("UPDATE stock SET linked_expense_id = (SELECT purchase_expense_id FROM shopping WHERE shopping.purchase_stock_id = stock.id) " +
                "WHERE EXISTS (SELECT 1 FROM shopping WHERE shopping.purchase_stock_id = stock.id AND purchase_expense_id IS NOT NULL)")
            // Preserve the historical row while distinguishing the transfer from actual spending.
            db.execSQL("UPDATE expenses SET is_budget_transfer = 1 WHERE description = 'Transferred to spares' AND category_id = 'food'")
            val recurringRows = db.rawQuery(
                "SELECT id, category_id, description FROM expenses WHERE is_recurring = 1 AND recurring_schedule_id IS NULL", null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
            recurringRows.forEach { (id, category, description) ->
                val key = "$category|${description.trim().lowercase()}"
                val scheduleId = "legacy-" + java.util.UUID.nameUUIDFromBytes(key.toByteArray()).toString()
                db.execSQL("UPDATE expenses SET recurring_schedule_id = ? WHERE id = ?", arrayOf(scheduleId, id))
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_recurring_schedule ON expenses(recurring_schedule_id)")
        }
        if (oldVersion < 12) {
            repairCurrentSchema(db)
        }
    }

    private fun repairCurrentSchema(db: SQLiteDatabase) {
        if (tableExists(db, "day_plans") && !hasColumn(db, "day_plans", "components_json")) {
            db.execSQL("ALTER TABLE day_plans ADD COLUMN components_json TEXT NOT NULL DEFAULT ''")
        }

        if (!tableExists(db, "meal_consumptions")) {
            createCurrentMealConsumptions(db)
        } else if (!hasColumn(db, "meal_consumptions", "stock_item_id")) {
            db.execSQL("DROP INDEX IF EXISTS idx_meal_consumptions_log")
            db.execSQL("DROP INDEX IF EXISTS idx_meal_consumptions_catalog")
            db.execSQL("ALTER TABLE meal_consumptions RENAME TO meal_consumptions_legacy_v12")
            createCurrentMealConsumptions(db)
            db.execSQL(
                """
                INSERT INTO meal_consumptions (
                    id, log_id, component_id, catalog_id, name, portion_unit,
                    requested_quantity, consumed_quantity, stock_quantity,
                    stock_unit, stock_item_id, created_at
                )
                SELECT
                    id, log_id, NULL, catalog_id, food_name, portion_unit,
                    portion_quantity, portion_quantity, purchase_quantity_used,
                    purchase_unit, NULL, 0
                FROM meal_consumptions_legacy_v12
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE meal_consumptions_legacy_v12")
        }

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_consumptions_log ON meal_consumptions(log_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_consumptions_catalog ON meal_consumptions(catalog_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_meal_logs_date_type ON meal_logs(date, meal_type)")
    }

    private fun createCurrentMealConsumptions(db: SQLiteDatabase) {
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
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return@use true
            }
            false
        }

    private data class StockRow(
        val id: String,
        val name: String,
        val unit: String,
        val totalQuantity: Double,
        val consumedQuantity: Double,
        val usagePerDay: Double,
        val usageHistory: String,
        val catalogId: String?,
    )

    private data class BaseRow(
        val unit: String,
        val totalQuantity: Double,
        val consumedQuantity: Double,
        val usagePerDay: Double,
        val usageHistory: String,
        val packageLabel: String,
        val packageSize: Double,
    )

    /**
     * Rewrites every existing batch so quantities are expressed in the base unit of its
     * food ("1000 g of tomatoes", "12 eggs") instead of whatever label the row carried.
     *
     * Batches we cannot convert keep their numbers and are flagged `conversion_known = 0`,
     * so the app reports "conversion unknown" instead of computing with a wrong number.
     */
    private fun migrateStockToBaseUnits(db: SQLiteDatabase) {
        val catalogs = db.query("food_catalog", null, null, null, null, null, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(readFoodCatalog(cursor))
                }
            }
        }
        val byId = catalogs.associateBy { it.id }
        val byName = catalogs.associateBy { it.name.trim().lowercase(java.util.Locale.US) }

        val rows = db.query(
            "stock",
            arrayOf("id", "name", "unit", "total_quantity", "consumed_quantity", "usage_per_day", "usage_history", "catalog_id"),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        StockRow(
                            id = cursor.getString(0),
                            name = cursor.getString(1).orEmpty(),
                            unit = cursor.getString(2).orEmpty(),
                            totalQuantity = cursor.getDouble(3),
                            consumedQuantity = cursor.getDouble(4),
                            usagePerDay = cursor.getDouble(5),
                            usageHistory = cursor.getString(6).orEmpty(),
                            catalogId = cursor.getString(7),
                        ),
                    )
                }
            }
        }

        rows.forEach { row ->
            val catalog = row.catalogId?.let { byId[it] }
                ?: byName[row.name.trim().lowercase(java.util.Locale.US)]
            val target = if (catalog == null) {
                // Nothing to convert against: the row's own unit stays its base unit.
                BaseRow(
                    unit = row.unit,
                    totalQuantity = row.totalQuantity,
                    consumedQuantity = row.consumedQuantity,
                    usagePerDay = row.usagePerDay,
                    usageHistory = row.usageHistory,
                    packageLabel = "",
                    packageSize = 0.0,
                )
            } else {
                convertRowToBase(row, catalog)
            }
            if (target == null) {
                db.execSQL("UPDATE stock SET conversion_known = 0 WHERE id = ?", arrayOf(row.id))
            } else {
                db.execSQL(
                    "UPDATE stock SET unit = ?, total_quantity = ?, consumed_quantity = ?, " +
                        "usage_per_day = ?, usage_history = ?, package_label = ?, package_size = ?, " +
                        "conversion_known = 1 WHERE id = ?",
                    arrayOf(
                        target.unit,
                        target.totalQuantity,
                        target.consumedQuantity,
                        target.usagePerDay,
                        target.usageHistory,
                        target.packageLabel,
                        target.packageSize,
                        row.id,
                    ),
                )
            }
        }
    }

    /** Converts one legacy row into base units, or null when that is not trustworthy. */
    private fun convertRowToBase(row: StockRow, catalog: FoodCatalogItem): BaseRow? {
        val baseUnit = catalog.baseUnit
        if (baseUnit.isBlank()) return null
        val total = Units.convert(row.totalQuantity, row.unit, baseUnit, catalog) ?: return null
        val consumed = Units.convert(row.consumedQuantity, row.unit, baseUnit, catalog) ?: return null
        val perDay = Units.convert(row.usagePerDay, row.unit, baseUnit, catalog) ?: return null
        val history = row.usageHistory.split(',')
            .mapNotNull { part -> part.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull() }
            .map { amount -> Units.convert(amount, row.unit, baseUnit, catalog) ?: amount }
        return BaseRow(
            unit = baseUnit,
            totalQuantity = total,
            consumedQuantity = consumed,
            usagePerDay = perDay,
            usageHistory = history.joinToString(","),
            packageLabel = catalog.packageLabel.orEmpty(),
            packageSize = if (catalog.packageSize != null) catalog.baseUnitsPerStockUnit else 0.0,
        )
    }

    private fun backfillDynamicMealData(db: SQLiteDatabase, fillKnownDefaults: Boolean) {
        val catalogIds = mutableMapOf<String, String>()
        val aliases = mapOf(
            "bread" to "Baladi bread",
            "tomato" to "Tomatoes",
            "cucumber" to "Cucumbers",
        )

        DynamicMealDefaults.catalog.forEach { known ->
            val existingId = db.query(
                "food_catalog",
                arrayOf("id"),
                "TRIM(name) = ? COLLATE NOCASE",
                arrayOf(known.name),
                null,
                null,
                null,
                "1",
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            val catalogId = existingId ?: known.id
            val existingFood = existingId?.let { id ->
                db.rawQuery("SELECT * FROM food_catalog WHERE id = ?", arrayOf(id)).use { cursor -> if (cursor.moveToFirst()) readFoodCatalog(cursor) else null }
            }
            val looksLikeLegacyDefault = existingFood?.let {
                (it.portionUnit.equals(it.stockUnit, ignoreCase = true) && it.portionsPerStockUnit == 1.0) ||
                (it.name.equals("Cheese", ignoreCase = true) && it.portionUnit == "g") ||
                (it.name.equals("Chips", ignoreCase = true) && it.portionUnit == "g")
            } == true
            val mergedFood = if (existingFood == null || looksLikeLegacyDefault) {
                known.copy(id = catalogId, name = existingFood?.name?.ifBlank { known.name } ?: known.name)
            } else {
                existingFood.copy(
                    name = existingFood.name.ifBlank { known.name },
                    category = existingFood.category,
                    stockUnit = existingFood.stockUnit.ifBlank { known.stockUnit },
                    portionUnit = existingFood.portionUnit.ifBlank { known.portionUnit },
                    portionsPerStockUnit = existingFood.portionsPerStockUnit.takeIf { it > 0.0 } ?: known.portionsPerStockUnit,
                    defaultCostPerPortion = when {
                        existingFood.defaultCostPerPortion <= 0.0 -> known.defaultCostPerPortion
                        existingFood.defaultCostPerPortion < known.defaultCostPerPortion -> known.defaultCostPerPortion
                        else -> existingFood.defaultCostPerPortion
                    },
                    notes = existingFood.notes.ifBlank { known.notes },
                )
            }
            val values = ContentValues().apply {
                put("id", mergedFood.id)
                put("name", mergedFood.name)
                put("category", mergedFood.category.name)
                put("stock_unit", mergedFood.stockUnit)
                put("portion_unit", mergedFood.portionUnit)
                put("portions_per_stock_unit", mergedFood.portionsPerStockUnit)
                put("default_cost_per_portion", mergedFood.defaultCostPerPortion)
                put("notes", mergedFood.notes)
                put("purchase_price", mergedFood.purchasePrice)
                put("purchase_quantity", mergedFood.purchaseQuantity)
                put("purchase_unit", mergedFood.purchaseUnit)
                put("meal_usage", mergedFood.mealUsage)
                put("price_options_json", FoodCatalogJsonCodec.encodePriceOptions(mergedFood.priceOptions))
                put("price_known", if (mergedFood.priceKnown) 1 else 0)
                put("conversion_known", if (mergedFood.conversionKnown) 1 else 0)
            }
            val updated = db.update("food_catalog", values, "id = ?", arrayOf(mergedFood.id))
            if (updated == 0) db.insert("food_catalog", null, values)
            catalogIds[known.id.removePrefix("catalog_")] = catalogId
            val duplicateIds = db.rawQuery(
                "SELECT id, stock_unit FROM food_catalog WHERE TRIM(LOWER(name)) = TRIM(LOWER(?)) AND id <> ?",
                arrayOf(known.name, catalogId),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                }
            }
            duplicateIds.forEach { (duplicateId, duplicateUnit) ->
                if (duplicateUnit.equals(mergedFood.stockUnit, ignoreCase = true)) {
                    remapCatalogReferences(db, duplicateId, catalogId)
                    db.delete("food_catalog", "id = ?", arrayOf(duplicateId))
                }
            }
            db.execSQL(
                "UPDATE stock SET catalog_id = ? WHERE TRIM(LOWER(name)) = TRIM(LOWER(?)) AND unit = ?",
                arrayOf(catalogId, known.name, mergedFood.stockUnit),
            )
            aliases.forEach { (alias, canonicalName) ->
                if (known.name == canonicalName) {
                    db.execSQL(
                        "UPDATE stock SET catalog_id = ? WHERE TRIM(LOWER(name)) = TRIM(LOWER(?)) AND unit = ?",
                        arrayOf(catalogId, alias, mergedFood.stockUnit),
                    )
                }
            }
        }

        val templateComponents = mutableMapOf<String, List<MealComponent>>()
        db.rawQuery("SELECT id, notes, components_json FROM meal_templates", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val notes = cursor.getString(1)
                val existingJson = cursor.getString(2)
                val existingComponents = MealDataCodec.decodeComponents(existingJson)
                val knownComponents = DynamicMealDefaults.componentsForTemplate(id, catalogIds)
                val components = when {
                    !fillKnownDefaults && existingComponents.isEmpty() -> emptyList()
                    fillKnownDefaults && knownComponents.isNotEmpty() && existingComponents.all { it.id.startsWith("legacy-") } -> knownComponents
                    existingComponents.isNotEmpty() -> existingComponents
                    fillKnownDefaults -> knownComponents.ifEmpty { MealDataCodec.legacyComponents(notes) }
                    else -> emptyList()
                }
                if (components.isNotEmpty()) {
                    templateComponents[id] = components
                    db.execSQL(
                        "UPDATE meal_templates SET components_json = ? WHERE id = ?",
                        arrayOf(MealDataCodec.encodeComponents(components), id),
                    )
                }
            }
        }

        db.rawQuery("SELECT id, template_id, foods, components_json FROM day_plans", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val templateId = if (cursor.isNull(1)) null else cursor.getString(1)
                val foods = cursor.getString(2)
                val existingJson = cursor.getString(3)
                val existingComponents = MealDataCodec.decodeComponents(existingJson)
                if (existingComponents.isNotEmpty() && existingComponents.none { it.id.startsWith("legacy-") }) continue
                if (!fillKnownDefaults && existingComponents.isEmpty()) continue
                val components = templateId?.let(templateComponents::get).orEmpty()
                    .ifEmpty { existingComponents.ifEmpty { MealDataCodec.legacyComponents(foods) } }
                if (components.isNotEmpty()) {
                    db.execSQL(
                        "UPDATE day_plans SET components_json = ? WHERE id = ?",
                        arrayOf(MealDataCodec.encodeComponents(components), id),
                    )
                }
            }
        }
    }

    private fun remapCatalogReferences(db: SQLiteDatabase, oldId: String, newId: String) {
        db.execSQL("UPDATE stock SET catalog_id = ? WHERE catalog_id = ?", arrayOf(newId, oldId))
        db.execSQL("UPDATE meal_consumptions SET catalog_id = ? WHERE catalog_id = ?", arrayOf(newId, oldId))
        listOf("meal_templates", "day_plans", "meal_logs").forEach { table ->
            val rows = db.rawQuery("SELECT id, components_json FROM $table", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                }
            }
            rows.forEach { (rowId, json) ->
                val components = MealDataCodec.decodeComponents(json)
                if (components.any { it.catalogId == oldId }) {
                    val remapped = components.map { if (it.catalogId == oldId) it.copy(catalogId = newId) else it }
                    db.execSQL(
                        "UPDATE $table SET components_json = ? WHERE id = ?",
                        arrayOf(MealDataCodec.encodeComponents(remapped), rowId),
                    )
                }
            }
        }
    }

    val STARTER_STOCK_IDS = setOf(
        "jam", "oil", "tea", "sugar", "eggs", "cheese", "yogurt", "tomatoes", "cucumbers", "fruit",
    )

    fun clearSampleStock(db: SQLiteDatabase) {
        val placeholders = STARTER_STOCK_IDS.joinToString(",") { "?" }
        db.delete("stock", "id IN ($placeholders)", STARTER_STOCK_IDS.toTypedArray())
    }

    fun seed(db: SQLiteDatabase, includeStarterPantry: Boolean = true) {
        val categories = listOf(
            ExpenseCategory("food", "Food", "restaurant", 3_000.0, true, false, 0),
            ExpenseCategory("internet_mobile", "Internet mobile", "wifi", 150.0, false, false, 1),
            ExpenseCategory("internet_home", "Internet home", "home", 50.0, false, false, 2),
            ExpenseCategory("calling", "Calling", "call", 100.0, false, false, 3),
            ExpenseCategory("household", "Household", "cleaning", 100.0, false, false, 4),
            ExpenseCategory("spares", "Spares", "celebration", 120.0, false, false, 5),
            ExpenseCategory("other", "Other", "more_horiz", 0.0, false, false, 6),
        )
        categories.forEach { category ->
            db.insert("categories", null, ContentValues().apply {
                put("id", category.id)
                put("name", category.name)
                put("icon", category.icon)
                put("monthly_budget", category.monthlyBudget)
                put("is_food", if (category.isFood) 1 else 0)
                put("is_custom", if (category.isCustom) 1 else 0)
                put("sort_order", category.sortOrder)
            })
        }

        val foodCatalog = DynamicMealDefaults.catalog
        foodCatalog.forEach { item ->
            db.insert("food_catalog", null, ContentValues().apply {
                put("id", item.id)
                put("name", item.name)
                put("category", item.category.name)
                put("stock_unit", item.stockUnit)
                put("portion_unit", item.portionUnit)
                put("portions_per_stock_unit", item.portionsPerStockUnit)
                put("default_cost_per_portion", item.defaultCostPerPortion)
                put("notes", item.notes)
                put("purchase_price", item.purchasePrice)
                put("purchase_quantity", item.purchaseQuantity)
                put("purchase_unit", item.purchaseUnit)
                put("meal_usage", item.mealUsage)
                put("price_options_json", FoodCatalogJsonCodec.encodePriceOptions(item.priceOptions))
                put("price_known", if (item.priceKnown) 1 else 0)
                put("conversion_known", if (item.conversionKnown) 1 else 0)
            })
        }

        fun component(id: String, catalogId: String, name: String, quantity: Double, unit: String, costPerUnit: Double) =
            MealComponent(id, catalogId, name, quantity, unit, costPerUnit)

        val templates = listOf(
            MealTemplate(
                "fuul_combo", "Fuul combo", MealType.LUNCH, 40.0, true,
                notes = "Ful medames, falafel, baladi bread, pickles",
                components = listOf(
                    component("component_fuul", "catalog_ful", "Ful medames", 150.0, "g", 0.1),
                    component("component_falafel", "catalog_falafel", "Falafel", 2.0, "piece", 5.0),
                    component("component_bread", "catalog_bread", "Baladi bread", 1.0, "piece", 12.0),
                    component("component_pickles", "catalog_pickles", "Pickles", 1.0, "piece", 3.0),
                ),
            ),
            MealTemplate(
                "koshary_day", "Koshary day", MealType.LUNCH, 70.0, true,
                notes = "Rice, koshari, split fava, tomato sauce, fried onions",
                components = listOf(
                    component("component_rice", "catalog_rice", "Rice", 150.0, "g", 0.07),
                    component("component_koshari", "catalog_koshari", "Koshari", 100.0, "g", 0.25),
                    component("component_fava", "catalog_fava", "Split fava", 100.0, "g", 0.15),
                    component("component_sauce", "catalog_tomato_sauce", "Tomato sauce", 50.0, "g", 0.2),
                    component("component_onions", "catalog_onions", "Fried onions", 20.0, "g", 0.4),
                ),
            ),
            MealTemplate(
                "egg_dinner", "Egg dinner", MealType.DINNER, 28.0, true,
                notes = "Eggs, yogurt, baladi bread, jam",
                components = listOf(
                    component("component_eggs", "catalog_eggs", "Eggs", 2.0, "piece", 8.0),
                    component("component_yogurt", "catalog_yogurt", "Yogurt", 1.0, "cup", 4.0),
                    component("component_bread_dinner", "catalog_bread", "Baladi bread", 1.0, "piece", 5.0),
                    component("component_jam_dinner", "catalog_jam", "Jam", 10.0, "g", 0.3),
                ),
            ),
            MealTemplate(
                "cheese_plate", "Cheese plate", MealType.BREAKFAST, 14.3, true,
                notes = "Cheese, bread, tomato, cucumber",
                components = listOf(
                    component("component_cheese", "catalog_cheese", "Cheese", 3.0, "spoon", 1.6),
                    component("component_bread_plate", "catalog_bread", "Baladi bread", 1.0, "piece", 5.0),
                    component("component_tomato_plate", "catalog_tomatoes", "Tomatoes", 1.0, "piece", 2.0),
                    component("component_cucumber_plate", "catalog_cucumbers", "Cucumbers", 1.0, "piece", 2.5),
                ),
            ),
            MealTemplate(
                "jam_breakfast", "Jam breakfast", MealType.BREAKFAST, 18.0, true,
                notes = "Jam, bread, milk",
                components = listOf(
                    component("component_jam_breakfast", "catalog_jam", "Jam", 20.0, "g", 0.25),
                    component("component_bread_breakfast", "catalog_bread", "Baladi bread", 1.0, "piece", 5.0),
                    component("component_milk", "catalog_milk", "Milk", 200.0, "ml", 0.04),
                ),
            ),
            MealTemplate(
                "falafel_night", "Falafel night", MealType.DINNER, 30.0, true,
                notes = "Falafel, bread, tahina, salad",
                components = listOf(
                    component("component_falafel_night", "catalog_falafel", "Falafel", 3.0, "piece", 5.0),
                    component("component_bread_night", "catalog_bread", "Baladi bread", 1.0, "piece", 5.0),
                    component("component_tahina", "catalog_tahina", "Tahina", 30.0, "g", 0.17),
                    component("component_salad_night", "catalog_salad", "Salad", 50.0, "g", 0.1),
                ),
            ),
            MealTemplate(
                "chips_treat", "Chips treat", MealType.SNACK, 21.9, false,
                notes = "Chips, soda",
                components = listOf(
                    component("component_chips", "catalog_chips", "Chips (Medium)", 1.0, "pack", 15.0),
                    component("component_soda", "catalog_soda", "Soda", 330.0, "ml", 0.021),
                ),
            ),
            MealTemplate(
                "ful_sandwich", "Ful sandwich", MealType.LUNCH, 40.0, true,
                notes = "Ful, baladi bread, salad",
                components = listOf(
                    component("component_ful_sandwich", "catalog_ful", "Ful medames", 100.0, "g", 0.1),
                    component("component_bread_sandwich", "catalog_bread", "Baladi bread", 1.0, "piece", 15.0),
                    component("component_salad_sandwich", "catalog_salad", "Salad", 50.0, "g", 0.1),
                ),
            ),
            MealTemplate(
                "yogurt_breakfast", "Yogurt breakfast", MealType.BREAKFAST, 23.0, true,
                notes = "Yogurt, bread, honey",
                components = listOf(
                    component("component_yogurt_breakfast", "catalog_yogurt", "Yogurt", 1.0, "cup", 10.0),
                    component("component_bread_yogurt", "catalog_bread", "Baladi bread", 1.0, "piece", 8.0),
                    component("component_honey", "catalog_honey", "Honey", 10.0, "g", 0.5),
                ),
            ),
        )
        templates.forEach { template ->
            db.insert("meal_templates", null, ContentValues().apply {
                put("id", template.id)
                put("name", template.name)
                put("meal_type", template.mealType.name)
                put("cost", template.cost)
                put("is_recurring", if (template.isRecurring) 1 else 0)
                put("day_of_week", template.dayOfWeek?.name)
                put("notes", template.notes)
                put("is_custom", if (template.isCustom) 1 else 0)
                put("components_json", MealDataCodec.encodeComponents(template.components))
            })
        }

        if (includeStarterPantry) {
            val today = LocalDate.now()
            // Starter pantry. Quantities are stored in each food's base unit, which is what the
            // catalog converts portions to (tomatoes: g with 6 pieces per kg, eggs: single eggs,
            // yogurt: cups). Package labels describe how the batch was bought.
            val stock = listOf(
                StockItem("jam", "Jam", ItemCategory.FOOD_STAPLE, "g", 650.0, 55.0, today, null, BatchType.MONTHLY, 30.0, 0.0, notes = "Monthly staple", catalogId = "catalog_jam", packageLabel = "jar (380g)", packageSize = 380.0),
                StockItem("oil", "Oil", ItemCategory.FOOD_STAPLE, "ml", 1_000.0, 120.0, today, null, BatchType.MONTHLY, 15.0, catalogId = "catalog_oil", packageLabel = "bottle (700ml)", packageSize = 700.0),
                StockItem("tea", "Tea", ItemCategory.FOOD_STAPLE, "g", 200.0, 50.0, today, null, BatchType.MONTHLY, 5.0, catalogId = "catalog_tea", packageLabel = "pack (100g)", packageSize = 100.0),
                StockItem("sugar", "Sugar", ItemCategory.FOOD_STAPLE, "g", 1_000.0, 40.0, today, null, BatchType.MONTHLY, 20.0, catalogId = "catalog_sugar"),
                StockItem("eggs", "Eggs", ItemCategory.FOOD_FRESH, "egg", 12.0, 60.0, today, null, BatchType.WEEKLY, 1.5, catalogId = "catalog_eggs", packageLabel = "carton (30)", packageSize = 30.0),
                StockItem("cheese", "Cheese", ItemCategory.FOOD_FRESH, "g", 250.0, 25.0, today, null, BatchType.WEEKLY, 25.0, catalogId = "catalog_cheese", packageLabel = "tub (500g)", packageSize = 500.0),
                StockItem("yogurt", "Yogurt", ItemCategory.FOOD_FRESH, "cup", 5.0, 25.0, today, null, BatchType.WEEKLY, 0.8, catalogId = "catalog_yogurt", packageLabel = "pack (6)", packageSize = 6.0),
                StockItem("tomatoes", "Tomatoes", ItemCategory.FOOD_FRESH, "g", 1_000.0, 30.0, today, null, BatchType.WEEKLY, 150.0, catalogId = "catalog_tomatoes"),
                StockItem("cucumbers", "Cucumbers", ItemCategory.FOOD_FRESH, "g", 1_000.0, 25.0, today, null, BatchType.WEEKLY, 100.0, catalogId = "catalog_cucumbers"),
                StockItem("fruit", "Fruit", ItemCategory.FOOD_FRESH, "g", 1_000.0, 35.0, today, null, BatchType.WEEKLY, 200.0, catalogId = "catalog_fruit"),
            )
            stock.forEach { item ->
                db.insert("stock", null, ContentValues().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("category", item.category.name)
                    put("unit", item.unit)
                    put("total_quantity", item.totalQuantity)
                    put("total_price", item.totalPrice)
                    put("purchase_date", item.purchaseDate.toEpochDay())
                    put("expiry_date", item.expiryDate?.toEpochDay())
                    put("batch_type", item.batchType.name)
                    put("usage_per_day", item.estimatedUsagePerDay)
                    put("consumed_quantity", item.consumedQuantity)
                    put("usage_history", "")
                    put("notes", item.notes)
                    put("catalog_id", item.catalogId)
                    put("package_label", item.packageLabel)
                    put("package_size", item.packageSize)
                    put("conversion_known", if (item.conversionKnown) 1 else 0)
                })
            }
        }

    }
}
