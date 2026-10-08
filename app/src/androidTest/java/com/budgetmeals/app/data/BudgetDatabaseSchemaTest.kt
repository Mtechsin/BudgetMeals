package com.budgetmeals.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetDatabaseSchemaTest {
    @get:Rule
    internal val storage = BudgetStorageRule()

    @Test
    fun newInstallLoadsDayPlansAndReversesMealConsumption() {
        val database = storage.openDatabase()
        val date = storage.today.plusDays(2)
        val catalog = FoodCatalogItem(
            id = "test-oats",
            name = "Test oats",
            category = ItemCategory.FOOD_STAPLE,
            stockUnit = "jar (100g)",
            portionUnit = "spoon",
            portionsPerStockUnit = 10.0,
            defaultCostPerPortion = 4.0,
            notes = FoodMeasurementCodec.encodeNotes(
                userNotes = "",
                measurement = FoodMeasurementType.SpoonsToGrams(10.0),
            ),
        )
        val component = MealComponent(
            id = "test-oats-component",
            catalogId = catalog.id,
            name = catalog.name,
            quantity = 5.0,
            unit = "g",
            costPerUnit = 0.4,
        )
        val template = MealTemplate(
            id = "test-oats-meal",
            name = "Oats",
            mealType = MealType.SNACK,
            cost = 2.0,
            components = listOf(component),
        )

        database.upsertFoodCatalogItem(catalog)
        database.upsertStock(
            StockItem(
                id = "test-oats-stock",
                name = catalog.name,
                category = catalog.category,
                unit = "g",
                totalQuantity = 100.0,
                totalPrice = 40.0,
                catalogId = catalog.id,
            ),
        )
        assertEquals(1, database.ensureDayPlan(date, listOf(template)))
        val planned = database.loadSnapshot(BudgetSettings()).mealPlan(date).single()
        assertEquals(component, planned.components.single())

        val log = MealLog(
            id = "test-oats-log",
            date = date,
            mealType = MealType.SNACK,
            templateId = template.id,
            name = template.name,
            cost = template.cost,
            components = listOf(component),
        )
        database.upsertMealLogs(listOf(log))
        assertEquals(5.0, requireNotNull(database.loadStockById("test-oats-stock")).consumedQuantity, 0.0001)
        assertEquals(5.0, database.loadSnapshot(BudgetSettings()).stock.single().consumedQuantity, 0.0001)
        assertEquals(1, rowCount(database.writableDatabase, "meal_consumptions"))
        database.writableDatabase.rawQuery(
            "SELECT requested_quantity, consumed_quantity, portion_unit, stock_quantity, stock_unit " +
                "FROM meal_consumptions WHERE log_id = ?",
            arrayOf(log.id),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5.0, cursor.getDouble(0), 0.0001)
            assertEquals(5.0, cursor.getDouble(1), 0.0001)
            assertEquals("g", cursor.getString(2))
            assertEquals(5.0, cursor.getDouble(3), 0.0001)
            assertEquals("g", cursor.getString(4))
        }

        database.deleteMealLog(log.id)
        assertEquals(0.0, requireNotNull(database.loadStockById("test-oats-stock")).consumedQuantity, 0.0001)
        assertEquals(0.0, database.loadSnapshot(BudgetSettings()).stock.single().consumedQuantity, 0.0001)
        assertEquals(0, rowCount(database.writableDatabase, "meal_consumptions"))
    }

    @Test
    fun validVersionElevenSchemaKeepsConsumptionRows() {
        createVersionElevenDatabase(legacyConsumptionSchema = false, hasPlanComponents = true)
        val file = storage.context.getDatabasePath(BudgetDao.DATABASE_NAME)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { beforeDb ->
            beforeDb.execSQL(
                """
                INSERT INTO day_plans (
                    id, date, meal_type, template_id, name, cost, foods, components_json, is_cleared
                ) VALUES ('plan-1', 20000, 'LUNCH', 'template-1', 'Saved lunch', 12, 'Oats', '[]', 0)
                """.trimIndent(),
            )
            beforeDb.execSQL(
                """
                INSERT INTO meal_consumptions (
                    id, log_id, component_id, catalog_id, name, portion_unit,
                    requested_quantity, consumed_quantity, stock_quantity,
                    stock_unit, stock_item_id, created_at
                ) VALUES ('row-1', 'log-1', 'component-1', 'catalog-1', 'Oats', 'g', 5, 4, 4, 'g', 'stock-1', 123)
                """.trimIndent(),
            )
            beforeDb.version = 11
        }

        val upgraded = storage.openDatabase()
        val row = upgraded.writableDatabase.rawQuery(
            "SELECT id, name, stock_item_id, created_at FROM meal_consumptions WHERE id = 'row-1'",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3).toString())
        }
        assertEquals(listOf("row-1", "Oats", "stock-1", "123"), row)
        val savedPlan = upgraded.writableDatabase.rawQuery(
            "SELECT name, components_json FROM day_plans WHERE id = 'plan-1'",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(cursor.getString(0), cursor.getString(1))
        }
        assertEquals(listOf("Saved lunch", "[]"), savedPlan)
        assertEquals(12, upgraded.writableDatabase.version)
        assertTrue(hasIndex(upgraded.writableDatabase, "meal_consumptions", "idx_meal_consumptions_log"))
        assertTrue(hasIndex(upgraded.writableDatabase, "meal_consumptions", "idx_meal_consumptions_catalog"))
        assertTrue(hasIndex(upgraded.writableDatabase, "meal_logs", "idx_meal_logs_date_type"))
    }

    @Test
    fun brokenVersionElevenSchemaCopiesConsumptionDataAndAddsMissingColumns() {
        createVersionElevenDatabase(legacyConsumptionSchema = true, hasPlanComponents = false)
        val file = storage.context.getDatabasePath(BudgetDao.DATABASE_NAME)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { oldDb ->
            oldDb.execSQL(
                """
                INSERT INTO meal_consumptions (
                    id, log_id, catalog_id, food_name, portion_quantity, portion_unit,
                    cost, purchase_quantity_used, purchase_unit
                ) VALUES ('legacy-1', 'log-1', 'catalog-1', 'Tomatoes', 3, 'piece', 6, 500, 'g')
                """.trimIndent(),
            )
            oldDb.version = 11
        }

        val upgraded = storage.openDatabase()
        val db = upgraded.writableDatabase
        assertTrue(columns(db, "day_plans").contains("components_json"))
        val row = db.rawQuery(
            """
            SELECT id, log_id, component_id, catalog_id, name, portion_unit,
                   requested_quantity, consumed_quantity, stock_quantity,
                   stock_unit, stock_item_id, created_at
            FROM meal_consumptions WHERE id = 'legacy-1'
            """.trimIndent(),
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getDouble(6),
                cursor.getDouble(7),
                cursor.getDouble(8),
                cursor.getString(9),
                cursor.getLong(11),
            )
        }
        assertEquals(
            listOf("legacy-1", "log-1", "catalog-1", "Tomatoes", "piece", 3.0, 3.0, 500.0, "g", 0L),
            row,
        )
        db.rawQuery("SELECT component_id, stock_item_id FROM meal_consumptions WHERE id = 'legacy-1'", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(cursor.getString(0))
            assertNull(cursor.getString(1))
        }
        assertFalse(tableExists(db, "meal_consumptions_legacy_v12"))
        assertTrue(hasIndex(db, "meal_consumptions", "idx_meal_consumptions_log"))
        assertTrue(hasIndex(db, "meal_consumptions", "idx_meal_consumptions_catalog"))
        assertTrue(hasIndex(db, "meal_logs", "idx_meal_logs_date_type"))
    }

    private fun createVersionElevenDatabase(legacyConsumptionSchema: Boolean, hasPlanComponents: Boolean) {
        val file = storage.context.getDatabasePath(BudgetDao.DATABASE_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val componentsColumn = if (hasPlanComponents) ", components_json TEXT NOT NULL DEFAULT ''" else ""
            db.execSQL(
                """
                CREATE TABLE day_plans (
                    id TEXT PRIMARY KEY NOT NULL, date INTEGER NOT NULL, meal_type TEXT NOT NULL,
                    template_id TEXT, name TEXT NOT NULL, cost REAL NOT NULL,
                    foods TEXT NOT NULL DEFAULT ''$componentsColumn, is_cleared INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(date, meal_type)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE TABLE meal_logs (id TEXT PRIMARY KEY NOT NULL, date INTEGER NOT NULL, meal_type TEXT NOT NULL)")
            if (legacyConsumptionSchema) {
                db.execSQL(
                    """
                    CREATE TABLE meal_consumptions (
                        id TEXT PRIMARY KEY NOT NULL, log_id TEXT NOT NULL, catalog_id TEXT NOT NULL,
                        food_name TEXT NOT NULL, portion_quantity REAL NOT NULL, portion_unit TEXT NOT NULL,
                        cost REAL NOT NULL, purchase_quantity_used REAL NOT NULL, purchase_unit TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX idx_meal_consumptions_log ON meal_consumptions(log_id)")
                db.execSQL("CREATE INDEX idx_meal_consumptions_catalog ON meal_consumptions(catalog_id)")
            } else {
                db.execSQL(
                    """
                    CREATE TABLE meal_consumptions (
                        id TEXT PRIMARY KEY NOT NULL, log_id TEXT NOT NULL, component_id TEXT,
                        catalog_id TEXT, name TEXT NOT NULL, portion_unit TEXT NOT NULL,
                        requested_quantity REAL NOT NULL, consumed_quantity REAL NOT NULL,
                        stock_quantity REAL NOT NULL, stock_unit TEXT, stock_item_id TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
            db.version = 11
        }
    }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun hasIndex(db: SQLiteDatabase, table: String, indexName: String): Boolean =
        db.rawQuery("PRAGMA index_list($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == indexName) return@use true
            }
            false
        }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private fun rowCount(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
