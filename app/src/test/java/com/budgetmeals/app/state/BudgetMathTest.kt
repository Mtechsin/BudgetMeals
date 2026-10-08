package com.budgetmeals.app.state

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.BudgetRepository
import com.budgetmeals.app.data.BatchType
import com.budgetmeals.app.data.DailyMealPlan
import com.budgetmeals.app.data.DatabaseMigrations
import com.budgetmeals.app.data.DatedUsage
import com.budgetmeals.app.data.DayClosure
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealDataCodec
import com.budgetmeals.app.data.Expense
import com.budgetmeals.app.data.ExpenseCategory
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodCatalogJsonCodec
import com.budgetmeals.app.data.ItemCategory
import com.budgetmeals.app.data.FoodMeasurementCodec
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.MealLog
import com.budgetmeals.app.data.MealStatus
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import com.budgetmeals.app.data.TieredSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetMathTest {
    private val today = LocalDate.now()
    private val foodCategory = ExpenseCategory(
        id = "food",
        name = "Food",
        icon = "restaurant",
        monthlyBudget = 3_000.0,
        isFood = true,
    )

    @Test
    fun markingAMealDoesNotChargeTheFoodBudgetAgain() {
        val snapshot = AppSnapshot(
            categories = listOf(foodCategory),
            expenses = listOf(
                Expense(categoryId = "food", amount = 100.0, date = today),
            ),
            mealLogs = listOf(
                MealLog(
                    date = today,
                    mealType = MealType.LUNCH,
                    name = "Lunch",
                    cost = 40.0,
                    status = MealStatus.EATEN,
                ),
            ),
        )

        assertEquals(100.0, snapshot.foodSpentThisMonth, 0.001)
        assertEquals(100.0, BudgetMath.foodSpentOn(today, snapshot), 0.001)
    }

    @Test
    fun foodCorrectionsRemainPartOfTheExpenseLedger() {
        val snapshot = AppSnapshot(
            categories = listOf(foodCategory),
            expenses = listOf(
                Expense(categoryId = "food", amount = 100.0, date = today),
                Expense(categoryId = "food", amount = 30.0, date = today, isCorrection = true),
                Expense(categoryId = "food", amount = -10.0, date = today, isCorrection = true),
            ),
        )

        assertEquals(120.0, snapshot.foodSpentThisMonth, 0.001)
    }

    @Test
    fun persistedDayPlanDoesNotChangeWhenTemplatesChange() {
        val savedPlan = DailyMealPlan(
            id = "saved-lunch",
            date = today,
            mealType = MealType.LUNCH,
            templateId = "old-template",
            name = "Saved lunch",
            cost = 55.0,
            foods = "Rice, beans, salad",
        )
        val snapshot = AppSnapshot(dayPlans = listOf(savedPlan))

        val planned = BudgetMath.plannedMealsForDate(snapshot, today)
        val summary = BudgetMath.dayMealSummary(snapshot, today)

        assertEquals("Saved lunch", planned.getValue(MealType.LUNCH).name)
        assertEquals(55.0, summary.plannedCost, 0.001)
        assertEquals(1, summary.pendingMeals)
    }

    @Test
    fun explicitStatusHandlesAZeroCostEatenMeal() {
        val plan = DailyMealPlan(
            id = "zero-cost-breakfast",
            date = today,
            mealType = MealType.BREAKFAST,
            templateId = "free-breakfast",
            name = "Free breakfast",
            cost = 0.0,
            foods = "Tea",
        )
        val log = MealLog(
            date = today,
            mealType = MealType.BREAKFAST,
            name = plan.name,
            cost = 0.0,
            consumedCost = 0.0,
            status = MealStatus.EATEN,
        )
        val snapshot = AppSnapshot(
            dayPlans = listOf(plan),
            mealLogs = listOf(log),
            dayClosures = listOf(
                DayClosure(
                    date = today,
                    closedAt = today.atTime(23, 0),
                    plannedCost = 0.0,
                    consumedCost = 0.0,
                    leftoverCost = 0.0,
                    skippedCost = 0.0,
                ),
            ),
        )

        val summary = BudgetMath.dayMealSummary(snapshot, today)

        assertEquals(1, summary.consumedMeals)
        assertEquals(0, summary.skippedMeals)
        assertEquals(0, summary.pendingMeals)
        assertTrue(summary.isClosed)
    }

    @Test
    fun mealPlanWindowAlwaysIncludesFullWeek() {
        val saturday = LocalDate.of(2026, 9, 26)
        val saturdayWindow = BudgetMath.mealPlanDates(saturday)
        val mondayWindow = BudgetMath.mealPlanDates(LocalDate.of(2026, 9, 28))
        val fridayWindow = BudgetMath.mealPlanDates(LocalDate.of(2026, 10, 2))

        assertEquals(7, fridayWindow.size)
        assertEquals(LocalDate.of(2026, 10, 2), fridayWindow.first())
        assertEquals(LocalDate.of(2026, 10, 8), fridayWindow.last())

        assertEquals(7, saturdayWindow.size)
        assertEquals(saturday, saturdayWindow.first())
        assertEquals(LocalDate.of(2026, 10, 2), saturdayWindow.last())

        assertEquals(7, mondayWindow.size)
        assertEquals(LocalDate.of(2026, 9, 28), mondayWindow.first())
        assertEquals(LocalDate.of(2026, 10, 4), mondayWindow.last())
    }

    @Test
    fun mealComponentsRoundTripThroughTheLocalDatabaseFormat() {
        val original = listOf(
            MealComponent(
                id = "tomato-row",
                catalogId = "catalog-tomatoes",
                name = "Tomatoes",
                quantity = 2.0,
                unit = "piece",
                costPerUnit = 5.0,
            ),
        )

        val restored = MealDataCodec.decodeComponents(MealDataCodec.encodeComponents(original))

        assertEquals(original, restored)
    }

    @Test
    fun starterTomatoesCountSixPiecesPerKilogram() {
        val tomatoes = FoodCatalogItem(
            id = "catalog_tomatoes",
            name = "Tomatoes",
            stockUnit = "kg",
            portionUnit = "piece",
            portionsPerStockUnit = 6.0,
            defaultCostPerPortion = 3.0,
        )
        val snapshot = AppSnapshot(
            foodCatalog = listOf(tomatoes),
            stock = listOf(
                StockItem(
                    id = "tomatoes",
                    name = "Tomatoes",
                    unit = "g",
                    totalQuantity = 1_000.0,
                    totalPrice = 30.0,
                    catalogId = tomatoes.id,
                ),
            ),
        )

        // 1000 g at 6 pieces per kg is 6 pieces, not 6000.
        assertEquals(6.0, snapshot.availabilityFor(tomatoes).portions, 0.001)
        assertEquals(6.0, BudgetMath.catalogAvailablePortions(snapshot, tomatoes), 0.001)
        // 0.03 EGP per gram at ~166.67 g per tomato is ~5 EGP per tomato.
        assertEquals(5.0, BudgetMath.catalogCostPerPortion(snapshot, tomatoes), 0.001)
    }

    @Test
    fun starterEggsLinkToTheirCarton() {
        val eggs = FoodCatalogItem(
            id = "catalog_eggs",
            name = "Eggs",
            stockUnit = "carton (30)",
            portionUnit = "egg",
            portionsPerStockUnit = 30.0,
            defaultCostPerPortion = 5.5,
        )
        val snapshot = AppSnapshot(
            foodCatalog = listOf(eggs),
            stock = listOf(
                StockItem(
                    id = "eggs",
                    name = "Eggs",
                    unit = "egg",
                    totalQuantity = 12.0,
                    totalPrice = 60.0,
                    catalogId = eggs.id,
                    packageLabel = "carton (30)",
                    packageSize = 30.0,
                ),
            ),
        )

        // 12 single eggs is just under half a carton of 30, not an empty shelf.
        assertEquals(12.0, snapshot.availabilityFor(eggs).portions, 0.001)
        assertEquals(1, snapshot.usableLots(eggs).size)
        assertEquals(0, snapshot.unusableLots(eggs).size)
        assertEquals(0.4, snapshot.usableLots(eggs).single().packageCount, 0.001)
        assertEquals(5.0, BudgetMath.catalogCostPerPortion(snapshot, eggs), 0.001)
    }

    @Test
    fun gramStockNeverInflatesToPackagePortions() {
        val jam = FoodCatalogItem(
            id = "catalog_jam",
            name = "Jam",
            stockUnit = "jar (380g)",
            portionUnit = "spoon",
            portionsPerStockUnit = 19.0,
            defaultCostPerPortion = 3.2,
        )
        val cheese = FoodCatalogItem(
            id = "catalog_cheese",
            name = "Cheese",
            stockUnit = "tub (500g)",
            portionUnit = "spoon",
            portionsPerStockUnit = 25.0,
            defaultCostPerPortion = 3.0,
        )
        val snapshot = AppSnapshot(
            foodCatalog = listOf(jam, cheese),
            stock = listOf(
                StockItem(id = "jam", name = "Jam", unit = "g", totalQuantity = 650.0, totalPrice = 55.0, catalogId = jam.id),
                StockItem(id = "cheese", name = "Cheese", unit = "g", totalQuantity = 250.0, totalPrice = 25.0, catalogId = cheese.id),
            ),
        )

        // 650 g at 20 g per spoon is 32.5 spoons, never 12,350.
        assertEquals(32.5, snapshot.availabilityFor(jam).portions, 0.001)
        // 250 g at 25 spoons per 500 g tub is 12.5 spoons, never 6,250.
        assertEquals(12.5, snapshot.availabilityFor(cheese).portions, 0.001)
    }

    @Test
    fun packageLabelsDoNotMatchThroughText() {
        assertFalse(BudgetMath.areStockUnitsMatching("g", "jar (380g)"))
        assertFalse(BudgetMath.areStockUnitsMatching("g", "tub (500g)"))
        assertFalse(BudgetMath.areStockUnitsMatching("ml", "bottle (700ml)"))
        assertNull(BudgetRepository.convertStockQuantity(650.0, "g", "jar (380g)", null))
        assertNull(BudgetRepository.convertStockQuantity(250.0, "g", "tub (500g)", null))
    }

    @Test
    fun unconvertableStockIsFlaggedInsteadOfCounted() {
        val jam = FoodCatalogItem(
            id = "catalog_jam",
            name = "Jam",
            stockUnit = "jar (380g)",
            portionUnit = "spoon",
            portionsPerStockUnit = 19.0,
            defaultCostPerPortion = 3.2,
        )
        val snapshot = AppSnapshot(
            foodCatalog = listOf(jam),
            stock = listOf(
                StockItem(
                    id = "jam-mystery",
                    name = "Jam",
                    unit = "jar",
                    totalQuantity = 2.0,
                    totalPrice = 40.0,
                    catalogId = jam.id,
                    conversionKnown = false,
                ),
            ),
        )

        val availability = snapshot.availabilityFor(jam)
        assertEquals(0.0, availability.portions, 0.001)
        assertEquals(0, availability.usableLots)
        assertEquals(1, availability.unusableLots)
        assertEquals("Stock unit could not be converted", availability.note)
        assertEquals(jam.defaultCostPerPortion, BudgetMath.catalogCostPerPortion(snapshot, jam), 0.001)
    }

    @Test
    fun batchPackageFactsDoNotRewriteTheSharedCatalog() {
        val jam = FoodCatalogItem(
            id = "catalog_jam",
            name = "Jam",
            stockUnit = "jar (380g)",
            portionUnit = "spoon",
            portionsPerStockUnit = 19.0,
        )
        val batch = StockItem(
            name = "Jam",
            unit = "g",
            totalQuantity = 500.0,
            totalPrice = 40.0,
            catalogId = jam.id,
            packageLabel = "jar (380g)",
            packageSize = 380.0,
        )

        // The shared item stays exactly as it was; only the batch carries the purchase.
        assertEquals(19.0, jam.portionsPerStockUnit, 0.001)
        assertEquals("spoon", jam.portionUnit)
        assertEquals("g", jam.baseUnit)
        assertEquals(20.0, jam.baseUnitsPerPortion, 0.001)
        assertEquals(500.0 / 380.0, batch.packageCount, 0.001)
    }

    @Test
    fun catalogItemCalculatesCostForMealComponent() {
        val cucumber = FoodCatalogItem(
            id = "cucumbers",
            name = "Cucumbers",
            stockUnit = "kg",
            portionUnit = "piece",
            portionsPerStockUnit = 10.0,
            defaultCostPerPortion = 2.5,
        )
        val snapshot = AppSnapshot(foodCatalog = listOf(cucumber))
        val cost = BudgetMath.catalogCostPerPortion(snapshot, cucumber)
        val component = MealComponent(
            catalogId = cucumber.id,
            name = cucumber.name,
            quantity = 3.0,
            unit = cucumber.portionUnit,
            costPerUnit = cost,
        )

        assertEquals(2.5, cost, 0.001)
        assertEquals(7.5, component.estimatedCost, 0.001)
    }

    @Test
    fun cheeseMeasuresInSpoonsAndConvertsToGrams() {
        val cheese = FoodCatalogItem(
            id = "cheese-item",
            name = "Cheese",
            stockUnit = "kg",
            portionUnit = "spoon",
            portionsPerStockUnit = 100.0,
            defaultCostPerPortion = 1.6,
            notes = FoodMeasurementCodec.encodeNotes(
                userNotes = "White cheese",
                measurement = FoodMeasurementType.SpoonsToGrams(gramsPerSpoon = 10.0, spoonUnitName = "spoon"),
            ),
        )

        assertTrue(cheese.isSpoons)
        val measurement = cheese.measurement as FoodMeasurementType.SpoonsToGrams
        assertEquals(10.0, measurement.gramsPerSpoon, 0.001)
        assertEquals("spoon", measurement.spoonUnitName)
        assertEquals("White cheese", cheese.cleanUserNotes)

        // 1 spoon should be ~10g
        assertEquals("1 spoon (~10g)", cheese.formatPortionQuantity(1.0))
        // 3 spoons should be ~30g
        assertEquals("3 spoons (~30g)", cheese.formatPortionQuantity(3.0))

        val component = MealComponent(
            catalogId = cheese.id,
            name = cheese.name,
            quantity = 3.0,
            unit = "spoon",
            costPerUnit = cheese.defaultCostPerPortion,
        )
        // 3 spoons at 1.6 EGP/spoon = 4.8 EGP
        assertEquals(4.8, component.estimatedCost, 0.001)
    }

    @Test
    fun chipsMeasureInTieredSizesWithPrices() {
        val chips = FoodCatalogItem(
            id = "chips-item",
            name = "Chips",
            stockUnit = "pack",
            portionUnit = "pack",
            portionsPerStockUnit = 1.0,
            defaultCostPerPortion = 15.0,
            notes = FoodMeasurementCodec.encodeNotes(
                userNotes = "Snack bags",
                measurement = FoodMeasurementType.TieredSizes(
                    listOf(
                        TieredSize("Small", 10.0),
                        TieredSize("Medium", 15.0),
                        TieredSize("Large", 20.0),
                    ),
                ),
            ),
        )

        assertTrue(cheeseOrChipsHasTiered(chips))
        val measurement = chips.measurement as FoodMeasurementType.TieredSizes
        assertEquals(3, measurement.sizes.size)
        assertEquals("Small", measurement.sizes[0].name)
        assertEquals(10.0, measurement.sizes[0].price, 0.001)
        assertEquals("Medium", measurement.sizes[1].name)
        assertEquals(15.0, measurement.sizes[1].price, 0.001)
        assertEquals("Large", measurement.sizes[2].name)
        assertEquals(20.0, measurement.sizes[2].price, 0.001)
        assertEquals("Snack bags", chips.cleanUserNotes)

        // A component with Small size costs 10 EGP
        val smallChips = MealComponent(
            catalogId = chips.id,
            name = "Chips (Small)",
            quantity = 1.0,
            unit = "Small",
            costPerUnit = 10.0,
        )
        assertEquals(10.0, smallChips.estimatedCost, 0.001)

        // A component with Large size costs 20 EGP
        val largeChips = MealComponent(
            catalogId = chips.id,
            name = "Chips (Large)",
            quantity = 2.0,
            unit = "Large",
            costPerUnit = 20.0,
        )
        assertEquals(40.0, largeChips.estimatedCost, 0.001)
    }

    @Test
    fun smartInferenceAutomaticallyDetectsCheeseAndChipsWithoutNotesJson() {
        val legacyCheese = FoodCatalogItem(
            name = "Cheese",
            stockUnit = "g",
            portionUnit = "g",
            defaultCostPerPortion = 0.16,
            notes = "",
        )
        assertTrue(legacyCheese.isSpoons)
        val cheeseMeasurement = legacyCheese.measurement as FoodMeasurementType.SpoonsToGrams
        assertEquals(10.0, cheeseMeasurement.gramsPerSpoon, 0.001)

        val legacyChips = FoodCatalogItem(
            name = "Chips",
            stockUnit = "pack",
            portionUnit = "pack",
            defaultCostPerPortion = 15.0,
            notes = "",
        )
        assertTrue(legacyChips.isTieredSizes)
        val chipsMeasurement = legacyChips.measurement as FoodMeasurementType.TieredSizes
        assertEquals(3, chipsMeasurement.sizes.size)
    }

    @Test
    fun areStockUnitsMatchingHandlesCaseAndContainers() {
        assertTrue(BudgetMath.areStockUnitsMatching("Jar", "jar"))
        assertTrue(BudgetMath.areStockUnitsMatching("jar (380g)", "jar"))
        assertTrue(BudgetMath.areStockUnitsMatching("Tub", "tub"))
        assertTrue(BudgetMath.areStockUnitsMatching("carton (30 eggs)", "carton"))
        assertTrue(BudgetMath.areStockUnitsMatching("carton", "carton (30)"))
        // Same dimension is not the same unit: quantities must still be converted.
        assertFalse(BudgetMath.areStockUnitsMatching("Kg", "g"))
        assertFalse(BudgetMath.areStockUnitsMatching("g", "kg"))
        assertFalse(BudgetMath.areStockUnitsMatching("ml", "l"))
        assertFalse(BudgetMath.areStockUnitsMatching("bottle", "loaf"))
    }

    @Test
    fun foodCatalogJsonRoundTripsAiEditableFields() {
        val original = FoodCatalogItem(
            id = "catalog_bread",
            name = "Bread",
            category = ItemCategory.FOOD_STAPLE,
            stockUnit = "loaf",
            portionUnit = "loaf",
            portionsPerStockUnit = 1.0,
            defaultCostPerPortion = 2.0,
            notes = "Any meal",
            purchasePrice = 2.0,
            purchaseQuantity = 1.0,
            purchaseUnit = "loaf",
            mealUsage = "any",
            priceKnown = true,
        )

        val restored = FoodCatalogJsonCodec.decode(FoodCatalogJsonCodec.encode(listOf(original))).getOrThrow().single()

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.purchasePrice!!, restored.purchasePrice!!, 0.001)
        assertEquals(original.mealUsage, restored.mealUsage)
        assertEquals(original.cleanUserNotes, restored.cleanUserNotes)
    }

    @Test
    fun foodCatalogJsonDerivesPerPortionCostFromPackagePrice() {
        val raw = """
            {
              "format": "budgetmeals.food-catalog",
              "version": 1,
              "items": [
                {
                  "id": "catalog_jam_user",
                  "name": "Jam",
                  "category": "FOOD_STAPLE",
                  "purchaseUnit": "jar",
                  "purchaseQuantity": 1,
                  "purchasePrice": 53,
                  "portionUnit": "g",
                  "portionsPerPurchaseUnit": 680,
                  "priceKnown": true,
                  "conversionKnown": true,
                  "mealUsage": "dinner or breakfast",
                  "notes": "680 g jar"
                }
              ]
            }
        """.trimIndent()

        val item = FoodCatalogJsonCodec.decode(raw).getOrThrow().single()

        assertEquals(53.0 / 680.0, item.defaultCostPerPortion, 0.0001)
        assertEquals("dinner or breakfast", item.mealUsage)
    }

    @Test
    fun foodCatalogJsonAcceptsTieredPricesFromAi() {
        val raw = """
            {
              "format": "budgetmeals.food-catalog",
              "version": 1,
              "items": [
                {
                  "name": "Chips",
                  "category": "SNACK",
                  "purchaseUnit": "pack",
                  "portionUnit": "pack",
                  "measurement": {
                    "type": "tiered",
                    "sizes": [
                      { "name": "Small", "price": 10 },
                      { "name": "Medium", "price": 15 },
                      { "name": "Large", "price": 20 }
                    ]
                  },
                  "mealUsage": "any",
                  "priceKnown": true
                }
              ]
            }
        """.trimIndent()

        val item = FoodCatalogJsonCodec.decode(raw).getOrThrow().single()
        val sizes = (item.measurement as FoodMeasurementType.TieredSizes).sizes

        assertEquals(3, sizes.size)
        assertEquals(10.0, sizes[0].price, 0.001)
        assertEquals(20.0, sizes[2].price, 0.001)
    }

    @Test
    fun stockConversionHandlesEgyptianMarketUnits() {
        val tomatoes = FoodCatalogItem(
            id = "tomatoes",
            name = "Tomatoes",
            stockUnit = "kg",
            portionUnit = "piece",
            portionsPerStockUnit = 6.0,
            defaultCostPerPortion = 3.0,
        )
        // 6 pieces of tomatoes = 1.0 kg
        assertEquals(1.0, BudgetRepository.convertStockQuantity(6.0, "piece", "kg", tomatoes)!!, 0.001)
        // 3 pieces of tomatoes = 0.5 kg
        assertEquals(0.5, BudgetRepository.convertStockQuantity(3.0, "pieces", "kg", tomatoes)!!, 0.001)
        // 1.5 kg of tomatoes = 9 pieces
        assertEquals(9.0, BudgetRepository.convertStockQuantity(1.5, "kg", "piece", tomatoes)!!, 0.001)

        val sugar = FoodCatalogItem(
            id = "sugar",
            name = "Sugar",
            stockUnit = "kg",
            portionUnit = "g",
            portionsPerStockUnit = 1000.0,
            defaultCostPerPortion = 0.035,
        )
        // 500 grams of sugar = 0.5 kg
        assertEquals(0.5, BudgetRepository.convertStockQuantity(500.0, "g", "kg", sugar)!!, 0.001)
        assertEquals(500.0, BudgetRepository.convertStockQuantity(0.5, "kg", "g", sugar)!!, 0.001)

        val eggs = FoodCatalogItem(
            id = "eggs",
            name = "Eggs",
            stockUnit = "carton",
            portionUnit = "egg",
            portionsPerStockUnit = 30.0,
            defaultCostPerPortion = 5.5,
        )
        // 15 eggs = 0.5 carton
        assertEquals(0.5, BudgetRepository.convertStockQuantity(15.0, "egg", "carton", eggs)!!, 0.001)
        assertEquals(30.0, BudgetRepository.convertStockQuantity(1.0, "carton", "eggs", eggs)!!, 0.001)

        val bread = FoodCatalogItem(
            id = "bread",
            name = "Baladi Bread",
            stockUnit = "bag",
            portionUnit = "loaf",
            portionsPerStockUnit = 5.0,
            defaultCostPerPortion = 5.0,
        )
        // 10 loaves = 2 bags
        assertEquals(2.0, BudgetRepository.convertStockQuantity(10.0, "loaves", "bag", bread)!!, 0.001)
        assertEquals(5.0, BudgetRepository.convertStockQuantity(1.0, "bag", "loaf", bread)!!, 0.001)
    }

    @Test
    fun unitMatchingHandlesPluralsAndContainers() {
        assertTrue(BudgetRepository.isUnitEquivalent("piece", "pieces"))
        assertTrue(BudgetRepository.isUnitEquivalent("egg", "eggs"))
        assertTrue(BudgetRepository.isUnitEquivalent("loaf", "loaves"))
        assertTrue(BudgetRepository.isUnitEquivalent("cup", "cups"))
        assertTrue(BudgetRepository.isUnitEquivalent("spoon", "spoons"))
        assertTrue(BudgetRepository.isUnitEquivalent("carton", "carton (30)"))
        assertTrue(BudgetRepository.isUnitEquivalent("bag (5)", "bag"))
        assertTrue(BudgetRepository.isUnitEquivalent("bottle (1L)", "bottle"))
        // Same dimension is not the same unit, so different units never compare equal:
        // amounts must be converted instead, e.g. 500 g is 0.5 kg.
        assertFalse(BudgetRepository.isUnitEquivalent("g", "kg"))
        assertFalse(BudgetRepository.isUnitEquivalent("kg", "g"))
        assertFalse(BudgetRepository.isUnitEquivalent("ml", "l"))
        assertEquals(0.5, BudgetRepository.convertStockQuantity(500.0, "g", "kg", null)!!, 0.001)
        assertEquals(500.0, BudgetRepository.convertStockQuantity(0.5, "kg", "g", null)!!, 0.001)
    }

    @Test
    fun moneyFormattingSupportsFractionsUnderTenPiasters() {
        assertEquals("0.035 EGP", BudgetMath.money(0.035))
        assertEquals("0.005 EGP", BudgetMath.money(0.005))
        assertEquals("0.5 EGP", BudgetMath.money(0.5))
        assertEquals("15 EGP", BudgetMath.money(15.0))
        assertEquals("15.5 EGP", BudgetMath.money(15.5))
        assertEquals("15.75 EGP", BudgetMath.money(15.75))
    }

    @Test
    fun foodCatalogJsonCodecAcceptsSnakeCaseAliases() {
        val raw = """
            {
              "items": [
                {
                  "name": "Tomatoes",
                  "purchase_unit": "kg",
                  "portion_unit": "piece",
                  "portions_per_purchase_unit": 6,
                  "purchase_price": 18,
                  "category": "VEGETABLE",
                  "meal_usage": "any"
                }
              ]
            }
        """.trimIndent()

        val item = FoodCatalogJsonCodec.decode(raw).getOrThrow().single()
        assertEquals("Tomatoes", item.name)
        assertEquals("kg", item.purchaseUnit)
        assertEquals("piece", item.portionUnit)
        assertEquals(6.0, item.portionsPerStockUnit, 0.001)
        assertEquals(18.0, item.purchasePrice!!, 0.001)
        assertEquals(3.0, item.defaultCostPerPortion, 0.001)
        assertEquals("any", item.mealUsage)
    }

    @Test
    fun todayFoodRemainingCannotExceedFoodRemainingThisMonth() {
        val settings = com.budgetmeals.app.data.BudgetSettings(
            monthlyFoodBudget = 1000.0,
        )
        val snapshot = AppSnapshot(
            settings = settings,
            categories = listOf(foodCategory),
            expenses = listOf(
                Expense(categoryId = "food", amount = 980.0, date = today.minusDays(1)),
            ),
        )
        assertEquals(20.0, snapshot.foodRemainingThisMonth, 0.001)
        assertEquals(20.0, snapshot.todayFoodRemaining, 0.001)
    }

    @Test
    fun shoppingItemPriceKnownDistinction() {
        val freeItem = com.budgetmeals.app.data.ShoppingItem(
            name = "Salt packet",
            estimatedPrice = 0.0,
            priceKnown = true,
        )
        val unknownPriceItem = com.budgetmeals.app.data.ShoppingItem(
            name = "Fresh fish",
            estimatedPrice = 0.0,
            priceKnown = false,
        )
        assertTrue(freeItem.priceKnown)
        assertFalse(unknownPriceItem.priceKnown)
    }

    @Test
    fun recurringExpenseGeneratedForCurrentMonthWhenMarkedRecurringInPreviousMonth() {
        val testToday = LocalDate.of(2026, 10, 5)
        val lastMonthDate = testToday.minusMonths(1).withDayOfMonth(5)
        val previousExpense = Expense(
            id = "prev-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = lastMonthDate,
            description = "Internet home VDSL",
            isRecurring = true,
            recurringFrequency = "MONTHLY",
        )

        val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousExpense),
            today = testToday,
        )

        assertEquals(1, toGenerate.size)
        val generated = toGenerate.first()
        assertEquals("internet-cat", generated.categoryId)
        assertEquals(350.0, generated.amount, 0.001)
        assertEquals("Internet home VDSL", generated.description)
        assertTrue(generated.isRecurring)
        assertEquals(testToday.year, generated.date.year)
        assertEquals(testToday.month, generated.date.month)
        assertEquals(minOf(5, testToday.lengthOfMonth()), generated.date.dayOfMonth)
    }

    @Test
    fun recurringExpenseIsNotGeneratedBeforeDueDay() {
        val previousExpense = Expense(
            id = "prev-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = LocalDate.of(2026, 9, 5),
            description = "Internet home VDSL",
            isRecurring = true,
            recurringFrequency = "MONTHLY",
        )
        assertTrue(BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousExpense),
            today = LocalDate.of(2026, 10, 2),
        ).isEmpty())
    }

    @Test
    fun recurringExpenseNotDuplicatedIfAlreadyPresentInCurrentMonth() {
        val lastMonthDate = LocalDate.now().minusMonths(1).withDayOfMonth(5)
        val currentMonthDate = LocalDate.now().withDayOfMonth(5)
        val previousExpense = Expense(
            id = "prev-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = lastMonthDate,
            description = "Internet home VDSL",
            isRecurring = true,
        )
        val existingThisMonth = Expense(
            id = "current-internet",
            categoryId = "internet-cat",
            amount = 350.0,
            date = currentMonthDate,
            description = "Internet home VDSL",
            isRecurring = true,
        )

        val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousExpense, existingThisMonth),
            today = currentMonthDate,
        )

        assertTrue("Should not duplicate existing recurring expense", toGenerate.isEmpty())
    }

    @Test
    fun nonRecurringExpensesFromPreviousMonthAreNotGenerated() {
        val lastMonthDate = LocalDate.now().minusMonths(1).withDayOfMonth(10)
        val previousNonRecurring = Expense(
            id = "prev-dinner",
            categoryId = "food",
            amount = 120.0,
            date = lastMonthDate,
            description = "Restaurant dinner",
            isRecurring = false,
        )

        val toGenerate = BudgetMath.computeRecurringExpensesToGenerate(
            existingExpenses = listOf(previousNonRecurring),
            today = LocalDate.now(),
        )

        assertTrue(toGenerate.isEmpty())
    }

    @Test
    fun suggestedMealsPrioritizesInStockAndChecksExtraPurchases() {
        val eggsCatalog = FoodCatalogItem(
            id = "eggs",
            name = "Eggs",
            purchaseUnit = "carton",
            purchasePrice = 120.0,
            portionUnit = "piece",
            portionsPerStockUnit = 30.0,
            defaultCostPerPortion = 4.0,
            priceKnown = true,
            conversionKnown = true,
        )
        val chickenCatalog = FoodCatalogItem(
            id = "chicken",
            name = "Chicken",
            purchaseUnit = "kg",
            purchasePrice = 160.0,
            portionUnit = "portion",
            portionsPerStockUnit = 4.0,
            defaultCostPerPortion = 40.0,
            priceKnown = true,
            conversionKnown = true,
        )
        // Eggs are in stock
        val eggStock = StockItem(
            id = "egg-stock",
            catalogId = "eggs",
            name = "Eggs",
            totalQuantity = 10.0,
            unit = "piece",
            totalPrice = 40.0,
            purchaseDate = today,
        )
        val omelet = MealTemplate(
            id = "omelet",
            name = "Omelet",
            mealType = MealType.BREAKFAST,
            cost = 12.0,
            components = listOf(
                MealComponent(name = "Eggs", catalogId = "eggs", quantity = 3.0, unit = "piece", costPerUnit = 4.0, useStock = true)
            ),
        )
        val chickenMeal = MealTemplate(
            id = "chicken-meal",
            name = "Chicken Meal",
            mealType = MealType.LUNCH,
            cost = 40.0,
            components = listOf(
                MealComponent(name = "Chicken", catalogId = "chicken", quantity = 1.0, unit = "portion", costPerUnit = 40.0, useStock = true)
            ),
        )

        // Today remaining is 20 EGP. ChickenMeal costs 40 (extra 40 > 20), Omelet costs 12 but extra purchases = 0 <= 20.
        val snapshot = AppSnapshot(
            foodCatalog = listOf(eggsCatalog, chickenCatalog),
            stock = listOf(eggStock),
            templates = listOf(omelet, chickenMeal),
            settings = com.budgetmeals.app.data.BudgetSettings(dailyFoodBudget = 20.0),
        )

        assertEquals(0.0, BudgetMath.extraPurchasesNeeded(snapshot, omelet), 0.001)
        assertEquals(40.0, BudgetMath.extraPurchasesNeeded(snapshot, chickenMeal), 0.001)

        val suggestions = BudgetMath.suggestedMeals(snapshot)
        assertTrue(suggestions.any { it.id == "omelet" })
        assertFalse(suggestions.any { it.id == "chicken-meal" })
    }

    @Test
    fun fifoCatalogCostPerPortionPrefersEarliestBatch() {
        val catalog = FoodCatalogItem(
            id = "rice",
            name = "Rice",
            purchaseUnit = "kg",
            portionUnit = "portion",
            portionsPerStockUnit = 5.0,
            defaultCostPerPortion = 6.0,
            priceKnown = true,
            conversionKnown = true,
        )
        // Earlier batch is MORE expensive (8 EGP/portion), newer batch is cheaper (4 EGP/portion)
        val earlierBatch = StockItem(
            id = "rice-early",
            catalogId = "rice",
            name = "Rice",
            totalQuantity = 5.0,
            unit = "portion",
            totalPrice = 40.0,
            purchaseDate = today.minusDays(5),
            expiryDate = today.plusDays(10),
        )
        val newerBatch = StockItem(
            id = "rice-new",
            catalogId = "rice",
            name = "Rice",
            totalQuantity = 5.0,
            unit = "portion",
            totalPrice = 20.0,
            purchaseDate = today,
            expiryDate = today.plusDays(30),
        )
        val snapshot = AppSnapshot(
            foodCatalog = listOf(catalog),
            stock = listOf(newerBatch, earlierBatch),
        )

        // FIFO must pick earlierBatch (8.0 EGP), not the cheapest batch (4.0 EGP)
        val cost = BudgetMath.catalogCostPerPortion(snapshot, catalog)
        assertEquals(8.0, cost, 0.001)
    }

    @Test
    fun mealCostEstimateIncompleteWhenPriceIsUnknown() {
        val knownCatalog = FoodCatalogItem(
            id = "known",
            name = "Known Item",
            defaultCostPerPortion = 10.0,
            priceKnown = true,
        )
        val unknownCatalog = FoodCatalogItem(
            id = "unknown",
            name = "Unknown Item",
            defaultCostPerPortion = 0.0,
            priceKnown = false,
        )
        val completeTemplate = MealTemplate(
            id = "complete-meal",
            name = "Complete Meal",
            mealType = MealType.LUNCH,
            cost = 10.0,
            components = listOf(
                MealComponent(name = "Known", catalogId = "known", quantity = 1.0, costPerUnit = 10.0)
            ),
        )
        val incompleteTemplate = MealTemplate(
            id = "incomplete-meal",
            name = "Incomplete Meal",
            mealType = MealType.LUNCH,
            cost = 10.0,
            components = listOf(
                MealComponent(name = "Known", catalogId = "known", quantity = 1.0, costPerUnit = 10.0),
                MealComponent(name = "Unknown", catalogId = "unknown", quantity = 1.0, costPerUnit = 0.0)
            ),
        )
        val snapshot = AppSnapshot(
            foodCatalog = listOf(knownCatalog, unknownCatalog),
            templates = listOf(completeTemplate, incompleteTemplate),
        )

        assertFalse(BudgetMath.isMealCostIncomplete(snapshot, completeTemplate))
        assertTrue(BudgetMath.isMealCostIncomplete(snapshot, incompleteTemplate))
    }

    @Test
    fun plannedMealsForDateFiltersClearedMeals() {
        val activeMeal = DailyMealPlan(
            id = "plan-lunch",
            date = today,
            mealType = MealType.LUNCH,
            templateId = "template-pasta",
            name = "Pasta",
            cost = 30.0,
            foods = "Pasta",
            isCleared = false,
        )
        val clearedMeal = DailyMealPlan(
            id = "plan-snack",
            date = today,
            mealType = MealType.SNACK,
            templateId = null,
            name = "Empty",
            cost = 0.0,
            foods = "",
            isCleared = true,
        )
        val snapshot = AppSnapshot(dayPlans = listOf(activeMeal, clearedMeal))

        val planned = BudgetMath.plannedMealsForDate(snapshot, today)
        assertTrue(planned.containsKey(MealType.LUNCH))
        assertFalse("Cleared meals must not be present in planned meals map", planned.containsKey(MealType.SNACK))
    }

    @Test
    fun buildBalancedWeekProducesVarietyAcrossDays() {
        val lunch1 = MealTemplate(id = "l1", name = "Lentils", mealType = MealType.LUNCH, cost = 15.0, isRecurring = true)
        val lunch2 = MealTemplate(id = "l2", name = "Ful Mudammas", mealType = MealType.LUNCH, cost = 16.0, isRecurring = true)
        val lunch3 = MealTemplate(id = "l3", name = "Vegetable Tagine", mealType = MealType.LUNCH, cost = 18.0, isRecurring = true)
        val snapshot = AppSnapshot(templates = listOf(lunch1, lunch2, lunch3))

        val week = buildBalancedWeek(snapshot, BudgetMath.currentWeekStart(today))
        assertEquals(7, week.size)

        val lunchNames = week.mapNotNull { it.meals[MealType.LUNCH]?.name }
        val distinctLunches = lunchNames.distinct()
        assertTrue("Week plan should include varied lunch options, found: $distinctLunches", distinctLunches.size > 1)
    }

    @Test
    fun singleMealBatchIsImmediatelyConsumedOnPurchaseDay() {
        val item = StockItem(
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

    private fun cheeseOrChipsHasTiered(item: FoodCatalogItem): Boolean = item.isTieredSizes
}
