package com.budgetmeals.app.ui

import com.budgetmeals.app.data.FoodCatalogItem
import com.budgetmeals.app.data.FoodMeasurementCodec
import com.budgetmeals.app.data.FoodMeasurementType
import com.budgetmeals.app.data.FoodPriceOption
import com.budgetmeals.app.data.TieredSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodItemFormStateTest {
    @Test
    fun optionalPriceMayBeUnknownButMalformedPriceCannotBeSavedAsFree() {
        val state = FoodItemFormState(null).apply { name = "Rice" }
        assertTrue(state.isValid)
        state.defaultCost = "."
        assertFalse(state.isValid)
        state.defaultCost = "0"
        assertTrue(state.isValid)
        assertTrue(state.buildCatalogItem(null).priceKnown)
        state.purchasePrice = "9".repeat(400)
        assertFalse(state.isValid)
    }

    @Test
    fun editingNameKeepsPurchaseUnitAndPriceOptions() {
        val options = listOf(FoodPriceOption("Half kilo", 25.0, 0.5, "kg"))
        val food = FoodCatalogItem(
            name = "Rice", stockUnit = "g", portionUnit = "g", purchaseUnit = "kg",
            purchasePrice = 50.0, purchaseQuantity = 1.0, priceOptions = options,
        )
        val state = FoodItemFormState(food).apply { name = "White rice" }
        val saved = state.buildCatalogItem(food.id)

        assertEquals("kg", saved.purchaseUnit)
        assertEquals(options, saved.priceOptions)
    }

    @Test
    fun unrelatedEditKeepsOptionsOnlyCatalogPriceKnown() {
        val food = FoodCatalogItem(
            name = "Snack", defaultCostPerPortion = 0.0, purchasePrice = null,
            priceOptions = listOf(FoodPriceOption("Family", 30.0)), priceKnown = true,
        )
        val state = FoodItemFormState(food).apply { name = "Family snack" }
        val saved = state.buildCatalogItem(food.id)

        assertTrue(saved.priceKnown)
        assertTrue(saved.hasKnownPrice)
        assertEquals(food.priceOptions, saved.priceOptions)
    }

    @Test
    fun unrelatedEditKeepsCustomTierPriceKnownWithoutDefaultPrice() {
        val sizes = listOf(TieredSize("Regular", 12.0), TieredSize("Family", 30.0))
        val food = FoodCatalogItem(
            name = "Snack", defaultCostPerPortion = 0.0, priceKnown = true,
            notes = FoodMeasurementCodec.encodeNotes("", FoodMeasurementType.TieredSizes(sizes)),
        )
        val state = FoodItemFormState(food).apply { name = "New snack name" }
        val saved = state.buildCatalogItem(food.id)

        assertTrue(saved.priceKnown)
        assertEquals(sizes, (saved.measurement as FoodMeasurementType.TieredSizes).sizes)
    }

    @Test
    fun fractionalSpoonSizeDoesNotRoundUpToOneGram() {
        val state = FoodItemFormState(null).apply {
            name = "Spice"
            measurementMode = MeasurementMode.SPOONS
            gramsPerSpoon = "0.5"
            portionsPerStockUnit = "0.5"
        }
        assertTrue(state.isValid)
        val saved = state.buildCatalogItem(null)

        assertEquals(0.5, (saved.measurement as FoodMeasurementType.SpoonsToGrams).gramsPerSpoon, 0.0)
        assertEquals(0.5, saved.portionsPerStockUnit, 0.0)
    }

    @Test
    fun unrelatedEditKeepsCustomTierNamesWeightsAndDefaultPrice() {
        val sizes = listOf(TieredSize("Regular", 12.0, 50.0), TieredSize("Family", 30.0, 200.0))
        val food = FoodCatalogItem(
            name = "Snack", stockUnit = "packet", portionUnit = "packet", defaultCostPerPortion = 12.0,
            notes = FoodMeasurementCodec.encodeNotes("", FoodMeasurementType.TieredSizes(sizes)),
        )
        val state = FoodItemFormState(food).apply { notes = "New note" }
        val saved = state.buildCatalogItem(food.id)

        assertEquals(sizes, (saved.measurement as FoodMeasurementType.TieredSizes).sizes)
        assertEquals(12.0, saved.defaultCostPerPortion, 0.0)
        assertEquals("packet", saved.stockUnit)
    }
}
