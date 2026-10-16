package com.budgetmeals.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealDataCodecTest {
    @Test
    fun invalidNumericValuesDoNotPoisonMealCalculations() {
        val components = MealDataCodec.decodeComponents("""[{"name":"Rice","quantity":"NaN","costPerUnit":"Infinity"}]""")

        assertEquals(0.0, components.single().estimatedCost, 0.0)
        assertTrue(components.single().quantity.isFinite())
        assertTrue(components.single().costPerUnit.isFinite())
    }

    @Test
    fun duplicateComponentIdsRemainDistinctForStockCoverage() {
        val components = MealDataCodec.decodeComponents("""[{"id":"same","name":"Rice"},{"id":"same","name":"Rice"}]""")

        assertEquals(2, components.map { it.id }.distinct().size)
        assertEquals(components, MealDataCodec.decodeComponents(MealDataCodec.encodeComponents(components)))
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
}
