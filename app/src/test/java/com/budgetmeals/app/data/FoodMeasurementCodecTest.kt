package com.budgetmeals.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMeasurementCodecTest {
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

        assertTrue(chips.isTieredSizes)
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
    fun encodedMeasurementAndNotesRoundTrip() {
        val spoonMeasurement = FoodMeasurementType.SpoonsToGrams(gramsPerSpoon = 12.5, spoonUnitName = "tablespoon")
        val spoonNotes = FoodMeasurementCodec.encodeNotes("  tahini  ", spoonMeasurement)
        assertEquals(spoonMeasurement, FoodMeasurementCodec.decodeMeasurement(spoonNotes))
        assertEquals("tahini", FoodMeasurementCodec.extractUserNotes(spoonNotes))

        val tieredMeasurement = FoodMeasurementType.TieredSizes(
            listOf(
                TieredSize("Small", 10.0, gramsEquivalent = 25.0),
                TieredSize("Large", 20.0),
            ),
        )
        val tieredNotes = FoodMeasurementCodec.encodeNotes("", tieredMeasurement)
        assertEquals(tieredMeasurement, FoodMeasurementCodec.decodeMeasurement(tieredNotes))
        assertEquals("", FoodMeasurementCodec.extractUserNotes(tieredNotes))
    }
}
