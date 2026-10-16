package com.budgetmeals.app.backup

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealLog
import com.budgetmeals.app.data.MealSpending
import com.budgetmeals.app.data.StockItem
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure Kotlin Excel (.xlsx) OpenXML streaming generator and CSV ZIP exporter.
 * Produces valid OpenXML SpreadsheetML workbooks openable in Microsoft Excel,
 * Google Sheets, LibreOffice, and mobile viewers without Apache POI.
 */
object SpreadsheetMlWorkbook {

    private val EXCEL_EPOCH = LocalDate.of(1899, 12, 30)

    fun generate(snapshot: AppSnapshot, outputStream: OutputStream) = generateXlsx(snapshot, outputStream)

    fun generateXlsx(snapshot: AppSnapshot, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        val sheets = listOf(
            SheetDef("Summary", 1) { writeSummarySheet(it, snapshot) },
            SheetDef("Spending", 2) { writeSpendingSheet(it, snapshot) },
            SheetDef("Meals", 3) { writeMealsSheet(it, snapshot) },
            SheetDef("Meal Ingredients", 4) { writeMealIngredientsSheet(it, snapshot) },
            SheetDef("Stock", 5) { writeStockSheet(it, snapshot) },
            SheetDef("Stock Usage", 6) { writeStockUsageSheet(it, snapshot) },
            SheetDef("Food Catalog", 7) { writeFoodCatalogSheet(it, snapshot) },
            SheetDef("Shopping", 8) { writeShoppingSheet(it, snapshot) },
            SheetDef("Savings", 9) { writeSavingsSheet(it, snapshot) },
            SheetDef("Day Plans", 10) { writeDayPlansSheet(it, snapshot) },
            SheetDef("Day Closures", 11) { writeDayClosuresSheet(it, snapshot) },
            SheetDef("Categories", 12) { writeCategoriesSheet(it, snapshot) },
            SheetDef("Settings", 13) { writeSettingsSheet(it, snapshot) },
        )

        // 1. [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        val contentTypes = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            for (sheet in sheets) {
                append("""<Override PartName="/xl/worksheets/sheet${sheet.id}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            append("""</Types>""")
        }
        zip.write(contentTypes.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 2. _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        val rootRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""")
            append("""</Relationships>""")
        }
        zip.write(rootRels.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 3. xl/_rels/workbook.xml.rels
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        val wbRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            for (sheet in sheets) {
                append("""<Relationship Id="rId${sheet.id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${sheet.id}.xml"/>""")
            }
            append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("""</Relationships>""")
        }
        zip.write(wbRels.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 4. xl/workbook.xml
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        val workbook = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            append("""<sheets>""")
            for (sheet in sheets) {
                append("""<sheet name="${escapeXml(sheet.name)}" sheetId="${sheet.id}" r:id="rId${sheet.id}"/>""")
            }
            append("""</sheets>""")
            append("""</workbook>""")
        }
        zip.write(workbook.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 5. xl/styles.xml
        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        val styles = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<fonts count="2">""")
            append("""<font><sz val="11"/><name val="Segoe UI"/></font>""")
            append("""<font><b/><sz val="11"/><color rgb="FF1F2937"/><name val="Segoe UI"/></font>""")
            append("""</fonts>""")
            append("""<fills count="3">""")
            append("""<fill><patternFill patternType="none"/></fill>""")
            append("""<fill><patternFill patternType="gray125"/></fill>""")
            append("""<fill><patternFill patternType="solid"><fgColor rgb="FFE5E7EB"/></patternFill></fill>""")
            append("""</fills>""")
            append("""<borders count="1">""")
            append("""<border><left/><right/><top/><bottom/></border>""")
            append("""</borders>""")
            append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
            append("""<cellXfs count="4">""")
            append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""")
            append("""<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""")
            append("""<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""")
            append("""<xf numFmtId="22" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""")
            append("""</cellXfs>""")
            append("""</styleSheet>""")
        }
        zip.write(styles.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 6. Worksheets
        for (sheet in sheets) {
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${sheet.id}.xml"))
            val writer = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            val sheetBuilder = XmlSheetBuilder(writer)
            sheet.builder(sheetBuilder)
            sheetBuilder.finish()
            writer.flush()
            zip.closeEntry()
        }

        zip.finish()
    }

    fun generateCsvZip(snapshot: AppSnapshot, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)
        val today = snapshot.today

        fun addCsvEntry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        // Summary
        addCsvEntry("01_summary.csv", buildString {
            appendLine("Metric,Value,Unit")
            appendLine("Export Date,${LocalDate.now()},")
            appendLine("Today Food Spent,${snapshot.todayFoodSpent},EGP")
            appendLine("Food Spent This Month,${snapshot.foodSpentThisMonth},EGP")
            appendLine("Spares Balance,${snapshot.sparesBalance},EGP")
            appendLine("Monthly Food Ceiling,${snapshot.settings.monthlyFoodBudget},EGP")
            appendLine("Daily Food Average,${snapshot.settings.dailyFoodBudget},EGP")
            appendLine("Weekly Food Limit,${snapshot.settings.weeklyFoodLimit},EGP")
            appendLine("Total Stock Items,${snapshot.stock.size},")
            appendLine("Total Catalog Items,${snapshot.foodCatalog.size},")
            appendLine("Open Shopping Count,${snapshot.openShoppingCount},")
        })

        // Spending
        addCsvEntry("02_spending.csv", buildString {
            appendLine("id,date,category_id,amount,food_classification,description,notes,is_recurring,is_correction,is_budget_transfer")
            snapshot.expenses.sortedByDescending { it.date }.forEach { e ->
                appendLine("${e.id},${e.date},${csvCell(e.categoryId)},${e.amount},${csvCell(e.foodClassification.orEmpty())},${csvCell(e.description)},${csvCell(e.notes)},${e.isRecurring},${e.isCorrection},${e.isBudgetTransfer}")
            }
        })

        // Meals
        addCsvEntry("03_meals.csv", buildString {
            appendLine("id,date,meal_type,template_id,name,meal_marker,status,actual_time,recipe_cost,consumed_cost,leftover_cost,cash_paid,foods")
            snapshot.mealLogs.sortedByDescending { it.date }.forEach { m ->
                appendLine("${m.id},${m.date},${m.mealType.name},${m.templateId.orEmpty()},${csvCell(m.name)},${m.marker.name},${m.status.name},${m.actualTime},${m.cost},${m.consumedCost},${m.leftoverCost},${m.cashPaid ?: ""},${csvCell(m.foodSummary)}")
            }
        })

        // Stock
        addCsvEntry("04_stock.csv", buildString {
            appendLine("id,name,category,unit,total_quantity,consumed_quantity,remaining_quantity,total_price,cost_per_unit,purchase_date,expiry_date,batch_type,usage_per_day,catalog_id,conversion_known,notes")
            snapshot.stock.forEach { s ->
                appendLine("${s.id},${csvCell(s.name)},${s.category.name},${s.unit},${s.totalQuantity},${s.consumedQuantity},${s.remainingQuantity},${s.totalPrice},${s.costPerUnit},${s.purchaseDate},${s.expiryDate ?: ""},${s.batchType.name},${s.effectiveUsagePerDay},${s.catalogId.orEmpty()},${s.conversionKnown},${csvCell(s.notes)}")
            }
        })

        // Food Catalog
        addCsvEntry("05_food_catalog.csv", buildString {
            appendLine("id,name,category,stock_unit,portion_unit,portions_per_stock_unit,default_cost_per_portion,purchase_price,purchase_quantity,purchase_unit,meal_usage,price_known,conversion_known,notes")
            snapshot.foodCatalog.forEach { c ->
                appendLine("${c.id},${csvCell(c.name)},${c.category.name},${c.stockUnit},${c.portionUnit},${c.portionsPerStockUnit},${c.defaultCostPerPortion},${c.purchasePrice ?: ""},${c.purchaseQuantity ?: ""},${csvCell(c.purchaseUnit.orEmpty())},${csvCell(c.mealUsage)},${c.priceKnown},${c.conversionKnown},${csvCell(c.notes)}")
            }
        })

        // Shopping
        addCsvEntry("06_shopping.csv", buildString {
            appendLine("id,name,quantity,unit,estimated_price,category,is_checked,created_date,note,purchase_expense_id,purchase_stock_id,price_known")
            snapshot.shopping.forEach { sh ->
                appendLine("${sh.id},${csvCell(sh.name)},${sh.quantity},${sh.unit},${sh.estimatedPrice},${sh.category.name},${sh.isChecked},${sh.createdDate},${csvCell(sh.note)},${sh.purchaseExpenseId.orEmpty()},${sh.purchaseStockId.orEmpty()},${sh.priceKnown}")
            }
        })

        // Savings
        addCsvEntry("07_savings.csv", buildString {
            appendLine("id,date,amount,reason,category")
            snapshot.spares.forEach { sp ->
                appendLine("${sp.id},${sp.date},${sp.amount},${csvCell(sp.reason)},${csvCell(sp.category.orEmpty())}")
            }
        })

        // Day Plans
        addCsvEntry("08_day_plans.csv", buildString {
            appendLine("id,date,meal_type,template_id,name,meal_marker,cost,foods,is_cleared")
            snapshot.dayPlans.forEach { dp ->
                appendLine("${dp.id},${dp.date},${dp.mealType.name},${dp.templateId.orEmpty()},${csvCell(dp.name)},${dp.marker.name},${dp.cost},${csvCell(dp.foods)},${dp.isCleared}")
            }
        })

        // Day Closures
        addCsvEntry("09_day_closures.csv", buildString {
            appendLine("date,closed_at,planned_cost,consumed_cost,leftover_cost,skipped_cost")
            snapshot.dayClosures.forEach { dc ->
                appendLine("${dc.date},${dc.closedAt},${dc.plannedCost},${dc.consumedCost},${dc.leftoverCost},${dc.skippedCost}")
            }
        })

        // Categories
        addCsvEntry("10_categories.csv", buildString {
            appendLine("id,name,icon,monthly_budget,is_food,is_custom,sort_order")
            snapshot.categories.forEach { cat ->
                appendLine("${cat.id},${csvCell(cat.name)},${cat.icon},${cat.monthlyBudget},${cat.isFood},${cat.isCustom},${cat.sortOrder}")
            }
        })

        // Settings
        addCsvEntry("11_settings.csv", buildString {
            val s = snapshot.settings
            appendLine("key,value")
            appendLine("monthly_food_budget,${s.monthlyFoodBudget}")
            appendLine("daily_food_budget,${s.dailyFoodBudget}")
            appendLine("weekly_food_limit,${s.weeklyFoodLimit}")
            appendLine("koshary_day,${s.kosharyDay.name}")
            appendLine("koshary_price,${s.kosharyPrice}")
            appendLine("auto_save_spares,${s.autoSaveSpares}")
            appendLine("reminders_enabled,${s.remindersEnabled}")
            appendLine("shopping_day,${s.shoppingDay.name}")
            appendLine("theme_mode,${s.themeMode.name}")
            appendLine("food_preferences,${csvCell(s.foodPreferences)}")
            appendLine("food_exclusions,${csvCell(s.foodExclusions)}")
            appendLine("habit_messages_enabled,${s.habitMessagesEnabled}")
            appendLine("breakfast_start,${s.breakfastStart}")
            appendLine("breakfast_end,${s.breakfastEnd}")
            appendLine("lunch_start,${s.lunchStart}")
            appendLine("lunch_end,${s.lunchEnd}")
            appendLine("dinner_start,${s.dinnerStart}")
            appendLine("dinner_end,${s.dinnerEnd}")
        })

        zip.finish()
    }

    private fun csvCell(raw: String): String {
        return if (raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')) {
            "\"" + raw.replace("\"", "\"\"") + "\""
        } else {
            raw
        }
    }

    private data class SheetDef(
        val name: String,
        val id: Int,
        val builder: (XmlSheetBuilder) -> Unit,
    )

    private fun writeSummarySheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Metric", "Value", "Unit", "Note"),
            widths = listOf(28.0, 18.0, 12.0, 40.0),
        )
        s.row(listOf("Export Generated", LocalDateTime.now(), "", "Automatic & manual backup available"))
        s.row(listOf("Snapshot Date", snapshot.today, "", "Phone local date"))
        s.row(listOf("Today Food Spent", snapshot.todayFoodSpent, "EGP", "Actual cash food expenses today"))
        s.row(listOf("Food Spent This Month", snapshot.foodSpentThisMonth, "EGP", "Food expenses recorded in current month"))
        s.row(listOf("Spares Savings Balance", snapshot.sparesBalance, "EGP", "Accumulated food savings / spares balance"))
        s.row(listOf("Monthly Food Ceiling", snapshot.settings.monthlyFoodBudget, "EGP", "Configured monthly target"))
        s.row(listOf("Daily Food Average Target", snapshot.settings.dailyFoodBudget, "EGP", "Configured daily average target"))
        s.row(listOf("Weekly Food Limit", snapshot.settings.weeklyFoodLimit, "EGP", "Configured weekly target"))
        s.row(listOf("Active Stock Items", snapshot.stock.count { !it.isFinished }, "", "Pantry batches with remaining inventory"))
        s.row(listOf("Total Catalog Foods", snapshot.foodCatalog.size, "", "Known foods and portion conversions"))
        s.row(listOf("Open Shopping Items", snapshot.openShoppingCount, "", "Items waiting to be purchased"))
    }

    private fun writeSpendingSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Expense ID", "Date", "Category ID", "Category Name", "Amount (EGP)", "Food Classification", "Description", "Notes", "Recurring", "Correction", "Transfer"),
            widths = listOf(26.0, 14.0, 16.0, 18.0, 16.0, 20.0, 32.0, 32.0, 12.0, 12.0, 12.0),
        )
        val catMap = snapshot.categories.associateBy { it.id }
        snapshot.expenses.sortedByDescending { it.date }.forEach { e ->
            s.row(listOf(
                e.id,
                e.date,
                e.categoryId,
                catMap[e.categoryId]?.name ?: e.categoryId,
                e.amount,
                e.foodClassification ?: "",
                e.description,
                e.notes,
                if (e.isRecurring) "Yes" else "No",
                if (e.isCorrection) "Yes" else "No",
                if (e.isBudgetTransfer) "Yes" else "No",
            ))
        }
    }

    private fun writeMealsSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Meal Log ID", "Date", "Meal Type", "Template ID", "Meal Name", "Meal Marker", "Status", "Actual Time", "Recipe Cost (EGP)", "Consumed Cost (EGP)", "Leftover Cost (EGP)", "Cash Paid (EGP)", "Foods Summary"),
            widths = listOf(26.0, 14.0, 14.0, 20.0, 28.0, 16.0, 14.0, 22.0, 18.0, 18.0, 18.0, 18.0, 38.0),
        )
        snapshot.mealLogs.sortedByDescending { it.date }.forEach { m ->
            s.row(listOf(
                m.id,
                m.date,
                m.mealType.label,
                m.templateId ?: "",
                m.name,
                m.marker.label,
                m.status.name,
                m.actualTime,
                m.cost,
                m.consumedCost,
                m.leftoverCost,
                m.cashPaid ?: "",
                m.foodSummary,
            ))
        }
    }

    private fun writeMealIngredientsSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Source", "Source ID", "Meal / Template Name", "Component ID", "Catalog ID", "Ingredient Name", "Quantity", "Unit", "Cost Per Unit (EGP)", "Estimated Cost (EGP)", "Use Stock"),
            widths = listOf(14.0, 26.0, 26.0, 26.0, 20.0, 24.0, 14.0, 12.0, 18.0, 18.0, 12.0),
        )
        snapshot.mealLogs.forEach { log ->
            log.components.forEach { c ->
                s.row(listOf(
                    "MealLog",
                    log.id,
                    log.name,
                    c.id,
                    c.catalogId ?: "",
                    c.name,
                    c.quantity,
                    c.unit,
                    c.costPerUnit,
                    c.estimatedCost,
                    if (c.useStock) "Yes" else "No",
                ))
            }
        }
        snapshot.templates.forEach { tmpl ->
            tmpl.components.forEach { c ->
                s.row(listOf(
                    "Template",
                    tmpl.id,
                    tmpl.name,
                    c.id,
                    c.catalogId ?: "",
                    c.name,
                    c.quantity,
                    c.unit,
                    c.costPerUnit,
                    c.estimatedCost,
                    if (c.useStock) "Yes" else "No",
                ))
            }
        }
    }

    private fun writeStockSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Stock ID", "Name", "Category", "Base Unit", "Total Qty", "Consumed Qty", "Remaining Qty", "Total Price (EGP)", "Cost Per Unit (EGP)", "Purchase Date", "Expiry Date", "Batch Type", "Usage Per Day", "Package Info", "Catalog ID", "Linked Expense ID", "Conversion Known", "Notes"),
            widths = listOf(24.0, 22.0, 16.0, 12.0, 14.0, 14.0, 14.0, 18.0, 18.0, 14.0, 14.0, 16.0, 16.0, 20.0, 18.0, 24.0, 16.0, 28.0),
        )
        snapshot.stock.forEach { item ->
            s.row(listOf(
                item.id,
                item.name,
                item.category.label,
                item.unit,
                item.totalQuantity,
                item.consumedQuantity,
                item.remainingQuantity,
                item.totalPrice,
                item.costPerUnit,
                item.purchaseDate,
                item.expiryDate,
                item.batchType.label,
                item.effectiveUsagePerDay,
                item.packageLabel,
                item.catalogId ?: "",
                item.linkedExpenseId ?: "",
                if (item.conversionKnown) "Yes" else "No",
                item.notes,
            ))
        }
    }

    private fun writeStockUsageSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Stock ID", "Stock Name", "Usage Date", "Amount Used", "Unit", "Source ID"),
            widths = listOf(24.0, 22.0, 14.0, 16.0, 12.0, 28.0),
        )
        snapshot.stock.forEach { item ->
            item.datedUsageHistory.forEach { usage ->
                s.row(listOf(
                    item.id,
                    item.name,
                    usage.date,
                    usage.amount,
                    item.unit,
                    usage.sourceId ?: "",
                ))
            }
        }
    }

    private fun writeFoodCatalogSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Catalog ID", "Name", "Category", "Stock Unit", "Portion Unit", "Portions / Stock Unit", "Default Cost / Portion (EGP)", "Purchase Price (EGP)", "Purchase Qty", "Purchase Unit", "Price Known", "Conversion Known", "Meal Usage", "Notes"),
            widths = listOf(20.0, 24.0, 16.0, 14.0, 14.0, 20.0, 24.0, 18.0, 16.0, 16.0, 14.0, 16.0, 26.0, 30.0),
        )
        snapshot.foodCatalog.forEach { c ->
            s.row(listOf(
                c.id,
                c.name,
                c.category.label,
                c.stockUnit,
                c.portionUnit,
                c.portionsPerStockUnit,
                c.defaultCostPerPortion,
                c.purchasePrice ?: "",
                c.purchaseQuantity ?: "",
                c.purchaseUnit ?: "",
                if (c.priceKnown) "Yes" else "No",
                if (c.conversionKnown) "Yes" else "No",
                c.mealUsage,
                c.notes,
            ))
        }
    }

    private fun writeShoppingSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Item ID", "Name", "Quantity", "Unit", "Estimated Price (EGP)", "Category", "Checked", "Created Date", "Note", "Price Known", "Linked Purchase Expense ID", "Linked Stock ID"),
            widths = listOf(24.0, 24.0, 14.0, 12.0, 20.0, 16.0, 12.0, 14.0, 28.0, 14.0, 26.0, 24.0),
        )
        snapshot.shopping.forEach { sh ->
            s.row(listOf(
                sh.id,
                sh.name,
                sh.quantity,
                sh.unit,
                sh.estimatedPrice,
                sh.category.label,
                if (sh.isChecked) "Yes" else "No",
                sh.createdDate,
                sh.note,
                if (sh.priceKnown) "Yes" else "No",
                sh.purchaseExpenseId ?: "",
                sh.purchaseStockId ?: "",
            ))
        }
    }

    private fun writeSavingsSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Savings Transaction ID", "Date", "Amount (EGP)", "Reason", "Category"),
            widths = listOf(26.0, 14.0, 16.0, 34.0, 20.0),
        )
        snapshot.spares.forEach { sp ->
            s.row(listOf(
                sp.id,
                sp.date,
                sp.amount,
                sp.reason,
                sp.category ?: "",
            ))
        }
    }

    private fun writeDayPlansSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Plan ID", "Date", "Meal Type", "Template ID", "Name", "Meal Marker", "Cost (EGP)", "Foods / Notes", "Cleared"),
            widths = listOf(24.0, 14.0, 14.0, 20.0, 26.0, 16.0, 16.0, 34.0, 12.0),
        )
        snapshot.dayPlans.forEach { dp ->
            s.row(listOf(
                dp.id,
                dp.date,
                dp.mealType.label,
                dp.templateId ?: "",
                dp.name,
                dp.marker.label,
                dp.cost,
                dp.foods,
                if (dp.isCleared) "Yes" else "No",
            ))
        }
    }

    private fun writeDayClosuresSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Date", "Closed At", "Planned Cost (EGP)", "Consumed Cost (EGP)", "Leftover Cost (EGP)", "Skipped Cost (EGP)"),
            widths = listOf(14.0, 24.0, 18.0, 20.0, 18.0, 18.0),
        )
        snapshot.dayClosures.forEach { dc ->
            s.row(listOf(
                dc.date,
                dc.closedAt,
                dc.plannedCost,
                dc.consumedCost,
                dc.leftoverCost,
                dc.skippedCost,
            ))
        }
    }

    private fun writeCategoriesSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Category ID", "Name", "Icon", "Monthly Budget (EGP)", "Is Food", "Is Custom", "Sort Order"),
            widths = listOf(18.0, 22.0, 16.0, 22.0, 12.0, 12.0, 12.0),
        )
        snapshot.categories.forEach { cat ->
            s.row(listOf(
                cat.id,
                cat.name,
                cat.icon,
                cat.monthlyBudget,
                if (cat.isFood) "Yes" else "No",
                if (cat.isCustom) "Yes" else "No",
                cat.sortOrder,
            ))
        }
    }

    private fun writeSettingsSheet(s: XmlSheetBuilder, snapshot: AppSnapshot) {
        s.init(
            headers = listOf("Setting Name", "Setting Value", "Description"),
            widths = listOf(26.0, 24.0, 44.0),
        )
        val st = snapshot.settings
        s.row(listOf("Monthly Food Budget", st.monthlyFoodBudget, "EGP monthly food budget ceiling"))
        s.row(listOf("Daily Food Budget Target", st.dailyFoodBudget, "EGP daily average food budget target"))
        s.row(listOf("Weekly Food Limit", st.weeklyFoodLimit, "EGP weekly food limit"))
        s.row(listOf("Koshary Day", st.kosharyDay.name, "Configured weekly koshary day"))
        s.row(listOf("Koshary Price", st.kosharyPrice, "EGP price for koshary day"))
        s.row(listOf("Auto Save Spares", if (st.autoSaveSpares) "Enabled" else "Disabled", "Automatically move unspent daily food budget to spares"))
        s.row(listOf("Reminders", if (st.remindersEnabled) "Enabled" else "Disabled", "Daily reminder notifications"))
        s.row(listOf("Shopping Day", st.shoppingDay.name, "Configured weekly shopping day"))
        s.row(listOf("Theme Mode", st.themeMode.label, "UI theme mode"))
        s.row(listOf("Food Preferences", st.foodPreferences, "Preferred meals or ingredients"))
        s.row(listOf("Food Exclusions", st.foodExclusions, "Excluded allergens, diets, or ingredients"))
        s.row(listOf("Habit Messages", if (st.habitMessagesEnabled) "Enabled" else "Disabled", "Factual habit observations on Home"))
        s.row(listOf("Breakfast Window", "${st.breakfastStart} - ${st.breakfastEnd}", "Start and end time for breakfast"))
        s.row(listOf("Lunch Window", "${st.lunchStart} - ${st.lunchEnd}", "Start and end time for lunch"))
        s.row(listOf("Dinner Window", "${st.dinnerStart} - ${st.dinnerEnd}", "Start and end time for dinner (supports overnight)"))
    }

    private class XmlSheetBuilder(private val writer: Writer) {
        private var rowCount = 0
        private var columnCount = 0

        fun init(headers: List<String>, widths: List<Double>) {
            columnCount = headers.size
            writer.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            writer.write("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            writer.write("""<sheetViews><sheetView tabSelected="0" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            writer.write("""<cols>""")
            widths.forEachIndexed { i, w ->
                val colIdx = i + 1
                val colWidth = String.format(Locale.US, "%.1f", w)
                writer.write("""<col min="$colIdx" max="$colIdx" width="$colWidth" customWidth="1"/>""")
            }
            writer.write("""</cols>""")
            writer.write("""<sheetData>""")

            // Write Header row
            rowCount = 1
            writer.write("""<row r="1">""")
            headers.forEachIndexed { i, h ->
                val ref = "${colLetter(i + 1)}1"
                writer.write("""<c r="$ref" t="inlineStr" s="1"><is><t>${escapeXml(h)}</t></is></c>""")
            }
            writer.write("""</row>""")
        }

        fun row(values: List<Any?>) {
            rowCount++
            writer.write("""<row r="$rowCount">""")
            values.forEachIndexed { i, v ->
                val colRef = "${colLetter(i + 1)}$rowCount"
                when (v) {
                    null -> {}
                    is LocalDate -> {
                        val serial = ChronoUnit.DAYS.between(EXCEL_EPOCH, v)
                        writer.write("""<c r="$colRef" t="n" s="2"><v>$serial</v></c>""")
                    }
                    is LocalDateTime -> {
                        val datePart = ChronoUnit.DAYS.between(EXCEL_EPOCH, v.toLocalDate()).toDouble()
                        val timePart = v.toLocalTime().toSecondOfDay().toDouble() / 86400.0
                        val serial = BigDecimal.valueOf(datePart + timePart).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                        writer.write("""<c r="$colRef" t="n" s="3"><v>$serial</v></c>""")
                    }
                    is Number -> {
                        val num = v.toDouble()
                        if (num.isFinite()) {
                            val str = BigDecimal.valueOf(num).stripTrailingZeros().toPlainString()
                            writer.write("""<c r="$colRef" t="n"><v>$str</v></c>""")
                        }
                    }
                    is Boolean -> {
                        writer.write("""<c r="$colRef" t="b"><v>${if (v) 1 else 0}</v></c>""")
                    }
                    else -> {
                        val s = v.toString()
                        if (s.isNotEmpty()) {
                            writer.write("""<c r="$colRef" t="inlineStr"><is><t>${escapeXml(s)}</t></is></c>""")
                        }
                    }
                }
            }
            writer.write("""</row>""")
        }

        fun finish() {
            writer.write("""</sheetData>""")
            if (rowCount >= 1 && columnCount >= 1) {
                val lastCol = colLetter(columnCount)
                writer.write("""<autoFilter ref="A1:$lastCol$rowCount"/>""")
            }
            writer.write("""</worksheet>""")
        }
    }

    private fun colLetter(colIndex: Int): String {
        var c = colIndex
        val sb = StringBuilder()
        while (c > 0) {
            val rem = (c - 1) % 26
            sb.append(('A'.code + rem).toChar())
            c = (c - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(text: String): String {
        val sb = java.lang.StringBuilder(text.length + 16)
        for (char in text) {
            when (char) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(char)
            }
        }
        return sb.toString()
    }
}
