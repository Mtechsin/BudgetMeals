package com.budgetmeals.app.state

import com.budgetmeals.app.data.AppSnapshot
import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodMeasurementCodec
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.MealComponent
import com.budgetmeals.app.data.MealTemplate
import com.budgetmeals.app.data.MealType
import com.budgetmeals.app.data.StockItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockCatalogAvailabilityTest {
    private val today = LocalDate.of(2026, 10, 8)

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
            today = today,
            foodCatalog = listOf(tomatoes),
            stock = listOf(
                StockItem(
                    purchaseDate = today,
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
            today = today,
            foodCatalog = listOf(eggs),
            stock = listOf(
                StockItem(
                    purchaseDate = today,
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
            today = today,
            foodCatalog = listOf(jam, cheese),
            stock = listOf(
                StockItem(
                    purchaseDate = today,
                    id = "jam",
                    name = "Jam",
                    unit = "g",
                    totalQuantity = 650.0,
                    totalPrice = 55.0,
                    catalogId = jam.id,
                ),
                StockItem(
                    purchaseDate = today,
                    id = "cheese",
                    name = "Cheese",
                    unit = "g",
                    totalQuantity = 250.0,
                    totalPrice = 25.0,
                    catalogId = cheese.id,
                ),
            ),
        )

        // 650 g at 20 g per spoon is 32.5 spoons, never 12,350.
        assertEquals(32.5, snapshot.availabilityFor(jam).portions, 0.001)
        // 250 g at 25 spoons per 500 g tub is 12.5 spoons, never 6,250.
        assertEquals(12.5, snapshot.availabilityFor(cheese).portions, 0.001)
    }

    @Test
    fun unconvertibleStockIsFlaggedInsteadOfCounted() {
        val jam = FoodCatalogItem(
            id = "catalog_jam",
            name = "Jam",
            stockUnit = "jar (380g)",
            portionUnit = "spoon",
            portionsPerStockUnit = 19.0,
            defaultCostPerPortion = 3.2,
        )
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(jam),
            stock = listOf(
                StockItem(
                    purchaseDate = today,
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
            purchaseDate = today,
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
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(cucumber),
        )
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
            today = today,
            foodCatalog = listOf(catalog),
            stock = listOf(newerBatch, earlierBatch),
        )

        // FIFO must pick earlierBatch (8.0 EGP), not the cheapest batch (4.0 EGP)
        val cost = BudgetMath.catalogCostPerPortion(snapshot, catalog)
        assertEquals(8.0, cost, 0.001)
    }

    @Test
    fun gramRequirementsUseGramStockWhenCatalogPortionIsASpoon() {
        val catalog = spoonCatalog()
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(catalog),
            stock = listOf(
                StockItem(
                    purchaseDate = today,
                    id = "paste-20g",
                    catalogId = catalog.id,
                    name = catalog.name,
                    unit = "g",
                    totalQuantity = 20.0,
                    totalPrice = 10.0,
                ),
            ),
        )
        val meal = template(
            MealComponent(
                id = "paste-50g",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 50.0,
                unit = "g",
                costPerUnit = 0.5,
            ),
        )

        assertEquals(15.0, BudgetMath.extraPurchasesNeeded(snapshot, meal), 0.0001)
        assertEquals(0.0, BudgetMath.inStockCompleteness(snapshot, meal), 0.0001)
    }

    @Test
    fun duplicateComponentsShareTheSameAvailableStock() {
        val catalog = spoonCatalog()
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(catalog),
            stock = listOf(
                StockItem(
                    purchaseDate = today,
                    id = "paste-one-spoon",
                    catalogId = catalog.id,
                    name = catalog.name,
                    unit = "g",
                    totalQuantity = 10.0,
                    totalPrice = 5.0,
                ),
            ),
        )
        val meal = template(
            MealComponent(
                id = "paste-spoon-a",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 1.0,
                unit = "spoon",
                costPerUnit = 5.0,
            ),
            MealComponent(
                id = "paste-spoon-b",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 1.0,
                unit = "spoon",
                costPerUnit = 5.0,
            ),
        )

        assertEquals(5.0, BudgetMath.extraPurchasesNeeded(snapshot, meal), 0.0001)
        assertEquals(0.5, BudgetMath.inStockCompleteness(snapshot, meal), 0.0001)
    }

    @Test
    fun stockWithUnsupportedConversionDoesNotCoverAComponent() {
        val catalog = FoodCatalogItem(
            id = "unconvertible-catalog",
            name = "Unknown unit flour",
            stockUnit = "crate",
            portionUnit = "g",
            portionsPerStockUnit = 1.0,
            defaultCostPerPortion = 3.0,
            conversionKnown = false,
        )
        val snapshot = AppSnapshot(
            today = today,
            foodCatalog = listOf(catalog),
            stock = listOf(
                StockItem(
                    purchaseDate = today,
                    id = "unconvertible-stock",
                    catalogId = catalog.id,
                    name = catalog.name,
                    unit = "crate",
                    totalQuantity = 100.0,
                    totalPrice = 50.0,
                ),
            ),
        )
        val meal = template(
            MealComponent(
                id = "unconvertible-component",
                catalogId = catalog.id,
                name = catalog.name,
                quantity = 1.0,
                unit = "g",
                costPerUnit = 3.0,
            ),
        )

        assertEquals(0.0, BudgetMath.inStockCompleteness(snapshot, meal), 0.0001)
        assertTrue(BudgetMath.extraPurchasesNeeded(snapshot, meal) > 0.0)
    }

    private fun spoonCatalog() = FoodCatalogItem(
        id = "spoon-catalog",
        name = "Test paste",
        stockUnit = "jar (100g)",
        portionUnit = "spoon",
        portionsPerStockUnit = 10.0,
        defaultCostPerPortion = 5.0,
        purchasePrice = 50.0,
        purchaseQuantity = 1.0,
        purchaseUnit = "jar (100g)",
        notes = FoodMeasurementCodec.encodeNotes(
            userNotes = "",
            measurement = FoodMeasurementType.SpoonsToGrams(10.0),
        ),
    )

    private fun template(vararg components: MealComponent) = MealTemplate(
        id = "quantity-regression-meal",
        name = "Quantity regression meal",
        mealType = MealType.LUNCH,
        cost = components.sumOf { it.estimatedCost },
        components = components.toList(),
    )
}
