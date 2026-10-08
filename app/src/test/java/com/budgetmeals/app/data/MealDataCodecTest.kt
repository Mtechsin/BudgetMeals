package com.budgetmeals.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MealDataCodecTest {
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
